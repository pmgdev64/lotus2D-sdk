package vn.pmgteam.lotus2d.framework.layer;

import static org.lwjgl.opengl.GL11.*;

public class LLayer {
    public String name;
    public int textureId;
    public int x, y, width, height;
    public boolean visible = true;

    public LLayer(String name, int textureId, int x, int y, int w, int h) {
        this.name = name;
        this.textureId = textureId;
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    public void render() {
        if (!visible || textureId == 0) return;
        glBindTexture(GL_TEXTURE_2D, textureId);
        glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(x, y);
            glTexCoord2f(1, 0); glVertex2f(x + width, y);
            glTexCoord2f(1, 1); glVertex2f(x + width, y + height);
            glTexCoord2f(0, 1); glVertex2f(x, y + height);
        glEnd();
    }
}