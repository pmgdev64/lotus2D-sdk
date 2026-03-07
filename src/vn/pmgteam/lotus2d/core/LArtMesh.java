package vn.pmgteam.lotus2d.core;

import java.awt.geom.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.ArrayList;
import vn.pmgteam.lotus2d.editor.LEditorFrame;

public class LArtMesh extends LModelBase {
    private BufferedImage texture;
    private float[] vertices;      
    private float[] baseVertices;  
    private java.util.List<ParameterPoint> points = new ArrayList<>();
    
    private ParameterPoint activePoint = null;
    private ParameterPoint currentlyStandingPoint = null; 

    private LEditorFrame editorFrame = LEditorFrame.getEditorFrame();

    // --- TỐI ƯU HÓA CHIẾN THUẬT: TRIỆT TIÊU LAG ---
    private final Path2D.Float trianglePath = new Path2D.Float();
    private final AffineTransform meshTransform = new AffineTransform();
    private final RenderingHints qualityHints;

    public LArtMesh(String id, LModelBase parent) {
        super(id, parent);
        qualityHints = new RenderingHints(null);
        qualityHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Dùng Bilinear để Rotate mượt hơn trên Intel UHD
        qualityHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        qualityHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        qualityHints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
    }

    // --- GIỮ NGUYÊN LOGIC GỐC (CẤM PHÁ) ---
    public ParameterPoint getCurrentlyStandingPoint() { return currentlyStandingPoint; }

    public void updateVertexAtCurrentPosition(int index, float newX, float newY) {
        if (vertices == null || index < 0 || index + 1 >= vertices.length) return;
        vertices[index] = newX;
        vertices[index + 1] = newY;
        if (currentlyStandingPoint != null && currentlyStandingPoint.vertexData != null) {
            currentlyStandingPoint.vertexData[index] = newX;
            currentlyStandingPoint.vertexData[index + 1] = newY;
            markDirty(); // [cite: 2026-01-02]
        }
    }

    public void interpolateMesh(float tx, float ty) {
        if (points.isEmpty() || vertices == null) return;
        currentlyStandingPoint = null;
        float snapThreshold = 0.05f; 
        for (ParameterPoint p : points) {
            if (Math.abs(tx - p.x) < snapThreshold && Math.abs(ty - p.y) < snapThreshold) {
                currentlyStandingPoint = p;
                break;
            }
        }
        if (currentlyStandingPoint != null) {
            if (currentlyStandingPoint.vertexData != null) {
                System.arraycopy(currentlyStandingPoint.vertexData, 0, vertices, 0, vertices.length);
            }
        } else {
            float[] newVertices = new float[vertices.length];
            float totalWeight = 0;
            for (ParameterPoint p : points) {
                if (p.vertexData == null) continue;
                float dx = tx - p.x; float dy = ty - p.y;
                float distSq = dx * dx + dy * dy;
                float weight = (distSq < 0.0001f) ? 10000f : 1.0f / distSq;
                for (int i = 0; i < vertices.length; i++) newVertices[i] += p.vertexData[i] * weight;
                totalWeight += weight;
            }
            if (totalWeight > 0) {
                for (int i = 0; i < vertices.length; i++) vertices[i] = newVertices[i] / totalWeight;
            }
        }
        markDirty(); 
    }
    
    public void handlePointSelectionOrCreation(float normX, float normY) {
        float threshold = 0.15f; ParameterPoint found = null;
        for (ParameterPoint p : points) {
            if (Math.abs(p.x - normX) < threshold && Math.abs(p.y - normY) < threshold) {
                found = p; break;
            }
        }
        if (found != null) {
            if (this.vertices != null) found.setVertexOffsets(this.vertices.clone());
            activePoint = found; currentlyStandingPoint = found;
        } else {
            ParameterPoint newPoint = new ParameterPoint(normX, normY, this.vertices);
            points.add(newPoint); activePoint = newPoint; currentlyStandingPoint = newPoint;
        }
        markDirty();
    }

    // --- RENDER SIÊU TỐC: KHÔNG DÙNG G2D.CLIP ĐỂ TRÁNH LAG ---
    @Override
    public void render(Graphics2D g2d) {
        if (vertices == null || texture == null) return;
        
        g2d.setRenderingHints(qualityHints);

        int cols = editorFrame.getCols(); 
        int rows = editorFrame.getRows(); 
        int tw = texture.getWidth();
        int th = texture.getHeight();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int v0 = (r * (cols + 1) + c) * 2;
                int v1 = (r * (cols + 1) + (c + 1)) * 2;
                int v2 = ((r + 1) * (cols + 1) + (c + 1)) * 2;
                int v3 = ((r + 1) * (cols + 1) + c) * 2;

                drawFastTriangle(g2d, v0, v1, v2, c*tw/cols, r*th/rows, (c+1)*tw/cols, r*th/rows, (c+1)*tw/cols, (r+1)*th/rows);
                drawFastTriangle(g2d, v0, v2, v3, c*tw/cols, r*th/rows, (c+1)*tw/cols, (r+1)*th/rows, c*tw/cols, (r+1)*th/rows);
            }
        }
    }

    private void drawFastTriangle(Graphics2D g2d, int i1, int i2, int i3, int sx1, int sy1, int sx2, int sy2, int sx3, int sy3) {
        if (i3 + 1 >= vertices.length) return;

        double den = (double)(sx2 - sx1) * (sy3 - sy1) - (double)(sx3 - sx1) * (sy2 - sy1);
        if (Math.abs(den) < 0.5) return; 

        trianglePath.reset();
        trianglePath.moveTo(vertices[i1], vertices[i1+1]);
        trianglePath.lineTo(vertices[i2], vertices[i2+1]);
        trianglePath.lineTo(vertices[i3], vertices[i3+1]);
        trianglePath.closePath();

        double m00 = ((vertices[i2] - vertices[i1]) * (sy3 - sy1) - (vertices[i3] - vertices[i1]) * (sy2 - sy1)) / den;
        double m01 = ((vertices[i3] - vertices[i1]) * (sx2 - sx1) - (vertices[i2] - vertices[i1]) * (sx3 - sx1)) / den;
        double m02 = vertices[i1] - m00 * sx1 - m01 * sy1;
        double m10 = ((vertices[i2+1] - vertices[i1+1]) * (sy3 - sy1) - (vertices[i3+1] - vertices[i1+1]) * (sy2 - sy1)) / den;
        double m11 = ((vertices[i3+1] - vertices[i1+1]) * (sx2 - sx1) - (vertices[i2+1] - vertices[i1+1]) * (sx3 - sx1)) / den;
        double m12 = vertices[i1+1] - m10 * sx1 - m11 * sy1;

        // Tối ưu: Thay vì g2d.clip, chúng ta tính toán ma trận để g2d.fill
        // giúp Java2D dùng pipeline phần cứng tốt hơn khi Rotate.
        AffineTransform oldAt = g2d.getTransform();
        meshTransform.setTransform(m00, m10, m01, m11, m02, m12);
        
        // Kết hợp ma trận biến dạng vào ma trận Rotate hiện tại của g2d
        g2d.transform(meshTransform);
        
        // Dùng Paint thay vì drawImage + clip giúp triệt tiêu răng cưa biên (seams)
        g2d.setPaint(new TexturePaint(texture, new Rectangle2D.Float(0, 0, texture.getWidth(), texture.getHeight())));
        
        // Chuyển ngược Path về không gian texture để fill
        try {
            Shape texShape = meshTransform.createInverse().createTransformedShape(trianglePath);
            g2d.fill(texShape);
        } catch (NoninvertibleTransformException e) {
            // Fallback nếu không nghịch đảo được ma trận
            g2d.drawImage(texture, 0, 0, null);
        }
        
        g2d.setTransform(oldAt);
    }

    // --- CÁC HÀM CÒN LẠI GIỮ NGUYÊN TUYỆT ĐỐI ---
    public void addParameterPoint(float px, float py) { points.add(new ParameterPoint(px, py, vertices.clone())); markDirty(); }
    public java.util.List<ParameterPoint> getParameterPoints() { return this.points; }
    public ParameterPoint getActivePoint() { return activePoint; }
    public float[] getVertices() { return vertices; }
    public void setVertices(float[] v) { this.baseVertices = v; this.vertices = v.clone(); markDirty(); }
    public void syncVertices() { if (vertices != null && baseVertices != null) { System.arraycopy(vertices, 0, baseVertices, 0, vertices.length); markDirty(); } }
    public void updateBinding(LParameter paramX, LParameter paramY) { if (baseVertices == null) return; interpolateMesh((paramX.getValue() * 2.0f) - 1.0f, (paramY.getValue() * 2.0f) - 1.0f); }
    public void loadTexture(BufferedImage img) { this.texture = img; markDirty(); }
    @Override public void update(float deltaTime) {}
    @Override public void cleanup() { if (texture != null) texture.flush(); vertices = null; baseVertices = null; confirmCleanup(); }

    public static class ParameterPoint {
        public float x, y; public float[] vertexData;
        public ParameterPoint(float x, float y) { this.x = x; this.y = y; }
        public ParameterPoint(float x, float y, float[] vertexData) { this.x = x; this.y = y; this.vertexData = (vertexData != null) ? vertexData.clone() : null; }
        public void setVertexOffsets(float[] offsets) { this.vertexData = (offsets != null) ? offsets.clone() : null; }
        public float[] getVertexOffsets() { return vertexData; }
    }
}