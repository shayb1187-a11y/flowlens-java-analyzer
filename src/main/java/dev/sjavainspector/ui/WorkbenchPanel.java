package dev.sjavainspector.ui;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Analyzer;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourceUnit;
import dev.sjavainspector.io.FileSourceRepository;
import dev.sjavainspector.report.JsonReportFormatter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

/** Presentation adapter. Expensive analysis runs off the Swing event thread. */
public final class WorkbenchPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Analyzer analyzer = new Analyzer();
    private final JTextArea editor = new JTextArea();
    private final JLabel status = new JLabel("Ready — choose an example and select Analyze");
    private final JButton analyze = new JButton("Analyze source");
    private final JButton export = new JButton("Export JSON");
    private final DiagnosticTable diagnostics = new DiagnosticTable();
    private final DefaultTableModel metrics = new DefaultTableModel(
            new String[]{"Method", "Line", "Statements", "Complexity", "Max nesting"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private String sourceName = "valid.sjava";
    private long revision;
    private AnalysisResult currentResult;

    public WorkbenchPanel() {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        setBackground(new Color(243, 246, 250));
        add(header(), BorderLayout.NORTH);

        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        editor.setTabSize(4);
        editor.setMargin(new java.awt.Insets(12, 14, 12, 14));
        editor.getAccessibleContext().setAccessibleName("s-Java source editor");
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        JTable issues = new JTable(diagnostics);
        issues.setRowHeight(26);
        issues.getColumnModel().getColumn(0).setPreferredWidth(70);
        issues.getColumnModel().getColumn(1).setPreferredWidth(55);
        issues.getColumnModel().getColumn(2).setPreferredWidth(70);
        issues.getColumnModel().getColumn(3).setPreferredWidth(700);
        issues.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && issues.getSelectedRow() >= 0) highlight(issues.getSelectedRow());
        });
        JTabbedPane results = new JTabbedPane();
        results.addTab("Diagnostics — select a row to locate it", new JScrollPane(issues));
        JTable measurements = new JTable(metrics);
        measurements.setRowHeight(26);
        results.addTab("Method metrics", new JScrollPane(measurements));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(editor), results);
        split.setResizeWeight(0.7);
        split.setDividerLocation(380);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        add(status, BorderLayout.SOUTH);
        loadExample("valid.sjava");
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        JPanel title = new JPanel(new BorderLayout(0, 5));
        title.setOpaque(false);
        JLabel name = new JLabel("FlowLens");
        name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 29));
        name.setForeground(new Color(25, 47, 78));
        title.add(name, BorderLayout.NORTH);
        title.add(new JLabel("Static analysis  /  Scope & type safety  /  Maintainability insights"), BorderLayout.SOUTH);
        header.add(title, BorderLayout.NORTH);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        JComboBox<String> examples = new JComboBox<>(new String[]{"Load an example…", "valid.sjava", "errors.sjava", "branching.sjava", "lint.sjava"});
        examples.getAccessibleContext().setAccessibleName("Bundled example");
        examples.addActionListener(e -> {
            if (examples.getSelectedIndex() == 0) return;
            if (confirmDiscard()) loadExample((String) examples.getSelectedItem());
            examples.setSelectedIndex(0);
        });
        JButton open = new JButton("Open .sjava");
        open.addActionListener(e -> open());
        JButton save = new JButton("Save source as…");
        save.addActionListener(e -> save(false));
        analyze.addActionListener(e -> analyzeSource());
        export.setEnabled(false);
        export.addActionListener(e -> save(true));
        toolbar.add(examples); toolbar.add(open); toolbar.add(save); toolbar.add(analyze); toolbar.add(export);
        header.add(toolbar, BorderLayout.SOUTH);
        return header;
    }

    private void changed() {
        revision++;
        currentResult = null;
        export.setEnabled(false);
        editor.getHighlighter().removeAllHighlights();
        diagnostics.set(List.of()); metrics.setRowCount(0);
        status.setText("Edited — analyze to refresh diagnostics");
    }
    private boolean confirmDiscard() {
        return JOptionPane.showConfirmDialog(this, "Replace the editor contents? Save your source first if needed.",
                "Open source", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }
    private void loadExample(String file) {
        try (var input = getClass().getResourceAsStream("/examples/" + file)) {
            if (input == null) throw new IOException("Bundled example not found: " + file);
            setSource(new SourceUnit(file, new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) { showError(e.getMessage()); }
    }
    /** Also useful for embedding the workbench in another Swing application. */
    public void setSource(SourceUnit source) {
        sourceName = source.name();
        editor.setText(source.text()); editor.setCaretPosition(0);
        status.setText(sourceName + " — select Analyze source");
    }
    private void analyzeSource() {
        SourceUnit snapshot = new SourceUnit(sourceName, editor.getText());
        long requestedRevision = revision;
        analyze.setEnabled(false); status.setText("Analyzing " + sourceName + "…");
        new SwingWorker<AnalysisResult, Void>() {
            @Override protected AnalysisResult doInBackground() { return analyzer.analyze(snapshot); }
            @Override protected void done() {
                analyze.setEnabled(true);
                if (revision != requestedRevision) {
                    status.setText("Source changed during analysis — analyze again"); return;
                }
                try {
                    currentResult = get();
                    diagnostics.set(currentResult.diagnostics());
                    metrics.setRowCount(0);
                    currentResult.methods().forEach(m -> metrics.addRow(new Object[]{m.name(), m.line(),
                            m.statements(), m.cyclomaticComplexity(), m.maxNesting()}));
                    long errors = currentResult.diagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).count();
                    long warnings = currentResult.diagnostics().size() - errors;
                    status.setText((currentResult.isValid() ? "VALID" : "INVALID") + "  |  " + errors + " errors  |  "
                            + warnings + " warnings  |  " + currentResult.methods().size() + " methods  |  " + sourceName);
                    export.setEnabled(true);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); status.setText("Analysis interrupted"); }
                catch (ExecutionException e) { showError("Analysis failed: " + e.getCause().getMessage()); }
            }
        }.execute();
    }
    private void highlight(int row) {
        if (row >= diagnostics.rows.size()) return;
        Diagnostic diagnostic = diagnostics.rows.get(row);
        int start = Math.min(diagnostic.position().offset(), editor.getDocument().getLength());
        editor.getHighlighter().removeAllHighlights();
        try {
            int end = Math.min(editor.getLineEndOffset(editor.getLineOfOffset(start)), editor.getDocument().getLength());
            editor.getHighlighter().addHighlight(start, end, new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 227, 170)));
            editor.setCaretPosition(start); editor.requestFocusInWindow();
        } catch (BadLocationException e) { showError("Could not locate diagnostic in the editor."); }
    }
    private void open() {
        if (!confirmDiscard()) return;
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try { setSource(new FileSourceRepository().read(chooser.getSelectedFile().toPath())); }
        catch (IOException e) { showError(e.getMessage()); }
    }
    private void save(boolean report) {
        if (report && currentResult == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(report ? "analysis.json" : "source.sjava"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this, "Replace " + target.getFileName() + "?",
                "Overwrite file", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            String text = report ? new JsonReportFormatter().format(List.of(currentResult)) : editor.getText();
            Files.writeString(target, text, StandardCharsets.UTF_8);
            if (!report) { sourceName = target.toString(); changed(); }
            status.setText("Saved " + target + (report ? "" : " — analyze to refresh the report name"));
        } catch (IOException e) { showError(e.getMessage()); }
    }
    private void showError(String message) { JOptionPane.showMessageDialog(this, message, "FlowLens", JOptionPane.ERROR_MESSAGE); }

    private static final class DiagnosticTable extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private List<Diagnostic> rows = List.of();
        private final String[] columns = {"Severity", "Code", "Position", "Message"};
        void set(List<Diagnostic> rows) { this.rows = List.copyOf(rows); fireTableDataChanged(); }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        public Object getValueAt(int row, int column) {
            Diagnostic d = rows.get(row);
            return switch (column) {
                case 0 -> d.severity(); case 1 -> d.code();
                case 2 -> d.position().line() + ":" + d.position().column(); default -> d.message();
            };
        }
    }
}
