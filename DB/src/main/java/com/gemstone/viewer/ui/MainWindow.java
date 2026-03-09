package com.gemstone.viewer.ui;

import com.gemstone.viewer.db.DatabaseManager;
import com.gemstone.viewer.model.EtsyListing;
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

    private static final Color BG        = new Color(18, 18, 30);
    private static final Color SIDEBAR_BG= new Color(24, 24, 40);
    private static final Color CARD_BG   = new Color(28, 28, 45);
    private static final Color ACCENT    = new Color(139, 92, 246);
    private static final Color ACCENT2   = new Color(99, 102, 241);
    private static final Color ETSY_COL  = new Color(245, 140, 50);
    private static final Color TEXT_PRI  = new Color(230, 230, 245);
    private static final Color TEXT_SEC  = new Color(150, 150, 175);
    private static final Color BORDER_C  = new Color(50, 50, 70);
    private static final Color TABLE_ALT = new Color(22, 22, 38);
    private static final Color TABLE_SEL = new Color(80, 55, 150);
    private static final Color SUCCESS   = new Color(52, 211, 153);

    private final DatabaseManager db;

    // Gemstone search fields
    private JTextField keywordField;
    private JComboBox<String> gemTypeCombo, colorCombo, shapeCombo, clarityCombo, treatmentCombo, originCombo;
    private JComboBox<String> statusFilterCombo;
    private JTextField minCaratsField, maxCaratsField, minPriceField, maxPriceField;
    private JTextField etsyRefFilterField;

    // Gemstone results
    private JTable gemTable;
    private GemstoneTableModel gemTableModel;
    private JLabel resultCountLabel, statusLabel;

    // Etsy tab
    private JTable etsyTable;
    private DefaultTableModel etsyTableModel;
    private JTextField etsySearchField;
    private JComboBox<String> etsyTypeFilter, etsyStatusFilter;
    private JLabel etsyCountLabel;
    private List<EtsyListing> currentEtsyListings;

    // Stats
    private JLabel statTotal, statValue, statCarats, statTypes, statOrigins;
    private JLabel statInStock, statUsed, statDamaged, statUnavailable;

    public MainWindow(DatabaseManager db) {
        super("💎 Gemstone Viewer — Collection de pierres précieuses");
        this.db = db;
        ensureStatusColumn();
        ensureEtsyTables();
        initUI();
        loadComboData();
        performGemSearch();
        loadStats();
        if (db.hasEtsyTables()) performEtsySearch();
    }

    private void ensureStatusColumn() {
        if (!db.hasStatusColumn()) {
            int c = JOptionPane.showConfirmDialog(null,
                "La colonne 'status' n'existe pas.\nEffectuer la migration automatiquement ?",
                "Migration requise", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                try { db.runStatusMigration();
                    JOptionPane.showMessageDialog(null, "✅ Migration statut réussie !"); }
                catch (SQLException e) { JOptionPane.showMessageDialog(null, "❌ Erreur : " + e.getMessage()); }
            }
        }
    }

    private void ensureEtsyTables() {
        if (!db.hasEtsyTables()) {
            JOptionPane.showMessageDialog(null,
                "Les tables Etsy ne sont pas encore créées.\n" +
                "Exécutez le script migrate_etsy.sql pour les créer :\n" +
                "  psql -U postgres -d gemstones -f migrate_etsy.sql",
                "Tables Etsy manquantes", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void initUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 840);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        // Main content: sidebar + tabbed results
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildSearchPanel(), buildMainTabs());
        split.setDividerLocation(295);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setContinuousLayout(true);
        root.add(split, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        add(root);
    }

    // ================================================================
    //  HEADER
    // ================================================================
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(20, 20, 36));
        p.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,BORDER_C), new EmptyBorder(12,24,12,24)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        JLabel ico = new JLabel("💎"); ico.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        JPanel tb = new JPanel(); tb.setOpaque(false); tb.setLayout(new BoxLayout(tb, BoxLayout.Y_AXIS));
        JLabel tit = new JLabel("Gemstone Viewer"); tit.setFont(new Font("Segoe UI",Font.BOLD,18)); tit.setForeground(TEXT_PRI);
        JLabel sub = new JLabel("Collection de pierres précieuses & fiches Etsy");
        sub.setFont(new Font("Segoe UI",Font.PLAIN,11)); sub.setForeground(TEXT_SEC);
        tb.add(tit); tb.add(sub);
        left.add(ico); left.add(Box.createHorizontalStrut(12)); left.add(tb);
        p.add(left, BorderLayout.WEST);

        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        statsRow.setOpaque(false);
        statTotal = statLbl(); statValue = statLbl(); statCarats = statLbl();
        statTypes = statLbl(); statOrigins = statLbl();
        statsRow.add(statBox("PIERRES", statTotal, SUCCESS));
        statsRow.add(statBox("VALEUR €", statValue, SUCCESS));
        statsRow.add(statBox("CARATS", statCarats, new Color(251,191,36)));
        statsRow.add(statBox("TYPES", statTypes, ACCENT));
        statsRow.add(statBox("ORIGINES", statOrigins, ACCENT));
        statsRow.add(vSep());
        statInStock = statLbl(); statUsed = statLbl(); statDamaged = statLbl(); statUnavailable = statLbl();
        statsRow.add(statBox("STOCK", statInStock, new Color(52,211,153)));
        statsRow.add(statBox("UTILISÉE", statUsed, new Color(251,191,36)));
        statsRow.add(statBox("ABÎMÉE", statDamaged, new Color(239,68,68)));
        statsRow.add(statBox("INDISPO", statUnavailable, new Color(107,114,128)));
        p.add(statsRow, BorderLayout.EAST);
        return p;
    }

    private JLabel statLbl() { JLabel l = new JLabel("—"); l.setFont(new Font("Segoe UI",Font.BOLD,14)); l.setForeground(SUCCESS); return l; }
    private JPanel statBox(String label, JLabel val, Color color) {
        JPanel b = new JPanel(); b.setOpaque(false); b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
        JLabel lbl = new JLabel(label); lbl.setFont(new Font("Segoe UI",Font.PLAIN,9)); lbl.setForeground(TEXT_SEC); lbl.setAlignmentX(CENTER_ALIGNMENT);
        val.setAlignmentX(CENTER_ALIGNMENT); val.setForeground(color);
        b.add(lbl); b.add(val); return b;
    }
    private JSeparator vSep() { JSeparator s = new JSeparator(JSeparator.VERTICAL); s.setForeground(BORDER_C); s.setPreferredSize(new Dimension(1,40)); return s; }

    // ================================================================
    //  SEARCH SIDEBAR
    // ================================================================
    private JScrollPane buildSearchPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(SIDEBAR_BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new CompoundBorder(new MatteBorder(0,0,0,1,BORDER_C), new EmptyBorder(16,14,16,14)));

        sectionTitle(panel, "🔍 Recherche pierres");

        sectionLbl(panel, "Mot-clé");
        keywordField = textField("Titre, description…");
        keywordField.setAlignmentX(LEFT_ALIGNMENT);
        keywordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        keywordField.addActionListener(e -> performGemSearch());
        panel.add(keywordField); panel.add(Box.createVerticalStrut(10));

        // Etsy ref filter — prominent
        sectionLbl(panel, "🛍️ Réf. fiche Etsy");
        etsyRefFilterField = textField("ex: ETY-2024-001");
        etsyRefFilterField.setAlignmentX(LEFT_ALIGNMENT);
        etsyRefFilterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        etsyRefFilterField.addActionListener(e -> performGemSearch());
        panel.add(etsyRefFilterField); panel.add(Box.createVerticalStrut(10));

        sectionLbl(panel, "Statut");
        statusFilterCombo = new JComboBox<>(new String[]{"— Tous —","En stock","Utilisée","Abîmée","Indisponible"});
        styleCombo(statusFilterCombo);
        statusFilterCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object val, int idx, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list,val,idx,sel,focus);
                setBackground(sel ? TABLE_SEL : CARD_BG);
                setForeground(switch(String.valueOf(val)) {
                    case "En stock" -> new Color(52,211,153); case "Utilisée" -> new Color(251,191,36);
                    case "Abîmée" -> new Color(239,68,68);    case "Indisponible" -> new Color(107,114,128);
                    default -> TEXT_PRI; });
                return this;
            }
        });
        panel.add(statusFilterCombo); panel.add(Box.createVerticalStrut(8));

        sectionLbl(panel, "Type"); gemTypeCombo = addCombo(panel);
        sectionLbl(panel, "Couleur"); colorCombo = addCombo(panel);
        sectionLbl(panel, "Forme"); shapeCombo = addCombo(panel);
        sectionLbl(panel, "Clarté"); clarityCombo = addCombo(panel);
        sectionLbl(panel, "Traitement"); treatmentCombo = addCombo(panel);
        sectionLbl(panel, "Origine"); originCombo = addCombo(panel);
        panel.add(Box.createVerticalStrut(10));

        panel.add(hSep()); panel.add(Box.createVerticalStrut(10));

        sectionLbl(panel, "Carats min / max");
        JPanel cr = new JPanel(new GridLayout(1,2,6,0)); cr.setOpaque(false);
        cr.setAlignmentX(LEFT_ALIGNMENT); cr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        minCaratsField = smallField("Min"); maxCaratsField = smallField("Max");
        cr.add(minCaratsField); cr.add(maxCaratsField); panel.add(cr); panel.add(Box.createVerticalStrut(8));

        sectionLbl(panel, "Prix min / max €");
        JPanel pr = new JPanel(new GridLayout(1,2,6,0)); pr.setOpaque(false);
        pr.setAlignmentX(LEFT_ALIGNMENT); pr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        minPriceField = smallField("Min"); maxPriceField = smallField("Max");
        pr.add(minPriceField); pr.add(maxPriceField); panel.add(pr); panel.add(Box.createVerticalStrut(14));

        JButton searchBtn = actionBtn("🔍  Rechercher", ACCENT2);
        searchBtn.addActionListener(e -> performGemSearch()); panel.add(searchBtn);
        panel.add(Box.createVerticalStrut(6));
        JButton resetBtn = actionBtn("↺  Réinitialiser", CARD_BG);
        resetBtn.setForeground(TEXT_SEC);
        resetBtn.addActionListener(e -> resetGemSearch()); panel.add(resetBtn);
        panel.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(SIDEBAR_BG);
        scroll.setBackground(SIDEBAR_BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    // ================================================================
    //  MAIN TABS: Pierres | Etsy
    // ================================================================
    private JTabbedPane buildMainTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG); tabs.setForeground(TEXT_PRI);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabs.addTab("💎 Pierres précieuses", buildGemResultsPanel());
        if (db.hasEtsyTables()) tabs.addTab("🛍️ Fiches Etsy", buildEtsyPanel());
        return tabs;
    }

    // ================================================================
    //  GEMSTONES RESULTS
    // ================================================================
    private JPanel buildGemResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(14, 16, 8, 16));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        resultCountLabel = new JLabel("Résultats : —");
        resultCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        resultCountLabel.setForeground(TEXT_PRI);
        JLabel hint = new JLabel("Double-clic → fiche  •  Photo cliquable dans la fiche");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11)); hint.setForeground(TEXT_SEC);
        topBar.add(resultCountLabel, BorderLayout.WEST); topBar.add(hint, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        gemTableModel = new GemstoneTableModel();
        gemTable = new JTable(gemTableModel);
        gemTable.setBackground(BG); gemTable.setForeground(TEXT_PRI);
        gemTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gemTable.setRowHeight(34); gemTable.setShowVerticalLines(false);
        gemTable.setGridColor(BORDER_C); gemTable.setSelectionBackground(TABLE_SEL);
        gemTable.setSelectionForeground(Color.WHITE); gemTable.setIntercellSpacing(new Dimension(0,1));
        gemTable.setFocusable(false); gemTable.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer dr = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                setBorder(new EmptyBorder(0,10,0,10));
                if (!sel) { setBackground(row%2==0?BG:TABLE_ALT); setForeground(TEXT_PRI); }
                return this;
            }
        };
        gemTable.setDefaultRenderer(Object.class, dr);
        gemTable.setDefaultRenderer(String.class, dr);
        gemTable.getColumnModel().getColumn(1).setCellRenderer(new GemstoneTableModel.StatusCellRenderer());
        gemTable.getColumnModel().getColumn(10).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                if (v instanceof BigDecimal bd) v = String.format("%.2f €", bd);
                super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                setBorder(new EmptyBorder(0,10,0,10)); setHorizontalAlignment(RIGHT);
                if (!sel) { setBackground(row%2==0?BG:TABLE_ALT); setForeground(SUCCESS); }
                return this;
            }
        });
        gemTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                if (v instanceof BigDecimal bd) v = bd + " ct";
                super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                setBorder(new EmptyBorder(0,10,0,10));
                if (!sel) { setBackground(row%2==0?BG:TABLE_ALT); setForeground(new Color(251,191,36)); }
                return this;
            }
        });

        int[] widths = {40,110,90,280,70,80,90,60,90,90,90,100};
        for (int i=0; i<widths.length; i++) gemTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JTableHeader h = gemTable.getTableHeader();
        h.setBackground(CARD_BG); h.setForeground(TEXT_SEC);
        h.setFont(new Font("Segoe UI",Font.BOLD,11)); h.setBorder(new MatteBorder(0,0,1,0,BORDER_C));
        ((DefaultTableCellRenderer)h.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);

        gemTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = gemTable.convertRowIndexToModel(gemTable.getSelectedRow());
                    if (row >= 0) new GemstoneDetailDialog(MainWindow.this,
                        gemTableModel.getGemstoneAt(row), db,
                        () -> SwingUtilities.invokeLater(() -> performGemSearch())).setVisible(true);
                }
            }
        });
        gemTable.setRowSorter(new TableRowSorter<>(gemTableModel));

        JScrollPane scroll = new JScrollPane(gemTable);
        scroll.setBackground(BG); scroll.getViewport().setBackground(BG);
        scroll.setBorder(new LineBorder(BORDER_C,1));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ================================================================
    //  ETSY PANEL
    // ================================================================
    private JPanel buildEtsyPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(14, 16, 8, 16));

        // Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setOpaque(false); toolbar.setBorder(new EmptyBorder(0, 0, 6, 0));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);

        etsySearchField = new JTextField(18);
        etsySearchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        etsySearchField.setBackground(CARD_BG); etsySearchField.setForeground(TEXT_PRI);
        etsySearchField.setCaretColor(TEXT_PRI);
        etsySearchField.setBorder(new CompoundBorder(new LineBorder(BORDER_C,1), new EmptyBorder(4,8,4,8)));
        etsySearchField.putClientProperty("JTextField.placeholderText", "Réf, titre, description…");
        etsySearchField.setPreferredSize(new Dimension(200, 32));
        etsySearchField.addActionListener(e -> performEtsySearch());

        etsyTypeFilter = new JComboBox<>(buildJewelryTypeItems());
        etsyTypeFilter.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        etsyTypeFilter.setBackground(CARD_BG); etsyTypeFilter.setForeground(TEXT_PRI);
        etsyTypeFilter.setPreferredSize(new Dimension(150, 32));

        etsyStatusFilter = new JComboBox<>(new String[]{"— Tous statuts —","Brouillon","Active","Vendue","Désactivée","Expirée"});
        etsyStatusFilter.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        etsyStatusFilter.setBackground(CARD_BG); etsyStatusFilter.setForeground(TEXT_PRI);
        etsyStatusFilter.setPreferredSize(new Dimension(140, 32));

        JButton searchBtn = new JButton("🔍"); searchBtn.setFont(new Font("Segoe UI",Font.PLAIN,13));
        searchBtn.setBackground(ETSY_COL); searchBtn.setForeground(Color.WHITE);
        searchBtn.setFocusPainted(false); searchBtn.setBorderPainted(false); searchBtn.setOpaque(true);
        searchBtn.setPreferredSize(new Dimension(40,32));
        searchBtn.addActionListener(e -> performEtsySearch());

        filterRow.add(new JLabel("🔍") {{ setForeground(TEXT_SEC); }});
        filterRow.add(etsySearchField);
        filterRow.add(etsyTypeFilter);
        filterRow.add(etsyStatusFilter);
        filterRow.add(searchBtn);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionRow.setOpaque(false);

        JButton newBtn = new JButton("➕ Nouvelle fiche");
        newBtn.setBackground(ETSY_COL); newBtn.setForeground(Color.WHITE);
        newBtn.setFocusPainted(false); newBtn.setBorderPainted(false); newBtn.setOpaque(true);
        newBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        newBtn.setPreferredSize(new Dimension(140, 32));
        newBtn.addActionListener(e -> openNewListing());

        etsyCountLabel = new JLabel("—");
        etsyCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        etsyCountLabel.setForeground(ETSY_COL);
        actionRow.add(etsyCountLabel); actionRow.add(newBtn);

        toolbar.add(filterRow, BorderLayout.WEST);
        toolbar.add(actionRow, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        // Table
        etsyTableModel = new DefaultTableModel(
            new String[]{"Réf.", "Type", "Statut", "Titre", "Métal", "Pierres", "Prix", "Modifié"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        etsyTable = new JTable(etsyTableModel);
        styleEtsyTable();

        etsyTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedListing();
            }
        });

        JScrollPane scroll = new JScrollPane(etsyTable);
        scroll.setBackground(BG); scroll.getViewport().setBackground(BG);
        scroll.setBorder(new LineBorder(BORDER_C,1));
        panel.add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel("Double-clic → ouvrir la fiche Etsy");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11)); hint.setForeground(TEXT_SEC);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private String[] buildJewelryTypeItems() {
        String[] items = new String[EtsyListing.JewelryType.values().length + 1];
        items[0] = "— Tous types —";
        for (int i=0; i<EtsyListing.JewelryType.values().length; i++)
            items[i+1] = EtsyListing.JewelryType.values()[i].toString();
        return items;
    }

    private void styleEtsyTable() {
        etsyTable.setBackground(BG); etsyTable.setForeground(TEXT_PRI);
        etsyTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        etsyTable.setRowHeight(34); etsyTable.setShowVerticalLines(false);
        etsyTable.setGridColor(BORDER_C); etsyTable.setSelectionBackground(TABLE_SEL);
        etsyTable.setSelectionForeground(Color.WHITE); etsyTable.setIntercellSpacing(new Dimension(0,1));
        etsyTable.setFocusable(false);

        DefaultTableCellRenderer dr = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                setBorder(new EmptyBorder(0,10,0,10));
                if (!sel) { setBackground(row%2==0?BG:TABLE_ALT); setForeground(TEXT_PRI); }
                return this;
            }
        };
        etsyTable.setDefaultRenderer(Object.class, dr);

        // Ref column — orange
        etsyTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                setBorder(new EmptyBorder(0,10,0,10));
                setFont(new Font("Segoe UI Mono", Font.BOLD, 12));
                if (!sel) { setBackground(row%2==0?BG:TABLE_ALT); setForeground(ETSY_COL); }
                return this;
            }
        });

        // Status column — colored badge
        etsyTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                setBorder(new EmptyBorder(0,10,0,10));
                if (!sel) {
                    setBackground(row%2==0?BG:TABLE_ALT);
                    if (currentEtsyListings != null && row < currentEtsyListings.size())
                        setForeground(currentEtsyListings.get(row).getStatus().getColor());
                }
                return this;
            }
        });

        // Price — green
        etsyTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                setBorder(new EmptyBorder(0,10,0,10)); setHorizontalAlignment(RIGHT);
                if (!sel) { setBackground(row%2==0?BG:TABLE_ALT); setForeground(SUCCESS); }
                return this;
            }
        });

        JTableHeader h = etsyTable.getTableHeader();
        h.setBackground(CARD_BG); h.setForeground(TEXT_SEC);
        h.setFont(new Font("Segoe UI",Font.BOLD,11)); h.setBorder(new MatteBorder(0,0,1,0,BORDER_C));

        int[] widths = {110, 130, 100, 300, 100, 60, 90, 120};
        for (int i=0; i<widths.length; i++) etsyTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    }

    private void openNewListing() {
        new EtsyListingDialog(this, null, db, () -> SwingUtilities.invokeLater(this::performEtsySearch)).setVisible(true);
    }

    private void openSelectedListing() {
        int row = etsyTable.getSelectedRow();
        if (row < 0 || currentEtsyListings == null || row >= currentEtsyListings.size()) return;
        EtsyListing l = currentEtsyListings.get(row);
        try {
            EtsyListing full = db.getListingById(l.getId());
            if (full != null) new EtsyListingDialog(this, full, db,
                () -> SwingUtilities.invokeLater(this::performEtsySearch)).setVisible(true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ================================================================
    //  STATUS BAR
    // ================================================================
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(15,15,26));
        bar.setBorder(new CompoundBorder(new MatteBorder(1,0,0,0,BORDER_C), new EmptyBorder(6,16,6,16)));
        statusLabel = new JLabel("✓ Connecté à la base de données");
        statusLabel.setFont(new Font("Segoe UI",Font.PLAIN,11)); statusLabel.setForeground(SUCCESS);
        JLabel ver = new JLabel("Gemstone Viewer v1.2 — Java 21 + Swing");
        ver.setFont(new Font("Segoe UI",Font.PLAIN,11)); ver.setForeground(TEXT_SEC);
        bar.add(statusLabel, BorderLayout.WEST); bar.add(ver, BorderLayout.EAST);
        return bar;
    }

    // ================================================================
    //  DATA
    // ================================================================
    private void loadComboData() {
        SwingWorker<Void,Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                fillCombo(gemTypeCombo,"gem_type"); fillCombo(colorCombo,"color");
                fillCombo(shapeCombo,"shape"); fillCombo(clarityCombo,"clarity");
                fillCombo(treatmentCombo,"treatment"); fillCombo(originCombo,"origin");
                return null;
            }
        };
        w.execute();
    }

    private void fillCombo(JComboBox<String> cb, String col) {
        try {
            List<String> vals = db.getDistinctValues(col);
            SwingUtilities.invokeLater(() -> {
                cb.removeAllItems();
                for (String v : vals) cb.addItem(v.isEmpty() ? "— Tous —" : v);
            });
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadStats() {
        new SwingWorker<Map<String,Object>,Void>() {
            @Override protected Map<String,Object> doInBackground() throws Exception { return db.getStatistics(); }
            @Override protected void done() {
                try {
                    Map<String,Object> s = get();
                    NumberFormat nf = NumberFormat.getInstance();
                    statTotal.setText(nf.format(s.get("total")));
                    BigDecimal tv = (BigDecimal)s.get("total_value");
                    statValue.setText(tv!=null?String.format("%.0f",tv):"—");
                    BigDecimal tc = (BigDecimal)s.get("total_carats");
                    statCarats.setText(tc!=null?String.format("%.1f",tc):"—");
                    statTypes.setText(String.valueOf(s.get("gem_types")));
                    statOrigins.setText(String.valueOf(s.get("origins")));
                    @SuppressWarnings("unchecked") Map<String,Integer> sc = (Map<String,Integer>)s.get("statusCounts");
                    if (sc!=null) {
                        statInStock.setText(String.valueOf(sc.getOrDefault("in_stock",0)));
                        statUsed.setText(String.valueOf(sc.getOrDefault("used",0)));
                        statDamaged.setText(String.valueOf(sc.getOrDefault("damaged",0)));
                        statUnavailable.setText(String.valueOf(sc.getOrDefault("unavailable",0)));
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }.execute();
    }

    private void performGemSearch() {
        SearchCriteria c = buildCriteria();
        statusLabel.setText("Recherche…"); statusLabel.setForeground(new Color(251,191,36));
        new SwingWorker<List<Gemstone>,Void>() {
            @Override protected List<Gemstone> doInBackground() throws Exception { return db.searchGemstones(c); }
            @Override protected void done() {
                try {
                    List<Gemstone> r = get();
                    gemTableModel.setData(r);
                    resultCountLabel.setText(r.size() + " résultat" + (r.size()>1?"s":""));
                    statusLabel.setText("✓  " + r.size() + " pierre(s)"); statusLabel.setForeground(SUCCESS);
                    loadStats();
                } catch (Exception e) { statusLabel.setText("❌ " + e.getMessage()); statusLabel.setForeground(new Color(220,80,80)); }
            }
        }.execute();
    }

    private void performEtsySearch() {
        if (!db.hasEtsyTables()) return;
        String kw = etsySearchField.getText().trim();
        String typeSel = (String) etsyTypeFilter.getSelectedItem();
        String stSel   = (String) etsyStatusFilter.getSelectedItem();

        EtsyListing.JewelryType type = null;
        if (typeSel != null && !typeSel.startsWith("—")) {
            for (EtsyListing.JewelryType t : EtsyListing.JewelryType.values())
                if (t.toString().equals(typeSel)) { type = t; break; }
        }
        EtsyListing.ListingStatus status = null;
        if (stSel != null && !stSel.startsWith("—")) {
            status = switch (stSel) {
                case "Brouillon" -> EtsyListing.ListingStatus.DRAFT;
                case "Active"    -> EtsyListing.ListingStatus.ACTIVE;
                case "Vendue"    -> EtsyListing.ListingStatus.SOLD;
                case "Désactivée"-> EtsyListing.ListingStatus.INACTIVE;
                case "Expirée"   -> EtsyListing.ListingStatus.EXPIRED;
                default -> null;
            };
        }

        final EtsyListing.JewelryType fType = type;
        final EtsyListing.ListingStatus fStatus = status;

        new SwingWorker<List<EtsyListing>,Void>() {
            @Override protected List<EtsyListing> doInBackground() throws Exception {
                return db.searchListings(kw.isEmpty()?null:kw, fType, fStatus);
            }
            @Override protected void done() {
                try {
                    currentEtsyListings = get();
                    etsyTableModel.setRowCount(0);
                    java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
                    for (EtsyListing l : currentEtsyListings) {
                        etsyTableModel.addRow(new Object[]{
                            l.getListingRef(),
                            l.getJewelryType().toString(),
                            "● " + l.getStatus().getLabel(),
                            l.getTitle() != null ? l.getTitle() : "",
                            l.getMetalType() != null ? l.getMetalType() : "",
                            l.getGemstoneCount() + " 💎",
                            l.getPrice() != null ? String.format("%.2f €", l.getPrice()) : "—",
                            l.getUpdatedAt() != null ? l.getUpdatedAt().format(dtf) : ""
                        });
                    }
                    etsyCountLabel.setText(currentEtsyListings.size() + " fiche(s)");
                } catch (Exception e) { e.printStackTrace(); }
            }
        }.execute();
    }

    private SearchCriteria buildCriteria() {
        SearchCriteria c = new SearchCriteria();
        c.setKeyword(keywordField.getText().trim());
        c.setEtsyListingRef(etsyRefFilterField.getText().trim().isEmpty() ? null : etsyRefFilterField.getText().trim());
        c.setGemType(sel(gemTypeCombo)); c.setColor(sel(colorCombo));
        c.setShape(sel(shapeCombo)); c.setClarity(sel(clarityCombo));
        c.setTreatment(sel(treatmentCombo)); c.setOrigin(sel(originCombo));
        String st = (String)statusFilterCombo.getSelectedItem();
        if (st != null && !st.startsWith("—"))
            c.setStatus(switch(st) {
                case "En stock" -> Gemstone.Status.IN_STOCK; case "Utilisée" -> Gemstone.Status.USED;
                case "Abîmée"   -> Gemstone.Status.DAMAGED;  default -> Gemstone.Status.UNAVAILABLE;
            });
        c.setMinCarats(bd(minCaratsField.getText())); c.setMaxCarats(bd(maxCaratsField.getText()));
        c.setMinPrice(bd(minPriceField.getText()));   c.setMaxPrice(bd(maxPriceField.getText()));
        return c;
    }

    private void resetGemSearch() {
        keywordField.setText(""); etsyRefFilterField.setText("");
        if (statusFilterCombo.getItemCount()>0) statusFilterCombo.setSelectedIndex(0);
        if (gemTypeCombo.getItemCount()>0) gemTypeCombo.setSelectedIndex(0);
        if (colorCombo.getItemCount()>0) colorCombo.setSelectedIndex(0);
        if (shapeCombo.getItemCount()>0) shapeCombo.setSelectedIndex(0);
        if (clarityCombo.getItemCount()>0) clarityCombo.setSelectedIndex(0);
        if (treatmentCombo.getItemCount()>0) treatmentCombo.setSelectedIndex(0);
        if (originCombo.getItemCount()>0) originCombo.setSelectedIndex(0);
        minCaratsField.setText(""); maxCaratsField.setText("");
        minPriceField.setText(""); maxPriceField.setText("");
        performGemSearch();
    }

    // ================================================================
    //  HELPERS
    // ================================================================
    private void sectionTitle(JPanel p, String t) {
        JLabel l = new JLabel(t); l.setFont(new Font("Segoe UI",Font.BOLD,14));
        l.setForeground(TEXT_PRI); l.setAlignmentX(LEFT_ALIGNMENT);
        p.add(l); p.add(Box.createVerticalStrut(12));
    }
    private void sectionLbl(JPanel p, String t) {
        JLabel l = new JLabel(t); l.setFont(new Font("Segoe UI",Font.BOLD,11));
        l.setForeground(TEXT_SEC); l.setAlignmentX(LEFT_ALIGNMENT);
        p.add(l); p.add(Box.createVerticalStrut(3));
    }
    private JTextField textField(String ph) {
        JTextField tf = new JTextField();
        tf.setFont(new Font("Segoe UI",Font.PLAIN,12));
        tf.setBackground(CARD_BG); tf.setForeground(TEXT_PRI); tf.setCaretColor(TEXT_PRI);
        tf.setBorder(new CompoundBorder(new LineBorder(BORDER_C,1), new EmptyBorder(4,8,4,8)));
        tf.putClientProperty("JTextField.placeholderText", ph);
        return tf;
    }
    private JTextField smallField(String ph) {
        JTextField tf = textField(ph); tf.setPreferredSize(new Dimension(0,30)); return tf;
    }
    private JComboBox<String> addCombo(JPanel p) {
        JComboBox<String> cb = new JComboBox<>(); styleCombo(cb); p.add(cb); p.add(Box.createVerticalStrut(8)); return cb;
    }
    private void styleCombo(JComboBox<?> cb) {
        cb.setFont(new Font("Segoe UI",Font.PLAIN,12));
        cb.setBackground(CARD_BG); cb.setForeground(TEXT_PRI);
        cb.setAlignmentX(LEFT_ALIGNMENT); cb.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));
    }
    private JSeparator hSep() {
        JSeparator s = new JSeparator(); s.setForeground(BORDER_C);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE,1)); s.setAlignmentX(LEFT_ALIGNMENT); return s;
    }
    private JButton actionBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI",Font.BOLD,12)); btn.setBackground(bg);
        btn.setForeground(bg.equals(CARD_BG)?TEXT_SEC:Color.WHITE);
        btn.setFocusPainted(false); btn.setBorderPainted(false); btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(LEFT_ALIGNMENT); btn.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));
        return btn;
    }
    private String sel(JComboBox<String> cb) {
        String v = (String)cb.getSelectedItem(); return (v==null||v.startsWith("—"))?null:v;
    }
    private BigDecimal bd(String s) {
        if (s==null||s.isBlank()) return null;
        try { return new BigDecimal(s.trim().replace(",",".")); } catch (Exception e) { return null; }
    }
}
