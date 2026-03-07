package vn.pmgteam.lotus2d.viewer;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.*;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import imgui.ImDrawList;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiViewport;
import imgui.extension.nodeditor.NodeEditor;
import imgui.extension.nodeditor.NodeEditorConfig;
import imgui.extension.nodeditor.NodeEditorContext;
import imgui.extension.nodeditor.flag.NodeEditorPinKind;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import imgui.type.ImLong;
import spout.JNISpout;
import vn.pmgteam.lotus2d.core.plugin.LPluginManager;
import vn.pmgteam.lotus2d.core.util.LoggerUtil;
import vn.pmgteam.lotus2d.framework.LModelCanvas;
import vn.pmgteam.lotus2d.plugins.test.StarfieldWarpPlugin;
import vn.pmgteam.lotus2d.viewer.gui.FontRenderer;
import vn.pmgteam.lotus2d.viewer.node.LNodeGraph;
import vn.pmgteam.lotus2d.viewer.util.tracking.LCameraTracker;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*; 
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.stb.STBImage.*;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

public class LViewer implements Runnable{
    private long window;
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private final Set<Long> iconifiedWindows = new HashSet<>();
    
    private final ImBoolean enableSpout = new ImBoolean(false);
    private boolean isSpoutInitialized = false;
    private long spoutPtr = 0; 
    private final String senderName = "Lotus2D_Viewer_MCP";
    
    private int width, height, fbWidth, fbHeight;
    private int menuLogoTextureId;
    private final ImBoolean showDebugWindow = new ImBoolean(true);

    private int fboId = 0;
    private int fboTextureId = 0;
    
    // Logic kéo cửa sổ nâng cao
    private double startMouseX, startMouseY;
    private boolean isDragging = false;
    
    private FontRenderer fontRenderer;
    
    private boolean isResizing = false;
    private int resizeType = 0; // 1: Ngang, 2: Dọc, 3: Cả hai (Góc)

    // Hiệu ứng Grid động
    private float gridOffset = 0.0f;
    private final float gridSpeed = 0.005f; // Tốc độ mưa grid
    
    private LModelCanvas canvas;
    
    private float[] bgPrimaryColor = {1.0f, 0.85f, 0.9f}; // Màu hồng nhạt (Pink Pastel)
    private float[] bgStripeColor = {1.0f, 0.95f, 0.97f}; // Màu sọc sáng hơn
    private float stripeSpeed = 0.002f;
    private float stripeOffset = 0.0f;
    
    // Màu nền Gradient phong cách VTube Studio
    private float[] bgTopColor = {1.0f, 0.90f, 0.94f};    
    private float[] bgBottomColor = {1.0f, 0.98f, 1.0f}; 

    // Biến cho các Icon trôi nổi
    private float bgIconOffset = 0.0f;
    private final float bgIconSpeed = 0.001f; 
    private final float bgIconAlpha = 0.15f;   
    private int[] bgIconTextures;         
    
    private NodeEditorContext nodeContext;
    private LNodeGraph accessoryGraph = new LNodeGraph();
    private ImBoolean showNodeEditor = new ImBoolean(false);
    
    private float[] speedArray = {0.002f}; 
    
    // Thêm danh sách quản lý Link vào class của bạn
    private final Map<Integer, long[]> links = new HashMap<>(); // ID -> [SourcePin, TargetPin]
    private int nextLinkId = 1;

    private boolean showContextErrorModal = false;
    
    private boolean showNodeErrorDialog = false;
    private String nodeErrorMessage = "";
    private boolean isNodeEditorCorrupted = false; // Biến đánh dấu trạng thái lỗi Native
    
 // Thêm các biến thành viên vào LViewer
    private long hResizeCursor, vResizeCursor, seResizeCursor;
    
 // Trong LViewer.java
    private static final LoggerUtil LOGGER = LoggerUtil.getLogger("LViewer");
    
    private LCameraTracker cameraTracker;
    private ImBoolean showCameraWindow = new ImBoolean(false);
    private int selectedCameraDevice = 0; // Index camera (thường là 0 cho webcam tích hợp)

    private void initCursors() {
        hResizeCursor = glfwCreateStandardCursor(GLFW_HRESIZE_CURSOR);
        vResizeCursor = glfwCreateStandardCursor(GLFW_VRESIZE_CURSOR);
        seResizeCursor = glfwCreateStandardCursor(GLFW_RESIZE_NWSE_CURSOR);
    }

    private void initSpoutSender() {
        try {
            spoutPtr = JNISpout.init();
            if (spoutPtr != 0) {
                isSpoutInitialized = JNISpout.createSender(senderName, fbWidth, fbHeight, spoutPtr);
                LOGGER.info("[Spout] Sender initialized: " + senderName);
            }
        } catch (UnsatisfiedLinkError e) {
            LOGGER.error("[Spout] CRITICAL: JNISpout_64.dll missing!");
        }
    }
    
    private NodeEditorContext getEditorContext() {
        if (nodeContext == null || nodeContext.ptr == 0) {
            LOGGER.error("[NodeEditor] Attempting to create context...");
            
            NodeEditorConfig config = new NodeEditorConfig();
            config.setSettingsFile("nodes_mcp.json"); 
            nodeContext = NodeEditor.createEditor(config);
            
            if (nodeContext == null || nodeContext.ptr == 0) {
                // Kích hoạt Dialog lỗi
                showNodeErrorDialog = true;
                nodeErrorMessage = "Không thể khởi tạo Native Context cho Node Editor.\n" +
                                   "Vui lòng kiểm tra Driver đồ họa hoặc xóa file nodes_mcp.json.";
                
                // Tắt module để tránh spam log ở frame sau
                showNodeEditor.set(false); 
                
                // [2026-01-02] Tự động lưu trạng thái module là "Tắt" để tránh crash loop khi restart
                autoSave(); 
                
                return null; 
            }
        }
        return nodeContext;
    }

    private void renderErrorModals() {
        // Chỉ gọi lệnh MỞ khi cờ vừa được bật. 
        // ImGui sẽ giữ trạng thái mở này cho đến khi kết thúc Modal.
        if (showNodeErrorDialog && !ImGui.isPopupOpen("Node Editor Critical Error")) {
            ImGui.openPopup("Node Editor Critical Error");
        }

        ImGui.setNextWindowPos(fbWidth / 2f, fbHeight / 2f, ImGuiCond.Always, 0.5f, 0.5f);
        int flags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoMove;

        if (ImGui.beginPopupModal("Node Editor Critical Error", null, flags)) {
            ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, "LỖI HỆ THỐNG NATIVE (JNI)");
            ImGui.separator();
            ImGui.textWrapped(nodeErrorMessage);

            if (ImGui.button("Đóng Editor", 120, 0)) {
                // THỨ TỰ QUAN TRỌNG:
                this.showNodeEditor.set(false);   // 1. Tắt module chính trước
                this.showNodeErrorDialog = false; // 2. Tắt popup
                this.isNodeEditorCorrupted = false; // 3. Reset trạng thái lỗi
                
                autoSave(); // [2026-01-02] Lưu trạng thái module là FALSE
                ImGui.closeCurrentPopup();
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Thử lại", 100, 0)) {
                this.showNodeErrorDialog = false;
                this.isNodeEditorCorrupted = false;
                // Không set showNodeEditor về false để nó tự init lại ở frame sau
            }
            ImGui.endPopup();
        }
    }
    
    public void loadAWTFontToImGui(String fontName, int fontSize) {
        try {
            ImGuiIO io = ImGui.getIO();
            ImFontConfig config = new ImFontConfig();
            // Cung cấp dải ký tự Tiếng Việt để Menubar không bị lỗi dấu
            short[] glyphRanges = io.getFonts().getGlyphRangesVietnamese();

            // Thử nạp trực tiếp từ thư mục assets (Giải pháp an toàn cho Java 11+)
            File localFont = new File("assets/fonts/" + fontName + ".ttf");
            
            if (localFont.exists()) {
                io.getFonts().addFontFromFileTTF(localFont.getAbsolutePath(), (float)fontSize, config, glyphRanges);
            } else {
                // Dự phòng: Tìm trực tiếp trong thư mục Fonts của Windows
                String winPath = System.getenv("WINDIR") + "\\Fonts\\" + fontName.replace("-Regular", "") + ".ttf";
                File f = new File(winPath);
                if (f.exists()) {
                    io.getFonts().addFontFromFileTTF(f.getAbsolutePath(), (float)fontSize, config, glyphRanges);
                } else {
                    // Nếu không thấy Comic Relief, nạp Tahoma để Menubar vẫn đọc được tiếng Việt
                    String tahomaPath = System.getenv("WINDIR") + "\\Fonts\\tahoma.ttf";
                    io.getFonts().addFontFromFileTTF(tahomaPath, (float)fontSize, config, glyphRanges);
                }
            }

            // QUAN TRỌNG: Phải build lại atlas và nạp lại texture cho GPU
            io.getFonts().build(); 
            imGuiGl3.destroyFontsTexture();
            imGuiGl3.createFontsTexture();

        } catch (Exception e) {
            LOGGER.error("Lỗi nạp Font Menubar: " + e.getMessage());
        }
    }

    private String getPhysicalFontPath(Font font) {
        try {
            Class<?> fontManagerClass = Class.forName("sun.font.FontManagerFactory");
            Object fontManager = fontManagerClass.getMethod("getInstance").invoke(null);
            
            Method findFont2DMethod = fontManager.getClass().getMethod("findFont2D", String.class, int.class, int.class);
            Object font2D = findFont2DMethod.invoke(fontManager, font.getFontName(), font.getStyle(), 0);
            
            // Thử lấy platName
            Field platNameField = font2D.getClass().getDeclaredField("platName");
            platNameField.setAccessible(true);
            String path = (String) platNameField.get(font2D);
            
            // Nếu platName không phải là đường dẫn file (không chứa dấu / hoặc \), thử dùng field khác
            if (path != null && (path.contains("/") || path.contains("\\"))) {
                return path;
            }

            // Phương án dự phòng 2: Thử lấy field 'fileName' (thường có trong Font2D của OpenJDK)
            try {
                Field fileNameField = font2D.getClass().getDeclaredField("fileName");
                fileNameField.setAccessible(true);
                return (String) fileNameField.get(font2D);
            } catch (NoSuchFieldException e) {
                // Bỏ qua nếu không có
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Fallback cho Comic Relief nếu được cài đặt trong máy
        String winDir = System.getenv("WINDIR");
        String comicPath = winDir + "\\Fonts\\ComicRelief.ttf";
        if (new File(comicPath).exists()) return comicPath;

        return (winDir != null) ? winDir + "\\Fonts\\tahoma.ttf" : null;
    }
    
    public void setupImGuiFonts() {
        // Gọi hàm bạn đã viết với tên font an toàn
        loadAWTFontToImGui("ComicRelief-Regular", 16);
        
        // Sau khi nạp font, nếu trước đó có lỗi, hãy đảm bảo popup vẫn có dữ liệu
        if (isNodeEditorCorrupted) {
            nodeErrorMessage = "Đã nạp lại Font hệ thống. Bạn có thể thử khởi động lại Node Editor.";
            autoSave(); // [cite: 2026-01-02]
        }
    }
   
    private void renderNodeEditor() {
     	if (!showNodeEditor.get()) {
            // Kiểm tra nếu Popup vẫn đang mở thì dập tắt nó ngay
            if (showNodeErrorDialog) {
                showNodeErrorDialog = false;    // Đóng Popup Modal
                isNodeEditorCorrupted = false;  // Giải phóng trạng thái lỗi
                autoSave();                     // [cite: 2026-01-02] Lưu lại trạng thái Module đã tắt
            }
            return;
        }
     	
     	if (isNodeEditorCorrupted) {
            return; 
        }

        // 1. Luôn mở Window trước
        ImGui.setNextWindowSize(800, 600, ImGuiCond.Once);
        // Lưu ý: ImGui.begin trả về boolean, nếu window bị thu nhỏ (collapsed) nó trả về false
        boolean isOpen = ImGui.begin("Lotus2D Node Editor - modcoderpack-redevelop", showNodeEditor);

        if (isOpen) {
            try {
                // 2. Lấy context
                NodeEditorContext ctx = getEditorContext();
                
                if (ctx == null || ctx.ptr == 0) {
                    // Đánh dấu lỗi để frame sau không chạy vào đây nữa
                    this.isNodeEditorCorrupted = true;
                    this.showNodeErrorDialog = true; 
                    this.nodeErrorMessage = "Phát hiện lỗi con trỏ Native (ctx is null).\n" +
                                            "Native library không thể cấp phát bộ nhớ.";
                }

                // 3. Thực thi Render
                NodeEditor.setCurrentEditor(ctx);
                NodeEditor.begin("Node Editor Canvas");

                // --- RENDER NODES ---
                for (LNodeGraph.AccessoryNode node : accessoryGraph.nodes.values()) {
                    NodeEditor.beginNode(node.nodeId);
                        ImGui.text(node.getName());
                        
                        NodeEditor.beginPin(node.inputPinId, NodeEditorPinKind.Input);
                            ImGui.text("-> In");
                        NodeEditor.endPin();
                        
                        ImGui.sameLine();
                        
                        NodeEditor.beginPin(node.outputPinId, NodeEditorPinKind.Output);
                            ImGui.text("Out ->");
                        NodeEditor.endPin();
                    NodeEditor.endNode();
                }

                // --- RENDER LINKS ---
                for (Map.Entry<Integer, long[]> entry : links.entrySet()) {
                    NodeEditor.link(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
                }

                handleNodeEditorLogic(); 

                NodeEditor.end();
                NodeEditor.setCurrentEditor(null);

            } catch (Exception e) {
                this.isNodeEditorCorrupted = true;
                this.showNodeErrorDialog = true;
                this.nodeErrorMessage = "Exception: " + e.getMessage();
            } finally {
                // 4. BẮT BUỘC: Luôn gọi End() để bảo vệ ImGui Window Stack
                ImGui.end(); 
            }
        } else {
            // Trường hợp window bị thu nhỏ nhưng vẫn đang "mở"
            ImGui.end();
        }
    }
    
    private void handleNodeEditorLogic() {
        // Logic tạo Link mới
        if (NodeEditor.beginCreate()) {
            ImLong startPin = new ImLong(), endPin = new ImLong();
            if (NodeEditor.queryNewLink(startPin, endPin)) {
                if (NodeEditor.acceptNewItem()) {
                    LNodeGraph.AccessoryNode source = accessoryGraph.findByOutput(startPin.get());
                    LNodeGraph.AccessoryNode target = accessoryGraph.findByInput(endPin.get());
                    if (source != null && target != null) {
                        source.outputNodeId = target.nodeId;
                        links.put(nextLinkId++, new long[]{startPin.get(), endPin.get()});
                        autoSave(); // [cite: 2026-01-02]
                    }
                }
            }
            NodeEditor.endCreate();
        }

        // Logic xóa
        if (NodeEditor.beginDelete()) {
            ImLong id = new ImLong();
            while (NodeEditor.queryDeletedLink(id)) {
                if (NodeEditor.acceptDeletedItem()) {
                    long[] pins = links.get((int) id.get());
                    if (pins != null) {
                        LNodeGraph.AccessoryNode src = accessoryGraph.findByOutput(pins[0]);
                        if (src != null) src.outputNodeId = -1;
                    }
                    links.remove((int) id.get());
                    autoSave(); // [cite: 2026-01-02]
                }
            }
            NodeEditor.endDelete();
        }
    }
    
    private void handleEditorInteractions() {
        NodeEditor.suspend();
        
        // Chuột phải vào khoảng trống để tạo Node mới
        if (NodeEditor.showBackgroundContextMenu()) {
            ImGui.openPopup("editor_menu");
        }
        
        if (ImGui.beginPopup("editor_menu")) {
            if (ImGui.menuItem("Add Accessory (Cat Ears)")) {
                LNodeGraph.AccessoryNode n = accessoryGraph.createNode("Cat Ears");
                NodeEditor.setNodePosition(n.nodeId, NodeEditor.screenToCanvas(ImGui.getMousePos()));
                autoSave(); // [cite: 2026-01-02]
            }
            if (ImGui.menuItem("Add Effect (Glow)")) {
                LNodeGraph.AccessoryNode n = accessoryGraph.createNode("Glow Effect");
                NodeEditor.setNodePosition(n.nodeId, NodeEditor.screenToCanvas(ImGui.getMousePos()));
                autoSave(); // [cite: 2026-01-02]
            }
            ImGui.endPopup();
        }
        
        NodeEditor.resume();
    }
    
    private void closeSpoutSender() {
        if (spoutPtr != 0) {
            JNISpout.releaseSender(spoutPtr);
            JNISpout.deInit(spoutPtr);
            spoutPtr = 0;
            isSpoutInitialized = false;
            LOGGER.info("[Spout] Sender released successfully.");
        }
    }

    private void setupFBO() {
        if (fbWidth <= 0 || fbHeight <= 0) return;
        if (fboId != 0) glDeleteFramebuffers(fboId);
        if (fboTextureId != 0) glDeleteTextures(fboTextureId);

        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        fboTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fboTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, fbWidth, fbHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fboTextureId, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.error("[FBO] Error: Framebuffer is not complete!");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    @Override
    public void run() {
        LSplash splash = new LSplash();
        try {
            splash.showSplash();
            
            // 1. Chạy init() - Giữ nguyên 100% logic bạn đã viết
            init(); 
            
            // 2. ẨN SPLASH TRƯỚC KHI VÀO LOOP
            splash.hideSplash();
            
            // 3. QUAN TRỌNG: Cưỡng bức hiển thị cửa sổ chính
            // Vì Splash (AlwaysOnTop) vừa mất, ta cần đòi lại quyền ưu tiên ngay lập tức
            if (window != 0) {
                glfwShowWindow(window);
                glfwFocusWindow(window);
                glfwRequestWindowAttention(window); // Nháy taskbar nếu cần để giành focus
            }
            
            // 4. Bắt đầu vòng lặp
            loop();
            
        } catch (Exception e) {
            LOGGER.error("[modcoderpack-redevelop] Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Đảm bảo splash luôn đóng nếu có lỗi xảy ra trong init
            if (splash != null) splash.hideSplash();
            cleanup();
        }
    }
    
    private void cleanup() {
        closeSpoutSender();
        if (fontRenderer != null) fontRenderer.cleanup();
        if (menuLogoTextureId != 0) glDeleteTextures(menuLogoTextureId);
        if (fboId != 0) glDeleteFramebuffers(fboId);
        if (fboTextureId != 0) glDeleteTextures(fboTextureId);
        if (dotTextureId != 0) glDeleteTextures(dotTextureId);
        if (cameraTracker != null) {
            cameraTracker.cleanup();
        }
        glfwDestroyCursor(hResizeCursor);
        glfwDestroyCursor(vResizeCursor);
        glfwDestroyCursor(seResizeCursor);
        imGuiGl3.shutdown();
        imGuiGlfw.shutdown();
        ImGui.destroyContext();
        Callbacks.glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE); 
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // Giữ cửa sổ ẩn lúc đang khởi tạo

        // [Personalization] Repo: modcoderpack-redevelop
        window = glfwCreateWindow(1280, 720, "Lotus2D Viewer - modcoderpack-redevelop", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        updateDimensions();
        glfwSetFramebufferSizeCallback(window, (windowPtr, newFbWidth, newFbHeight) -> {
            this.fbWidth = newFbWidth;
            this.fbHeight = newFbHeight;
            updateDimensions();
            setupFBO();
            if (isSpoutInitialized && spoutPtr != 0) {
                JNISpout.updateSender(senderName, fbWidth, fbHeight, spoutPtr);
            }
            autoSave(); // [cite: 2026-01-02] Tự động lưu khi thay đổi kích thước
        });

        this.setWindowIcon(window, "assets/icons.png");
        glfwMakeContextCurrent(window);
        
        // QUAN TRỌNG: Đặt về 0 để bỏ qua sự kiểm soát của Windows V-Sync
        // Điều này ngăn chặn việc Thread bị đóng băng khi cực tiểu hóa.
        glfwSwapInterval(0); 
        
        glfwShowWindow(window);
        glfwFocusWindow(window); // Ép cửa sổ chính phải nổi lên

        GL.createCapabilities();
        this.showDebugWindow.set(true);
        
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        imGuiGlfw.init(window, true);
        imGuiGl3.init("#version 330");
        
        initFont();
        initCursors();
        
        this.nodeContext = null;
        
        if (this.nodeContext == null || this.nodeContext.ptr == 0) {
            LOGGER.error("[NodeEditor] Critical Error: Context still null after proper init!");
        }
        
        setupFBO();
        createDotTexture();
        this.fontRenderer = new FontRenderer(new Font("Comic Relief", Font.PLAIN, 16));
        this.menuLogoTextureId = loadTexture("assets/icons/icons.png");
        this.canvas = new LModelCanvas();
        
        try {
            this.cameraTracker = new LCameraTracker();
            LOGGER.info("Camera Tracker loaded.");
        } catch (Throwable t) {
            // Nếu DLL sai, n_Mat() crash sẽ rơi vào đây
            LOGGER.error("OpenCV Fatal: DLL mismatch or file missing. Module disabled.");
            this.cameraTracker = null; 
        }

        // BẮT BUỘC: Khởi tạo NodeEditor nằm ngoài khối try trên để cứu Context
        if (this.nodeContext == null) {
            this.nodeContext = NodeEditor.createEditor(new NodeEditorConfig());
            if (this.nodeContext == null) {
                LOGGER.error("[NodeEditor] Critical Error: Context still null!");
            }
        }
    }
    
    private void renderCameraUI() {
        // Thêm vào thanh Menu chính
        // Cửa sổ hiển thị Camera
        if (showCameraWindow.get()) {
            ImGui.setNextWindowSize(660, 520, ImGuiCond.FirstUseEver);
            if (ImGui.begin("Camera Live Feed", showCameraWindow)) {
                
                // Cập nhật frame từ OpenCV và đẩy lên Texture
                cameraTracker.updateAndRenderTexture();
                
                // Lấy ID texture để vẽ lên ImGui
                int textureId = cameraTracker.getTextureId();
                
                // Hiển thị ảnh (tự động co dãn theo cửa sổ)
                float regionX = ImGui.getContentRegionAvailX();
                float regionY = regionX * 0.75f; // Giữ tỉ lệ 4:3
                ImGui.image(textureId, regionX, regionY);
                
                ImGui.separator();
                ImGui.text("Camera Status: " + (cameraTracker.isActive() ? "Running" : "Stopped"));
                
                if (ImGui.button("Stop Camera")) {
                    showCameraWindow.set(false);
                    cameraTracker.stop();
                    autoSave();
                }
                
                ImGui.end();
            }
        }
    }

    private void updateDimensions() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetWindowSize(window, w, h);
            this.width = w.get(0);
            this.height = h.get(0);
            glfwGetFramebufferSize(window, w, h);
            this.fbWidth = w.get(0);
            this.fbHeight = h.get(0);
        }
    }
    
    private void initFont() {
        try {
            ImGuiIO io = ImGui.getIO();
            // Đảm bảo file nằm trong src/main/resources/assets/fonts/
            String resourcePath = "assets/fonts/ComicRelief-Regular.ttf"; 
            
            // Nạp font từ JAR vào ByteBuffer
            ByteBuffer fontBuffer = ioResourceToByteBuffer(resourcePath);
            
            if (fontBuffer != null) {
                // Chuyển ByteBuffer sang byte array để dùng với addFontFromMemoryTTF
                byte[] fontData = new byte[fontBuffer.remaining()];
                fontBuffer.get(fontData);

                ImFontConfig config = new ImFontConfig();
                // Nạp font vào Atlas với dải ký tự Tiếng Việt
                io.getFonts().addFontFromMemoryTTF(fontData, 18.0f, config, io.getFonts().getGlyphRangesVietnamese());
                
                // Rebuild texture cho GPU
                io.getFonts().build();
                imGuiGl3.destroyFontsTexture();
                imGuiGl3.createFontsTexture();
            }
        } catch (Exception e) {
            // Bắt lỗi tại đây để không làm sập hàm init()
            System.err.println("Lỗi nạp font từ JAR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private boolean isPluginLoaded = false;

    private void loop() {
        final double targetFPS = 130.0;
        final double frameTime = 1.0 / targetFPS;
        double lastFrameTime = glfwGetTime();

        LPluginManager pluginManager = new LPluginManager();
        
        if (isPluginLoaded) {
        	 pluginManager.addInternalPlugin(new StarfieldWarpPlugin());
        }

        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;

            glfwPollEvents();
            handleResizeLogic(); 
            updateDimensions();

            pluginManager.updateAll((float)deltaTime);

         // --- BẮT ĐẦU VẼ VÀO FBO ---
            glBindFramebuffer(GL_FRAMEBUFFER, fboId);
            glViewport(0, 0, fbWidth, fbHeight);
            
            boolean hasBG = pluginManager.hasActiveBackground();

            if (!hasBG) {
                // TRƯỜNG HỢP 1: KHÔNG LOAD PLUGIN -> Dùng Background gốc
                glClearColor(0.12f, 0.12f, 0.12f, 1.0f); 
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                
                // Vẽ Grid làm nền gốc ở đây
                drawDynamicGrid(fbWidth, fbHeight);
            } else {
                // TRƯỜNG HỢP 2: CÓ LOAD PLUGIN -> Dùng Plugin làm nền
                glClearColor(0.0f, 0.0f, 0.0f, 0.0f); 
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                
                // Gọi Plugin vẽ (Manager sẽ lo phần bảo vệ trạng thái glPushAttrib)
                pluginManager.renderBackgrounds(fbWidth, fbHeight);
            }

            // --- CÁC LỚP VẼ ĐÈ LÊN TRÊN NỀN ---
            
            // Vẽ nhân vật (Luôn vẽ dù là nền gì)
            canvas.draw(fbWidth, fbHeight, fontRenderer);

            // Vẽ Foreground Plugins (Hiệu ứng tuyết, mưa, bloom...)
            pluginManager.renderForegrounds(fbWidth, fbHeight);

            // Spout (Luôn gửi từ FBO)
            if (enableSpout.get() && isSpoutInitialized && spoutPtr != 0) {
                JNISpout.sendTexture(fbWidth, fbHeight, fboTextureId, GL_TEXTURE_2D, true, spoutPtr);
            }
            
            glBindFramebuffer(GL_FRAMEBUFFER, 0);

            // --- HIỂN THỊ LÊN MÀN HÌNH ---
            if (glfwGetWindowAttrib(window, GLFW_ICONIFIED) != GLFW_TRUE) {
                renderGUI(); 
                glfwSwapBuffers(window);
            } 
            
            // Giữ nhịp 60 FPS & AutoSave [2026-01-02]
            double actualWait = frameTime - (glfwGetTime() - currentTime);
            if (actualWait > 0) {
                try { Thread.sleep((long)(actualWait * 1000)); } catch (InterruptedException ignored) {}
            }
            lastFrameTime = currentTime;
        }
    }
    
    private void handleResizeLogic() {
        if (glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE) return;
        
        int margin = 12; 
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer cx = stack.mallocDouble(1), cy = stack.mallocDouble(1);
            glfwGetCursorPos(window, cx, cy);
            
            double mx = cx.get(0); 
            double my = cy.get(0);

            if (!isResizing) {
                boolean onR = mx >= width - margin && mx <= width + 5; 
                boolean onB = my >= height - margin && my <= height + 5;

                if (onR || onB) {
                    // Sử dụng các Cursor đã pre-load
                    if (onR && onB) glfwSetCursor(window, seResizeCursor);
                    else if (onR) glfwSetCursor(window, hResizeCursor);
                    else glfwSetCursor(window, vResizeCursor);

                    if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
                        isResizing = true;
                        if (onR && onB) resizeType = 3; 
                        else if (onR) resizeType = 1; 
                        else resizeType = 2;
                    }
                } else {
                    // Trả về cursor mặc định khi không ở mép
                    glfwSetCursor(window, 0);
                }
            } else {
                // Đang trong quá trình resize - Giữ nguyên icon resize
                if (resizeType == 3) glfwSetCursor(window, seResizeCursor);
                else if (resizeType == 1) glfwSetCursor(window, hResizeCursor);
                else if (resizeType == 2) glfwSetCursor(window, vResizeCursor);

                if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_RELEASE) { 
                    isResizing = false; 
                    glfwSetCursor(window, 0); // Reset icon khi thả chuột
                    autoSave(); // [cite: 2026-01-02]
                } else {
                    int nw = (resizeType == 1 || resizeType == 3) ? (int) Math.max(mx, 400) : width;
                    int nh = (resizeType == 2 || resizeType == 3) ? (int) Math.max(my, 300) : height;
                    
                    // Chỉ set size nếu thực sự có thay đổi để tránh flicker
                    if (nw != width || nh != height) {
                        glfwSetWindowSize(window, nw, nh);
                        // Cập nhật lại width/height ngay lập tức để logic loop sau không bị lệch
                        this.width = nw;
                        this.height = nh;
                    }
                }
            }
        }
    }

    private void renderGUI() {
        // 1. Reset Viewport về kích thước buffer
        glViewport(0, 0, fbWidth, fbHeight);
        
        // 2. Clear Backbuffer màn hình (tránh rác hình ảnh cũ)
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        // 3. COPY DỮ LIỆU TỪ FBO RA MÀN HÌNH CHÍNH
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fboId);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBlitFramebuffer(0, 0, fbWidth, fbHeight, 0, 0, fbWidth, fbHeight, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);

        // 4. VẼ GUI ĐÈ LÊN
        imGuiGl3.newFrame();
        imGuiGlfw.newFrame();
        ImGui.newFrame();

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 8);
        if (ImGui.beginMainMenuBar()) {
            renderAppMenus(); 
            ImGui.endMainMenuBar();
        }
        ImGui.popStyleVar();

        if (showNodeEditor.get()) {
            try { renderNodeEditor(); } catch (Exception e) {
                showNodeEditor.set(false);
                autoSave(); // [cite: 2026-01-02]
            }
        }

        if (showDebugWindow.get()) {
            ImGui.begin("Lotus2D Debug", showDebugWindow);
            ImGui.text("FPS: " + (int)ImGui.getIO().getFramerate());
            ImGui.end();
        }
        
        renderErrorModals();
        renderCameraUI();

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());

        // Xử lý Viewports (Kéo cửa sổ ra ngoài)
        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            final long backupPtr = glfwGetCurrentContext();
            syncViewportIcons();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
            glfwMakeContextCurrent(backupPtr);
        }
    }
    
    private int dotTextureId;

    private void createDotTexture() {
        int size = 128; // Kích thước texture
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        // Bật khử răng cưa mượt mà
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Xóa nền trong suốt
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, size, size);
        g2.setComposite(AlphaComposite.SrcOver);

        // --- SỬA TẠI ĐÂY: Vẽ hạt dọc (Capsule standing up) ---
        g2.setColor(Color.WHITE); 
        int dotW = 16; // Chiều rộng hạt (nhỏ)
        int dotH = 80; // Chiều cao hạt (dài)
        int arc  = 16; // Độ bo (arc = width để tạo đầu tròn)
        
        // Căn giữa hạt dọc trong texture
        g2.fillRoundRect((size - dotW) / 2, (size - dotH) / 2, dotW, dotH, arc, arc);
        g2.dispose();

        // Upload texture lên GPU
        dotTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, dotTextureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, getPixels(img));
    }
    
    // Hàm hỗ trợ lấy buffer pixel
    private IntBuffer getPixels(BufferedImage img) {
        int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        IntBuffer buffer = BufferUtils.createIntBuffer(pixels.length);
        buffer.put(pixels).flip();
        return buffer;
    }

    private void drawDynamicGrid(int w, int h) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glUseProgram(0);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

     // 1. Vẽ Nền Gradient Pastel
        glBegin(GL_QUADS);
            glColor3f(bgPrimaryColor[0], bgPrimaryColor[1], bgPrimaryColor[2]);
            glVertex2f(0, 0); glVertex2f(w, 0);
            glColor3f(bgPrimaryColor[0] * 0.95f, bgPrimaryColor[1] * 0.95f, bgPrimaryColor[2] * 0.98f); 
            glVertex2f(w, h); glVertex2f(0, h);
        glEnd();

        // Trôi theo hướng chéo xuống bên trái [cite: 2026-01-02]
        stripeOffset += speedArray[0] * 120.0f;
        if (stripeOffset >= 1000.0f) stripeOffset = 0;

        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, dotTextureId);
        
        glTranslatef(w / 2.0f, h / 2.0f, 0);
        glRotatef(45.0f, 0, 0, 1); 
        glTranslatef(-w * 1.2f, -h * 1.2f, 0);

        float spacing = 64.0f;
        float quadSize = 80.0f; // Kích thước ô hiển thị hạt dot
        
        // Màu dot từ config [cite: 2026-01-02]
        glColor4f(bgStripeColor[0], bgStripeColor[1], bgStripeColor[2], 0.6f);

        glBegin(GL_QUADS);
        for (float x = 0; x < w * 2.5f; x += spacing) {
            for (float y = 0; y < h * 2.5f; y += spacing) {
                float currentY = (y + stripeOffset) % (h * 2.5f);
                
                // Vẽ Quad chứa Texture RoundRect
                glTexCoord2f(0, 0); glVertex2f(x, currentY);
                glTexCoord2f(1, 0); glVertex2f(x + quadSize, currentY);
                glTexCoord2f(1, 1); glVertex2f(x + quadSize, currentY + quadSize);
                glTexCoord2f(0, 1); glVertex2f(x, currentY + quadSize);
            }
        }
        glEnd();

        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glPopAttrib();
    }
    
    
    private void renderAppMenus() {
        float barHeight = ImGui.getFrameHeight();
        float iconSize = 22.0f;
        float windowWidth = ImGui.getWindowWidth();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float winPosX = ImGui.getWindowPosX();
        float winPosY = ImGui.getWindowPosY();

        handleDraggingLogic();

        if (menuLogoTextureId != 0) {
            ImGui.setCursorPosY((barHeight - iconSize) / 2.0f);
            ImGui.image(menuLogoTextureId, iconSize, iconSize);
            ImGui.sameLine(); ImGui.spacing();
        }

        ImGui.setCursorPosY(0);
        if (ImGui.beginMenu("File")) {         
            if (ImGui.menuItem("Open Model...", "Ctrl+O")) {
            	String filePath = openFileDialog();
                if (filePath != null) {
                    LOGGER.info("Đang nạp model từ: " + filePath);
                    // Logic nạp dữ liệu của bạn ở đây
                    // loadModelFromFile(filePath);
                    autoSave(); // [2026-01-02]
                }
            }

            ImGui.separator(); // Vạch phân cách các nhóm chức năng

            if (ImGui.menuItem("Save Project", "Ctrl+S")) autoSave(); 
            
            if (ImGui.menuItem("Export as JSON...")) {
                // Logic export ra file tùy chỉnh thay vì file autoSave mặc định
            }

            ImGui.separator();

            if (ImGui.menuItem("Exit", "Alt+F4")) {
                glfwSetWindowShouldClose(window, true);
            }
            ImGui.endMenu();
        }
        
        ImGui.setCursorPosY(0);
        if (ImGui.beginMenu("View")) {
            // Toggle cho Node Editor
            if (ImGui.menuItem("Node Editor (NodeTree)", "Ctrl+N", showNodeEditor.get())) {
                showNodeEditor.set(!showNodeEditor.get());
                autoSave(); // [cite: 2026-01-02]
            }
            
            if (ImGui.menuItem("Spout Output", null, enableSpout.get())) {
                enableSpout.set(!enableSpout.get());
                if (enableSpout.get()) initSpoutSender(); else closeSpoutSender();
                autoSave(); 
            }
            ImGui.endMenu();
        }
        
        ImGui.setCursorPosY(0);
        // Trong phần render ImGui của bạn
        if (ImGui.beginMenu("Theme")) {
            if (ImGui.colorEdit3("Base Color", bgPrimaryColor)) {
                autoSave(); // [cite: 2026-01-02]
            }
            if (ImGui.colorEdit3("Stripe Color", bgStripeColor)) {
                autoSave(); // [cite: 2026-01-02]
            }
            if (ImGui.sliderFloat("Stripe Speed", speedArray, 0.001f, 0.01f)) {
                this.stripeSpeed = speedArray[0];
                autoSave(); // [cite: 2026-01-02]
            }
            ImGui.endMenu();
        }
        
        ImGui.setCursorPosY(0);
        if (ImGui.beginMenu("Modules")) {
        	// Trong LViewer.java
        	if (ImGui.menuItem("Camera Tracking", "", showCameraWindow.get())) {
        	    showCameraWindow.set(!showCameraWindow.get());
        	    
        	    // Kiểm tra an toàn trước khi gọi các hàm của tracker
        	    if (this.cameraTracker != null) {
        	        if (showCameraWindow.get()) {
        	            this.cameraTracker.start(0);
        	        } else {
        	            this.cameraTracker.stop();
        	        }
        	        // [2026-01-02] Tự động lưu trạng thái khi module được thay đổi
        	        autoSave(); 
        	    } else {
        	        // Nếu tracker null (do lỗi DLL ở trên), ép đóng window và báo lỗi
        	        showCameraWindow.set(false);
        	        LOGGER.warn("Không thể kích hoạt Camera: Module chưa được khởi tạo thành công.");
        	    }
        	}
            ImGui.endMenu();
        }


        float btnW = 45.0f;
        int colorText = ImGui.getColorU32(imgui.flag.ImGuiCol.Text);
        boolean isMax = glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE;
        
        float xBtnPos = windowWidth - btnW;
        ImGui.setCursorPosX(xBtnPos);
        if (ImGui.invisibleButton("##CloseBtn", btnW, barHeight)) glfwSetWindowShouldClose(window, true);
        if (ImGui.isItemHovered()) drawList.addRectFilled(winPosX + xBtnPos, winPosY, winPosX + xBtnPos + btnW, winPosY + barHeight, ImGui.getColorU32(0.9f, 0.1f, 0.1f, 1.0f));
        
        float centerX = winPosX + xBtnPos + btnW / 2.0f;
        float centerY = winPosY + barHeight / 2.0f;
        drawList.addLine(centerX - 5, centerY - 5, centerX + 5, centerY + 5, colorText, 1.5f);
        drawList.addLine(centerX + 5, centerY - 5, centerX - 5, centerY + 5, colorText, 1.5f);

        float maxBtnPos = windowWidth - (btnW * 2);
        ImGui.setCursorPosX(maxBtnPos);
        if (ImGui.invisibleButton("##MaxBtn", btnW, barHeight)) {
            if (isMax) glfwRestoreWindow(window); else glfwMaximizeWindow(window);
            autoSave();
        }
        if (ImGui.isItemHovered()) drawList.addRectFilled(winPosX + maxBtnPos, winPosY, winPosX + maxBtnPos + btnW, winPosY + barHeight, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 0.1f));
        
        float mxC = winPosX + maxBtnPos + btnW / 2.0f;
        if (!isMax) {
            drawList.addRect(mxC - 5, centerY - 5, mxC + 5, centerY + 5, colorText, 0, 0, 1.5f);
        } else {
            drawList.addRect(mxC - 4, centerY - 2, mxC + 6, centerY + 8, colorText, 0, 0, 1.0f);
            drawList.addRectFilled(mxC - 6, centerY - 4, mxC + 4, centerY + 6, ImGui.getColorU32(imgui.flag.ImGuiCol.MenuBarBg));
            drawList.addRect(mxC - 6, centerY - 4, mxC + 4, centerY + 6, colorText, 0, 0, 1.0f);
        }

        float minBtnPos = windowWidth - (btnW * 3);
        ImGui.setCursorPosX(minBtnPos);
        if (ImGui.invisibleButton("##MinBtn", btnW, barHeight)) glfwIconifyWindow(window);
        if (ImGui.isItemHovered()) drawList.addRectFilled(winPosX + minBtnPos, winPosY, winPosX + minBtnPos + btnW, winPosY + barHeight, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 0.1f));
        
        float miC = winPosX + minBtnPos + btnW / 2.0f;
        drawList.addLine(miC - 7, centerY + 6, miC + 7, centerY + 6, colorText, 1.5f);
    }
    
    private String openFileDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Cấu hình các định dạng file muốn lọc (ví dụ: .json, .mcp)
            PointerBuffer filters = stack.mallocPointer(2);
            filters.put(stack.UTF8("*.lmp"));
            filters.put(stack.UTF8("*.zip"));
            filters.flip();

            // Hiển thị cửa sổ chọn file native
            return TinyFileDialogs.tinyfd_openFileDialog(
                "Chọn Model File - modcoderpack-redevelop", // Tiêu đề
                "",                                          // Đường dẫn mặc định
                filters,                                     // Bộ lọc
                "Lotus Model Package (.lmp, .zip)",                 // Mô tả bộ lọc
                false                                        // Cho phép chọn nhiều file?
            );
        }
    }

    private void renderWindowControls() {
        float btnW = 45.0f;
        float barH = ImGui.getFrameHeight();
        float winW = ImGui.getWindowWidth();
        float winX = ImGui.getWindowPosX();
        float winY = ImGui.getWindowPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        int txtCol = ImGui.getColorU32(imgui.flag.ImGuiCol.Text);

        ImGui.setCursorPosX(winW - btnW);
        if (ImGui.invisibleButton("##Close", btnW, barH)) glfwSetWindowShouldClose(window, true);
        if (ImGui.isItemHovered()) dl.addRectFilled(winX + winW - btnW, winY, winX + winW, winY + barH, ImGui.getColorU32(0.9f, 0.1f, 0.1f, 1.0f));
        
        ImGui.setCursorPosX(winW - btnW * 2);
        if (ImGui.invisibleButton("##Max", btnW, barH)) {
            if (glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE) glfwRestoreWindow(window); 
            else glfwMaximizeWindow(window);
            autoSave();
        }
        
        ImGui.setCursorPosX(winW - btnW * 3);
        if (ImGui.invisibleButton("##Min", btnW, barH)) glfwIconifyWindow(window);
    }

    private void handleDraggingLogic() {
        if (ImGui.isWindowHovered() && ImGui.isMouseClicked(0)) {
            isDragging = true;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                DoubleBuffer px = stack.mallocDouble(1), py = stack.mallocDouble(1);
                glfwGetCursorPos(window, px, py);
                startMouseX = px.get(0); startMouseY = py.get(0);
            }
        }
        if (isDragging) {
            if (ImGui.isMouseDown(0)) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    DoubleBuffer cx = stack.mallocDouble(1), cy = stack.mallocDouble(1);
                    IntBuffer wx = stack.mallocInt(1), wy = stack.mallocInt(1);
                    glfwGetCursorPos(window, cx, cy); glfwGetWindowPos(window, wx, wy);
                    glfwSetWindowPos(window, wx.get(0) + (int)(cx.get(0) - startMouseX), wy.get(0) + (int)(cy.get(0) - startMouseY));
                }
            } else { isDragging = false; autoSave(); }
        }
    }
    
    private void autoSave() {
        // [cite: 2026-01-02] Logic lưu cấu hình tự động cho modcoderpack-redevelop
    }

    private int loadTexture(String path) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            ByteBuffer img = stbi_load_from_memory(ioResourceToByteBuffer(path), w, h, c, 4);
            if (img == null) return 0;
            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, img);
            stbi_image_free(img); return id;
        } catch (Exception e) { return 0; }
    }

    public void setWindowIcon(long handle, String path) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            ByteBuffer px = stbi_load_from_memory(ioResourceToByteBuffer(path), w, h, c, 4);
            if (px == null) return;
            GLFWImage.Buffer icons = GLFWImage.malloc(1);
            icons.get(0).set(w.get(0), h.get(0), px);
            glfwSetWindowIcon(handle, icons);
            stbi_image_free(px);
        } catch (Exception ignored) {}
    }

    private void syncViewportIcons() {
        int count = ImGui.getPlatformIO().getViewportsSize();
        for (int i = 0; i < count; i++) {
            long handle = ImGui.getPlatformIO().getViewports(i).getPlatformHandle();
            if (handle != 0 && !iconifiedWindows.contains(handle)) {
                setWindowIcon(handle, "assets/icons/icons.png");
                iconifiedWindows.add(handle);
            }
        }
    }

    private ByteBuffer ioResourceToByteBuffer(String res) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(res)) {
            if (in == null) throw new IOException("Not found: " + res);
            byte[] b = in.readAllBytes();
            ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(b.length);
            return buf.put(b).flip();
        }
    }

    public static void main(String[] args) { new LViewer().run(); }
}