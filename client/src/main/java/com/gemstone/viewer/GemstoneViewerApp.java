package com.gemstone.viewer;

import com.gemstone.viewer.db.DatabaseManager;
import com.gemstone.viewer.ui.ConnectionDialog;
import com.gemstone.viewer.ui.MainWindow;

import javax.swing.*;
import java.awt.*;

public class GemstoneViewerApp {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Try FlatLaf first (if available at runtime), fallback to Nimbus
        try {
            Class<?> flatLaf = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
            flatLaf.getMethod("setup").invoke(null);
            System.out.println("Using FlatLaf Dark theme");
        } catch (ClassNotFoundException e) {
            // Fallback: Nimbus with dark customization
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
                applyDarkNimbus();
                System.out.println("Using Nimbus Dark theme");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            ConnectionDialog connDialog = new ConnectionDialog(null);
            connDialog.setVisible(true);

            if (connDialog.isConnected()) {
                DatabaseManager db = connDialog.getDbManager();
                MainWindow window = new MainWindow(db);
                window.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }

    private static void applyDarkNimbus() {
        UIManager.put("control", new Color(18, 18, 30));
        UIManager.put("info", new Color(28, 28, 45));
        UIManager.put("nimbusBase", new Color(18, 18, 30));
        UIManager.put("nimbusAlertYellow", new Color(248, 187, 0));
        UIManager.put("nimbusDisabledText", new Color(128, 128, 128));
        UIManager.put("nimbusFocus", new Color(139, 92, 246));
        UIManager.put("nimbusGreen", new Color(52, 211, 153));
        UIManager.put("nimbusInfoBlue", new Color(66, 139, 202));
        UIManager.put("nimbusLightBackground", new Color(28, 28, 45));
        UIManager.put("nimbusOrange", new Color(248, 187, 0));
        UIManager.put("nimbusRed", new Color(220, 80, 80));
        UIManager.put("nimbusSelectedText", Color.WHITE);
        UIManager.put("nimbusSelectionBackground", new Color(99, 102, 241));
        UIManager.put("text", new Color(230, 230, 245));
    }
}
