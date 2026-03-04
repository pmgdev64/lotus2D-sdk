package vn.pmgteam.lotus2d.sdk.util;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private final PrintStream printStream = System.out;
    private final String name;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

 // Thay đổi trong file Logger.java
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";         // Chữ đỏ
    public static final String ANSI_RED_BOLD = "\u001B[1;31m";  // Chữ đỏ đậm (nên dùng cho FATAL)
    public static final String ANSI_YELLOW = "\u001B[33m";      // Chữ vàng
    public static final String ANSI_GREEN = "\u001B[32m";       // Chữ xanh lá
    public static final String ANSI_WHITE_ON_RED = "\u001B[41;37m"; // Nền đỏ chữ trắng cho FATAL

    public Logger(String name) {
        this.name = name;
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public void info(String msg, Object... args) {
        log(ANSI_GREEN, "INFO", msg, args);
    }

    public void warn(String msg, Object... args) {
        log(ANSI_YELLOW, "WARN", msg, args);
    }

    public void error(String msg, Object... args) {
        log(ANSI_RED, "ERROR", msg, args);
    }

    public void fatal(String msg, Object... args) {
        // Sử dụng màu nền đỏ để cảnh báo cực độ trước khi System.exit(1)
        log(ANSI_RED, "FATAL", msg, args);
        System.exit(1);
    }

    private void log(String color, String level, String msg, Object... args) {
        String time = dtf.format(LocalDateTime.now());
        String formattedMsg = msg;

        if (args != null && args.length > 0) {
            for (Object arg : args) {
                // Regex để thay thế đúng cặp dấu {}
                formattedMsg = formattedMsg.replaceFirst("\\{}", String.valueOf(arg));
            }
        }

        // Cấu trúc log: [Thời gian] [Màu][Cấp độ][Reset] [Tên Logger]: Tin nhắn
        printStream.printf("[%s] [%s%s%s/%s]: %s%n", 
            time, color, level, ANSI_RESET, name, formattedMsg);
    }
}