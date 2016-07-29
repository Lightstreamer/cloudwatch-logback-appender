package com.lightstreamer.cloudwatch.logback.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import org.slf4j.Marker;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

final class LogbackUtils {

    static ThreadLocal<DateFormat> dateFormatThreadLocal = ThreadLocal.withInitial(() -> {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat;
    });

    static Event iLoggingEvent2Map(final ILoggingEvent eventObject) {
        final Event event = new Event();

        event.message = eventObject.getFormattedMessage();
        event.level = eventObject.getLevel().toString();
        event.threadName = eventObject.getThreadName();
        event.loggerName = eventObject.getLoggerName();
        event.throwableProxy = getThrowableProxyMap(eventObject.getThrowableProxy());
        event.markers = getMarkers(eventObject);
        event.timeStamp = dateFormatThreadLocal.get().format(eventObject.getTimeStamp());
        final Map<String, String> mdcPropertyMap = eventObject.getMDCPropertyMap();
        if (!mdcPropertyMap.isEmpty())
            event.mdcPropertyMap = new TreeMap<>(mdcPropertyMap);
        if (eventObject.hasCallerData())
            event.callerData = eventObject.getCallerData();

        return event;
    }

    private static ThrowableProxy getThrowableProxyMap(final IThrowableProxy iThrowableProxy) {
        if (iThrowableProxy == null) return null;
        final ThrowableProxy throwableProxy = new ThrowableProxy();
        throwableProxy.message = iThrowableProxy.getMessage();
        throwableProxy.className = iThrowableProxy.getClassName();
        throwableProxy.cause = getThrowableProxyMap(iThrowableProxy.getCause());
        final StackTraceElementProxy[] stackTraceElementProxyArray = iThrowableProxy.getStackTraceElementProxyArray();
        throwableProxy.stackTraceElements = new String[stackTraceElementProxyArray.length];
        int i = 0;
        for (final StackTraceElementProxy stackTraceElementProxy : stackTraceElementProxyArray) {
            throwableProxy.stackTraceElements[i] = stackTraceElementProxy.getStackTraceElement().toString();
            i++;
        }
        return throwableProxy;
    }

    private static Set<String> getMarkers(final ILoggingEvent eventObject) {
        final Marker marker = eventObject.getMarker();
        if (marker == null) {
            return null;
        }
        final Set<String> markers = new TreeSet<>();
        Iterator<?> i = marker.iterator();
        while (i.hasNext()) {
            markers.add(i.next().toString());
        }
        return markers;
    }

    private LogbackUtils() {
    }
}
