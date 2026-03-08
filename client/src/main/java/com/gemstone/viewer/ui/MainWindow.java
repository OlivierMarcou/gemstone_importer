package com.gemstone.viewer.ui;

import com.gemstone.viewer.db.DatabaseManager;
import com.gemstone.viewer.model.Gemstone;
import com.gemstone.viewer.model.SearchCriteria;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

public class MainWindow extends JFrame {

    private static final Color BG = new Color(18, 18, 30);
    private static final Color SIDEBAR_BG = new Color(24, 24, 40);
    private static final Color CARD_BG = new Color(28, 28, 45);
    private static final Color ACCENT = new Color(139, 92, 246);
    private static final Color ACCENT2 = new Color(99, 102, 241);
    private static final Color TEXT_PRIMARY = new Color(230, 230, 245);
    private static final Color TEXT_SECONDARY = new Color(150, 150, 175);
    private static final Color BORDER_COLOR = new Color(50, 50, 70);
    private static final Color TABLE_ALT = new Color(22, 22, 38);
    private static final Color TABLE_SEL = new Color(80, 55, 150);
    private static final Color SUCCESS = new Color(52, 211, 153);

    private final DatabaseManager db;

    // Search fields
    private JTextField keywordField;
    private JComboBox<String> gemTypeCombo;
    private JComboBox<String> colorCombo;
    private JComboBox<String> shapeCombo;
    private JComboBox<String> clarityCombo;
    private JComboBox<String> treatmentCombo;
    private JComboBox<String> originCombo;
    private JComboBox<String> statusFilterCombo; // "Tous", "En stock", ...
    private JTextField minCaratsField, maxCaratsField;
    private JTextField minPriceField, maxPriceField;

    // Results
    private JTable table;
    private GemstoneTableModel tableModel;
    private JLabel resultCountLabel;
    private JLabel statusLabel;

    // Stats
    private JLabel statTotal, statValue, statCarats, statTypes, statOrigins;
    private JLabel statInStock, statUsed, statDamaged, statUnavailable;

    public MainWindow(DatabaseManager db) {
        super("💎 Gemstone Viewer — Collection de pierres précieuses");
        this.db = db;
        ensureStatusColumn();
        initUI();
        loadComboData();
        performSearch();
        loadStats();
    }

    /** Auto-run the migration if the status column is missing */
    private void ensureStatusColumn() {
        if (!db.hasStatusColumn()) {
            int choice = JOptionPane.showConfirmDialog(null,
                "La colonne 'status' n'existe pas encore dans la base.\n" +
                "Voulez-vous effectuer la migration automatiquement ?",
                "Migration requise", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    db.runStatusMigration();
                    JOptionPane.showMessageDialog(null, "✅ Migration réussie !\n" +
                        "Pierres sans prix → Indisponible\nAutres pierres → En stock");
                } catch (SQLException e) {
                    JOptionPane.showMessageDialog(null,
                        "❌ Erreur de migration :\n" + e.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void initUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1350, 820);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildSearchPanel(), buildResultsPanel());
        splitPane.setDividerLocation(290);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);
        splitPane.setContinuousLayout(true);
        root.add(splitPane, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        add(root);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(20, 20, 36));
        panel.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
            new EmptyBorder(12, 24, 12, 24)
        ));

        // Title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        JLabel icon = new JLabel("💎");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Gemstone Viewer");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);
        JLabel sub = new JLabel("Collection de pierres précieuses");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(TEXT_SECONDARY);
        titleBox.add(title);
        titleBox.add(sub);
        left.add(icon);
        left.add(Box.createHorizontalStrut(12));
        left.add(titleBox);
        panel.add(left, BorderLayout.WEST);

        // Stats row
        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        statsRow.setOpaque(false);
        statTotal = createStatLabel("—");
        statValue = createStatLabel("—");
        statCarats = createStatLabel("—");
        statTypes = createStatLabel("—");
        statOrigins = createStatLabel("—");
        statsRow.add(createStatBox("PIERRES", statTotal, SUCCESS));
        statsRow.add(createStatBox("VALEUR €", statValue, SUCCESS));
        statsRow.add(createStatBox("CARATS", statCarats, new Color(251, 191, 36)));
        statsRow.add(createStatBox("TYPES", statTypes, ACCENT));
        statsRow.add(createStatBox("ORIGINES", statOrigins, ACCENT));

        // Vertical sep
        JSeparator vs = new JSeparator(JSeparator.VERTICAL);
        vs.setForeground(BORDER_COLOR);
        vs.setPreferredSize(new Dimension(1, 40));
        statsRow.add(vs);

        // Status counts
        statInStock = createStatLabel("—");
        statUsed = createStatLabel("—");
        statDamaged = createStatLabel("—");
        statUnavailable = createStatLabel("—");
        statsRow.add(createStatBox("STOCK", statInStock, new Color(52, 211, 153)));
        statsRow.add(createStatBox("UTILISÉE", statUsed, new Color(251, 191, 36)));
        statsRow.add(createStatBox("ABÎMÉE", statDamaged, new Color(239, 68, 68)));
        statsRow.add(createStatBox("INDISPO", statUnavailable, new Color(107, 114, 128)));

        panel.add(statsRow, BorderLayout.EAST);
        return panel;
    }

    private JLabel createStatLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 14));
        l.setForeground(SUCCESS);
        return l;
    }

    private JPanel createStatBox(String label, JLabel valueLabel, Color color) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        lbl.setForeground(TEXT_SECONDARY);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        valueLabel.setForeground(color);
        box.add(lbl);
        box.add(valueLabel);
        return box;
    }

    private JScrollPane buildSearchPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(SIDEBAR_BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 0, 1, BORDER_COLOR),
            new EmptyBorder(16, 14, 16, 14)
        ));

        JLabel searchTitle = new JLabel("🔍 Recherche");
        searchTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        searchTitle.setForeground(TEXT_PRIMARY);
        searchTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(searchTitle);
        panel.add(Box.createVerticalStrut(14));

        addSectionLabel(panel, "Mot-clé");
        keywordField = createTextField("Titre, description...");
        keywordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        keywordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        keywordField.addActionListener(e -> performSearch());
        panel.add(keywordField);
        panel.add(Box.createVerticalStrut(12));

        // Status filter — first and prominent
        addSectionLabel(panel, "Statut");
        statusFilterCombo = new JComboBox<>(new String[]{
            "— Tous —", "En stock", "Utilisée", "Abîmée", "Indisponible"
        });
        styleCombo(statusFilterCombo);
        // Color the selected item
        statusFilterCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? TABLE_SEL : CARD_BG);
                setForeground(switch (String.valueOf(value)) {
                    case "En stock" -> new Color(52, 211, 153);
                    case "Utilisée" -> new Color(251, 191, 36);
                    case "Abîmée" -> new Color(239, 68, 68);
                    case "Indisponible" -> new Color(107, 114, 128);
                    default -> TEXT_PRIMARY;
                });
                return this;
            }
        });
        panel.add(statusFilterCombo);
        panel.add(Box.createVerticalStrut(10));

        addSectionLabel(panel, "Type de pierre");
        gemTypeCombo = createCombo();
        panel.add(gemTypeCombo);
        panel.add(Box.createVerticalStrut(10));

        addSectionLabel(panel, "Couleur");
        colorCombo = createCombo();
        panel.add(colorCombo);
        panel.add(Box.createVerticalStrut(10));

        addSectionLabel(panel, "Forme");
        shapeCombo = createCombo();
        panel.add(shapeCombo);
        panel.add(Box.createVerticalStrut(10));

        addSectionLabel(panel, "Clarté");
        clarityCombo = createCombo();
        panel.add(clarityCombo);
        panel.add(Box.createVerticalStrut(10));

        addSectionLabel(panel, "Traitement");
        treatmentCombo = createCombo();
        panel.add(treatmentCombo);
        panel.add(Box.createVerticalStrut(10));

        addSectionLabel(panel, "Origine");
        originCombo = createCombo();
        panel.add(originCombo);
        panel.add(Box.createVerticalStrut(14));

        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sep);
        panel.add(Box.createVerticalStrut(14));

        addSectionLabel(panel, "Carats (min / max)");
        JPanel caratsRow = new JPanel(new GridLayout(1, 2, 6, 0));
        caratsRow.setOpaque(false);
        caratsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        caratsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        minCaratsField = createSmallTextField("Min");
        maxCaratsField = createSmallTextField("Max");
        caratsRow.add(minCaratsField);
        caratsRow.add(maxCaratsField);
        panel.add(caratsRow);
        panel.add(Box.createVerticalStrut(10));

        addSectionLabel(panel, "Prix (min / max) €");
        JPanel priceRow = new JPanel(new GridLayout(1, 2, 6, 0));
        priceRow.setOpaque(false);
        priceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        minPriceField = createSmallTextField("Min");
        maxPriceField = createSmallTextField("Max");
        priceRow.add(minPriceField);
        priceRow.add(maxPriceField);
        panel.add(priceRow);
        panel.add(Box.createVerticalStrut(16));

        JButton searchBtn = new JButton("🔍  Rechercher");
        searchBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        searchBtn.setBackground(ACCENT2);
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setFocusPainted(false);
        searchBtn.setBorderPainted(false);
        searchBtn.setOpaque(true);
        searchBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        searchBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        searchBtn.addActionListener(e -> performSearch());
        panel.add(searchBtn);
        panel.add(Box.createVerticalStrut(8));

        JButton resetBtn = new JButton("↺  Réinitialiser");
        resetBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resetBtn.setBackground(CARD_BG);
        resetBtn.setForeground(TEXT_SECONDARY);
        resetBtn.setFocusPainted(false);
        resetBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        resetBtn.addActionListener(e -> resetSearch());
        panel.add(resetBtn);
        panel.add(Box.createVerticalGlue());

        JScrollPane sidebarScroll = new JScrollPane(panel);
        sidebarScroll.setBorder(null);
        sidebarScroll.getViewport().setBackground(SIDEBAR_BG);
        sidebarScroll.setBackground(SIDEBAR_BG);
        sidebarScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return sidebarScroll;
    }

    private JPanel buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(16, 16, 8, 16));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        resultCountLabel = new JLabel("Résultats : —");
        resultCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        resultCountLabel.setForeground(TEXT_PRIMARY);
        JLabel hint = new JLabel("Double-clic → fiche détaillée  •  La photo est cliquable dans la fiche");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(TEXT_SECONDARY);
        topBar.add(resultCountLabel, BorderLayout.WEST);
        topBar.add(hint, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        tableModel = new GemstoneTableModel();
        table = new JTable(tableModel);
        table.setBackground(BG);
        table.setForeground(TEXT_PRIMARY);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(TABLE_SEL);
        table.setSelectionForeground(Color.WHITE);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFocusable(false);
        table.getTableHeader().setReorderingAllowed(false);

        // Default renderer (dark rows)
        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                if (!isSelected) { setBackground(row % 2 == 0 ? BG : TABLE_ALT); setForeground(TEXT_PRIMARY); }
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, defaultRenderer);
        table.setDefaultRenderer(String.class, defaultRenderer);

        // Status column renderer (col 1)
        table.getColumnModel().getColumn(1).setCellRenderer(new GemstoneTableModel.StatusCellRenderer());

        // Price renderer (col 10)
        table.getColumnModel().getColumn(10).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                if (value instanceof BigDecimal bd) value = String.format("%.2f €", bd);
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                if (!isSelected) { setBackground(row % 2 == 0 ? BG : TABLE_ALT); setForeground(SUCCESS); }
                setHorizontalAlignment(RIGHT);
                return this;
            }
        });

        // Carats renderer (col 4)
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                if (value instanceof BigDecimal bd) value = bd + " ct";
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                if (!isSelected) { setBackground(row % 2 == 0 ? BG : TABLE_ALT); setForeground(new Color(251, 191, 36)); }
                return this;
            }
        });

        // Column widths: #, Statut, Type, Titre, Carats, Forme, Couleur, Clarté, Traitement, Origine, Prix, Dim
        int[] widths = {40, 110, 90, 280, 70, 80, 90, 60, 90, 90, 90, 100};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        // Header
        JTableHeader header = table.getTableHeader();
        header.setBackground(CARD_BG);
        header.setForeground(TEXT_SECONDARY);
        header.setFont(new Font("Segoe UI", Font.BOLD, 11));
        header.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_COLOR));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);

        // Double-click → open detail
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        row = table.convertRowIndexToModel(row);
                        Gemstone gem = tableModel.getGemstoneAt(row);
                        new GemstoneDetailDialog(MainWindow.this, gem, db,
                            () -> SwingUtilities.invokeLater(() -> performSearch())
                        ).setVisible(true);
                    }
                }
            }
        });

        TableRowSorter<GemstoneTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(15, 15, 26));
        bar.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
            new EmptyBorder(6, 16, 6, 16)
        ));
        statusLabel = new JLabel("✓ Connecté à la base de données");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(SUCCESS);
        bar.add(statusLabel, BorderLayout.WEST);
        JLabel version = new JLabel("Gemstone Viewer v1.1 — Java 21 + Swing");
        version.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        version.setForeground(TEXT_SECONDARY);
        bar.add(version, BorderLayout.EAST);
        return bar;
    }

    // ---- Helpers ----

    private void addSectionLabel(JPanel panel, String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(TEXT_SECONDARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lbl);
        panel.add(Box.createVerticalStrut(4));
    }

    private JTextField createTextField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tf.setBackground(CARD_BG);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COLOR, 1),
            new EmptyBorder(4, 8, 4, 8)
        ));
        tf.putClientProperty("JTextField.placeholderText", placeholder);
        return tf;
    }

    private JTextField createSmallTextField(String placeholder) {
        JTextField tf = createTextField(placeholder);
        tf.setPreferredSize(new Dimension(0, 30));
        return tf;
    }

    private JComboBox<String> createCombo() {
        JComboBox<String> cb = new JComboBox<>();
        styleCombo(cb);
        return cb;
    }

    private void styleCombo(JComboBox<?> cb) {
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cb.setBackground(CARD_BG);
        cb.setForeground(TEXT_PRIMARY);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    }

    // ---- Data loading ----

    private void loadComboData() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                fillCombo(gemTypeCombo, "gem_type");
                fillCombo(colorCombo, "color");
                fillCombo(shapeCombo, "shape");
                fillCombo(clarityCombo, "clarity");
                fillCombo(treatmentCombo, "treatment");
                fillCombo(originCombo, "origin");
                return null;
            }
        };
        worker.execute();
    }

    private void fillCombo(JComboBox<String> combo, String column) {
        try {
            List<String> vals = db.getDistinctValues(column);
            SwingUtilities.invokeLater(() -> {
                combo.removeAllItems();
                for (String v : vals) combo.addItem(v.isEmpty() ? "— Tous —" : v);
            });
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadStats() {
        SwingWorker<Map<String, Object>, Void> worker = new SwingWorker<>() {
            @Override protected Map<String, Object> doInBackground() throws Exception {
                return db.getStatistics();
            }
            @Override protected void done() {
                try {
                    Map<String, Object> stats = get();
                    NumberFormat nf = NumberFormat.getInstance();
                    statTotal.setText(nf.format(stats.get("total")));
                    BigDecimal tv = (BigDecimal) stats.get("total_value");
                    statValue.setText(tv != null ? String.format("%.0f", tv) : "—");
                    BigDecimal tc = (BigDecimal) stats.get("total_carats");
                    statCarats.setText(tc != null ? String.format("%.1f", tc) : "—");
                    statTypes.setText(String.valueOf(stats.get("gem_types")));
                    statOrigins.setText(String.valueOf(stats.get("origins")));

                    @SuppressWarnings("unchecked")
                    Map<String, Integer> sc = (Map<String, Integer>) stats.get("statusCounts");
                    if (sc != null) {
                        statInStock.setText(String.valueOf(sc.getOrDefault("in_stock", 0)));
                        statUsed.setText(String.valueOf(sc.getOrDefault("used", 0)));
                        statDamaged.setText(String.valueOf(sc.getOrDefault("damaged", 0)));
                        statUnavailable.setText(String.valueOf(sc.getOrDefault("unavailable", 0)));
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        };
        worker.execute();
    }

    // ---- Search ----

    private void performSearch() {
        SearchCriteria criteria = buildCriteria();
        statusLabel.setText("Recherche en cours...");
        statusLabel.setForeground(new Color(251, 191, 36));

        SwingWorker<List<Gemstone>, Void> worker = new SwingWorker<>() {
            @Override protected List<Gemstone> doInBackground() throws Exception {
                return db.searchGemstones(criteria);
            }
            @Override protected void done() {
                try {
                    List<Gemstone> results = get();
                    tableModel.setData(results);
                    int n = results.size();
                    resultCountLabel.setText(n + " résultat" + (n > 1 ? "s" : ""));
                    statusLabel.setText("✓  " + n + " pierre(s) trouvée(s)");
                    statusLabel.setForeground(SUCCESS);
                    loadStats(); // refresh stats
                } catch (Exception e) {
                    statusLabel.setText("❌ Erreur : " + e.getMessage());
                    statusLabel.setForeground(new Color(220, 80, 80));
                }
            }
        };
        worker.execute();
    }

    private SearchCriteria buildCriteria() {
        SearchCriteria c = new SearchCriteria();
        c.setKeyword(keywordField.getText().trim());
        c.setGemType(selectedValue(gemTypeCombo));
        c.setColor(selectedValue(colorCombo));
        c.setShape(selectedValue(shapeCombo));
        c.setClarity(selectedValue(clarityCombo));
        c.setTreatment(selectedValue(treatmentCombo));
        c.setOrigin(selectedValue(originCombo));

        // Status filter
        String statusSel = (String) statusFilterCombo.getSelectedItem();
        if (statusSel != null && !statusSel.startsWith("—")) {
            c.setStatus(switch (statusSel) {
                case "En stock" -> Gemstone.Status.IN_STOCK;
                case "Utilisée" -> Gemstone.Status.USED;
                case "Abîmée" -> Gemstone.Status.DAMAGED;
                case "Indisponible" -> Gemstone.Status.UNAVAILABLE;
                default -> null;
            });
        }

        c.setMinCarats(parseBD(minCaratsField.getText()));
        c.setMaxCarats(parseBD(maxCaratsField.getText()));
        c.setMinPrice(parseBD(minPriceField.getText()));
        c.setMaxPrice(parseBD(maxPriceField.getText()));
        return c;
    }

    private String selectedValue(JComboBox<String> cb) {
        String v = (String) cb.getSelectedItem();
        return (v == null || v.startsWith("—")) ? null : v;
    }

    private BigDecimal parseBD(String text) {
        if (text == null || text.isBlank()) return null;
        try { return new BigDecimal(text.trim().replace(",", ".")); }
        catch (NumberFormatException e) { return null; }
    }

    private void resetSearch() {
        keywordField.setText("");
        if (statusFilterCombo.getItemCount() > 0) statusFilterCombo.setSelectedIndex(0);
        if (gemTypeCombo.getItemCount() > 0) gemTypeCombo.setSelectedIndex(0);
        if (colorCombo.getItemCount() > 0) colorCombo.setSelectedIndex(0);
        if (shapeCombo.getItemCount() > 0) shapeCombo.setSelectedIndex(0);
        if (clarityCombo.getItemCount() > 0) clarityCombo.setSelectedIndex(0);
        if (treatmentCombo.getItemCount() > 0) treatmentCombo.setSelectedIndex(0);
        if (originCombo.getItemCount() > 0) originCombo.setSelectedIndex(0);
        minCaratsField.setText("");
        maxCaratsField.setText("");
        minPriceField.setText("");
        maxPriceField.setText("");
        performSearch();
    }
}
