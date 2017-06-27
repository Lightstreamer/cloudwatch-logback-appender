package com.lightstreamer.cloudwatch.logback.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class AwsLogsJsonAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private final ObjectMapper om;

    private AWSLogsClient awsLogsClient;

    private String awsRegionName;

    private String createLogGroup = "false";

    private String logGroupName = "test-log-group";

    private String logStreamName;

    private int maxLogSize = 1024;

    private long logPollTimeMillis = 3000;

    private WorkerThread workerThread;

    private ArrayBlockingQueue<Event> logEvents;

    public AwsLogsJsonAppender() {
        om = new ObjectMapper().findAndRegisterModules();
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        om.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        om.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public void start() {
        super.start();
        addInfo(getClass().getSimpleName() + " start " + getName());
        if (logStreamName == null) {
            try {
                logStreamName = LogbackUtils.dateFormatThreadLocal.get().format(System.currentTimeMillis()) + ' ' + InetAddress.getLocalHost().getCanonicalHostName();
            } catch (Exception e) {
                logStreamName = LogbackUtils.dateFormatThreadLocal.get().format(System.currentTimeMillis());
            }
            logStreamName = logStreamName.replace(':', '.');
        }

        awsLogsClient = new AWSLogsClient();
        if (awsRegionName != null) {
            awsLogsClient.setRegion(Region.getRegion(Regions.fromName(awsRegionName)));
        }

        logEvents = new ArrayBlockingQueue<>(maxLogSize * 2);
    }

    @Override
    public void stop() {
        super.stop();
        addInfo(getClass().getSimpleName() + " stopping " + getName());

        // stop and wait worker
        if (workerThread != null) {
            try {
                workerThread.join(logPollTimeMillis);
            } catch (InterruptedException e) {
            }
            workerThread.interrupt();
            workerThread = null;
        }
        addInfo(getClass().getSimpleName() + " stopped " + getName());
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        try {
            logEvents.offer(LogbackUtils.iLoggingEvent2Map(eventObject));

            // start worker
            if (workerThread == null || !workerThread.isAlive()) {
                synchronized (this) {
                    if (workerThread == null || !workerThread.isAlive()) {
                        workerThread = new WorkerThread(getClass().getSimpleName() + ' ' + getName());
                        workerThread.start();
                    }
                }
            }
        } catch (Exception e) {
            addError("Error while sending a message", e);
        }
    }

    public void setAwsRegionName(String awsRegionName) {
        this.awsRegionName = awsRegionName;
    }

    public void setCreateLogGroup(String createLogGroup) {
        this.createLogGroup = createLogGroup;
    }

    public void setLogGroupName(String logGroupName) {
        this.logGroupName = logGroupName;
    }

    public void setLogStreamName(String logStreamName) {
        this.logStreamName = logStreamName;
    }

    public void setMaxLogSize(String maxLogSize) {
        this.maxLogSize = Integer.valueOf(maxLogSize);
    }

    public void setLogPollTimeMillis(String logPollTimeMillis) {
        this.logPollTimeMillis = Long.valueOf(logPollTimeMillis);
    }

    /**
     * Sends event to CloudWatch
     */
    private final class WorkerThread extends Thread {

        private final List<InputLogEvent> logs = new ArrayList<>(maxLogSize);

        WorkerThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            assert isStarted();
            try {
                addInfo(getClass().getSimpleName() + " starting thread " + workerThread.getName());

                if (Boolean.parseBoolean(createLogGroup)) {
                    CreateLogGroupRequest createLogGroupRequest = new CreateLogGroupRequest(logGroupName);
                    try {
                        awsLogsClient.createLogGroup(createLogGroupRequest);
                    } catch (ResourceAlreadyExistsException e) {
                        addInfo("Log group " + logGroupName + "already exists");
                    }
                }

                CreateLogStreamRequest createLogStreamRequest = new CreateLogStreamRequest(logGroupName, logStreamName);
                try {
                    awsLogsClient.createLogStream(createLogStreamRequest);
                } catch (ResourceAlreadyExistsException e) {
                    addInfo("Log stream " + logStreamName + "already exists", e);
                }

                // Last log submission time
                long lastSubmit = System.currentTimeMillis();
                // cloudwatch sequence token
                String lastSequenceToken = null;
                while (isStarted()) {
                    long pollRemainTime = lastSubmit + logPollTimeMillis - System.currentTimeMillis();
                    while (pollRemainTime > 0 && logs.size() < maxLogSize) {
                        try {
                            final Event event = logEvents.poll(pollRemainTime, TimeUnit.MILLISECONDS);
                            final long now = System.currentTimeMillis();
                            if (event != null) {
                                // create event object for submission
                                final InputLogEvent inputLogEvent = new InputLogEvent();
                                inputLogEvent.setMessage(om.writeValueAsString(event));
                                inputLogEvent.setTimestamp(now);
                                logs.add(inputLogEvent);
                            }
                            pollRemainTime = lastSubmit - now + logPollTimeMillis;
                        } catch (InterruptedException e) {
                            addInfo(Thread.currentThread().getName() + " interrupted");
                            pollRemainTime = -1;
                        } catch (JsonProcessingException e) {
                            addError("Unable to serialize message", e);
                        }
                    }

                    if (!logs.isEmpty()) {
                        // submit events
                        try {
                            PutLogEventsRequest putLogEventsRequest = new PutLogEventsRequest(logGroupName, logStreamName, logs);
                            putLogEventsRequest.setSequenceToken(lastSequenceToken);

                            PutLogEventsResult putLogEventsResult = awsLogsClient.putLogEvents(putLogEventsRequest);
                            lastSequenceToken = putLogEventsResult.getNextSequenceToken();
                            logs.clear();
                        } catch (InvalidSequenceTokenException e) {
                            lastSequenceToken = e.getExpectedSequenceToken();
                        }
                    }
                    // always update polling time
                    lastSubmit = System.currentTimeMillis();
                }
            } catch (Exception e) {
                addError("Cloudwatch appender thread error", e);
            } finally {
                addInfo(getClass().getSimpleName() + " stopped thread " + Thread.currentThread().getName());
            }
        }
    }
}
