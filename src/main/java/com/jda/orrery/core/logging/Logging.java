package com.jda.orrery.core.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Centralized logging configuration. */
public class Logging {
    private static final String LOGGER_NAME = "com.jda.orrery";
    private static final Logger logger;

    static {
        // Configure root logger for package
        logger = Logger.getLogger(LOGGER_NAME);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);

        // Console handler with custom formatting
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(
                new SimpleFormatter() {
                    @Override
                    public String format(java.util.logging.LogRecord record) {
                        return String.format(
                                "[%s] %s: %s%n",
                                record.getLevel().getName(),
                                record.getSourceClassName()
                                        .substring(
                                                record.getSourceClassName().lastIndexOf('.') + 1),
                                record.getMessage());
                    }
                });

        logger.addHandler(consoleHandler);
    }

    /** Get a logger for a specific class */
    public static Logger logger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }

    /** Set global logging level */
    public static void setLevel(Level level) {
        logger.setLevel(level);
        for (var handler : logger.getHandlers()) {
            handler.setLevel(level);
        }
    }
}
