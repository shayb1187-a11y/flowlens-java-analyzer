package dev.sjavainspector.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.sjavainspector.api.SourceUnit;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkbenchPanelTest {
    @Test @DisplayName("Swing analysis, diagnostic navigation, stale-result rejection and rendering")
    void desktop() throws Exception {
        AtomicReference<WorkbenchPanel> reference = new AtomicReference<>();
        String source = Files.readString(Path.of("examples/errors.sjava"));
        SwingUtilities.invokeAndWait(() -> {
            WorkbenchPanel panel = new WorkbenchPanel(); reference.set(panel);
            panel.setSource(new SourceUnit("errors.sjava", source));
            find(panel, JButton.class, b -> b.getText().equals("Analyze source")).doClick();
        });
        WorkbenchPanel panel = reference.get();
        waitUntilAnalyzed(panel);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(panel, JTable.class, t -> t.getColumnCount() == 4);
            assertEquals(5, table.getRowCount());
            table.setRowSelectionInterval(0, 0);
            JTextArea editor = find(panel, JTextArea.class, t -> true);
            assertEquals(source.indexOf("retries;", source.indexOf("double budget")), editor.getCaretPosition());
            assertTrue(find(panel, JButton.class, b -> b.getText().equals("Export JSON")).isEnabled(), "No export after analysis");
            panel.setSize(1160, 760); layout(panel);
            BufferedImage image = new BufferedImage(1160, 760, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            panel.printAll(graphics); graphics.dispose();
            try { ImageIO.write(image, "png", Files.createDirectories(Path.of("target")).resolve("workbench-preview.png").toFile()); }
            catch (IOException e) { throw new UncheckedIOException(e); }
            // Edit immediately after starting another run, before its callback can run on the EDT.
            find(panel, JButton.class, b -> b.getText().equals("Analyze source")).doClick();
            editor.append("\n// edited during analysis");
            assertEquals(0, table.getRowCount());
            assertFalse(find(panel, JButton.class, b -> b.getText().equals("Export JSON")).isEnabled(), "Stale report remains exportable");
        });
        waitUntilAnalyzed(panel);
        SwingUtilities.invokeAndWait(() -> assertNotNull(find(panel, JLabel.class,
                label -> label.getText().startsWith("Source changed during analysis")), "Stale result was displayed"));
    }

    private static void waitUntilAnalyzed(WorkbenchPanel panel) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> done = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> done.set(find(panel, JButton.class,
                    b -> b.getText().equals("Analyze source")).isEnabled()));
            if (done.get()) return;
            Thread.sleep(10);
        }
        fail("Swing analysis timed out");
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }

    private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> match) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && match.test(type.cast(child))) return type.cast(child);
            if (child instanceof Container container) {
                T result = find(container, type, match);
                if (result != null) return result;
            }
        }
        return null;
    }
}
