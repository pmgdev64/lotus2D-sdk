package vn.pmgteam.lotus2d.core.util;

public class MemoryGuardian {
    public static void checkAndForceGC() {
        if (Runtime.getRuntime().freeMemory() < 100 * 1024 * 1024) { // Dưới 100MB
            System.gc(); // Gợi ý JVM dọn dẹp ngay lập tức
            Logger.getLogger("Lotus2D-SDK").info("Low memory! System.gc() invoked.");
        }
    }
}