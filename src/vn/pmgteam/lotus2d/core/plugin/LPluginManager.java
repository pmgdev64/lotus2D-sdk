package vn.pmgteam.lotus2d.core.plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LPluginManager {

    private final List<IPluginEffect> activePlugins = new ArrayList<>();

    public LPluginManager() {
        scanAndLoadPlugins();
    }
    
    public void scanAndLoadPlugins() {
        File folder = new File("plugins");
        if (!folder.exists()) folder.mkdir();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null) return;

        for (File file : files) {
            try (JarFile jar = new JarFile(file)) {
                URL[] urls = { file.toURI().toURL() };
                // Không đóng loader ngay trong try-with-resources nếu class cần dùng sau này
                URLClassLoader loader = new URLClassLoader(urls, this.getClass().getClassLoader());
                
                java.util.Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    
                    if (entryName.endsWith(".class")) {
                        String className = entryName.replace("/", ".").replace(".class", "");
                        try {
                            Class<?> cls = loader.loadClass(className);

                            if (cls.isAnnotationPresent(LotusPlugin.class)) {
                                if (IPluginEffect.class.isAssignableFrom(cls)) {
                                    LotusPlugin anno = cls.getAnnotation(LotusPlugin.class);
                                    IPluginEffect effect = (IPluginEffect) cls.getDeclaredConstructor().newInstance();
                                    
                                    effect.onInit();
                                    activePlugins.add(effect);
                                    System.out.println("[Lotus2D] Loaded Plugin: " + anno.name() + " v" + anno.version());
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Exception e) {
                System.err.println("[Lỗi] Khong the nap plugin " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    // --- CÁC PHƯƠNG THỨC RENDER PHÂN TẦNG ---

    /**
     * Vẽ các plugin được đánh dấu là Background (isBackground = true)
     */
    public void renderBackgrounds(int width, int height) {
        for (IPluginEffect plugin : activePlugins) {
            LotusPlugin anno = plugin.getClass().getAnnotation(LotusPlugin.class);
            if (anno != null && anno.isBackground()) {
                // LƯU TRẠNG THÁI VÀ CHUẨN HÓA
                org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
                org.lwjgl.opengl.GL11.glPushMatrix();
                
                // Thiết lập hệ tọa độ Pixel cho Plugin dễ vẽ
                org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
                org.lwjgl.opengl.GL11.glLoadIdentity();
                org.lwjgl.opengl.GL11.glOrtho(0, width, height, 0, -1, 1);
                org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
                org.lwjgl.opengl.GL11.glLoadIdentity();

                // Tắt Texture/Depth để tránh lỗi nền trắng/mất hình
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
                
                // Gọi render của Plugin
                plugin.onRender(width, height);
                
                // KHÔI PHỤC TRẠNG THÁI
                org.lwjgl.opengl.GL11.glPopMatrix();
                org.lwjgl.opengl.GL11.glPopAttrib();
            }
        }
    }

    public void renderForegrounds(int width, int height) {
        for (IPluginEffect plugin : activePlugins) {
            LotusPlugin anno = plugin.getClass().getAnnotation(LotusPlugin.class);
            if (anno != null && !anno.isBackground()) {
                org.lwjgl.opengl.GL11.glPushMatrix();
                org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
                
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                
                plugin.onRender(width, height);
                
                org.lwjgl.opengl.GL11.glPopAttrib();
                org.lwjgl.opengl.GL11.glPopMatrix();
            }
        }
    }
    
 // Trong LPluginManager.java
    public boolean hasActiveBackground() {
        if (activePlugins.isEmpty()) return false;
        for (IPluginEffect plugin : activePlugins) {
            LotusPlugin anno = plugin.getClass().getAnnotation(LotusPlugin.class);
            if (anno != null && anno.isBackground()) return true;
        }
        return false;
    }

    public void updateAll(float deltaTime) {
        for (IPluginEffect plugin : activePlugins) {
            plugin.onUpdate(deltaTime);
        }
    }

    public List<IPluginEffect> getActivePlugins() {
        return activePlugins;
    }
    
    // Thêm hàm này vào LPluginManager để test nhanh
    public void addInternalPlugin(IPluginEffect effect) {
        if (effect.getClass().isAnnotationPresent(LotusPlugin.class)) {
            effect.onInit();
            this.getActivePlugins().add(effect);
        }
    }

    public java.util.Map<String, Object> getAllPluginStates() {
        java.util.Map<String, Object> allData = new java.util.HashMap<>();
        // Logic để tích hợp với autoSaveConfig() [2026-01-02]
        return allData;
    }
}