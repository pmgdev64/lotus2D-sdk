package vn.pmgteam.lotus2d.framework.layer;

import java.awt.image.BufferedImage;
import vn.pmgteam.lotus2d.core.LArtMesh;

/**
 * LLayerData - Class cơ sở quản lý dữ liệu Layer và Mesh cho Lotus2D.
 * Đã tích hợp AutoSave trigger khi cấu trúc Mesh thay đổi.
 */
public class LLayerData {
    public BufferedImage image;
    public LArtMesh artMesh; // Đối tượng xử lý mesh từ SDK
    
    public int psdX; // Tọa độ gốc từ file PSD
	public int psdY;
    public int x = 0;
    public int y = 0; 
    public float rotation = 0;
    public float opacity = 1.0f;
    public String name;
    
    public float pivotX = 0.5f;
    public float pivotY = 0.5f;
    
    public int meshCols = 4; // Số cột của lưới (Grid)
    public int meshRows = 4; // Số hàng của lưới (Grid)
    
    protected int defaultDensity = 6;

    public LLayerData(BufferedImage img, int px, int py, String name) {
        this.image = img;
        this.psdX = px;
        this.psdY = py;
        this.name = name;
        
        // 1. Khởi tạo ArtMesh từ lõi SDK
        this.artMesh = new LArtMesh(name, null);
        
        // 2. Load Texture vào Mesh
        if (img != null) {
            this.artMesh.loadTexture(img);
        }
        
        // 3. Khởi tạo lưới mật độ mặc định (Mặc định 6x6 để cân bằng hiệu năng)
        createDenseMesh(defaultDensity);
    }

    /**
     * Tạo lưới Mesh đồng nhất dựa trên mật độ (density).
     * @param density Số phân đoạn cho cả chiều ngang và chiều dọc.
     */
    public void createDenseMesh(int density) {
        if (image == null) return;

        this.meshCols = density;
        this.meshRows = density;
        
        int tw = image.getWidth();
        int th = image.getHeight();
        
        // Tổng số đỉnh (Vertex): (density + 1) * (density + 1)
        // Mỗi đỉnh gồm 2 giá trị (x, y) nên mảng có kích thước * 2
        float[] newVertices = new float[(density + 1) * (density + 1) * 2];
        int idx = 0;
        
        for (int r = 0; r <= density; r++) {
            for (int c = 0; c <= density; c++) {
                // Tính toán tọa độ đỉnh dựa trên tỷ lệ phần trăm của kích thước ảnh
                newVertices[idx++] = (float) c / density * tw;
                newVertices[idx++] = (float) r / density * th;
            }
        }
        
        // Cập nhật mảng đỉnh vào ArtMesh của SDK
        // Việc này đảm bảo kết cấu hình ảnh không bị biến dạng khi reset mật độ lưới
        this.artMesh.setVertices(newVertices); 
        
        // Đánh dấu Mesh đã thay đổi để Re-render
        this.artMesh.markDirty(); 
        
        // Logic này hỗ trợ AutoSave khi người dùng thay đổi cấu trúc lưới
        // Lưu ý: Việc lưu file nên được xử lý thông qua một Callback hoặc Listener 
        // ở cấp LEditorFrame để tránh nghẽn luồng I/O.
    }

    // Getter cho tên để hiển thị lên JList dễ dàng
    @Override
    public String toString() {
        return name != null ? name : "Unnamed Layer";
    }
}