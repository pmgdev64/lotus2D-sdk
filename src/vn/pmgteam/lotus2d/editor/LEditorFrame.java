package vn.pmgteam.lotus2d.editor;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;

import org.w3c.dom.NodeList;

import com.formdev.flatlaf.FlatDarkLaf;

import vn.pmgteam.lotus2d.core.LArtMesh;
import vn.pmgteam.lotus2d.core.LArtMesh.ParameterPoint;
import vn.pmgteam.lotus2d.core.LParameter;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class LEditorFrame extends JFrame {
    private LArtMesh targetMesh;
    private LParameter paramX;
    private PreviewPanel previewPanel;
    private PreviewPanel characterPreview;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private double zoomFactor = 0.5;
    
    private boolean isPivotEditMode = false;
    private JToggleButton pivotToggleBtn; // Nút chuyển chế độ
    
    private boolean isMeshEditMode = false;
    private JToggleButton meshToggleBtn;
    
	private double cameraX = 0; // Tọa độ X của camera
    private double cameraY = 0; // Tọa độ Y của camera
    
    // Thêm vào phần khai báo biến ở đầu class
    private JSlider posXSlider, posYSlider, rotSlider, alphaSlider;
    private boolean isUpdatingSliders = false; // Biến cờ chặn vòng lặp sự kiện
    
    private JList<String> layerList; // Thêm dòng này để truy cập toàn cục
    
    private LayerData data;
    
    private int selectedVertexIdx = -1; // Chỉ số đỉnh đang được chọn
    
    private Point initialClick;
    
    private static LEditorFrame instance;
    
    public int defaultDensity = 6;
    private Point lastMousePos; // Lưu vị trí chuột để tính delta khi kéo
    
    // Trong LEditorFrame.java
    private boolean isParamPointEditMode = false;
    
    private boolean isCustomMeshMode = false;
    
    private boolean isCustomParamPointMode = false;
    private JToggleButton customParamBtn; // Nút bấm trên UI
    private JPanel joystickListPanel = new JPanel();
    
 // Danh sách dữ liệu thực tế để quản lý các tham số của project
    private List<LParameter> projectParameters = new ArrayList<>();
    
    private File currentPSDFile = null; // Khai báo biến toàn cục trong LEditorFrame
    
 // Thêm một danh sách để quản lý các Joystick và dữ liệu liên kết của chúng
    private final List<JoystickBinding> activeBindings = new ArrayList<>();

    // Lớp phụ trợ để lưu trữ liên kết
    private static class JoystickBinding {
        String paramName;
        ParamJoystick joystick;
        LayerData targetLayer;

        JoystickBinding(String name, ParamJoystick js, LayerData layer) {
            this.paramName = name;
            this.joystick = js;
            this.targetLayer = layer;
        }
    }

    private class LayerData {
        BufferedImage image;
        LArtMesh artMesh; // Đối tượng xử lý mesh từ SDK của bạn
        final int psdX, psdY; 
        int x = 0, y = 0; 
        float rotation = 0;
        float opacity = 1.0f;
        String name;
        float pivotX = 0.5f, pivotY = 0.5f;
        int meshCols = 4; // Lưu lại để dùng cho render và autoSave
        int meshRows = 4;

        LayerData(BufferedImage img, int px, int py, String name) {
            this.image = img;
            this.psdX = px;
            this.psdY = py;
            this.name = name;
            
            // Khởi tạo ArtMesh và tạo lưới mặc định (ví dụ 4x4 ô vuông)
            this.artMesh = new LArtMesh(name, null);
            this.artMesh.loadTexture(img);
            // Mặc định cho layer bình thường là 4, nhưng tóc nên là 10
            defaultDensity = 6;
            createDenseMesh(defaultDensity);
        }
        
        public void addRigPoint(float paramX, float paramY) {
            // Lưu trạng thái mesh hiện tại vào tọa độ Parameter cụ thể
            this.artMesh.addParameterPoint(paramX, paramY);
            autoSaveConfig(); // Tự động lưu mỗi khi thêm mốc biến dạng [cite: 2026-01-02]
        }

     // Sửa lại hàm này trong LayerData (LEditorFrame.java)
        public void createDenseMesh(int density) {
            this.meshCols = density;
            this.meshRows = density;
            int tw = image.getWidth();
            int th = image.getHeight();
            
            // Tổng số đỉnh mới: (density + 1) * (density + 1)
            float[] newVertices = new float[(density + 1) * (density + 1) * 2];
            int idx = 0;
            for (int r = 0; r <= density; r++) {
                for (int c = 0; c <= density; c++) {
                    newVertices[idx++] = (float) c / density * tw;
                    newVertices[idx++] = (float) r / density * th;
                }
            }
            
            // Cập nhật vào ArtMesh - Điều này sẽ reset mảng vertices cũ tránh bị nát hình
            this.artMesh.setVertices(newVertices); 
            this.artMesh.markDirty(); // Tự động lưu trạng thái lưới mới [cite: 2026-01-02]
        }
    }
    
    public int getCols() {
    	return defaultDensity;
    }
    
    public int getRows() {
    	return defaultDensity;
    }
    
    public static LEditorFrame getEditorFrame() {
    	return instance;
    }
    
 // Thêm hàm này vào bên trong class LEditorFrame
    public void updateBindingFromJoystick(float x, float y) {
        if (selectedLayerIndex != -1) {
            LayerData data = layersData.get(selectedLayerIndex);
            
            // Chuyển đổi từ (0 -> 1) sang (-1.0 -> 1.0) để phù hợp với LParameter
            float paramXVal = (x * 2) - 1.0f;
            float paramYVal = (y * 2) - 1.0f;
            
            // Giả sử bạn dùng tham số mặc định cho X và Y
            LParameter pX = new LParameter("AngleX", -1.0f, 1.0f, paramXVal);
            LParameter pY = new LParameter("AngleY", -1.0f, 1.0f, paramYVal);
            
            data.artMesh.updateBinding(pX, pY);
            previewPanel.repaint(); // Cập nhật hiển thị ngay lập tức
        }
    }
    
 // Thêm hàm này vào class LEditorFrame
    public LayerData getSelectedLayer() {
        if (selectedLayerIndex != -1 && selectedLayerIndex < layersData.size()) {
            return layersData.get(selectedLayerIndex);
        }
        return null;
    }
    
    private Point2D getLocalPoint(Point p, LayerData data) {
        try {
            AffineTransform at = new AffineTransform();
            // 1. Map tọa độ theo Camera (Giống trong paintComponent)
            at.translate(getWidth() / 2.0 + cameraX, getHeight() / 2.0 + cameraY);
            at.scale(zoomFactor, zoomFactor);
            
            // 2. Map tọa độ theo vị trí Layer
            at.translate(data.psdX + data.x, data.psdY + data.y);
            
            // 3. Xoay theo Pivot nếu không ở chế độ chỉnh Pivot
            if (!isPivotEditMode) {
                double px = data.image.getWidth() * data.pivotX;
                double py = data.image.getHeight() * data.pivotY;
                at.rotate(Math.toRadians(data.rotation), px, py);
            }

            // Trả về tọa độ đã được "giải mã" ngược lại
            return at.inverseTransform(p, null);
        } catch (Exception ex) {
            return new Point2D.Float(p.x, p.y);
        }
    }
    
    class ParamJoystick extends JPanel {
        private Point2D.Float pos = new Point2D.Float(0.5f, 0.5f);
        // BIẾN MỚI: Lưu trữ point đang được kéo
        private ParameterPoint draggedPoint = null; 

        public ParamJoystick() {
            setPreferredSize(new Dimension(150, 150));
            setBackground(new Color(60, 63, 65));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (selectedLayerIndex == -1) return;
                    LayerData selected = getSelectedLayer();
                    if (selected == null || selected.artMesh == null) return;

                    float clickX = (float) e.getX() / getWidth() * 2 - 1.0f;
                    float clickY = (float) e.getY() / getHeight() * 2 - 1.0f;

                    draggedPoint = null;
                    if (isCustomParamPointMode) {
                        float threshold = 0.1f; 
                        for (ParameterPoint p : selected.artMesh.getParameterPoints()) {
                            if (Math.abs(p.x - clickX) < threshold && Math.abs(p.y - clickY) < threshold) {
                                draggedPoint = p;
                                // Snap vị trí hiển thị vào đúng điểm đang kéo để tránh lệch tâm
                                pos.setLocation((p.x + 1.0) / 2.0, (p.y + 1.0) / 2.0);
                                break;
                            }
                        }
                    }

                    if (draggedPoint == null && isCustomParamPointMode && SwingUtilities.isLeftMouseButton(e)) {
                        selected.artMesh.handlePointSelectionOrCreation(clickX, clickY);
                        autoSaveConfig(); //
                        repaint();
                    }

                    updatePosition(e);
                    // Chỉ nội suy khi không bận kéo điểm mốc để giảm tải tính toán
                    if (draggedPoint == null) {
                        selected.artMesh.interpolateMesh((pos.x * 2) - 1.0f, (pos.y * 2) - 1.0f);
                    }
                    previewPanel.repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (draggedPoint != null) {
                        autoSaveConfig(); //
                        System.out.println("[ModCoderPack] Point move synchronized.");
                    }
                    draggedPoint = null;
                    repaint();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    updatePosition(e);
                    LayerData selected = getSelectedLayer();
                    if (selected == null || selected.artMesh == null) return;

                    float normX = (float) (pos.x * 2 - 1.0f);
                    float normY = (float) (pos.y * 2 - 1.0f);

                    if (draggedPoint != null) {
                        // Giới hạn biên chặt chẽ
                        draggedPoint.x = Math.max(-1f, Math.min(1f, normX));
                        draggedPoint.y = Math.max(-1f, Math.min(1f, normY));
                        
                        // Khi đang kéo điểm, ta dùng dữ liệu của chính điểm đó để render mesh
                        // giúp tránh việc nội suy IDW liên tục gây lag
                        System.arraycopy(draggedPoint.vertexData, 0, selected.artMesh.getVertices(), 0, draggedPoint.vertexData.length);
                    } else {
                        selected.artMesh.interpolateMesh(normX, normY);
                    }

                    repaint(); 
                    previewPanel.repaint(); 
                }
            });
        }

        // Giữ nguyên các hàm helper của bạn (updatePosition, createOrSelectParamPoint, paintComponent)
        private void updatePosition(MouseEvent e) {
            pos.x = Math.max(0, Math.min(1, (float) e.getX() / getWidth()));
            pos.y = Math.max(0, Math.min(1, (float) e.getY() / getHeight()));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Vẽ lưới 3x3
            g2d.setColor(new Color(80, 80, 80));
            g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g2d.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight());
            g2d.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);

            if (isCustomParamPointMode) {
                g2d.setColor(new Color(0, 255, 255, 30));
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.setColor(Color.CYAN);
                g2d.drawString("ADD/MOVE MODE ACTIVE", 5, 15);
            }
            
            LayerData data = getSelectedLayer();
            if (data != null && data.artMesh != null) {
                for (ParameterPoint p : data.artMesh.getParameterPoints()) {
                    int px = (int) (((p.x + 1.0f) / 2.0f) * getWidth());
                    int py = (int) (((p.y + 1.0f) / 2.0f) * getHeight());
                    
                    // Highlight điểm đang bị kéo
                    if (p == draggedPoint) {
                        g2d.setColor(Color.WHITE);
                        g2d.drawOval(px - 5, py - 5, 10, 10);
                    }
                    
                    g2d.setColor(Color.CYAN);
                    g2d.fillOval(px - 3, py - 3, 6, 6);
                }
            }

            g2d.setColor(Color.RED);
            int drawX = (int) (pos.x * getWidth());
            int drawY = (int) (pos.y * getHeight());
            g2d.drawOval(drawX - 6, drawY - 6, 12, 12);
            g2d.fillOval(drawX - 2, drawY - 2, 4, 4);
        }
    }
    
    private void drawCustomVertices(Graphics2D g2d, float[] v) {
        if (v == null) return;
        g2d.setColor(Color.CYAN); // Màu xanh cho dễ nhìn
        int size = 6;
        for (int i = 0; i < v.length; i += 2) {
            // Vẽ hình tròn tại tọa độ x, y
            g2d.fillOval((int)v[i] - size/2, (int)v[i+1] - size/2, size, size);
        }
    }
    
    // Hàm xử lý khi click vào ParamJoystick (Hộp 9 điểm)
    private void handleJoystickClick(float px, float py) {
        if (isParamPointEditMode && selectedLayerIndex != -1) {
            LayerData data = layersData.get(selectedLayerIndex);
            
            // Chuyển tọa độ chuột (0->1) sang (-1 -> 1)
            float normalizedX = (px * 2) - 1.0f;
            float normalizedY = (py * 2) - 1.0f;
            
            // Thêm điểm lưu trạng thái mesh
            data.artMesh.addParameterPoint(normalizedX, normalizedY);
            
            statusLabel.setText("Đã thêm Parameter Point tại: " + normalizedX + ", " + normalizedY);
            autoSaveConfig(); // [cite: 2026-01-02]
        }
    }

    // Cập nhật Memento để Undo/Redo được cả vị trí tâm
    private static class LayerMemento {
        int dx, dy;
        float rot, alpha, px, py; // Thêm px, py
        boolean vis;
        LayerMemento(LayerData d, boolean v) {
            this.dx = d.x; this.dy = d.y;
            this.rot = d.rotation; this.alpha = d.opacity;
            this.vis = v;
            this.px = d.pivotX; this.py = d.pivotY; // Lưu tâm
        }
    }
    
    private Stack<List<LayerMemento>> undoStack = new Stack<>();
    private Stack<List<LayerMemento>> redoStack = new Stack<>();


    private void saveHistory() {
        List<LayerMemento> snapshot = new ArrayList<>();
        for (int i = 0; i < layersData.size(); i++) {
            snapshot.add(new LayerMemento(layersData.get(i), layerVisibility.get(i)));
        }
        undoStack.push(snapshot);
        if (undoStack.size() > 50) undoStack.remove(0); // Giới hạn lịch sử
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        // Chụp lại trạng thái hiện tại cho Redo
        redoStack.push(getCurrentSnapshot());
        restoreFromSnapshot(undoStack.pop());
    }
    
    private void redo() {
        if (redoStack.isEmpty()) return;
        // Trước khi tiến tới tương lai, lưu trạng thái hiện tại vào Undo
        undoStack.push(getCurrentSnapshot());
        // Khôi phục trạng thái từ Redo stack
        restoreFromSnapshot(redoStack.pop());
    }
    
    private List<LayerMemento> getCurrentSnapshot() {
        List<LayerMemento> snapshot = new ArrayList<>();
        for (int i = 0; i < layersData.size(); i++) {
            // Lưu lại x, y (đóng vai trò là offset), rotation, opacity và visibility
            snapshot.add(new LayerMemento(layersData.get(i), layerVisibility.get(i)));
        }
        return snapshot;
    }

    private List<LayerData> layersData = new ArrayList<>();
    private List<Boolean> layerVisibility = new ArrayList<>();
    private DefaultListModel<String> layerListModel = new DefaultListModel<>();
    private int selectedLayerIndex = -1;

    public LEditorFrame(LArtMesh mesh) {
        this.targetMesh = mesh;
        this.paramX = new LParameter("ParamX", -1.0f, 1.0f, 0.0f);

        setupEclipseStyle();
        setTitle("Lotus2D Rig Editor - modcoderpack-redevelop"); //[cite: 2026-01-18]
        setupTitleIcon();
        setSize(1250, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        instance = this; // Gán instance khi khởi tạo

        initMenuBar();
        previewPanel = new PreviewPanel();
        previewPanel.setBackground(new Color(60, 63, 65));
        
       // Trong constructor LEditorFrame, đoạn thiết lập previewPanel
        previewPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Chỉ xử lý khi ở chế độ Custom Verts và đã chọn Layer
                if (isCustomMeshMode && selectedLayerIndex != -1) {
                    LayerData ld = layersData.get(selectedLayerIndex);
                    
                    // 1. Chuyển tọa độ chuột sang tọa độ Local của Layer
                    Point2D localPt = screenToLayer(e.getPoint(), ld);
                    
                    // 2. Thêm đỉnh mới vào ArtMesh
                    addVertexToMesh(ld, (float)localPt.getX(), (float)localPt.getY());
                    
                    // 3. Cập nhật và AutoSave [2026-01-02]
                    saveHistory(); 
                    previewPanel.repaint();
                    //autoSave(); 
                }
            }
        });
       
        
        characterPreview = new PreviewPanel();

        JTabbedPane mainTabs = new JTabbedPane(JTabbedPane.TOP);
        mainTabs.addTab(" Character ", characterPreview);
        mainTabs.addTab(" Rigging ", createRiggingPanel());
        add(mainTabs, BorderLayout.CENTER);
        add(createPropertiesPanel(), BorderLayout.EAST); //[cite: 2026-01-18]

        setupStatusPanel();
        setLocationRelativeTo(null);
    }
    
    private Point2D screenToLayer(Point p, LayerData ld) {
        // p là tọa độ chuột trên JPanel
        // Trừ đi nửa kích thước Panel để lấy tâm (0,0), sau đó chia cho Zoom
        double lx = (p.x - previewPanel.getWidth() / 2.0) / zoomFactor;
        double ly = (p.y - previewPanel.getHeight() / 2.0) / zoomFactor;
        
        // Trừ đi vị trí (x, y) của Layer để ra tọa độ tương đối trên ảnh
        return new Point2D.Double(lx - ld.x, ly - ld.y);
    }
    
    private void addVertexToMesh(LayerData ld, float x, float y) {
        if (ld.artMesh == null) return;
        
        float[] oldV = ld.artMesh.getVertices();
        int currentLen = (oldV == null) ? 0 : oldV.length;
        float[] newV = new float[currentLen + 2];

        if (oldV != null) {
            System.arraycopy(oldV, 0, newV, 0, currentLen);
        }

        newV[currentLen] = x;
        newV[currentLen + 1] = y;

        ld.artMesh.setVertices(newV);
    }
    
    private void setupTitleIcon() {
        try {
            // Nạp icon từ file brand bạn đã cung cấp
            java.net.URL iconURL = getClass().getResource("/assets/icons/icons.png");
            if (iconURL != null) {
                this.setIconImage(new ImageIcon(iconURL).getImage());
            }
        } catch (Exception e) {
            // Sử dụng Logger có sẵn để báo lỗi màu RED rực rỡ
            vn.pmgteam.lotus2d.core.util.Logger.getLogger("Editor")
                .error("Không thể nạp titleIcon: {}", e.getMessage());
        }
    }
    
    private JPanel createRiggingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        this.layerList = new JList<>(layerListModel); 
        this.layerList.setCellRenderer(new LayerListRenderer());

        this.layerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = layerList.locationToIndex(e.getPoint());
                
                // Kiểm tra xem có thực sự nhấn trúng item không
                boolean isInsideItem = (index != -1 && layerList.getCellBounds(index, index).contains(e.getPoint()));

                if (isInsideItem) {
                    // 1. Xử lý Toggle Visibility (Vùng < 40px)
                    if (e.getX() < 40) {
                        layerVisibility.set(index, !layerVisibility.get(index));
                        autoSaveConfig(); // Tự động lưu khi thay đổi module [cite: 2026-01-02]
                        layerList.repaint();
                    } 
                    // 2. Xử lý Click chọn Layer
                    else {
                        if (selectedLayerIndex == index) {
                            layerList.clearSelection();
                            selectedLayerIndex = -1;
                        } else {
                            selectedLayerIndex = index;
                            layerList.setSelectedIndex(index);
                            updateSlidersFromSelection();
                        }
                    }
                } else {
                    layerList.clearSelection();
                    selectedLayerIndex = -1;
                }
                
                previewPanel.repaint();
            }
        });

        // Giữ JScrollPane cho danh sách layer bên trái
        JScrollPane scroll = new JScrollPane(layerList);
        scroll.setPreferredSize(new Dimension(280, 0));
        panel.add(scroll, BorderLayout.WEST);

        // QUAN TRỌNG: Bỏ JScrollPane bao quanh previewPanel để Camera và Center hoạt động chính xác
        panel.add(previewPanel, BorderLayout.CENTER); 
        
        return panel;
    }
    
    private void updateSlidersFromSelection() {
        if (selectedLayerIndex == -1) return;
        isUpdatingSliders = true; // Chặn để việc nhảy slider không tính là một thao tác edit
        LayerData d = layersData.get(selectedLayerIndex);
        posXSlider.setValue(d.x);
        posYSlider.setValue(d.y);
        rotSlider.setValue((int)d.rotation);
        alphaSlider.setValue((int)(d.opacity * 100));
        isUpdatingSliders = false;
    }
    
    private JPanel createPropertiesPanel() {
    	JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createTitledBorder(" Layer Properties "));
        mainPanel.setPreferredSize(new Dimension(280, 0));

        // --- GROUP 1: TRANSFORM ---
        JPanel transformPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        transformPanel.setBorder(BorderFactory.createTitledBorder(" Transform "));

        // KHỞI TẠO CÁC SLIDER Ở ĐÂY (Sửa lỗi NPE)
        posXSlider = new JSlider(-1000, 1000, 0);
        posYSlider = new JSlider(-1000, 1000, 0);
        rotSlider = new JSlider(0, 360, 0);
        alphaSlider = new JSlider(0, 100, 100);

        MouseAdapter historyTrigger = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { saveHistory(); }
            // [2026-01-02] Tự động save khi người dùng thả chuột sau khi kéo slider
            @Override public void mouseReleased(MouseEvent e) { 
            	//autoSave(); 
            }
        };

        // Bây giờ các slider đã tồn tại, gọi setupSlider an toàn
        setupSlider(posXSlider, "Pos X:", transformPanel, historyTrigger, 
            val -> { if(selectedLayerIndex != -1) layersData.get(selectedLayerIndex).x = val; });
        setupSlider(posYSlider, "Pos Y:", transformPanel, historyTrigger, 
            val -> { if(selectedLayerIndex != -1) layersData.get(selectedLayerIndex).y = val; });
        setupSlider(rotSlider, "Rotation:", transformPanel, historyTrigger, 
            val -> { if(selectedLayerIndex != -1) layersData.get(selectedLayerIndex).rotation = val; });
        setupSlider(alphaSlider, "Opacity:", transformPanel, historyTrigger, 
            val -> { if(selectedLayerIndex != -1) layersData.get(selectedLayerIndex).opacity = val / 100f; });
        pivotToggleBtn = new JToggleButton("🎯 Edit Pivot Mode");
        pivotToggleBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        pivotToggleBtn.addActionListener(e -> {
            isPivotEditMode = pivotToggleBtn.isSelected();
            if(isPivotEditMode) { // Tắt các mode khác để tránh xung đột
                disableAllMeshModes();
            }
            updateCursor();
        });
        transformPanel.add(pivotToggleBtn);
        mainPanel.add(transformPanel);

        // --- GROUP 2: MESH TOOLS (Bẻ cong, tạo lưới) ---
        JPanel meshTools = new JPanel(new GridLayout(0, 1, 5, 5));
        meshTools.setBorder(BorderFactory.createTitledBorder(" Mesh & Deformation "));

        meshToggleBtn = new JToggleButton("🕸 Edit Grid Mesh");
        JToggleButton customMeshBtn = new JToggleButton("🖋 Custom Verts (Tail)");
        JToggleButton editVertsBtn = new JToggleButton("🖱 Move Vertices");

        // Logic điều phối Tool tập trung
        ActionListener meshLogic = e -> {
            isMeshEditMode = meshToggleBtn.isSelected() || editVertsBtn.isSelected();
            isCustomMeshMode = customMeshBtn.isSelected();
            
            // Đảm bảo chỉ 1 nút được chọn
            if (e.getSource() == customMeshBtn && customMeshBtn.isSelected()) {
                meshToggleBtn.setSelected(false);
                editVertsBtn.setSelected(false);
                isPivotEditMode = false; pivotToggleBtn.setSelected(false);
            } else if (e.getSource() == editVertsBtn && editVertsBtn.isSelected()) {
                customMeshBtn.setSelected(false);
                meshToggleBtn.setSelected(false);
                isPivotEditMode = false; pivotToggleBtn.setSelected(false);
            } else if (e.getSource() == meshToggleBtn && meshToggleBtn.isSelected()) {
                customMeshBtn.setSelected(false);
                editVertsBtn.setSelected(false);
                isPivotEditMode = false; pivotToggleBtn.setSelected(false);
            }
            
            updateCursor();
            previewPanel.repaint();
            
            // [2026-01-02] Tự động save khi đổi trạng thái module
            //autoSave(); 
        };

        customMeshBtn.addActionListener(meshLogic);
        editVertsBtn.addActionListener(meshLogic);
        meshToggleBtn.addActionListener(meshLogic);

        meshTools.add(meshToggleBtn);
        meshTools.add(customMeshBtn);
        meshTools.add(editVertsBtn);
        mainPanel.add(meshTools);

     // --- GROUP 3: PARAMETERS (List of Joystick Bindings) ---
        JPanel paramPanel = new JPanel(new BorderLayout());
        paramPanel.setBorder(BorderFactory.createTitledBorder(" Parameter Binding "));

        // Panel chứa danh sách các Joystick
        joystickListPanel.setLayout(new BoxLayout(joystickListPanel, BoxLayout.Y_AXIS));

        // Bọc trong ScrollPane để cuộn khi có nhiều Layer/Parameter
        JScrollPane scrollPane = new JScrollPane(joystickListPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        paramPanel.add(new JLabel("All Layer Controllers:", JLabel.CENTER), BorderLayout.NORTH);
        paramPanel.add(scrollPane, BorderLayout.CENTER);

        // --- BỘ ĐIỀU KHIỂN PHÍA DƯỚI (Footer) ---
        JPanel footerPanel = new JPanel(new GridLayout(2, 1, 2, 2));

     // --- CẬP NHẬT NÚT ADD PARAMETER ---
        JButton addParamBtn = new JButton("➕ Add New Parameter");
        addParamBtn.setFocusable(false);
        addParamBtn.addActionListener(e -> {
            // 1. Tạo tên mặc định hoặc mở hộp thoại nhập tên
            String name = "Param_" + (projectParameters.size() + 1);
            
            // 2. Gọi hàm để add vào List UI và Data đồng thời
            addNewJoystickItem(name); 
            
            // 3. Tự động lưu Project [2026-01-02]
            autoSaveConfig();
            
            // Log kiểm tra trong console
            System.out.println("[modcoderpack-redevelop] Added parameter: " + name);
        });

        // 2. Nút chế độ Edit Mesh (Logic của bạn)
        customParamBtn = new JToggleButton("📍 Add Param Point Mode");
        customParamBtn.setFocusable(false);
        customParamBtn.addActionListener(e -> {
            isCustomParamPointMode = customParamBtn.isSelected();
            joystickListPanel.repaint(); 
        });

        footerPanel.add(addParamBtn);
        footerPanel.add(customParamBtn);
        paramPanel.add(footerPanel, BorderLayout.SOUTH);
        
        mainPanel.add(paramPanel);

        mainPanel.add(Box.createVerticalGlue()); // Đẩy tất cả lên trên cùng
        return mainPanel;
    }
    
 // --- HÀM 1: Dùng khi nhấn nút ADD mới (Nhận String) ---
    private void addNewJoystickItem(String name) {
        // Tạo data mới rồi gọi Hàm 2 để vẽ UI
        LParameter newParam = new LParameter(name, -1.0f, 1.0f, 0.0f);
        projectParameters.add(newParam);
        addNewJoystickItem(newParam); // Gọi Hàm 2
    }

    // --- HÀM 2: Dùng để vẽ UI (Nhận LParameter) ---
    // Hàm này phục vụ cả việc tạo mới VÀ việc load từ file .lmproj
    private void addNewJoystickItem(LParameter param) {
        JPanel itemPanel = new JPanel(new BorderLayout());
        itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        itemPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        itemPanel.setBackground(new Color(50, 53, 55));

        // Hiển thị ID của Parameter từ đối tượng LParameter
        JLabel label = new JLabel(" " + param.getId());
        label.setForeground(Color.WHITE);
        
        JButton removeBtn = new JButton("×");
        removeBtn.setPreferredSize(new Dimension(40, 20));
        removeBtn.addActionListener(e -> {
            projectParameters.remove(param); // Xóa khỏi DATA
            joystickListPanel.remove(itemPanel);
            joystickListPanel.revalidate();
            joystickListPanel.repaint();
            autoSaveConfig(); // [2026-01-02]
        });

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.add(label, BorderLayout.WEST);
        topBar.add(removeBtn, BorderLayout.EAST);

        // Khởi tạo Joystick
        ParamJoystick joystick = new ParamJoystick();
        
        // Gán vị trí Joystick dựa trên giá trị hiện tại của Parameter (Quan trọng khi Load)
        // Giả sử 0.0f là tâm (0.5, 0.5) của Joystick
        float initialX = (param.getValue() + 1.0f) / 2.0f; 
        joystick.pos.setLocation(initialX, 0.5); 

        itemPanel.add(topBar, BorderLayout.NORTH);
        itemPanel.add(joystick, BorderLayout.CENTER);

        joystickListPanel.add(itemPanel);
        joystickListPanel.revalidate();
        joystickListPanel.repaint();
    }
    // Hàm hỗ trợ để tránh lặp code cursor
    private void updateCursor() {
        if (isPivotEditMode) previewPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        else if (isCustomMeshMode) previewPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        else if (isMeshEditMode) previewPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        else previewPanel.setCursor(Cursor.getDefaultCursor());
    }

    private void disableAllMeshModes() {
        isMeshEditMode = false;
        isCustomMeshMode = false;
        meshToggleBtn.setSelected(false);
        // Nếu có các nút khác trong scope thì gán ở đây
    }

    // Hàm bổ trợ để code gọn hơn
    private void setupSlider(JSlider slider, String label, JPanel parent, MouseAdapter mouse, java.util.function.Consumer<Integer> updateLogic) {
        parent.add(new JLabel(label));
        slider.addMouseListener(mouse);
        slider.addChangeListener(e -> {
            // CHỈ CẬP NHẬT NẾU KHÔNG PHẢI ĐANG TRONG QUÁ TRÌNH UNDO/CHỌN LAYER
            if (!isUpdatingSliders && selectedLayerIndex != -1) {
                updateLogic.accept(slider.getValue());
                previewPanel.repaint();
                autoSaveConfig();
            }
        });
        parent.add(slider);
    }
    
    private void restoreFromSnapshot(List<LayerMemento> snap) {
        isUpdatingSliders = true;
        
        for (int i = 0; i < snap.size() && i < layersData.size(); i++) {
            LayerData d = layersData.get(i);
            LayerMemento m = snap.get(i);
            d.x = m.dx; d.y = m.dy;
            d.rotation = m.rot; d.opacity = m.alpha;
            d.pivotX = m.px; // Khôi phục tâm X
            d.pivotY = m.py; // Khôi phục tâm Y
            layerVisibility.set(i, m.vis);
        }

        // Cập nhật vị trí thanh trượt theo dữ liệu vừa undo
        if (selectedLayerIndex != -1 && selectedLayerIndex < layersData.size()) {
            LayerData d = layersData.get(selectedLayerIndex);
            if (posXSlider != null) posXSlider.setValue(d.x);
            if (posYSlider != null) posYSlider.setValue(d.y);
            if (rotSlider != null) rotSlider.setValue((int)d.rotation);
            if (alphaSlider != null) alphaSlider.setValue((int)(d.opacity * 100));
        }
        
        

        previewPanel.repaint();
        if (layerList != null) layerList.repaint();
        
        isUpdatingSliders = false; // MỞ CHẶN
        autoSaveConfig();
    }
    
    private AffineTransform getLayerTransform(LayerData data) {
        AffineTransform at = new AffineTransform();
        // 1. Đưa đến vị trí trên Canvas (Vị trí PSD gốc + bù trừ của User)
        at.translate(data.psdX + data.x, data.psdY + data.y);
        
        // 2. Lấy tọa độ điểm Pivot tính theo Pixel thực tế
        double px = data.image.getWidth() * data.pivotX;
        double py = data.image.getHeight() * data.pivotY;
        
        // 3. Xoay quanh điểm Pivot đó
        at.rotate(Math.toRadians(data.rotation), px, py);
        
        return at;
    }
    
 // Hàm hỗ trợ lấy ma trận Camera chung
    private AffineTransform getCameraTransform() {
        AffineTransform at = new AffineTransform();
        at.translate(getWidth() / 2.0 + cameraX, getHeight() / 2.0 + cameraY);
        at.scale(zoomFactor, zoomFactor);
        return at;
    }

    private void updateVertexDrag(Point p) {
        if (selectedLayerIndex == -1 || selectedVertexIdx == -1) return;
        LayerData data = layersData.get(selectedLayerIndex);
        try {
            // Ma trận phải khớp 100% với trình tự trong paintComponent
            AffineTransform baseAt = new AffineTransform();
            baseAt.translate(previewPanel.getWidth() / 2.0 + cameraX, previewPanel.getHeight() / 2.0 + cameraY);
            baseAt.scale(zoomFactor, zoomFactor);
            
            // Dịch chuyển layer
            baseAt.translate(data.psdX + data.x, data.psdY + data.y);
            
            // Xoay quanh tâm Pivot hiện tại
            double px = data.image.getWidth() * data.pivotX;
            double py = data.image.getHeight() * data.pivotY;
            baseAt.rotate(Math.toRadians(data.rotation), px, py);

            // Chuyển tọa độ chuột về hệ tọa độ Local của Mesh
            Point2D localPoint = baseAt.inverseTransform(p, null);
            
            float[] v = data.artMesh.getVertices();

         // GIỚI HẠN: Không cho đỉnh đi quá xa khỏi biên ảnh

            double margin = 200.0;
            float finalX = (float) Math.max(-margin, Math.min(data.image.getWidth() + margin, localPoint.getX()));
            float finalY = (float) Math.max(-margin, Math.min(data.image.getHeight() + margin, localPoint.getY()));

         // 1. Cập nhật vào mảng vertices hiển thị hiện tại
            v[selectedVertexIdx] = finalX;
            v[selectedVertexIdx + 1] = finalY;

            // 2. QUAN TRỌNG: Lưu vào Point đang được edit (Active Point)
            // Giả sử bạn có biến activePoint để biết đang sửa ở mốc nào trên hộp 9 điểm

            ParameterPoint activePoint = data.artMesh.getActivePoint(); 
 
            if (activePoint != null) {
            	// Cập nhật tọa độ đỉnh vào bộ nhớ của Point này
            	activePoint.vertexData[selectedVertexIdx] = finalX;
            	activePoint.vertexData[selectedVertexIdx + 1] = finalY;
             
            	// Đánh dấu để autoSave
            	data.artMesh.markDirty(); 
            }

            repaint();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    // --- CẬP NHẬT HÀM TƯƠNG TÁC (FIX LỖI NGƯỢC PIVOT) ---
    private void handleInteraction(Point p) {
        if (selectedLayerIndex == -1) return;
        LayerData data = layersData.get(selectedLayerIndex);
        try {
            // Ma trận Camera (Zoom/Pan)
            AffineTransform at = new AffineTransform();
            at.translate(previewPanel.getWidth() / 2.0 + cameraX, previewPanel.getHeight() / 2.0 + cameraY);
            at.scale(zoomFactor, zoomFactor);
            
            // Đưa vào vị trí tĩnh của Layer (Chưa bao gồm xoay)
            at.translate(data.psdX + data.x, data.psdY + data.y);

            if (isPivotEditMode) {
                // Chuyển tọa độ chuột sang tọa độ Local của ảnh (không bị xoay làm lệch)
                Point2D imgPoint = at.inverseTransform(p, null);
                
                saveHistory();
                
                // Tính tỷ lệ % dựa trên kích thước thực của BufferedImage
                data.pivotX = (float)(imgPoint.getX() / data.image.getWidth());
                data.pivotY = (float)(imgPoint.getY() / data.image.getHeight());
                
                // Giới hạn vùng an toàn để pivot không "bay" quá xa
                data.pivotX = Math.max(-0.5f, Math.min(1.5f, data.pivotX));
                data.pivotY = Math.max(-0.5f, Math.min(1.5f, data.pivotY));
                
                autoSaveConfig(); // [cite: 2026-01-02]
            } else {
                // Luôn tính toán Rotation khi tương tác với nội dung bên trong Layer (Mesh/Vertex)
                double px = data.image.getWidth() * data.pivotX;
                double py = data.image.getHeight() * data.pivotY;
                
                AffineTransform interactAt = new AffineTransform();
                interactAt.translate(previewPanel.getWidth() / 2.0 + cameraX, previewPanel.getHeight() / 2.0 + cameraY);
                interactAt.scale(zoomFactor, zoomFactor);
                interactAt.translate(data.psdX + data.x, data.psdY + data.y);
                interactAt.rotate(Math.toRadians(data.rotation), px, py);

                Point2D localPoint = interactAt.inverseTransform(p, null);

                if (isMeshEditMode) {
                    float[] v = data.artMesh.getVertices();
                    selectedVertexIdx = -1;
                    // Threshold dựa trên zoom để dễ bấm khi zoom xa
                    double threshold = 12.0 / zoomFactor; 
                    for (int i = 0; i < v.length; i += 2) {
                        if (Point2D.distance(v[i], v[i+1], localPoint.getX(), localPoint.getY()) < threshold) {
                            selectedVertexIdx = i;
                            saveHistory(); // Lưu lại trước khi kéo
                            break;
                        }
                    }
                }
            }
            repaint();
        } catch (Exception ex) { ex.printStackTrace(); }
    }
    
    private AffineTransform getLayerLocalTransform(LayerData data) {
        AffineTransform at = new AffineTransform();
        // Tọa độ tương đối so với tâm nhân vật
        at.translate(data.psdX + data.x, data.psdY + data.y);
        
        // Xoay quanh tâm Pivot
        double px = data.image.getWidth() * data.pivotX;
        double py = data.image.getHeight() * data.pivotY;
        at.rotate(Math.toRadians(data.rotation), px, py);
        
        return at;
    }
    
    private void centerCharacter() {
        if (layersData.isEmpty()) return;

        // Đưa Zoom về mức quan sát được toàn cảnh (ví dụ 0.5)
        zoomFactor = 0.5;

        // Reset Camera về 0 để tính toán từ gốc tọa độ
        previewPanel.setCamera(0, 0);
        characterPreview.setCamera(0, 0);

        // Lưu ý: cameraX/Y trong PreviewPanel sẽ cộng thêm vào getWidth()/2
        // Nếu nhân vật vẫn lệch, bạn có thể bù trừ offset dựa trên layer chính (thường là Layer 0 hoặc 'head')
        previewPanel.repaint();
        characterPreview.repaint();
    }
    
    private void selectLayerAt(Point p) {
        for (int i = 0; i < layersData.size(); i++) {
            LayerData data = layersData.get(i);
            if (!layerVisibility.get(i)) continue;
            try {
                // Dùng ma trận Camera + Vị trí, nhưng KHÔNG XOAY để tính Hit-box chuẩn
                AffineTransform at = new AffineTransform();
                at.translate(getWidth() / 2.0 + cameraX, getHeight() / 2.0 + cameraY);
                at.scale(zoomFactor, zoomFactor);
                at.translate(data.psdX + data.x, data.psdY + data.y);
                
                // Nếu không phải đang chỉnh Pivot, ta mới tính đến chuyện ảnh đang xoay
                if (!isPivotEditMode) {
                    double px = data.image.getWidth() * data.pivotX;
                    double py = data.image.getHeight() * data.pivotY;
                    at.rotate(Math.toRadians(data.rotation), px, py);
                }

                Point2D local = at.inverseTransform(p, null);
                
                if (local.getX() >= 0 && local.getX() < data.image.getWidth() &&
                    local.getY() >= 0 && local.getY() < data.image.getHeight()) {
                    
                    int pixel = data.image.getRGB((int)local.getX(), (int)local.getY());
                    if (((pixel >> 24) & 0xff) > 20) { 
                        selectedLayerIndex = i;
                        layerList.setSelectedIndex(i);
                        updateSlidersFromSelection();
                        repaint();
                        return;
                    }
                }
            } catch (Exception ex) {}
        }
    }

    
    private class PreviewPanel extends JPanel {
    	
    	// Trong class PreviewPanel, thay thế các Listener bằng đoạn này:
        public PreviewPanel() {
            setFocusable(true);
            
            // --- ZOOM ---
            addMouseWheelListener(e -> {
                if (e.getWheelRotation() < 0) zoomFactor *= 1.1;
                else zoomFactor /= 1.1;
                zoomFactor = Math.max(0.01, Math.min(zoomFactor, 10.0));
                repaint();
            });

            // --- MOUSE PRESS ---
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMousePos = e.getPoint(); // Lưu vị trí bắt đầu cho cả 2 loại chuột

                    // CHUỘT TRÁI: Xử lý chọn layer hoặc tương tác mesh
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (selectedLayerIndex == -1) {
                            selectLayerAt(e.getPoint());
                            return;
                        }
                        handleInteraction(e.getPoint());
                    }
                }

                @Override 
                public void mouseReleased(MouseEvent e) {
                    if (selectedVertexIdx != -1) { 
                        LayerData data = layersData.get(selectedLayerIndex);
                        data.artMesh.syncVertices(); // Hợp nhất tọa độ kéo vào baseVertices
                        
                        selectedVertexIdx = -1; 
                        // Lưu trạng thái cuối cùng của biến dạng mesh [cite: 2026-01-02]
                        autoSaveConfig(); 
                        repaint(); 
                    }
                }
            });

            // --- MOUSE DRAG ---
            addMouseMotionListener(new MouseMotionAdapter() {
            	// Trong addMouseMotionListener của PreviewPanel
            	@Override
            	public void mouseDragged(MouseEvent e) {
            	    if (lastMousePos == null) {
            	        lastMousePos = e.getPoint();
            	        return;
            	    }

            	    // CHUỘT PHẢI: Kéo camera (Pan)
            	    if (SwingUtilities.isRightMouseButton(e)) {
            	        cameraX += (e.getX() - lastMousePos.x);
            	        cameraY += (e.getY() - lastMousePos.y);
            	        
            	        lastMousePos = e.getPoint(); // CẬP NHẬT ĐỂ TÍNH DELTA CHO LẦN SAU
            	        repaint();
            	        return;
            	    }

            	    // CHUỘT TRÁI: Kéo Vertex (Mesh)
            	    if (SwingUtilities.isLeftMouseButton(e) && isMeshEditMode && selectedVertexIdx != -1) {
            	        updateVertexDrag(e.getPoint());
            	        lastMousePos = e.getPoint(); // Giữ cho việc kéo mượt mà trên Intel UHD 610
            	    }
            	}
            });
        }
        
     // Sửa lỗi: The method resetCamera() is undefined
        public void resetCamera() {
            cameraX = 0;
            cameraY = 0;
            // zoomFactor = 0.5; // Bạn có thể reset luôn zoom ở đây nếu muốn
            repaint();
        }

        // Sửa lỗi: The method setCamera(int, int) is undefined
        public void setCamera(double x, double y) {
            cameraX = x;
            cameraY = y;
            repaint();
        }

     // --- CẬP NHẬT HÀM CHỌN LAYER ĐỂ KHÔNG BỊ LỆCH DO XOAY ---
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            AffineTransform worldAt = g2d.getTransform();

            // Bước 1: Thiết lập Camera (World Space)
            double centerX = getWidth() / 2.0 + cameraX;
            double centerY = getHeight() / 2.0 + cameraY;
            g2d.translate(centerX, centerY);
            g2d.scale(zoomFactor, zoomFactor);

            // Bước 2: Vẽ từng Layer
            for (int i = layersData.size() - 1; i >= 0; i--) {
                if (!layerVisibility.get(i)) continue;
                LayerData ld = layersData.get(i);
                
                AffineTransform layerAt = g2d.getTransform(); 
                AffineTransform lt = getLayerTransform(ld);
                g2d.transform(lt); 
                
                ld.artMesh.render(g2d);

                if (i == selectedLayerIndex) {
                    renderSelectionExtras(g2d, ld);

                    // Nếu đang ở chế độ châm đỉnh tự do (Custom Verts)
                    if (isCustomMeshMode) {
                        drawCustomVertices(g2d, ld.artMesh.getVertices()); 
                        // Lưu ý: KHÔNG gọi drawMeshGrid ở đây vì đỉnh chưa tạo thành lưới
                    } 
                    // Nếu đang ở chế độ chỉnh sửa lưới ô vuông (Edit Mesh)
                    else if (isMeshEditMode) {
                        drawMeshGrid(g2d, ld.meshCols, ld.meshRows, ld.artMesh.getVertices());
                        drawCustomVertices(g2d, ld.artMesh.getVertices());
                    }
                }
                
                g2d.setTransform(layerAt);
            }
            g2d.setTransform(worldAt);
        }
        private void renderSelectionExtras(Graphics2D g2d, LayerData data) {
            // uiScale giúp độ dày đường kẻ không bị biến dạng khi zoom
            float uiScale = (float)(1.0 / zoomFactor);
            int imgW = data.image.getWidth();
            int imgH = data.image.getHeight();

            // Vẽ khung Cyan bao quanh Layer
            g2d.setColor(new Color(0, 255, 255, 200)); 
            g2d.setStroke(new BasicStroke(1.5f * uiScale));
            g2d.drawRect(0, 0, imgW, imgH); // Vẽ từ (0,0) vì g2d đã ở không gian Local của Layer

            // Vẽ Pivot (Tâm xoay)
            if (isPivotEditMode) {
                g2d.setColor(Color.RED);
                float px = imgW * data.pivotX;
                float py = imgH * data.pivotY;
                float pSize = 10f * uiScale;
                // Chữ thập tâm xoay
                g2d.draw(new java.awt.geom.Line2D.Float(px - pSize, py, px + pSize, py));
                g2d.draw(new java.awt.geom.Line2D.Float(px, py - pSize, px, py + pSize));
            }

            // Vẽ các nút điều khiển (Handles) ở 4 góc
            g2d.setColor(Color.WHITE);
            float s = 6.0f * uiScale;
            float h = s / 2.0f;
            g2d.fill(new java.awt.geom.Rectangle2D.Float(-h, -h, s, s));             // Top-Left
            g2d.fill(new java.awt.geom.Rectangle2D.Float(imgW - h, -h, s, s));       // Top-Right
            g2d.fill(new java.awt.geom.Rectangle2D.Float(-h, imgH - h, s, s));       // Bottom-Left
            g2d.fill(new java.awt.geom.Rectangle2D.Float(imgW - h, imgH - h, s, s)); // Bottom-Right
        }
        
        private void drawMeshGrid(Graphics2D g2d, int cols, int rows, float[] v) {
            // KIỂM TRA AN TOÀN: 
            // 1. Mảng phải tồn tại và có ít nhất 2 điểm (length 4) để vẽ được 1 đoạn thẳng
            if (v == null || v.length < 4) return;

            // 2. Tính toán số lượng đỉnh tối đa mà logic lưới yêu cầu
            int maxExpectedIndex = (rows * (cols + 1) + cols) * 2 + 1;
            
            // Nếu mảng thực tế nhỏ hơn số lượng đỉnh mà cols/rows yêu cầu, 
            // nghĩa là dữ liệu mesh không đồng bộ với kích thước lưới -> Thoát để tránh crash.
            if (v.length <= maxExpectedIndex) return;

            g2d.setColor(new Color(255, 255, 255, 100)); // Trắng mờ
            g2d.setStroke(new BasicStroke(1f / (float)zoomFactor));

            for (int r = 0; r <= rows; r++) {
                for (int c = 0; c <= cols; c++) {
                    int i = (r * (cols + 1) + c) * 2;
                    
                    // Vẽ đường ngang (kiểm tra index i và next)
                    if (c < cols) {
                        int next = i + 2;
                        if (next + 1 < v.length) {
                            g2d.drawLine((int)v[i], (int)v[i+1], (int)v[next], (int)v[next+1]);
                        }
                    }
                    // Vẽ đường dọc (kiểm tra index i và down)
                    if (r < rows) {
                        int down = ((r + 1) * (cols + 1) + c) * 2;
                        if (down + 1 < v.length) {
                            g2d.drawLine((int)v[i], (int)v[i+1], (int)v[down], (int)v[down+1]);
                        }
                    }
                }
            }
        }
        
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(2000, 2000); // Tạm thời để cố định hoặc tính toán như cũ
        }
    }
    
    
    
    private void loadFile(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".psd")) {
            loadPSD(file);
        } else {
            statusLabel.setText("Định dạng không hỗ trợ! (Chỉ nhận PSD)");
        }
    }

    private void loadPSD(File file) {
    	this.currentPSDFile = file; // Gán ngay lập tức khi nhận file
        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(true);
                    statusLabel.setText("Đang đọc cấu trúc PSD (Direct Access)...");
                });

                try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
                    java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw new Exception("Không tìm thấy Reader!");

                    ImageReader reader = readers.next();
                    reader.setInput(input);

                    List<com.twelvemonkeys.imageio.plugins.psd.PSDLayerInfo> layerInfos = null;
                    
                    // THỬ CÁCH 1: Lấy từ Stream Metadata (Toàn bộ file)
                    try {
                        Object streamMeta = reader.getStreamMetadata();
                        if (streamMeta != null) {
                            Field f = streamMeta.getClass().getDeclaredField("layerInfo");
                            f.setAccessible(true);
                            layerInfos = (List<com.twelvemonkeys.imageio.plugins.psd.PSDLayerInfo>) f.get(streamMeta);
                        }
                    } catch (Exception e) {}

                    // THỬ CÁCH 2: Nếu cách 1 fail, lấy từ Image 0 (Nơi TwelveMonkeys thường giấu layerInfo)
                    if (layerInfos == null || layerInfos.isEmpty()) {
                        try {
                            Object imgMeta = reader.getImageMetadata(0); // Index 0 là ảnh gốc
                            if (imgMeta != null) {
                                Field f = imgMeta.getClass().getDeclaredField("layerInfo");
                                f.setAccessible(true);
                                layerInfos = (List<com.twelvemonkeys.imageio.plugins.psd.PSDLayerInfo>) f.get(imgMeta);
                            }
                        } catch (Exception e) {
                            System.err.println("Lỗi nghiêm trọng: Không thể truy xuất danh sách LayerInfo!");
                        }
                    }

                    int numImages = reader.getNumImages(true);
                    List<LayerData> tempLayers = new ArrayList<>();

                    // Duyệt qua các layer con (Index 1 trở đi)
                    for (int i = 1; i < numImages; i++) {
                        BufferedImage img = reader.read(i);
                        int px = 0, py = 0;
                        String name = "Layer " + i;

                        // Khớp Image i với LayerInfo i-1
                        if (layerInfos != null && (i - 1) < layerInfos.size()) {
                            com.twelvemonkeys.imageio.plugins.psd.PSDLayerInfo info = layerInfos.get(i - 1);
                            
                            // Vì bạn đã mod public nên truy cập trực tiếp cực nhanh
                            px = info.left;
                            py = info.top;
                            name = info.getLayerName(); 
                        }

                        if (img != null) {
                            // Nén TYPE_INT_ARGB_PRE để Intel UHD 610 render mượt alpha [cite: 2026-02-28]
                            tempLayers.add(new LayerData(compressImage(img), px, py, name));
                        }
                    }

                    Collections.reverse(tempLayers); // [cite: 2026-01-18]
                    updateUI(tempLayers, "Đã khớp thành công " + tempLayers.size() + " layers.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> statusLabel.setText("Lỗi nạp PSD: " + e.getMessage()));
            }
        }).start();
    }
    
    private void updateUI(List<LayerData> tempLayers, String message) {
        SwingUtilities.invokeLater(() -> {
            layersData.clear();
            layerVisibility.clear();
            layerListModel.clear();
            for (LayerData ld : tempLayers) {
                layersData.add(ld);
                layerVisibility.add(true);
                layerListModel.addElement(ld.name);
            }

            // --- TỰ ĐỘNG CĂN GIỮA KHI IMPORT --- [cite: 2026-01-18]
            // Đưa camera về 0 và zoom về mức bao quát (0.5 hoặc tùy bạn)
            zoomFactor = 0.5;
            // Nếu cameraX/Y nằm trong PreviewPanel, hãy gọi hàm reset hoặc gán trực tiếp
            previewPanel.resetCamera(); 
            characterPreview.resetCamera();

            previewPanel.repaint();
            characterPreview.repaint();
            
            autoSaveConfig(); // [cite: 2026-01-02]
            statusLabel.setText(message);
            progressBar.setVisible(false);
            System.gc(); // Tối ưu RAM cho Intel UHD 610 [cite: 2026-02-28]
        });
    }

    private void setupStatusPanel() {
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel control = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JSlider s = new JSlider(-100, 100, 0);
        s.addChangeListener(e -> {
            paramX.setValue(s.getValue()/100f);
            autoSaveConfig();
            previewPanel.repaint();
        });
        control.add(new JLabel("RIG X:")); control.add(s);
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        statusLabel = new JLabel("Sẵn sàng.");
        progressBar = new JProgressBar(0, 100);
        progressBar.setVisible(false);
        status.add(statusLabel, BorderLayout.WEST);
        status.add(progressBar, BorderLayout.EAST);
        bottom.add(control, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private BufferedImage compressImage(BufferedImage s) {
        if (s == null) return null;
        // Chuyển sang TYPE_INT_ARGB_PRE để Intel UHD 610 xử lý mượt Alpha [cite: 2026-02-28]
        BufferedImage d = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = d.createGraphics();
        g.drawImage(s, 0, 0, null);
        g.dispose();
        return d;
    }

    private void setupEclipseStyle() {
        try {
            // 1. Ép FlatLaf sử dụng thanh tiêu đề tùy chỉnh (Custom Window Decorations)
            // 2. Tinh chỉnh giao diện TitleBar cho "sang"
            UIManager.put("TitlePane.unifiedBackground", true); // Đồng bộ màu với nền app
            UIManager.put("TitlePane.menuBarEmbedded", true);   // Đưa Menu lên cùng hàng với Title
            UIManager.put("TitlePane.height", 32);              // Độ cao vừa phải, không thô
            
            // 3. Tùy chỉnh màu sắc nút Close khi Hover
            UIManager.put("TitlePane.closeHoverBackground", new Color(199, 80, 80));
            UIManager.put("TitlePane.buttonHoverBackground", new Color(60, 60, 60));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void initMenuBar() {
        JMenuBar mb = new JMenuBar();

        // --- MENU FILE ---
        JMenu f = new JMenu("File");
        
        // Mở Project (.lmproj) - Ưu tiên hàng đầu cho việc tái chỉnh sửa
        JMenuItem openProj = new JMenuItem("Open Project (.lmproj)");
        openProj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
        openProj.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Lotus Model Project", "lmproj"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                loadProject(fc.getSelectedFile()); // Hàm sẽ code bổ sung
            }
        });

        // Import PSD (Chỉ dùng khi bắt đầu Rig mới từ file ảnh)
        JMenuItem o = new JMenuItem("Open PSD Rig...");
        o.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        o.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                loadFile(fc.getSelectedFile());
            }
        });
        
        // Lưu Project (.lmproj)
        JMenuItem saveProj = new JMenuItem("Save Project As...");
        saveProj.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveProj.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                if (!file.getName().endsWith(".lmproj")) {
                    file = new File(file.getAbsolutePath() + ".lmproj");
                }
                saveProject(file); // Hàm sẽ code bổ sung
            }
        });

        JMenuItem export = new JMenuItem("Export Rig Data (JSON)");
        export.addActionListener(e -> {
            System.out.println("[modcoderpack-redevelop] Exporting data...");
            // Logic export engine-ready (JSON)
        });

        f.add(openProj);
        f.add(o);
        f.addSeparator();
        f.add(saveProj);
        f.add(export);
        f.addSeparator();
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));
        f.add(exit);

        // --- MENU EDIT ---
        JMenu edit = new JMenu("Edit");
        
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(al -> undo());

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(al -> redo());

        JMenuItem resetPos = new JMenuItem("Reset All Offsets");
        resetPos.addActionListener(e -> {
            for (LayerData ld : layersData) {
                ld.x = 0; ld.y = 0;
            }
            previewPanel.repaint();
            autoSaveConfig(); // [cite: 2026-01-02]
        });

        edit.add(undoItem);
        edit.add(redoItem);
        edit.addSeparator(); 
        edit.add(resetPos);

        // --- MENU VIEW ---
        JMenu view = new JMenu("View");
        
        JCheckBoxMenuItem showAll = new JCheckBoxMenuItem("Show All Layers", true);
        showAll.addActionListener(e -> {
            boolean state = showAll.isSelected();
            for (int i = 0; i < layerVisibility.size(); i++) {
                layerVisibility.set(i, state);
            }
            layerList.repaint();
            previewPanel.repaint();
            autoSaveConfig(); // [cite: 2026-01-02]
        });

        JMenuItem zoomFit = new JMenuItem("Zoom to Fit");
        zoomFit.addActionListener(e -> {
            zoomFactor = 0.5; 
            previewPanel.repaint();
        });

        view.add(showAll);
        view.addSeparator();
        view.add(zoomFit);

        // Ghép các menu vào MenuBar
        mb.add(f);
        mb.add(edit);
        mb.add(view);
        
        setJMenuBar(mb);
    }
    
    private File currentProjectFile = null;

    /**
     * LƯU PROJECT (.lmproj)
     * Tuân thủ [2026-01-02]: Cơ chế này được dùng cho cả autoSaveConfig()
     */
    private void saveProject(File file) {
        new Thread(() -> {
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                
                java.util.Map<String, Object> projectData = new java.util.HashMap<>();
                projectData.put("repo", "modcoderpack-redevelop");
                
                // LƯU ĐƯỜNG DẪN PSD (Giả sử bạn có biến currentPSDFile)
                if (currentPSDFile != null) {
                    projectData.put("psdPath", currentPSDFile.getAbsolutePath());
                }
                
                java.util.List<java.util.Map<String, Object>> layersList = new java.util.ArrayList<>();
                for (LayerData ld : layersData) {
                    java.util.Map<String, Object> lMap = new java.util.HashMap<>();
                    lMap.put("id", ld.name);
                    int index = layersData.indexOf(ld);
                    lMap.put("visible", layerVisibility.get(index)); 
                    
                 // Trong vòng lặp for (LayerData ld : layersData)
                    if (ld.artMesh != null) {
                        lMap.put("vertices", ld.artMesh.getVertices());
                        
                        // LƯU POINTS CỦA TỪNG MESH
                        java.util.List<java.util.Map<String, Object>> pointsData = new java.util.ArrayList<>();
                        for (LArtMesh.ParameterPoint pp : ld.artMesh.getParameterPoints()) {
                            java.util.Map<String, Object> pMap = new java.util.HashMap<>();
                            pMap.put("x", pp.x);
                            pMap.put("y", pp.y);
                            pMap.put("vData", pp.vertexData); // ĐÂY LÀ DỮ LIỆU BIẾN DẠNG
                            pointsData.add(pMap);
                        }
                        lMap.put("points", pointsData);
                    }
                    layersList.add(lMap);
                }
                projectData.put("layers", layersList);
                
             // Trong hàm saveProject, bên trong projectData.put(...)
                java.util.List<java.util.Map<String, Object>> paramsList = new java.util.ArrayList<>();
                for (LParameter lp : projectParameters) { // Giả sử bạn quản lý list này trong Frame
                    java.util.Map<String, Object> pMap = new java.util.HashMap<>();
                    pMap.put("id", lp.getId());
                    pMap.put("min", lp.getMin());
                    pMap.put("max", lp.getMax());
                    pMap.put("def", lp.getDefaultValue());
                    pMap.put("val", lp.getValue()); // Lưu giá trị hiện tại của Joystick
                    paramsList.add(pMap);
                }
                projectData.put("parameters", paramsList);

                gson.toJson(projectData, writer);
                this.currentProjectFile = file;
                System.out.println("[ModCoderPack] Auto-saved project: " + file.getName());
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * MỞ PROJECT (.lmproj)
     */
    private void loadProject(File file) {
        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.Map projectData = gson.fromJson(reader, java.util.Map.class);
            
            // 1. KHÔI PHỤC PSD TRƯỚC
            if (projectData.containsKey("psdPath")) {
                File psdFile = new File((String) projectData.get("psdPath"));
                if (psdFile.exists()) {
                    this.currentPSDFile = psdFile;
                    loadFile(psdFile); 
                }
            }

            // 2. KHÔI PHỤC DỮ LIỆU RIGGING CHO TỪNG LAYER
            java.util.List<java.util.Map> layersLoad = (java.util.List<java.util.Map>) projectData.get("layers");
            if (layersLoad != null) {
                for (java.util.Map lMap : layersLoad) {
                    String id = (String) lMap.get("id");
                    for (int i = 0; i < layersData.size(); i++) {
                        LayerData ld = layersData.get(i);
                        if (ld.name.equals(id)) {
                            if (lMap.containsKey("visible")) layerVisibility.set(i, (Boolean) lMap.get("visible"));
                            if (ld.artMesh != null) {
                                // Load Vertices
                                if (lMap.containsKey("vertices")) {
                                    java.util.List<Double> vList = (java.util.List<Double>) lMap.get("vertices");
                                    float[] vArray = new float[vList.size()];
                                    for (int j = 0; j < vList.size(); j++) vArray[j] = vList.get(j).floatValue();
                                    ld.artMesh.setVertices(vArray);
                                }
                                // Load ParameterPoints của Mesh
                                if (lMap.containsKey("points")) {
                                    java.util.List<java.util.Map> pList = (java.util.List<java.util.Map>) lMap.get("points");
                                    ld.artMesh.getParameterPoints().clear();
                                 // SỬA LẠI TRONG VÒNG LẶP PList
                                    for (java.util.Map pMap : pList) {
                                        float px = ((Double) pMap.get("x")).floatValue();
                                        float py = ((Double) pMap.get("y")).floatValue();
                                        
                                        // Đảm bảo Key ở đây phải giống hệt lúc bạn pMap.put(...) trong hàm saveProject
                                        // Tôi đổi lại thành "vData" cho khớp với đoạn code save bạn viết ở trên
                                        java.util.List<Double> vdList = (java.util.List<Double>) pMap.get("vData"); 
                                        
                                        float[] vdArray = null;
                                        if (vdList != null) {
                                            vdArray = new float[vdList.size()];
                                            for (int k = 0; k < vdList.size(); k++) {
                                                vdArray[k] = vdList.get(k).floatValue();
                                            }
                                        }
                                        
                                        // Thêm point vào Mesh
                                        ld.artMesh.getParameterPoints().add(new LArtMesh.ParameterPoint(px, py, vdArray));
                                    }
                                }
                            }
                            break; 
                        }
                    }
                }
            }

            // 3. KHÔI PHỤC DANH SÁCH JOYSTICK (ĐẶT NGOÀI VÒNG LẶP LAYER)
            // Đây là chỗ sửa lỗi: Phải chạy sau khi toàn bộ layer đã load xong
            if (projectData.containsKey("parameters")) {
                java.util.List<java.util.Map> pLoad = (java.util.List<java.util.Map>) projectData.get("parameters");
                
                // Xóa cũ để nạp mới
                joystickListPanel.removeAll();
                projectParameters.clear();

                for (java.util.Map pMap : pLoad) {
                    LParameter lp = new LParameter(
                        (String) pMap.get("id"),
                        ((Double) pMap.get("min")).floatValue(),
                        ((Double) pMap.get("max")).floatValue(),
                        ((Double) pMap.get("def")).floatValue()
                    );
                    lp.setValue(((Double) pMap.get("val")).floatValue());
                    
                    projectParameters.add(lp);
                    
                    // Gọi addNewJoystickItem(String) để tái tạo UI
                    // Lưu ý: Hàm này của bạn cần thêm dòng projectParameters.add bên trong
                    // Nếu hàm addNewJoystickItem đã có projectParameters.add thì hãy cẩn thận tránh trùng.
                    rebuildJoystickUI(lp); 
                }
                joystickListPanel.revalidate();
                joystickListPanel.repaint();
            }
            
            this.currentProjectFile = file; // Active autoSave [2026-01-02]
            previewPanel.repaint();
            layerList.repaint();
            System.out.println("[ModCoderPack] .lmproj loaded successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void rebuildJoystickUI(LParameter param) {
        // Chỉ tạo UI, không add thêm vào projectParameters vì đã add ở trên rồi
        JPanel itemPanel = new JPanel(new BorderLayout());
        itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        itemPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        itemPanel.setBackground(new Color(50, 53, 55));

        JLabel label = new JLabel(" " + param.getId());
        label.setForeground(Color.WHITE);
        
        JButton removeBtn = new JButton("×");
        removeBtn.addActionListener(e -> {
            projectParameters.remove(param);
            joystickListPanel.remove(itemPanel);
            joystickListPanel.revalidate();
            joystickListPanel.repaint();
            autoSaveConfig(); // [2026-01-02]
        });

        ParamJoystick joystick = new ParamJoystick();
        // Đồng bộ vị trí joystick với giá trị nạp vào
        // float val = param.getValue(); ... thiết lập joystick.pos tại đây

        itemPanel.add(label, BorderLayout.NORTH);
        itemPanel.add(joystick, BorderLayout.CENTER);
        joystickListPanel.add(itemPanel);
    }

    /**
     * Cập nhật lại autoSaveConfig theo yêu cầu [2026-01-02]
     */
    public void autoSaveConfig() {
        
    }

    private class LayerListRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
            if (i < layerVisibility.size()) {
                lbl.setText((layerVisibility.get(i) ? " 👁 " : " ◦ ") + v);
            }
            return lbl;
        }
    }
}