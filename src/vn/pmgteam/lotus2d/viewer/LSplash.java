package vn.pmgteam.lotus2d.viewer;

import javax.swing.*;
import java.awt.*;

public class LSplash extends JWindow {
	public LSplash() {
	    JPanel content = new JPanel(new BorderLayout());
	    content.setBackground(new Color(30, 30, 30)); // Màu tối hơn một chút cho chuẩn Dark Mode
	    content.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));

	    JLabel label = new JLabel("Loading Lotus2D Viewer...", SwingConstants.CENTER);
	    label.setForeground(new Color(200, 200, 200));
	    label.setFont(new Font("Tahoma", Font.BOLD, 14));
	    
	    // Thêm ProgressBar phía dưới
	    JProgressBar progressBar = new JProgressBar();
	    progressBar.setIndeterminate(true);
	    progressBar.setPreferredSize(new Dimension(400, 5));
	    progressBar.setBackground(new Color(30, 30, 30));
	    progressBar.setForeground(new Color(0, 120, 215)); // Màu xanh Windows đặc trưng
	    progressBar.setBorder(BorderFactory.createEmptyBorder());

	    content.add(label, BorderLayout.CENTER);
	    content.add(progressBar, BorderLayout.SOUTH);

	    setSize(450, 250);
	    setLocationRelativeTo(null);
	    setContentPane(content);
	    setAlwaysOnTop(true);
	}

    public void showSplash() {
        setVisible(true);
    }

    public void hideSplash() {
        setVisible(false);
        dispose(); // Giải phóng bộ nhớ
    }
}