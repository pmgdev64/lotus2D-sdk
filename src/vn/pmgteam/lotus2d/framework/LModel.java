package vn.pmgteam.lotus2d.framework;

import java.util.ArrayList;
import java.util.List;

import vn.pmgteam.lotus2d.framework.layer.LLayer;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

public class LModel {
    private String name;
    private final List<LLayer> layers = new ArrayList<>();

    public LModel(String name) { this.name = name; }

    public void addLayer(LLayer layer) { layers.add(layer); }
    
    public List<LLayer> getLayers() { return layers; }

    public void render() {
        for (LLayer layer : layers) {
            layer.render();
        }
    }

    public void cleanup() {
        for (LLayer l : layers) glDeleteTextures(l.textureId);
        layers.clear();
    }

    public String getName() { return name; }
}