package vn.pmgteam.lotus2d.editor.project;

import java.util.ArrayList;
import java.util.List;

public class LProjectData {
    public String projectName;
    public int gridCols;
    public int gridRows;
    public List<LayerSaveData> layers = new ArrayList<>();

    public static class LayerSaveData {
        public String layerId;
        public String texturePath; // Đường dẫn tương đối
        public float[] baseVertices;
        public List<ParamPointData> paramPoints = new ArrayList<>();
    }

    public static class ParamPointData {
        public float x, y;
        public float[] vertexData;
    }
}