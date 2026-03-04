package vn.pmgteam.lotus2d.sdk;

import java.awt.geom.*;
import java.awt.*;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class LArtMesh extends LModelBase {
    private BufferedImage texture;
    private boolean isSwapped = false;

    // --- Bổ sung cho Rigging ---
    private float[] vertices;      // Tọa độ hiện tại (sau khi rig)
    private float[] baseVertices;  // Tọa độ gốc để tính toán lại khi cần

    public LArtMesh(String id, LModelBase parent) {
        super(id, parent);
    }
    
    public float[] getVertices() {
    	return vertices;
    }

    /**
     * Thiết lập lưới đỉnh ban đầu
     */
    public void setVertices(float[] v) {
        this.baseVertices = v;
        this.vertices = v.clone();
        markDirty(); // Kích hoạt autoSave theo yêu cầu
    }

    /**
     * Hàm Test Rig: Biến dạng các đỉnh
     */
    public void rigDeform(float offsetX, float offsetY) {
        if (baseVertices == null) return;
        for (int i = 0; i < baseVertices.length; i += 2) {
            vertices[i] = baseVertices[i] + offsetX;
            vertices[i + 1] = baseVertices[i + 1] + offsetY;
        }
        markDirty(); // Module thay đổi -> autoSave
    }
    
    // Thêm vào LArtMesh.java
    public void syncVertices() {
        if (vertices != null && baseVertices != null) {
            // Copy tọa độ đã kéo vào tọa độ gốc để updateBinding không làm mất vị trí mới
            System.arraycopy(vertices, 0, baseVertices, 0, vertices.length);
            markDirty(); 
        }
    }

    // --- GIỮ NGUYÊN LOGIC CỦA BẠN ---
    public void loadTexture(BufferedImage img) {
        if (img == null) return;
        if (shouldSwap()) { 
            try {
                File tempFile = getSwapFile("texture");
                ImageIO.write(img, "png", tempFile);
                img.flush(); 
                this.isSwapped = true;
                logger.info("RAM limit reached! Layer '{}' offloaded to swap directory.", id);
            } catch (IOException e) {
                logger.error("Swap error for layer {}: {}", id, e.getMessage());
            }
        } else {
            this.texture = img;
            this.isSwapped = false;
        }
        markDirty();
    }
    
    // Thêm vào trong LArtMesh.java
    public void updateBinding(LParameter paramX, LParameter paramY) {
        if (baseVertices == null) return;
        
        float offsetX = paramX.getValue() * 15.0f; 
        float offsetY = paramY.getValue() * 15.0f;
        
        for (int i = 0; i < baseVertices.length; i += 2) {
            vertices[i] = baseVertices[i] + offsetX;
            vertices[i + 1] = baseVertices[i + 1] + offsetY;
        }
        
        markDirty(); // [cite: 2026-01-02] 
        // Quan trọng: Phải có cơ chế callback để PreviewPanel gọi repaint()
    }
    
    @Override
    public void update(float deltaTime) {
        // Logic cập nhật tọa độ cho mesh
    }

    @Override
    public void render(java.awt.Graphics2D g2d) {
        if (vertices == null || texture == null) return;

        int cols = 4; // Số cột lưới bạn tạo ở LayerData [cite: 2026-01-18]
        int rows = 4; // Số hàng lưới
        int tw = texture.getWidth();
        int th = texture.getHeight();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                // Lấy index 4 đỉnh của 1 ô lưới vuông
                int v0 = (r * (cols + 1) + c) * 2;
                int v1 = (r * (cols + 1) + (c + 1)) * 2;
                int v2 = ((r + 1) * (cols + 1) + (c + 1)) * 2;
                int v3 = ((r + 1) * (cols + 1) + c) * 2;

                // Tọa độ cắt ảnh gốc (UV)
                int x1 = c * tw / cols;
                int y1 = r * th / rows;
                int x2 = (c + 1) * tw / cols;
                int y2 = (r + 1) * th / rows;

                // Vẽ 2 tam giác để tạo thành 1 ô vuông bị méo theo vertices
                drawTriangle(g2d, v0, v1, v2, x1, y1, x2, y1, x2, y2);
                drawTriangle(g2d, v0, v2, v3, x1, y1, x2, y2, x1, y2);
            }
        }
    }

    private void drawTriangle(java.awt.Graphics2D g2d, int i1, int i2, int i3, int sx1, int sy1, int sx2, int sy2, int sx3, int sy3) {
        // 1. Kiểm tra diện tích tam giác để tránh chia cho 0 (Gây nát hình)
    	g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); // Quan trọng nhất để mượt
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        double den = (double)(sx2 - sx1) * (sy3 - sy1) - (double)(sx3 - sx1) * (sy2 - sy1);
        if (Math.abs(den) < 1.0) return; // Nếu vùng texture quá nhỏ, bỏ qua không vẽ

        // 2. Tạo Path cho tam giác biến dạng (Target)
        Path2D.Float path = new Path2D.Float();
        path.moveTo(vertices[i1], vertices[i1+1]);
        path.lineTo(vertices[i2], vertices[i2+1]);
        path.lineTo(vertices[i3], vertices[i3+1]);
        path.closePath();

        // 3. Tính toán ma trận biến đổi TỪ Texture (Source) SANG Mesh (Target)
        double m00 = ((vertices[i2] - vertices[i1]) * (sy3 - sy1) - (vertices[i3] - vertices[i1]) * (sy2 - sy1)) / den;
        double m01 = ((vertices[i3] - vertices[i1]) * (sx2 - sx1) - (vertices[i2] - vertices[i1]) * (sx3 - sx1)) / den;
        double m02 = vertices[i1] - m00 * sx1 - m01 * sy1;

        double m10 = ((vertices[i2+1] - vertices[i1+1]) * (sy3 - sy1) - (vertices[i3+1] - vertices[i1+1]) * (sy2 - sy1)) / den;
        double m11 = ((vertices[i3+1] - vertices[i1+1]) * (sx2 - sx1) - (vertices[i2+1] - vertices[i1+1]) * (sx3 - sx1)) / den;
        double m12 = vertices[i1+1] - m10 * sx1 - m11 * sy1;

        // 4. KHẮC PHỤC LỖI NHÂN BẢN: Giới hạn vùng vẽ
        Shape oldClip = g2d.getClip();
        g2d.clip(path); // Chỉ cho phép vẽ bên trong tam giác mesh

        AffineTransform oldAt = g2d.getTransform();
        // Áp dụng ma trận nội bộ cho tam giác
        g2d.transform(new AffineTransform(m00, m10, m01, m11, m02, m12));
        
        // Vẽ texture tại gốc (0,0) của ảnh, Clip sẽ tự cắt theo mesh
        g2d.drawImage(texture, 0, 0, null);
        
     // Kiểm tra định thức ma trận (diện tích tam giác)
        den = (double)(sx2 - sx1) * (sy3 - sy1) - (double)(sx3 - sx1) * (sy2 - sy1);

        // Nếu diện tích quá nhỏ (tam giác bị nát thành đường thẳng) thì không vẽ 
        // để tránh hiện tượng vệt sọc lố lăng trên màn hình
        if (Math.abs(den) < 0.5) return;

        g2d.setTransform(oldAt);
        g2d.setClip(oldClip); // Trả lại Clip cũ
    }

    @Override
    public void cleanup() {
        if (texture != null) {
            texture.flush();
            texture = null;
        }
        
        File f = getSwapFile("texture");
        if (f.exists()) {
            f.delete();
        }

        // Dọn dẹp thêm mảng vertices để giải phóng RAM triệt để
        vertices = null;
        baseVertices = null;

        confirmCleanup();
    }
}