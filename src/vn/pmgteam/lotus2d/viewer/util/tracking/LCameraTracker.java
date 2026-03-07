package vn.pmgteam.lotus2d.viewer.util.tracking;

import org.opencv.core.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import java.io.*;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;

public class LCameraTracker {
    private VideoCapture capture;
    private Mat frame;
    private Mat rgba;
    private int textureId;
    private boolean active = false;
    private CascadeClassifier faceCascade;
    private ByteBuffer buffer;
    
    // Tối ưu hóa: Tránh tạo mới liên tục
    private int frameCounter = 0;
    private MatOfRect lastDetectedFaces = new MatOfRect();
    private byte[] dataArray;

    public LCameraTracker() {
        // 1. Nạp DLL
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        
        try {
            // 2. Khởi tạo các Mat - CHỖ NÀY DỄ LỖI NẾU DLL SAI PHIÊN BẢN
            this.frame = new Mat();
            this.rgba = new Mat();
            this.capture = new VideoCapture();
            this.textureId = glGenTextures();
            
            // 3. Trích xuất và nạp XML
            String tempXmlPath = extractResource("/assets/tracking/haarcascade_frontalface_alt.xml");
            this.faceCascade = new CascadeClassifier(tempXmlPath);
            
            if (this.faceCascade.empty()) {
                System.err.println("[LCameraTracker] Error: Cascade is empty!");
            }
        } catch (Exception | UnsatisfiedLinkError e) {
            // Ném lỗi ngược về LViewer để catch và gán tracker = null
            throw new RuntimeException("OpenCV Native Init Failed: " + e.getMessage());
        }
    }

    private String extractResource(String resourcePath) throws IOException {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) throw new FileNotFoundException("Không tìm thấy: " + resourcePath);

        File tempFile = File.createTempFile("mcp_track_", ".xml");
        tempFile.deleteOnExit(); // Chống đầy ổ cứng

        try (FileOutputStream os = new FileOutputStream(tempFile)) {
            byte[] buf = new byte[8192];
            int length;
            while ((length = is.read(buf)) > 0) {
                os.write(buf, 0, length);
            }
        }
        return tempFile.getAbsolutePath();
    }

    public void start(int deviceIndex) {
        if (!active && capture != null) {
            capture.open(deviceIndex);
            // Giảm resolution ngay từ đầu để hết lag
            capture.set(3, 480); // CAP_PROP_FRAME_WIDTH
            capture.set(4, 360); // CAP_PROP_FRAME_HEIGHT
            active = capture.isOpened();
        }
    }

    public void stop() {
        if (active && capture != null) {
            active = false;
            capture.release();
        }
    }

    public void updateAndRenderTexture() {
        if (!active || capture == null || !capture.read(frame)) return;
        frameCounter++;

        // Logic Skip Frame: Chỉ track 1/5 số frame để CPU không bị quá tải
        if (faceCascade != null && !faceCascade.empty()) {
            if (frameCounter % 5 == 0) {
                Mat grayFrame = new Mat();
                Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
                Imgproc.equalizeHist(grayFrame, grayFrame);

                lastDetectedFaces.release();
                lastDetectedFaces = new MatOfRect();
                faceCascade.detectMultiScale(grayFrame, lastDetectedFaces, 1.1, 2, 0, new Size(30, 30), new Size());
                
                grayFrame.release();
            }

            // Luôn vẽ kết quả gần nhất
            for (Rect rect : lastDetectedFaces.toArray()) {
                Imgproc.rectangle(frame, new Point(rect.x, rect.y), 
                                 new Point(rect.x + rect.width, rect.y + rect.height), 
                                 new Scalar(0, 255, 0), 2);
            }
        }

        // Chuyển đổi RGBA
        Imgproc.cvtColor(frame, rgba, Imgproc.COLOR_BGR2RGBA);
        int width = rgba.cols();
        int height = rgba.rows();
        int dataSize = width * height * 4;

        if (buffer == null || buffer.capacity() < dataSize) {
            buffer = BufferUtils.createByteBuffer(dataSize);
            dataArray = new byte[dataSize];
        }

        rgba.get(0, 0, dataArray);
        buffer.clear();
        buffer.put(dataArray).flip();

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    }

    public int getTextureId() { return textureId; }
    public boolean isActive() { return active; }
    
    public void cleanup() {
        stop();
        glDeleteTextures(textureId);
        if (frame != null) frame.release();
        if (rgba != null) rgba.release();
        if (lastDetectedFaces != null) lastDetectedFaces.release();
    }
}