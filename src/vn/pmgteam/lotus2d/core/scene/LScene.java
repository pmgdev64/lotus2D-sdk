package vn.pmgteam.lotus2d.core.scene;

import java.util.concurrent.CopyOnWriteArrayList;

import vn.pmgteam.lotus2d.core.LModelBase;

import java.awt.Graphics2D;
import java.util.List;

public class LScene {
    // Sử dụng CopyOnWriteArrayList để tránh lỗi khi vừa render vừa add/remove model
    private final List<LModelBase> models = new CopyOnWriteArrayList<>();
    private final String sceneName;
    private Graphics2D g2d;

    public LScene(String name) {
        this.sceneName = name;
    }

    public void addModel(LModelBase model) {
        models.add(model);
    }

    public void update(float deltaTime) {
        for (LModelBase model : models) {
            model.update(deltaTime);
        }
    }

    public void render() {
        for (LModelBase model : models) {
            model.render(g2d);
        }
    }

    /**
     * Giải phóng toàn bộ cảnh. 
     * Đây là nơi "máy chém" terminate() sẽ hoạt động cực kỳ hiệu quả.
     */
    public void clearScene() {
        for (LModelBase model : models) {
            model.destroy(); // Gọi terminate/destroy mà chúng ta đã viết
        }
        models.clear();
    }
}