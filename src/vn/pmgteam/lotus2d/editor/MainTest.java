package vn.pmgteam.lotus2d.editor;

import javax.swing.JFrame; // Cần import JFrame
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;

import vn.pmgteam.lotus2d.core.LArtMesh;

public class MainTest {
	public static void main(String[] args) {
	    LArtMesh eye = new LArtMesh("EyeLayer", null);
        float[] quad = {300, 200, 400, 200, 400, 300, 300, 300};
        eye.setVertices(quad);
		
	    // 1. Phải nạp LookAndFeel TRƯỚC khi bật flag Decorated
	    try {
	        UIManager.setLookAndFeel(new FlatDarkLaf());
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    // 2. Bây giờ mới bật flag này, FlatLaf sẽ biết để vẽ TitleBar theo kiểu Dark Mode
	    JFrame.setDefaultLookAndFeelDecorated(true); 

	    SwingUtilities.invokeLater(() -> {
	        LEditorFrame editor = new LEditorFrame(eye);
	        editor.setVisible(true);
	    });
	}
}