package vn.pmgteam.lotus2d.core;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import vn.pmgteam.lotus2d.core.util.Logger;

public abstract class LModelBase {
    protected static final Logger logger = Logger.getLogger("Lotus2D-SDK");

    protected LModelBase parent;
    protected List<LModelBase> children = new ArrayList<>();
    protected String id;
    
    // Flag để "bắt thóp" các class con lười biếng
    private boolean isCleanupConfirmed = false;

	private File currentSwapDir;

    public LModelBase(String id, LModelBase parent) {
        validateCleanupReflection();
        this.id = id;
        this.parent = parent;
        if (parent != null) {
            parent.addChild(this);
        }
    }
    
    protected boolean shouldSwap() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        // Ngưỡng 80% RAM JVM
        return (double) usedMemory / runtime.maxMemory() > 0.8;
    }

    // Thay đổi sang protected
    protected File getSwapFile(String suffix) {
        if (currentSwapDir == null) {
            setSwapDirectory(); // Đảm bảo thư mục swap luôn tồn tại
        }
        return new File(currentSwapDir, id + "_" + suffix + ".tmp");
    }
    
    public void setSwapDirectory() {
        // Lấy đường dẫn từ System Property
        String swapPath = System.getProperty("vn.pmgteam.lotus2d.sdk.swapDirectory");
        
        File tempDir;
        if (swapPath != null && !swapPath.isEmpty()) {
            tempDir = new File(swapPath);
        } else {
            // Mặc định tạo thư mục .swap trong thư mục chạy chương trình
            tempDir = new File(System.getProperty("user.dir"), ".lotus_swap");
        }

        // Tạo thư mục nếu chưa tồn tại
        if (!tempDir.exists()) {
            if (tempDir.mkdirs()) {
                logger.info("Swap directory created at: {}", tempDir.getAbsolutePath());
            } else {
                // Nếu không tạo được thư mục quan trọng này, crash với cảnh báo đỏ
                logger.fatal("SWAP_INIT_FAILURE", "Cannot create swap directory at: {}", tempDir.getAbsolutePath());
            }
        }
        
        // Lưu trữ File object này vào một biến instance để các module khác sử dụng
        this.currentSwapDir = tempDir;
    }
    /**
     * Bước 1: Kiểm tra xem có ghi đè (Override) phương thức không
     */
    private void validateCleanupReflection() {
        try {
            Method m = this.getClass().getMethod("cleanup");
            if (m.getDeclaringClass().equals(LModelBase.class)) {
                logger.fatal("Security Violation: Class '{}' forgot to override cleanup()!", 
                             this.getClass().getSimpleName());
            }
        } catch (NoSuchMethodException e) {
            logger.fatal("Critical Error: cleanup() missing in '{}'", id);
        }
    }

    /**
     * Bước 2: Lớp con BẮT BUỘC gọi hàm này trong cleanup()
     */
    protected final void confirmCleanup() {
        this.isCleanupConfirmed = true;
    }

    /**
     * Phương thức 'máy chém' thực sự
     */
    public final void destroy() {
        this.isCleanupConfirmed = false; // Reset flag
        
        this.cleanup(); // Gọi logic của lớp con

        if (!isCleanupConfirmed) {
            logger.fatal("Resource Leak: Class '{}' has an empty cleanup()!", 
                         this.getClass().getSimpleName());
        } else {
            logger.info("Model '{}' and its children cleaned up successfully.", id);
        }

        // Hủy các con theo dây chuyền (Cascade cleanup)
        for (LModelBase child : children) {
            child.destroy();
        }
    }

    // Sửa lỗi undefined cho addChild
    public void addChild(LModelBase child) {
        if (child != null) {
            this.children.add(child);
        }
    }

    public <T extends LModelBase> T getInstance() {
        return (T) this;
    }

    public void markDirty() {
        logger.info("Module '{}' state changed. AutoSave triggered.", id);
    }

    public abstract void update(float deltaTime);
    // Trong LModelBase.java
    public abstract void render(java.awt.Graphics2D g2d);
    public abstract void cleanup();
}