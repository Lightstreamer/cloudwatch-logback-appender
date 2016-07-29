package com.lightstreamer.cloudwatch.logback.appender;

import java.util.Map;
import java.util.Set;

public class Event {
    public String message;
    public String level;
    public String threadName;
    public String loggerName;
    public ThrowableProxy throwableProxy;
    public StackTraceElement[] callerData;
    public Set<String> markers;
    public Map<String, String> mdcPropertyMap;
    public String timeStamp;
}
