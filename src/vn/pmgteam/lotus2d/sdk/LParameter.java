package vn.pmgteam.lotus2d.sdk;

public class LParameter {
    private String id;
    private float value;
    private float min, max, defaultValue;

    public LParameter(String id, float min, float max, float defaultValue) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public void setValue(float newValue) {
        // Clamp giá trị để không vượt quá giới hạn
        this.value = Math.max(min, Math.min(max, newValue));
    }

    public float getValue() {
        return value;
    }

    public String getId() {
        return id;
    }
}