package com.example.javaparser;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * Helper to toggle global logging verbosity.
 */
public final class LogLevelController {
    private LogLevelController() {
    }

    /**
     * Switch the root logger between INFO and DEBUG.
     */
    public static void setVerbose(boolean verbose) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(verbose ? Level.DEBUG : Level.INFO);
    }
}
