package com.dbtool.util;

import com.dbtool.model.VisualJoinState;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.swing.*;
import java.io.File;

public class VisualJoinProjectManager {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void saveProject(VisualJoinState state, java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Visual Join Project");
        if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fc.getSelectedFile();
                if (!file.getName().endsWith(".json")) {
                    file = new File(file.getAbsolutePath() + ".json");
                }
                mapper.writerWithDefaultPrettyPrinter().writeValue(file, state);
                JOptionPane.showMessageDialog(parent, "Project saved to " + file.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Failed to save project: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static VisualJoinState loadProject(java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Load Visual Join Project");
        if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            try {
                return mapper.readValue(fc.getSelectedFile(), VisualJoinState.class);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Failed to load project: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return null;
    }
}
