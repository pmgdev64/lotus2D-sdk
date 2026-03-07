package vn.pmgteam.lotus2d.editor.render;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import vn.pmgteam.lotus2d.core.LParameter;

public class ParameterRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        LParameter p = (LParameter) value;
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(5, 10, 5, 10));

        JLabel nameLabel = new JLabel(p.getId());
        JLabel valueLabel = new JLabel(String.format("%.2f", p.getValue()));
        valueLabel.setForeground(new Color(0, 150, 255)); // Màu xanh Lotus

        panel.add(nameLabel, BorderLayout.WEST);
        panel.add(valueLabel, BorderLayout.EAST);

        if (isSelected) {
            panel.setBackground(list.getSelectionBackground());
            nameLabel.setForeground(list.getSelectionForeground());
        }
        return panel;
    }
}