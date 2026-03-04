package vn.pmgteam.lotus2d.sdk.test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;

import org.w3c.dom.NodeList;

import com.formdev.flatlaf.FlatDarkLaf;

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

import vn.pmgteam.lotus2d.sdk.LArtMesh;
import vn.pmgteam.lotus2d.sdk.LParameter;

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
            int defaultDensity = 6;
            createDenseMesh(defaultDensity);
        }

     // Sửa lại hàm này trong LayerData (LEditorFrame.java)
        public void createDenseMesh(int density) {
            this.meshCols = density;
            this.meshRows = density;
            int tw = image.getWidth();
            int th = image.getHeight();
            
            float[] v = new float[(density + 1) * (density + 1) * 2];
            int idx = 0;
            for (int r = 0; r <= density; r++) {
                for (int c = 0; c <= density; c++) {
                    v[idx++] = (float) c / density * tw;
                    v[idx++] = (float) r / density * th;
                }
            }
            artMesh.setVertices(v);
            // Khi đổi mật độ lưới, phải báo hiệu để lưu lại cấu trúc mới
            artMesh.markDirty(); //[cite: 2026-01-02]
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

        initMenuBar();
        previewPanel = new PreviewPanel();
        previewPanel.setBackground(new Color(60, 63, 65));
        
        characterPreview = new PreviewPanel();

        JTabbedPane mainTabs = new JTabbedPane(JTabbedPane.TOP);
        mainTabs.addTab(" Character ", characterPreview);
        mainTabs.addTab(" Rigging ", createRiggingPanel());
        add(mainTabs, BorderLayout.CENTER);
        add(createPropertiesPanel(), BorderLayout.EAST); //[cite: 2026-01-18]

        setupStatusPanel();
        setLocationRelativeTo(null);
    }
    
    private void setupTitleIcon() {
        try {
            // Nạp icon từ file brand bạn đã cung cấp
            java.net.URL iconURL = getClass().getResource("/assets/icons.png");
            if (iconURL != null) {
                this.setIconImage(new ImageIcon(iconURL).getImage());
            }
        } catch (Exception e) {
            // Sử dụng Logger có sẵn để báo lỗi màu RED rực rỡ
            vn.pmgteam.lotus2d.sdk.util.Logger.getLogger("Editor")
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
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(" Layer Properties "));
        panel.setPreferredSize(new Dimension(250, 0));

        // Khởi tạo các Slider (Gán vào field của class)
        posXSlider = new JSlider(-1000, 1000, 0);
        posYSlider = new JSlider(-1000, 1000, 0);
        rotSlider = new JSlider(0, 360, 0);
        alphaSlider = new JSlider(0, 100, 100);
        
     // Trong createPropertiesPanel
        pivotToggleBtn = new JToggleButton("🎯 Edit Pivot Mode");
        pivotToggleBtn.setFocusable(false);
        pivotToggleBtn.addActionListener(e -> {
            isPivotEditMode = pivotToggleBtn.isSelected();
            previewPanel.setCursor(isPivotEditMode ? 
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : 
                Cursor.getDefaultCursor());
        });
        panel.add(pivotToggleBtn);
        panel.add(Box.createVerticalStrut(10)); // Khoảng cách

        // Listener dùng chung để lưu lịch sử
        MouseAdapter historyTrigger = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                saveHistory(); 
            }
        };
        
     // Trong createPropertiesPanel(), thêm sau đoạn pivotToggleBtn:
        meshToggleBtn = new JToggleButton("🕸 Edit Mesh Mode");
        meshToggleBtn.setFocusable(false);
        meshToggleBtn.addActionListener(e -> {
            isMeshEditMode = meshToggleBtn.isSelected();
            if (isMeshEditMode) {
                isPivotEditMode = false;
                pivotToggleBtn.setSelected(false);
            }
            previewPanel.repaint();
        });
        panel.add(meshToggleBtn);

        // Thiết lập cho từng Slider
        setupSlider(posXSlider, "Position X:", panel, historyTrigger, 
            val -> { if(selectedLayerIndex != -1) layersData.get(selectedLayerIndex).x = val; });
        
        setupSlider(posYSlider, "Position Y:", panel, historyTrigger, 
            val -> { if(selectedLayerIndex != -1) layersData.get(selectedLayerIndex).y = val; });

        setupSlider(rotSlider, "Rotation:", panel, historyTrigger, 
            val -> { if(selectedLayerIndex != -1) layersData.get(selectedLayerIndex).rotation = val; });

        setupSlider(alphaSlider, "Opacity:", panel, historyTrigger, 
            val -> { if(selectedLayerIndex != -1) layersData.get(selectedLayerIndex).opacity = val / 100f; });

        return panel;
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
            
            // GIỚI HẠN: Không cho đỉnh đi quá xa khỏi biên ảnh (ví dụ 200px)
            double margin = 200.0;
            float finalX = (float) Math.max(-margin, Math.min(data.image.getWidth() + margin, localPoint.getX()));
            float finalY = (float) Math.max(-margin, Math.min(data.image.getHeight() + margin, localPoint.getY()));

            v[selectedVertexIdx] = finalX;
            v[selectedVertexIdx + 1] = finalY;
            
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
    
    private class PreviewPanel extends JPanel {
        private Point lastMousePos; // Lưu vị trí chuột để tính delta khi kéo
    	
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
                        // Sau khi thả chuột, đồng bộ vertices vào baseVertices
                        LayerData data = layersData.get(selectedLayerIndex);
                        data.artMesh.syncVertices(); // Gọi hàm vừa thêm ở trên
                        
                        selectedVertexIdx = -1; 
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
                
                // Sử dụng hàm transform đã sửa ở trên
                AffineTransform lt = getLayerTransform(ld);
                g2d.transform(lt); 
                
                ld.artMesh.render(g2d);

                if (i == selectedLayerIndex) {
                    renderSelectionExtras(g2d, ld); // Vẽ khung và Pivot đỏ
                }
                
                // Nếu là mesh edit mode, vẽ lưới
                if (isMeshEditMode && i == selectedLayerIndex) {
                    drawMeshGrid(g2d, ld.meshCols, ld.meshRows, ld.artMesh.getVertices());
                }
                
                g2d.setTransform(layerAt); // Khôi phục về World Space cho layer tiếp theo
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
            // Màu xanh cyan nhạt giúp nhìn rõ cấu trúc mesh trên hầu hết các nền
            g2d.setColor(new Color(0, 255, 255, 80)); 
            float uiScale = (float)(1.0 / zoomFactor);
            g2d.setStroke(new BasicStroke(0.5f * uiScale));
            
            for (int r = 0; r <= rows; r++) {
                for (int c = 0; c <= cols; c++) {
                    int i = (r * (cols + 1) + c) * 2;
                    
                    // Vẽ đường ngang kết nối các đỉnh
                    if (c < cols) {
                        int next = i + 2;
                        g2d.drawLine((int)v[i], (int)v[i+1], (int)v[next], (int)v[next+1]);
                    }
                    // Vẽ đường dọc kết nối các đỉnh
                    if (r < rows) {
                        int down = i + (cols + 1) * 2;
                        g2d.drawLine((int)v[i], (int)v[i+1], (int)v[down], (int)v[down+1]);
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

    private void autoSaveConfig() {
        System.out.println("[AutoSave] Synced state to modcoderpack-redevelop"); // [cite: 2026-01-18]
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
        JMenuItem o = new JMenuItem("Open PSD Rig...");
        o.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        o.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) loadFile(fc.getSelectedFile());
        });
        
        JMenuItem export = new JMenuItem("Export Rig Data (JSON)");
        export.addActionListener(e -> {
            System.out.println("[modcoderpack-redevelop] Exporting data...");
            // Logic export sẽ code thêm sau
        });

        f.add(o);
        f.add(export);
        f.addSeparator();
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));
        f.add(exit);

        // --- MENU EDIT ---
        JMenu edit = new JMenu("Edit");
        JMenuItem resetPos = new JMenuItem("Reset All Offsets");
        resetPos.addActionListener(e -> {
            for (LayerData ld : layersData) {
                ld.x = 0; ld.y = 0;
            }
            previewPanel.repaint();
            autoSaveConfig(); // [cite: 2026-01-02]
        });
        edit.add(resetPos);

        // --- MENU VIEW ---
        JMenu view = new JMenu("View");
        
        // Toggle hiển thị toàn bộ layer
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
            zoomFactor = 0.5; // Reset zoom về mặc định
            previewPanel.repaint();
        });
        
     // Trong menu Edit của initMenuBar()
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(al -> undo());

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(al -> redo());


        view.add(showAll);
        view.addSeparator();
        view.add(zoomFit);

        // Thêm các tab vào MenuBar
        mb.add(f);
        mb.add(edit);
        mb.add(view);
        
        edit.add(undoItem);
        edit.add(redoItem);
        edit.addSeparator(); // Ngăn cách với Reset All Offsets

        setJMenuBar(mb);
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