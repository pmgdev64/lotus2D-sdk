package vn.pmgteam.lotus2d.plugins.test;

import vn.pmgteam.lotus2d.core.plugin.IPluginEffect;
import vn.pmgteam.lotus2d.core.plugin.LotusPlugin;
import java.util.Random;
import static org.lwjgl.opengl.GL11.*;

@LotusPlugin(name = "Meteor Shower", author = "Gemini", version = "1.0", isBackground = true)
public class StarfieldWarpPlugin implements IPluginEffect {
    
    private static final int METEOR_COUNT = 25; // Số lượng vệt sao băng cùng lúc
    private final float[] x = new float[METEOR_COUNT];
    private final float[] y = new float[METEOR_COUNT];
    private final float[] length = new float[METEOR_COUNT];
    private final float[] speed = new float[METEOR_COUNT];
    private final Random rand = new Random();

    @Override
    public void onInit() {
        for (int i = 0; i < METEOR_COUNT; i++) {
            resetMeteor(i, true);
        }
    }

    private void resetMeteor(int i, boolean fullRandom) {
        x[i] = rand.nextFloat() * 2000f; // Spawning rộng hơn màn hình
        y[i] = fullRandom ? (rand.nextFloat() * 1000f) : -100f; // Reset lên đỉnh màn hình
        length[i] = 50f + rand.nextFloat() * 150f;
        speed[i] = 10f + rand.nextFloat() * 20f;
    }

    @Override
    public void onUpdate(float deltaTime) {
        float dtFactor = deltaTime * 60f; // Chuẩn hóa tốc độ theo 60fps
        for (int i = 0; i < METEOR_COUNT; i++) {
            // Di chuyển chéo: Xuống dưới và sang trái
            x[i] -= speed[i] * 0.8f * dtFactor;
            y[i] += speed[i] * dtFactor;

            // Nếu bay ra khỏi màn hình thì reset
            if (y[i] > 1200 || x[i] < -200) {
                resetMeteor(i, false);
            }
        }
    }

    @Override
    public void onRender(int width, int height) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glPushMatrix();

        // 1. Cấu hình tọa độ Pixel chuẩn
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // 2. Nền trời đêm (Dark Blue/Purple thay vì đen tuyền)
        glClearColor(0.02f, 0.02f, 0.05f, 1.0f);
        
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE); // Chế độ Additive để vệt sáng rực rỡ hơn

        // 3. Vẽ vệt sao băng (Dùng Gradient Line)
        for (int i = 0; i < METEOR_COUNT; i++) {
            glBegin(GL_LINES);
                // Đỉnh sao băng (Sáng trắng)
                glColor4f(1.0f, 1.0f, 1.0f, 0.8f);
                glVertex2f(x[i], y[i]);
                
                // Đuôi sao băng (Mờ dần sang xanh nhạt)
                glColor4f(0.4f, 0.6f, 1.0f, 0.0f);
                glVertex2f(x[i] + length[i] * 0.8f, y[i] - length[i]);
            glEnd();

            // Vẽ thêm một điểm sáng ở đầu để tạo cảm giác "hạt" sao băng
            glPointSize(3.0f);
            glBegin(GL_POINTS);
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                glVertex2f(x[i], y[i]);
            glEnd();
        }

        glPopMatrix();
        glPopAttrib();
    }
}