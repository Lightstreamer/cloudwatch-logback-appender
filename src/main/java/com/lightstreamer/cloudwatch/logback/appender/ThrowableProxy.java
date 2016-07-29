package com.lightstreamer.cloudwatch.logback.appender;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;

public class ThrowableProxy {
    public String message;
    public String className;
    public String[] stackTraceElements;
    public ThrowableProxy cause;
}
