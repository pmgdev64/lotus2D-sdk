package vn.pmgteam.lotus2d.framework.loader;

import com.twelvemonkeys.imageio.plugins.psd.PSDLayerInfo;

import vn.pmgteam.lotus2d.framework.LModel;
import vn.pmgteam.lotus2d.framework.layer.LLayer;
import vn.pmgteam.lotus2d.framework.util.TextureUtil;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public class LPSDLoader {

    public static LModel loadFromStream(InputStream is, String modelName) throws Exception {
        LModel model = new LModel(modelName);
        ImageInputStream input = ImageIO.createImageInputStream(is);
        var readers = ImageIO.getImageReaders(input);
        
        if (!readers.hasNext()) throw new Exception("No PSD Reader found!");
        ImageReader reader = readers.next();
        reader.setInput(input);

        List<PSDLayerInfo> layerInfos = extractLayerInfo(reader);
        int numImages = reader.getNumImages(true);

        for (int i = 1; i < numImages; i++) {
            BufferedImage img = reader.read(i);
            if (img == null) continue;

            int px = 0, py = 0;
            String name = "Layer " + i;

            if (layerInfos != null && (i - 1) < layerInfos.size()) {
                PSDLayerInfo info = layerInfos.get(i - 1);
                px = info.left;
                py = info.top;
                name = info.getLayerName();
            }

            // Chuyển BufferedImage sang Texture ID của OpenGL
            int texID = TextureUtil.uploadTexture(img); 
            model.addLayer(new LLayer(name, texID, px, py, img.getWidth(), img.getHeight()));
        }

        Collections.reverse(model.getLayers()); // Đảo thứ tự vẽ [cite: 2026-01-18]
        return model;
    }

    private static List<PSDLayerInfo> extractLayerInfo(ImageReader reader) {
        try {
            // Thử lấy từ Stream Metadata (Direct Access)
            Object meta = reader.getStreamMetadata();
            Field f = meta.getClass().getDeclaredField("layerInfo");
            f.setAccessible(true);
            return (List<PSDLayerInfo>) f.get(meta);
        } catch (Exception e) {
            return null; // Fallback hoặc log lỗi
        }
    }
}