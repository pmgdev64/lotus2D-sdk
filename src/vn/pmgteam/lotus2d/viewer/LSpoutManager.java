package vn.pmgteam.lotus2d.viewer;

import spout.JNISpout;
import static org.lwjgl.opengl.GL11.*;

public class LSpoutManager {
    private long spoutPtr;
    private boolean isInitialized = false;
    private String senderName;

    public LSpoutManager(String senderName) {
        this.senderName = senderName;
        // Khởi tạo đối tượng Spout trong C++ và lấy con trỏ
        this.spoutPtr = JNISpout.init(); 
    }

    public void initSender(int width, int height) {
        if (spoutPtr != 0) {
            isInitialized = JNISpout.createSender(senderName, width, height, spoutPtr);
        }
    }

    public void updateSender(int width, int height) {
        if (isInitialized) {
            JNISpout.updateSender(senderName, width, height, spoutPtr);
        }
    }

    public void sendFrame(int width, int height, int textureID) {
        if (isInitialized) {
            // bInvert = true vì OpenGL (Y-up) ngược với OBS (Y-down)
            JNISpout.sendTexture(width, height, textureID, GL_TEXTURE_2D, true, spoutPtr);
        }
    }

    public void close() {
        if (isInitialized) {
            JNISpout.releaseSender(spoutPtr);
            JNISpout.deInit(spoutPtr);
            isInitialized = false;
        }
    }
    
    public boolean isReady() { return isInitialized; }
}