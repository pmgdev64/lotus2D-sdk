package vn.pmgteam.lotus2d.core.util;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class LoggerUtil { // Đổi tên để tránh trùng với org.slf4j.Logger

    private final Logger slf4jLogger;
    private final String name;

    public LoggerUtil(String name) {
        this.name = name;
        this.slf4jLogger = LoggerFactory.getLogger(name);
    }

    public static LoggerUtil getLogger(String name) {
        return new LoggerUtil(name);
    }

    public void info(String msg, Object... args) {
        slf4jLogger.info(msg, args);
    }

    public void warn(String msg, Object... args) {
        slf4jLogger.warn(msg, args);
    }

    public void error(String msg, Object... args) {
        slf4jLogger.error(msg, args);
    }

    public void fatal(String msg, Object... args) {
        slf4jLogger.error("[FATAL] " + msg, args);
        // Đảm bảo log được ghi xong trước khi thoát
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        System.exit(1);
    }
}