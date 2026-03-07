package vn.pmgteam.lotus2d.core.plugin;

public interface IPluginEffect {
    void onInit();
    // Thay Graphics2D bằng render thuần OpenGL (gọi trực tiếp các hàm GL11...)
    void onRender(int width, int height);
    void onUpdate(float deltaTime);
}