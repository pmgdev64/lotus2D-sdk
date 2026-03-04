package vn.pmgteam.lotus2d.sdk.loader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import vn.pmgteam.lotus2d.sdk.LArtMesh;
import vn.pmgteam.lotus2d.sdk.scene.LScene;
import vn.pmgteam.lotus2d.sdk.util.Logger;

public class LPSDLoader {
    private static final Logger logger = Logger.getLogger("PSD-Loader");

    public static void load(String path, LScene scene) {
        File psdFile = new File(path);
        if (!psdFile.exists()) {
            logger.fatal("FILE_NOT_FOUND", "Khong tim thay file PSD tai: {}", path);
            return;
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(psdFile)) {
            // Tim Reader ho tro format PSD (TwelveMonkeys se nhay vao day)
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("PSD");
            if (!readers.hasNext()) {
                logger.fatal("MISSING_LIB", "TwelveMonkeys PSD Reader chua duoc nap dung cach!");
                return;
            }

            ImageReader reader = readers.next();
            reader.setInput(input);

            // Lay so luong Layer (Anh trong PSD duoc TwelveMonkeys coi la index hinh anh)
            int numLayers = reader.getNumImages(true);
            logger.info("Dang mổ xẻ file PSD: {} có {} layers", psdFile.getName(), numLayers);

            for (int i = 0; i < numLayers; i++) {
                try {
                    // Doc tung layer vao BufferedImage
                    BufferedImage layerImg = reader.read(i);
                    
                    // Tao ArtMesh cho layer nay
                    // Luu y: ID layer tam thoi dat theo index
                    LArtMesh mesh = new LArtMesh("Layer_" + i, null);
                    
                    // Nap texture (Ham nay trong LArtMesh se tu swap neu RAM 4GB bi day)
                    mesh.loadTexture(layerImg);
                    
                    scene.addModel(mesh);
                    
                    // Giai phong BufferedImage ngay sau khi da swap hoac gan vao mesh
                    layerImg.flush();
                    
                } catch (Exception e) {
                    logger.error("Loi khi doc layer index {}: {}", i, e.getMessage());
                }
            }
            
            reader.dispose();
            logger.info("Nap PSD hoan tat cho repo modcoderpack-redevelop.");

        } catch (IOException e) {
            logger.error("Loi I/O khi xu ly PSD: {}", e.getMessage());
        }
    }
}