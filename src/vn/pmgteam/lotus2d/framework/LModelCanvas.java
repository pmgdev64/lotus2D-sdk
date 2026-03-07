package vn.pmgteam.lotus2d.framework;

import static org.lwjgl.opengl.GL11.*;
import java.awt.Color;
import vn.pmgteam.lotus2d.viewer.gui.FontRenderer;

public class LModelCanvas {
    
    private LModel activeModel = null;

    public LModelCanvas() {}

    public void draw(int fbWidth, int fbHeight, FontRenderer fontRenderer) {
        setupOrtho(fbWidth, fbHeight);

        if (activeModel == null || activeModel.getLayers().isEmpty()) {
            renderPlaceholder(fbWidth, fbHeight, fontRenderer);
        } else {
            // Vẽ model (đã được đảo ngược layer từ PSDLoader) [cite: 2026-01-18]
            activeModel.render();
        }

        renderWatermark(fbWidth, fbHeight, fontRenderer);
    }

    private void setupOrtho(int w, int h) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void renderPlaceholder(int w, int h, FontRenderer fr) {
        String text = "No model opened";
        float tx = (w - fr.getWidth(text)) / 2.0f;
        float ty = (h - 16) / 2.0f;
        fr.drawString(text, tx, ty, Color.GRAY);
    }

    private void renderWatermark(int w, int h, FontRenderer fr) {
        String wm = "Created with Lotus2D. The Best Vtube Stream Tool & Model Viewer";
        fr.drawString(wm, w - fr.getWidth(wm) - 10, h - 25, Color.BLACK);
    }

    public void setModel(LModel model) {
        if (this.activeModel != null) this.activeModel.cleanup();
        this.activeModel = model;
        // Tự động lưu trạng thái khi nạp model mới [cite: 2026-01-02]
    }
}