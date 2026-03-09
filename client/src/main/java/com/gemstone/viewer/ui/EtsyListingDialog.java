package com.gemstone.viewer.ui;

import com.gemstone.viewer.db.DatabaseManager;
import com.gemstone.viewer.model.EtsyListing;
import com.gemstone.viewer.model.Gemstone;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dialogue affichant la fiche produit Etsy complète + ses pierres liées.
 * Peut être ouvert en mode lecture ou en mode édition/création.
 */
public class EtsyListingDialog extends JDialog {

    private static final Color BG        = new Color(18, 18, 30);
    private static final Color CARD_BG   = new Color(28, 28, 45);
    private static final Color ACCENT    = new Color(245, 140, 50);   // orange Etsy
    private static final Color ACCENT2   = new Color(226, 90, 36);
    private static final Color TEXT_PRI  = new Color(230, 230, 245);
    private static final Color TEXT_SEC  = new Color(150, 150, 175);
    private static final Color BORDER_C  = new Color(50, 50, 70);
    private static final Color SUCCESS   = new Color(52, 211, 153);
    private static final Color TABLE_ALT = new Color(22, 22, 38);
    private static final Color TABLE_SEL = new Color(80, 55, 150);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DatabaseManager db;
    private EtsyListing listing;
    private final boolean createMode;
    private Runnable onSaved;

    // Form fields (edit mode)
    private JTextField refField, etsyIdField, titleField, etsyUrlField;
    private JTextField metalTypeField, metalColorField, ringSizeField;
    private JTextField weightField, priceField, processingField;
    private JComboBox<EtsyListing.JewelryType>    typeCombo;
    private JComboBox<EtsyListing.ListingStatus>  statusCombo;
    private JTextArea descArea, tagsArea, materialsArea;

    // Image
    private JLabel imageLabel;
    private boolean hasImage = false;

    // Gemstones table
    private DefaultTableModel gemsTableModel;
    private JTable gemsTable;

    public EtsyListingDialog(Window parent, EtsyListing listing, DatabaseManager db, Runnable onSaved) {
        super(parent, listing == null ? "Nouvelle fiche Etsy" : "Fiche Etsy — " + listing.getListingRef(),
              ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.listing = listing;
        this.createMode = (listing == null);
        this.onSaved = onSaved;
        if (this.listing == null) this.listing = new EtsyListing();
        initUI();
        setSize(1000, 760);
        setLocationRelativeTo(parent);
        if (!createMode) loadImage();
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        // Left: image + stats  |  Right: details + gemstones
        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(16, 20, 8, 20));
        body.add(buildLeftPanel(), BorderLayout.WEST);

        JTabbedPane tabs = buildTabs();
        body.add(tabs, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        add(root);
    }

    // ── Header ──────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(25, 25, 42));
        p.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_C),
            new EmptyBorder(16, 24, 16, 24)
        ));

        JPanel left = new JPanel(); left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel badge = new JLabel(listing.getJewelryType().getEmoji() + "  " +
            (createMode ? "Nouvelle fiche produit" : listing.getJewelryType().getLabel().toUpperCase()));
        badge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        badge.setForeground(ACCENT); badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        String titleTxt = createMode ? "Nouvelle fiche Etsy" :
            (listing.getTitle() != null ? listing.getTitle() : listing.getListingRef());
        if (titleTxt.length() > 80) titleTxt = titleTxt.substring(0, 80) + "…";
        JLabel titleLbl = new JLabel("<html><body style='width:520px'>" + esc(titleTxt) + "</body></html>");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLbl.setForeground(TEXT_PRI); titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(badge);
        left.add(Box.createVerticalStrut(5));
        left.add(titleLbl);

        // Right: ref + price + status
        JPanel right = new JPanel(); right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        if (!createMode) {
            JLabel refLbl = new JLabel(listing.getListingRef());
            refLbl.setFont(new Font("Segoe UI Mono", Font.BOLD, 14));
            refLbl.setForeground(ACCENT); refLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(refLbl);
            right.add(Box.createVerticalStrut(4));
        }
        if (listing.getPrice() != null) {
            JLabel priceLbl = new JLabel(String.format("%.2f %s", listing.getPrice(),
                listing.getCurrency() != null ? listing.getCurrency() : "EUR"));
            priceLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
            priceLbl.setForeground(SUCCESS); priceLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(priceLbl);
            right.add(Box.createVerticalStrut(4));
        }
        EtsyListing.ListingStatus st = listing.getStatus();
        JLabel statusBadge = new JLabel("● " + st.getLabel());
        statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusBadge.setForeground(st.getColor()); statusBadge.setAlignmentX(Component.RIGHT_ALIGNMENT);
        right.add(statusBadge);

        p.add(left, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ── Left panel: image + mini-stats ──────────────────────────────
    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BG);
        panel.setPreferredSize(new Dimension(240, 0));

        // Image
        JPanel imgCard = new JPanel(new BorderLayout());
        imgCard.setBackground(CARD_BG);
        imgCard.setBorder(new CompoundBorder(
            new LineBorder(BORDER_C, 1, true),
            new EmptyBorder(8, 8, 8, 8)
        ));
        imgCard.setPreferredSize(new Dimension(224, 224));

        imageLabel = new JLabel("Image du bijou", SwingConstants.CENTER);
        imageLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        imageLabel.setForeground(TEXT_SEC);
        imageLabel.setPreferredSize(new Dimension(208, 208));
        imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (hasImage) new ImageViewerDialog(EtsyListingDialog.this,
                    listing.getMainImagePath(), listing.getMainImageUrl(), listing.getTitle()).setVisible(true);
            }
        });
        imgCard.add(imageLabel, BorderLayout.CENTER);

        // Stats mini cards
        JPanel statsPanel = new JPanel(new GridLayout(3, 1, 0, 6));
        statsPanel.setOpaque(false);
        if (!createMode) {
            statsPanel.add(miniStat("🔮", "Pierres", String.valueOf(listing.getGemstoneCount())));
            statsPanel.add(miniStat("👁", "Vues", listing.getViews() != null ? String.valueOf(listing.getViews()) : "—"));
            statsPanel.add(miniStat("❤", "Favoris", listing.getFavorites() != null ? String.valueOf(listing.getFavorites()) : "—"));
        }

        panel.add(imgCard, BorderLayout.NORTH);
        panel.add(statsPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel miniStat(String ico, String label, String val) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(CARD_BG);
        p.setBorder(new CompoundBorder(new LineBorder(BORDER_C,1,true), new EmptyBorder(8,10,8,10)));
        JLabel icoLbl = new JLabel(ico); icoLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        JPanel txt = new JPanel(); txt.setOpaque(false); txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        JLabel lbl = new JLabel(label); lbl.setFont(new Font("Segoe UI",Font.PLAIN,10)); lbl.setForeground(TEXT_SEC);
        JLabel vl  = new JLabel(val);   vl.setFont(new Font("Segoe UI",Font.BOLD,14));  vl.setForeground(TEXT_PRI);
        txt.add(lbl); txt.add(vl);
        p.add(icoLbl, BorderLayout.WEST); p.add(txt, BorderLayout.CENTER);
        return p;
    }

    // ── Tabs: Détails | Pierres ─────────────────────────────────────
    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG); tabs.setForeground(TEXT_PRI);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabs.addTab("📋 Informations", buildDetailsTab());
        tabs.addTab("💎 Pierres liées (" + listing.getGemstoneCount() + ")", buildGemstonesTab());
        return tabs;
    }

    // ── Details tab ─────────────────────────────────────────────────
    private JScrollPane buildDetailsTab() {
        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 0, 16, 0));

        // ---- Identité ----
        JPanel idCard = card("🏷️ Référence & Identité");
        refField   = field(idCard, "Référence *", listing.getListingRef(), false);
        etsyIdField = field(idCard, "ID Etsy", listing.getEtsyListingId(), false);
        titleField = field(idCard, "Titre *", listing.getTitle(), false);

        JPanel typeRow = hrow(idCard, "Type de bijou");
        typeCombo = new JComboBox<>(EtsyListing.JewelryType.values());
        typeCombo.setSelectedItem(listing.getJewelryType());
        typeCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        typeCombo.setBackground(CARD_BG); typeCombo.setForeground(TEXT_PRI);
        typeRow.add(typeCombo, BorderLayout.CENTER);

        JPanel stRow = hrow(idCard, "Statut");
        statusCombo = new JComboBox<>(EtsyListing.ListingStatus.values());
        statusCombo.setSelectedItem(listing.getStatus());
        statusCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusCombo.setBackground(CARD_BG); statusCombo.setForeground(TEXT_PRI);
        statusCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int idx, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list,value,idx,sel,focus);
                if (value instanceof EtsyListing.ListingStatus s) setForeground(sel ? Color.WHITE : s.getColor());
                return this;
            }
        });
        stRow.add(statusCombo, BorderLayout.CENTER);

        etsyUrlField = field(idCard, "URL Etsy", listing.getEtsyUrl(), false);
        panel.add(idCard); panel.add(gap());

        // ---- Prix & Infos commerciales ----
        JPanel priceCard = card("💰 Prix & Infos commerciales");
        priceField      = field(priceCard, "Prix de vente (€)", listing.getPrice() != null ? listing.getPrice().toPlainString() : "", false);
        processingField = field(priceCard, "Délai fabrication (jours)", listing.getProcessingDays() != null ? listing.getProcessingDays().toString() : "3", false);
        panel.add(priceCard); panel.add(gap());

        // ---- Caractéristiques ----
        JPanel specCard = card("⚙️ Caractéristiques");
        metalTypeField  = field(specCard, "Métal", listing.getMetalType(), false);
        metalColorField = field(specCard, "Couleur métal", listing.getMetalColor(), false);
        ringSizeField   = field(specCard, "Taille bague", listing.getRingSize(), false);
        weightField     = field(specCard, "Poids total (g)", listing.getWeightGrams() != null ? listing.getWeightGrams().toPlainString() : "", false);
        panel.add(specCard); panel.add(gap());

        // ---- Description ----
        JPanel descCard = card("📝 Description");
        descArea = new JTextArea(listing.getDescription() != null ? listing.getDescription() : "", 5, 40);
        descArea.setLineWrap(true); descArea.setWrapStyleWord(true);
        descArea.setBackground(new Color(35,35,55)); descArea.setForeground(TEXT_PRI);
        descArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descArea.setBorder(new EmptyBorder(6,8,6,8));
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        descScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        descCard.add(descScroll); panel.add(descCard); panel.add(gap());

        // ---- Tags & Matériaux ----
        JPanel tagsCard = card("🏷️ Tags & Matériaux");
        tagsArea = new JTextArea(listing.getTags() != null ? String.join(", ", listing.getTags()) : "", 2, 40);
        styleTextArea(tagsArea);
        JLabel tagsHint = hint("Tags séparés par des virgules");
        materialsArea = new JTextArea(listing.getMaterials() != null ? String.join(", ", listing.getMaterials()) : "", 2, 40);
        styleTextArea(materialsArea);
        JLabel mHint = hint("Matériaux séparés par des virgules");

        addLabelRow(tagsCard, "Tags Etsy");
        tagsCard.add(new JScrollPane(tagsArea) {{ setMaximumSize(new Dimension(Integer.MAX_VALUE, 60)); setAlignmentX(LEFT_ALIGNMENT); }});
        tagsCard.add(tagsHint);
        tagsCard.add(Box.createVerticalStrut(8));
        addLabelRow(tagsCard, "Matériaux");
        tagsCard.add(new JScrollPane(materialsArea) {{ setMaximumSize(new Dimension(Integer.MAX_VALUE, 60)); setAlignmentX(LEFT_ALIGNMENT); }});
        tagsCard.add(mHint);
        panel.add(tagsCard); panel.add(gap());

        // Dates (read-only)
        if (!createMode) {
            JPanel datesCard = card("📅 Dates");
            addReadField(datesCard, "Créée le", listing.getCreatedAt() != null ? listing.getCreatedAt().format(DTF) : null);
            addReadField(datesCard, "Modifiée le", listing.getUpdatedAt() != null ? listing.getUpdatedAt().format(DTF) : null);
            addReadField(datesCard, "Mise en ligne", listing.getListedAt() != null ? listing.getListedAt().format(DTF) : null);
            addReadField(datesCard, "Vendue le", listing.getSoldAt() != null ? listing.getSoldAt().format(DTF) : null);
            panel.add(datesCard);
        }

        panel.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setBackground(BG);
        return scroll;
    }

    // ── Gemstones tab ────────────────────────────────────────────────
    private JPanel buildGemstonesTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(14, 0, 0, 0));

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);

        JButton addBtn = etsyBtn("➕ Ajouter une pierre", ACCENT2);
        addBtn.addActionListener(e -> addGemstoneToListing());

        JButton removeBtn = etsyBtn("🗑 Retirer", new Color(180, 50, 50));
        removeBtn.addActionListener(e -> removeSelectedGemstone());

        JButton openBtn = etsyBtn("👁 Voir la pierre", new Color(50, 100, 200));
        openBtn.addActionListener(e -> openSelectedGemstone());

        toolbar.add(addBtn); toolbar.add(removeBtn); toolbar.add(openBtn);
        panel.add(toolbar, BorderLayout.NORTH);

        // Table
        gemsTableModel = new DefaultTableModel(
            new String[]{"#", "Type", "Titre", "Carats", "Couleur", "Forme", "Origine", "Statut"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        gemsTable = new JTable(gemsTableModel);
        styleGemsTable();
        refreshGemsTable();

        JScrollPane scroll = new JScrollPane(gemsTable);
        scroll.setBackground(BG); scroll.getViewport().setBackground(BG);
        scroll.setBorder(new LineBorder(BORDER_C, 1));
        panel.add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel("Double-clic → ouvrir la fiche de la pierre");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(TEXT_SEC);
        panel.add(hint, BorderLayout.SOUTH);

        gemsTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedGemstone();
            }
        });
        return panel;
    }

    private void styleGemsTable() {
        gemsTable.setBackground(BG); gemsTable.setForeground(TEXT_PRI);
        gemsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gemsTable.setRowHeight(30); gemsTable.setShowVerticalLines(false);
        gemsTable.setGridColor(BORDER_C); gemsTable.setSelectionBackground(TABLE_SEL);
        gemsTable.setSelectionForeground(Color.WHITE); gemsTable.setFocusable(false);

        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                setBorder(new EmptyBorder(0,8,0,8));
                if (!sel) { setBackground(row%2==0?BG:TABLE_ALT); setForeground(TEXT_PRI); }
                return this;
            }
        };
        gemsTable.setDefaultRenderer(Object.class, r);

        JTableHeader h = gemsTable.getTableHeader();
        h.setBackground(CARD_BG); h.setForeground(TEXT_SEC);
        h.setFont(new Font("Segoe UI", Font.BOLD, 11));
        h.setBorder(new MatteBorder(0,0,1,0,BORDER_C));

        int[] widths = {40, 90, 260, 70, 80, 80, 90, 100};
        for (int i=0; i<widths.length; i++) gemsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    }

    private void refreshGemsTable() {
        gemsTableModel.setRowCount(0);
        for (Gemstone g : listing.getGemstones()) {
            gemsTableModel.addRow(new Object[]{
                g.getId(), nvl(g.getGemType()), nvl(g.getTitle()),
                g.getCarats() != null ? g.getCarats() + " ct" : "",
                nvl(g.getColor()), nvl(g.getShape()), nvl(g.getOrigin()),
                g.getStatus().getLabel()
            });
        }
    }

    private void addGemstoneToListing() {
        if (createMode || listing.getId() <= 0) {
            JOptionPane.showMessageDialog(this, "Enregistrez d'abord la fiche avant d'ajouter des pierres.",
                "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // Get available refs from DB
        String refInput = JOptionPane.showInputDialog(this,
            "Entrez l'ID de la pierre précieuse (numérique) :", "Ajouter une pierre", JOptionPane.PLAIN_MESSAGE);
        if (refInput == null || refInput.isBlank()) return;
        try {
            int gId = Integer.parseInt(refInput.trim());
            Gemstone g = db.getGemstoneById(gId);
            if (g == null) {
                JOptionPane.showMessageDialog(this, "Pierre #" + gId + " introuvable.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String notes = JOptionPane.showInputDialog(this,
                "Note sur le rôle de cette pierre (optionnel) :", "Pierre principale, accentuation...");
            db.linkGemstoneToListing(gId, listing.getId(), notes, listing.getGemstones().size() + 1);
            listing.getGemstones().add(g);
            listing.setGemstoneCount(listing.getGemstoneCount() + 1);
            refreshGemsTable();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "L'ID doit être un nombre entier.", "Erreur", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erreur : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSelectedGemstone() {
        int row = gemsTable.getSelectedRow();
        if (row < 0) return;
        int gId = (int) gemsTableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Retirer la pierre #" + gId + " de cette fiche Etsy ?",
            "Confirmer", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            db.unlinkGemstoneFromListing(gId, listing.getId());
            listing.getGemstones().removeIf(g -> g.getId() == gId);
            listing.setGemstoneCount(listing.getGemstoneCount() - 1);
            refreshGemsTable();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erreur : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSelectedGemstone() {
        int row = gemsTable.getSelectedRow();
        if (row < 0) return;
        int gId = (int) gemsTableModel.getValueAt(row, 0);
        try {
            Gemstone g = db.getGemstoneById(gId);
            if (g != null) new GemstoneDetailDialog(this, g, db, null).setVisible(true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Footer ───────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(16, 0));
        footer.setBackground(new Color(25, 25, 42));
        footer.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_C),
            new EmptyBorder(10, 20, 10, 20)
        ));

        // Left: Etsy link
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        if (!createMode && listing.getEtsyUrl() != null && !listing.getEtsyUrl().isBlank()) {
            JButton etsyLink = new JButton("Ouvrir sur Etsy ↗");
            etsyLink.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            etsyLink.setForeground(ACCENT); etsyLink.setBackground(CARD_BG);
            etsyLink.setBorderPainted(false); etsyLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            etsyLink.addActionListener(e -> {
                try { Desktop.getDesktop().browse(new URI(listing.getEtsyUrl())); }
                catch (Exception ex) { JOptionPane.showMessageDialog(this, "Impossible d'ouvrir l'URL."); }
            });
            left.add(etsyLink);
        }
        footer.add(left, BorderLayout.WEST);

        // Right: action buttons
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        JButton cancelBtn = new JButton("Fermer");
        cancelBtn.setPreferredSize(new Dimension(100, 32));
        cancelBtn.addActionListener(e -> dispose());

        JButton saveBtn = new JButton(createMode ? "Créer la fiche" : "Enregistrer");
        saveBtn.setPreferredSize(new Dimension(140, 32));
        saveBtn.setBackground(ACCENT2); saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false); saveBtn.setBorderPainted(false); saveBtn.setOpaque(true);
        saveBtn.addActionListener(e -> saveListing(saveBtn));

        right.add(cancelBtn); right.add(saveBtn);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    private void saveListing(JButton btn) {
        // Validate
        if (refField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "La référence est obligatoire.", "Validation", JOptionPane.WARNING_MESSAGE);
            refField.requestFocus(); return;
        }
        if (titleField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Le titre est obligatoire.", "Validation", JOptionPane.WARNING_MESSAGE);
            titleField.requestFocus(); return;
        }

        // Populate model
        listing.setListingRef(refField.getText().trim());
        listing.setEtsyListingId(etsyIdField.getText().trim().isEmpty() ? null : etsyIdField.getText().trim());
        listing.setTitle(titleField.getText().trim());
        listing.setDescription(descArea.getText().trim().isEmpty() ? null : descArea.getText().trim());
        listing.setJewelryType((EtsyListing.JewelryType) typeCombo.getSelectedItem());
        listing.setStatus((EtsyListing.ListingStatus) statusCombo.getSelectedItem());
        listing.setEtsyUrl(etsyUrlField.getText().trim().isEmpty() ? null : etsyUrlField.getText().trim());
        listing.setMetalType(metalTypeField.getText().trim().isEmpty() ? null : metalTypeField.getText().trim());
        listing.setMetalColor(metalColorField.getText().trim().isEmpty() ? null : metalColorField.getText().trim());
        listing.setRingSize(ringSizeField.getText().trim().isEmpty() ? null : ringSizeField.getText().trim());
        listing.setPrice(parseBD(priceField.getText()));
        listing.setWeightGrams(parseBD(weightField.getText()));
        listing.setProcessingDays(parseI(processingField.getText()));
        listing.setTags(splitArray(tagsArea.getText()));
        listing.setMaterials(splitArray(materialsArea.getText()));

        btn.setEnabled(false); btn.setText("Sauvegarde…");

        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override protected Integer doInBackground() throws Exception {
                if (createMode) return db.createListing(listing);
                else { db.updateListing(listing); return listing.getId(); }
            }
            @Override protected void done() {
                try {
                    int id = get();
                    if (id > 0) listing.setId(id);
                    btn.setText("✓ Enregistré");
                    btn.setBackground(new Color(34, 197, 94));
                    Timer t = new Timer(1500, ev -> {
                        btn.setEnabled(true);
                        btn.setText(createMode ? "Créer la fiche" : "Enregistrer");
                        btn.setBackground(ACCENT2);
                    });
                    t.setRepeats(false); t.start();
                    if (onSaved != null) onSaved.run();
                } catch (Exception e) {
                    btn.setEnabled(true);
                    btn.setText(createMode ? "Créer la fiche" : "Enregistrer");
                    JOptionPane.showMessageDialog(EtsyListingDialog.this,
                        "Erreur : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ── Image loading ─────────────────────────────────────────────────
    private void loadImage() {
        SwingWorker<ImageIcon, Void> w = new SwingWorker<>() {
            @Override protected ImageIcon doInBackground() throws Exception {
                if (listing.getMainImagePath() != null) {
                    File f = new File(listing.getMainImagePath());
                    if (f.exists()) {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) { hasImage = true; return scale(img, 208, 208); }
                    }
                }
                if (listing.getMainImageUrl() != null && !listing.getMainImageUrl().isBlank()) {
                    try {
                        BufferedImage img = ImageIO.read(new URL(listing.getMainImageUrl()));
                        if (img != null) { hasImage = true; return scale(img, 208, 208); }
                    } catch (Exception ignored) {}
                }
                return null;
            }
            @Override protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) { imageLabel.setIcon(icon); imageLabel.setText(null); }
                    else imageLabel.setText("<html><center>🖼<br><small>Pas d'image</small></center></html>");
                } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private ImageIcon scale(BufferedImage img, int maxW, int maxH) {
        double s = Math.min((double)maxW/img.getWidth(), (double)maxH/img.getHeight());
        return new ImageIcon(img.getScaledInstance((int)(img.getWidth()*s), (int)(img.getHeight()*s), Image.SCALE_SMOOTH));
    }

    // ── UI helpers ────────────────────────────────────────────────────

    private JPanel card(String title) {
        JPanel c = new JPanel();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBackground(CARD_BG);
        c.setBorder(new CompoundBorder(
            new LineBorder(BORDER_C, 1, true),
            new EmptyBorder(12, 14, 12, 14)
        ));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(ACCENT); lbl.setBorder(new EmptyBorder(0,0,8,0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.add(lbl);
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_C); sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        c.add(sep); c.add(Box.createVerticalStrut(8));
        return c;
    }

    private JTextField field(JPanel card, String label, String value, boolean readOnly) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false); row.setBorder(new EmptyBorder(3,0,3,0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel lbl = new JLabel(label + " :"); lbl.setFont(new Font("Segoe UI",Font.PLAIN,11));
        lbl.setForeground(TEXT_SEC); lbl.setPreferredSize(new Dimension(130, 22));
        JTextField tf = new JTextField(value != null ? value : "");
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tf.setBackground(readOnly ? BG : new Color(35,35,55));
        tf.setForeground(readOnly ? TEXT_SEC : TEXT_PRI);
        tf.setCaretColor(TEXT_PRI); tf.setEditable(!readOnly);
        tf.setBorder(readOnly ? BorderFactory.createEmptyBorder(4,4,4,4)
            : new CompoundBorder(new LineBorder(BORDER_C,1), new EmptyBorder(4,6,4,6)));
        row.add(lbl, BorderLayout.WEST); row.add(tf, BorderLayout.CENTER);
        card.add(row);
        return tf;
    }

    private JPanel hrow(JPanel card, String label) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false); row.setBorder(new EmptyBorder(3,0,3,0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel lbl = new JLabel(label + " :"); lbl.setFont(new Font("Segoe UI",Font.PLAIN,11));
        lbl.setForeground(TEXT_SEC); lbl.setPreferredSize(new Dimension(130, 22));
        row.add(lbl, BorderLayout.WEST);
        card.add(row);
        return row;
    }

    private void addReadField(JPanel card, String label, String value) {
        if (value == null || value.isBlank()) return;
        field(card, label, value, true);
    }

    private void addLabelRow(JPanel card, String label) {
        JLabel lbl = new JLabel(label + " :"); lbl.setFont(new Font("Segoe UI",Font.PLAIN,11));
        lbl.setForeground(TEXT_SEC); lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(lbl); card.add(Box.createVerticalStrut(3));
    }

    private void styleTextArea(JTextArea ta) {
        ta.setLineWrap(true); ta.setWrapStyleWord(true);
        ta.setBackground(new Color(35,35,55)); ta.setForeground(TEXT_PRI);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ta.setBorder(new EmptyBorder(4,6,4,6));
    }

    private JLabel hint(String text) {
        JLabel l = new JLabel(text); l.setFont(new Font("Segoe UI",Font.ITALIC,10));
        l.setForeground(TEXT_SEC); l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(2,0,4,0));
        return l;
    }

    private Box gap() {
        Box b = Box.createVerticalBox();
        b.add(Box.createVerticalStrut(10));
        return b;
    }

    private JButton etsyBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBackground(bg); btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false); btn.setBorderPainted(false); btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 16, 28));
        return btn;
    }

    private String nvl(String s) { return s != null ? s : ""; }
    private String esc(String s) { return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }

    private BigDecimal parseBD(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s.trim().replace(",",".")); } catch (Exception e) { return null; }
    }
    private Integer parseI(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
    private String[] splitArray(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split(",");
        List<String> out = new java.util.ArrayList<>();
        for (String p : parts) { String t = p.trim(); if (!t.isEmpty()) out.add(t); }
        return out.isEmpty() ? null : out.toArray(new String[0]);
    }
}
