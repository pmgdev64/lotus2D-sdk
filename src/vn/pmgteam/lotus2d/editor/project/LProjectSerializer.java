package vn.pmgteam.lotus2d.editor.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;

public class LProjectSerializer {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // LƯU PROJECT (.lmproj)
    public static void saveProject(File file, LProjectData data) {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(data, writer);
            System.out.println("[ModCoderPack] Project saved to: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // MỞ PROJECT (.lmproj)
    public static LProjectData loadProject(File file) {
        try (Reader reader = new FileReader(file)) {
            return gson.fromJson(reader, LProjectData.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}