package vn.pmgteam.lotus2d.viewer.gui;

import org.lwjgl.BufferUtils;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

public class FontRenderer {
    private final Font font;
    private final int textureId;

    public FontRenderer(Font font) {
        this.font = font;
        this.textureId = glGenTextures();
    }

    public void drawString(String text, float x, float y, Color color) {
        if (text == null || text.isEmpty()) return;

        int width = Math.max(1, getWidth(text));
        int height = Math.max(1, font.getSize() + 10);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        
        // --- KHỬ RĂNG CƯA NÂNG CAO ---
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB); // Tối ưu cho màn hình LCD
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON); // Tránh chữ bị "dính" hoặc nhòe tọa độ lẻ
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, 0, font.getSize());
        g.dispose();

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        int[] pixels = new int[width * height];
        img.getRGB(0, 0, width, height, pixels, 0, width);

        for (int p : pixels) {
            buffer.put((byte) ((p >> 16) & 0xFF));
            buffer.put((byte) ((p >> 8) & 0xFF));
            buffer.put((byte) (p & 0xFF));
            buffer.put((byte) ((p >> 24) & 0xFF));
        }
        buffer.flip();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        // Thiết lập tham số Texture để tránh nhòe khi scale
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Làm tròn tọa độ x, y để tránh hiện tượng "half-pixel blur"
        float ix = Math.round(x);
        float iy = Math.round(y);

        glEnable(GL_TEXTURE_2D);
        glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(ix, iy);
            glTexCoord2f(1, 0); glVertex2f(ix + width, iy);
            glTexCoord2f(1, 1); glVertex2f(ix + width, iy + height);
            glTexCoord2f(0, 1); glVertex2f(ix, iy + height);
        glEnd();
        
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    public int getWidth(String text) {
        // Sử dụng một BufferedImage tạm thời để lấy FontMetrics chính xác hơn Canvas
        BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = temp.createGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(text);
        g.dispose();
        return width;
    }
    
    public void cleanup() {
        glDeleteTextures(textureId);
    }
}