package dev.sjavainspector.ui;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Workbench {
    private Workbench() { }
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // Swing's built-in look and feel remains usable.
            }
            JFrame frame = new JFrame("FlowLens | Analysis Workbench");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new WorkbenchPanel());
            frame.setSize(1180, 800);
            frame.setMinimumSize(new java.awt.Dimension(800, 600));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
