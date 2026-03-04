package vn.pmgteam.lotus2d.sdk;

import java.lang.reflect.*;

import vn.pmgteam.lotus2d.sdk.scene.LScene;

/**
 * [LOTUS2D SDK - THE COMPLETE DEVELOPER MANUAL]
 * Tổng hợp danh sách hàm, biến và hướng dẫn triển khai thực tế.
 */
public class LShowcase {

    // --- BẢNG MÀU SIÊU ĐẬM (VIBRANT RGB) ---
    public static final String RESET    = "\u001B[0m";
    public static final String KEYWORD  = "\u001B[38;2;210;130;40m"; 
    public static final String TYPE     = "\u001B[38;2;80;180;255m"; 
    public static final String METHOD   = "\u001B[38;2;140;255;0m";  
    public static final String CYAN_COL = "\u001B[38;2;0;255;255m";  
    public static final String COMMENT  = "\u001B[38;2;160;160;160m"; 
    public static final String VARIABLE = "\u001B[38;2;255;255;255m"; 
    public static final String WHITE    = "\u001B[38;2;255;255;255m"; 
    public static final String STRING   = "\u001B[38;2;255;255;100m"; 
    public static final String BOLD     = "\u001B[1m";

    public static void main(String[] args) {
        printHeader();
        
        // 1. DANH SÁCH HÀM & BIẾN (TỰ ĐỘNG QUÉT)
        printSection("1. CHI TIẾT CẤU TRÚC SDK (API REFERENCE)");
        Class<?>[] classes = { LArtMesh.class, LModelBase.class, LParameter.class, LScene.class };
        for (Class<?> c : classes) showClassStructure(c);

        // 2. HƯỚNG DẪN CODE CỤ THỂ
        printSection("2. HƯỚNG DẪN TRIỂN KHAI CODE (CODE EXAMPLES)");
        showUsageGuide();

        // 3. QUY TẮC VẬN HÀNH HỆ THỐNG
        printSection("3. QUY TẮC VÀNG & TỐI ƯU HÓA");
        showSystemRules();
    }

    private static void printHeader() {
        System.out.println(BOLD + "================================================================" + RESET);
        System.out.println(BOLD + VARIABLE + "    LOTUS2D SDK MANUAL - REPO: modcoderpack-redevelop    " + RESET);
        System.out.println(BOLD + "================================================================" + RESET);
    }

    private static void printSection(String title) {
        System.out.println("\n" + BOLD + CYAN_COL + ">>> " + title + " <<<" + RESET);
    }

    private static void showClassStructure(Class<?> clazz) {
        System.out.println("\n" + KEYWORD + "class " + RESET + BOLD + TYPE + clazz.getSimpleName() + RESET);
        
        System.out.println(COMMENT + "  // Các biến có sẵn (Fields):" + RESET);
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println("    " + TYPE + f.getType().getSimpleName() + " " + VARIABLE + f.getName() + RESET + ";");
        }

        System.out.println(COMMENT + "  // Các hàm có sẵn (Methods):" + RESET);
        for (Method m : clazz.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                System.out.print("    " + METHOD + m.getName() + RESET + "(");
                Parameter[] ps = m.getParameters();
                for (int i = 0; i < ps.length; i++) {
                    System.out.print(TYPE + ps[i].getType().getSimpleName() + " " + VARIABLE + "arg" + i + (i < ps.length - 1 ? ", " : ""));
                }
                System.out.println(");");
            }
        }
    }

    private static void showUsageGuide() {
        System.out.println(BOLD + WHITE + "A. Khởi tạo một Model hoàn chỉnh:" + RESET);
        System.out.println(STRING + "    LModelBase model = new LModelBase(\"my_avatar\", null) {...};" + RESET);
        System.out.println(STRING + "    LArtMesh eyeLayer = new LArtMesh(\"eye_left\", model);" + RESET);
        System.out.println(STRING + "    eyeLayer.setVertices(new float[]{...});" + RESET);
        System.out.println(STRING + "    eyeLayer.loadTexture(image);" + RESET);

        System.out.println("\n" + BOLD + WHITE + "B. Điều khiển Rigging bằng Parameter:" + RESET);
        System.out.println(STRING + "    LParameter paramX = new LParameter(\"eye_x\", -1.0f, 1.0f, 0.0f);" + RESET);
        System.out.println(STRING + "    // Cập nhật tọa độ mesh theo parameter" + RESET);
        System.out.println(STRING + "    eyeLayer.updateBinding(paramX, paramY);" + RESET);

        System.out.println("\n" + BOLD + WHITE + "C. Quản lý Scene & Hủy bỏ (Cleanup):" + RESET);
        System.out.println(STRING + "    LScene scene = new LScene(\"MainScene\");" + RESET);
        System.out.println(STRING + "    scene.addModel(model);" + RESET);
        System.out.println(STRING + "    scene.clearScene(); // Kích hoạt 'máy chém' destroy()" + RESET);
    }

    private static void showSystemRules() {
        System.out.println(BOLD + "1. Quy tắc AutoSave:" + RESET);
        System.out.println("   - Mọi thay đổi qua " + METHOD + "markDirty()" + RESET + " sẽ kích hoạt lưu file .lmp.");
        
        System.out.println("\n" + BOLD + "2. Quản lý RAM (Intel UHD 610):" + RESET);
        System.out.println("   - Nếu RAM > 80%, Texture tự động chuyển sang Swap file (.tmp).");
        System.out.println("   - Gọi " + METHOD + "MemoryGuardian.checkAndForceGC()" + RESET + " trước các tác vụ nặng.");

        System.out.println("\n" + BOLD + "3. Cấu trúc File .lmp:" + RESET);
        System.out.println("   - Phải có " + STRING + "model.manifest.json" + RESET + " ở root ZIP.");
    }
}