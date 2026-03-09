package com.gemstone.viewer.ui;

import com.gemstone.viewer.db.DatabaseManager;
import com.gemstone.viewer.model.EtsyListing;
import com.gemstone.viewer.model.Gemstone;

import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.time.format.DateTimeFormatter;

public class GemstoneDetailDialog extends JDialog {

    private static final Color BG = new Color(18, 18, 30);
    private static final Color CARD_BG = new Color(28, 28, 45);
    private static final Color ACCENT = new Color(139, 92, 246);
    private static final Color ACCENT2 = new Color(99, 102, 241);
    private static final Color TEXT_PRIMARY = new Color(230, 230, 245);
    private static final Color TEXT_SECONDARY = new Color(150, 150, 175);
    private static final Color BORDER = new Color(50, 50, 70);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Gemstone gem;
    private final DatabaseManager db;
    private Runnable onStatusChanged; // callback to refresh table

    private JLabel imageLabel;
    private JLabel statusDotLabel;
    private JLabel statusTextLabel;
    private JComboBox<Gemstone.Status> statusCombo;
    private JButton saveStatusBtn;

    // Track if high-res image is available
    private boolean hasLocalImage = false;
    private boolean hasUrlImage = false;

    public GemstoneDetailDialog(Window parent, Gemstone gem, DatabaseManager db, Runnable onStatusChanged) {
        super(parent, "Fiche Gemme — " + (gem.getTitle() != null ? gem.getTitle() : ""), ModalityType.APPLICATION_MODAL);
        this.gem = gem;
        this.db = db;
        this.onStatusChanged = onStatusChanged;
        initUI();
        setSize(860, 700);
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(16, 20, 8, 20));
        body.add(buildImagePanel(), BorderLayout.WEST);

        JPanel detailsPanel = buildDetailsPanel();
        JScrollPane scroll = new JScrollPane(detailsPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setBackground(BG);
        body.add(scroll, BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        add(root);
        loadImage();
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(25, 25, 42));
        panel.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(18, 24, 18, 24)
        ));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel gemTypeLabel = new JLabel(
            (gem.getGemType() != null ? "💎 " + gem.getGemType() : "💎 Pierre précieuse").toUpperCase());
        gemTypeLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gemTypeLabel.setForeground(ACCENT);
        gemTypeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String titleText = gem.getTitle() != null ? gem.getTitle() : "Sans titre";
        if (titleText.length() > 80) titleText = titleText.substring(0, 80) + "...";
        JLabel titleLabel = new JLabel("<html><body style='width:480px'>" + escapeHtml(titleText) + "</body></html>");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(gemTypeLabel);
        left.add(Box.createVerticalStrut(6));
        left.add(titleLabel);

        // Right: price + status badge
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setAlignmentY(Component.TOP_ALIGNMENT);

        if (gem.getPrice() != null) {
            JLabel priceLabel = new JLabel(String.format("%.2f €", gem.getPrice()));
            priceLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
            priceLabel.setForeground(new Color(52, 211, 153));
            priceLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(priceLabel);
            right.add(Box.createVerticalStrut(6));
        }

        // Status badge
        JPanel statusBadge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        statusBadge.setOpaque(false);
        statusDotLabel = new JLabel("●");
        statusDotLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        statusTextLabel = new JLabel(gem.getStatus().getLabel());
        statusTextLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        updateStatusColors(gem.getStatus());
        statusBadge.add(statusDotLabel);
        statusBadge.add(statusTextLabel);
        right.add(statusBadge);

        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildImagePanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.setBorder(new EmptyBorder(0, 0, 0, 16));

        JPanel imageCard = new JPanel(new BorderLayout());
        imageCard.setBackground(CARD_BG);
        imageCard.setBorder(new CompoundBorder(
            new LineBorder(BORDER, 1, true),
            new EmptyBorder(8, 8, 8, 8)
        ));
        imageCard.setPreferredSize(new Dimension(280, 280));

        imageLabel = new JLabel("Chargement...", SwingConstants.CENTER);
        imageLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        imageLabel.setForeground(TEXT_SECONDARY);
        imageLabel.setPreferredSize(new Dimension(264, 264));
        imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openHighResViewer();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                imageCard.setBorder(new CompoundBorder(
                    new LineBorder(ACCENT, 2, true),
                    new EmptyBorder(7, 7, 7, 7)
                ));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                imageCard.setBorder(new CompoundBorder(
                    new LineBorder(BORDER, 1, true),
                    new EmptyBorder(8, 8, 8, 8)
                ));
            }
        });

        imageCard.add(imageLabel, BorderLayout.CENTER);

        // "Click to enlarge" hint
        JLabel hint = new JLabel("🔍 Cliquer pour agrandir", SwingConstants.CENTER);
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        hint.setForeground(TEXT_SECONDARY);
        hint.setBorder(new EmptyBorder(6, 0, 0, 0));

        wrapper.add(imageCard, BorderLayout.NORTH);
        wrapper.add(hint, BorderLayout.CENTER);
        return wrapper;
    }

    private void openHighResViewer() {
        if (!hasLocalImage && !hasUrlImage) {
            JOptionPane.showMessageDialog(this, "Aucune image disponible.", "Image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ImageViewerDialog viewer = new ImageViewerDialog(
            this,
            hasLocalImage ? gem.getLocalImagePath() : null,
            hasUrlImage ? gem.getImageUrl() : null,
            gem.getTitle()
        );
        viewer.setVisible(true);
    }

    private JPanel buildDetailsPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // === Statut ===
        JPanel statusCard = buildCard("🏷️ Statut de la pierre");
        JPanel statusRow = new JPanel(new BorderLayout(10, 0));
        statusRow.setOpaque(false);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        statusCombo = new JComboBox<>(Gemstone.Status.values());
        statusCombo.setSelectedItem(gem.getStatus());
        statusCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusCombo.setBackground(CARD_BG);
        statusCombo.setForeground(TEXT_PRIMARY);
        statusCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Gemstone.Status s) {
                    setText("● " + s.getLabel());
                    setForeground(isSelected ? Color.WHITE : s.getColor());
                }
                return this;
            }
        });

        saveStatusBtn = new JButton("Enregistrer");
        saveStatusBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        saveStatusBtn.setBackground(ACCENT2);
        saveStatusBtn.setForeground(Color.WHITE);
        saveStatusBtn.setFocusPainted(false);
        saveStatusBtn.setBorderPainted(false);
        saveStatusBtn.setOpaque(true);
        saveStatusBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveStatusBtn.setPreferredSize(new Dimension(120, 32));
        saveStatusBtn.addActionListener(e -> saveStatus());

        statusRow.add(statusCombo, BorderLayout.CENTER);
        statusRow.add(saveStatusBtn, BorderLayout.EAST);
        statusCard.add(statusRow);
        panel.add(statusCard);
        panel.add(Box.createVerticalStrut(12));

        // === Caractéristiques physiques ===
        JPanel physCard = buildCard("✨ Caractéristiques physiques");
        addField(physCard, "Poids", gem.getCarats() != null ? gem.getCarats() + " ct" : null);
        addField(physCard, "Dimensions", gem.getDimensions());
        addField(physCard, "Forme", gem.getShape());
        addField(physCard, "Couleur", gem.getColor());
        addField(physCard, "Clarté", gem.getClarity());
        addField(physCard, "Traitement", gem.getTreatment());
        addField(physCard, "Origine", gem.getOrigin());
        panel.add(physCard);
        panel.add(Box.createVerticalStrut(12));

        // === Informations commerciales ===
        JPanel comCard = buildCard("🛒 Informations commerciales");
        addField(comCard, "Prix", gem.getPrice() != null ? String.format("%.2f €", gem.getPrice()) : null);
        addField(comCard, "Quantité", gem.getQuantity() != null ? gem.getQuantity().toString() : null);
        addField(comCard, "ID Article", gem.getArticleId());
        addField(comCard, "N° Commande", gem.getOrderId());
        addField(comCard, "N° Transaction", gem.getTransactionId());
        if (gem.getPurchaseDate() != null) addField(comCard, "Date d'achat", gem.getPurchaseDate().format(DTF));
        panel.add(comCard);
        panel.add(Box.createVerticalStrut(12));

        // === Description ===
        if (gem.getDescription() != null && !gem.getDescription().isBlank()) {
            JPanel descCard = buildCard("📝 Description");
            JTextArea ta = new JTextArea(gem.getDescription());
            ta.setEditable(false);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setBackground(CARD_BG);
            ta.setForeground(TEXT_SECONDARY);
            ta.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            ta.setBorder(new EmptyBorder(4, 0, 4, 0));
            descCard.add(ta);
            panel.add(descCard);
            panel.add(Box.createVerticalStrut(12));
        }

        // === Liens ===
        if (gem.getListingUrl() != null && !gem.getListingUrl().isBlank()) {
            JPanel linksCard = buildCard("🔗 Liens");
            JButton urlBtn = new JButton("Ouvrir l'annonce eBay ↗");
            urlBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            urlBtn.setForeground(ACCENT2);
            urlBtn.setBackground(CARD_BG);
            urlBtn.setBorderPainted(false);
            urlBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            urlBtn.addActionListener(e -> {
                try { Desktop.getDesktop().browse(new URI(gem.getListingUrl())); }
                catch (Exception ex) { JOptionPane.showMessageDialog(this, "Impossible d'ouvrir l'URL."); }
            });
            linksCard.add(urlBtn);
            panel.add(linksCard);
            panel.add(Box.createVerticalStrut(12));
        }

        // === Fiches Etsy liées ===
        if (db != null && db.hasEtsyTables()) {
            JPanel etsyCard = buildCard("🛍️ Fiches Etsy associées");
            etsyCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            refreshEtsySection(etsyCard);
            panel.add(etsyCard);
            panel.add(Box.createVerticalStrut(12));
        }

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void refreshEtsySection(JPanel etsyCard) {
        // Remove children after the separator
        // Rebuild from scratch each call
        // Keep the title label and separator (first 3 children added by buildCard)
        while (etsyCard.getComponentCount() > 3) etsyCard.remove(etsyCard.getComponentCount() - 1);

        SwingWorker<List<EtsyListing>, Void> worker = new SwingWorker<>() {
            @Override protected List<EtsyListing> doInBackground() throws Exception {
                return db.getListingsForGemstone(gem.getId());
            }
            @Override protected void done() {
                try {
                    List<EtsyListing> listings = get();

                    if (listings.isEmpty()) {
                        JLabel empty = new JLabel("Aucune fiche Etsy associée");
                        empty.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                        empty.setForeground(TEXT_SECONDARY);
                        empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                        empty.setBorder(new EmptyBorder(0, 0, 6, 0));
                        etsyCard.add(empty);
                    } else {
                        for (EtsyListing l : listings) {
                            JPanel row = new JPanel(new BorderLayout(8, 0));
                            row.setOpaque(false);
                            row.setAlignmentX(Component.LEFT_ALIGNMENT);
                            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                            row.setBorder(new EmptyBorder(2, 0, 2, 0));

                            // Ref badge
                            JLabel refBadge = new JLabel(l.getJewelryType().getEmoji() + " " + l.getListingRef());
                            refBadge.setFont(new Font("Segoe UI Mono", Font.BOLD, 12));
                            refBadge.setForeground(new Color(245, 140, 50));
                            refBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            refBadge.setPreferredSize(new Dimension(140, 22));
                            refBadge.setToolTipText("Cliquer pour ouvrir la fiche");
                            refBadge.addMouseListener(new MouseAdapter() {
                                @Override public void mouseClicked(MouseEvent e) {
                                    openEtsyListing(l.getId());
                                }
                            });

                            // Title truncated
                            String t = l.getTitle() != null ? l.getTitle() : "";
                            if (t.length() > 40) t = t.substring(0, 40) + "…";
                            JLabel titleLbl = new JLabel(t);
                            titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                            titleLbl.setForeground(TEXT_SECONDARY);

                            // Status
                            JLabel stLbl = new JLabel("● " + l.getStatus().getLabel());
                            stLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
                            stLbl.setForeground(l.getStatus().getColor());
                            stLbl.setPreferredSize(new Dimension(90, 18));

                            row.add(refBadge, BorderLayout.WEST);
                            row.add(titleLbl, BorderLayout.CENTER);
                            row.add(stLbl, BorderLayout.EAST);
                            etsyCard.add(row);
                        }
                    }

                    // "Link" button
                    etsyCard.add(Box.createVerticalStrut(8));
                    JButton linkBtn = new JButton("➕ Associer à une fiche Etsy");
                    linkBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    linkBtn.setBackground(CARD_BG);
                    linkBtn.setForeground(new Color(245, 140, 50));
                    linkBtn.setBorder(new LineBorder(new Color(245, 140, 50), 1, true));
                    linkBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    linkBtn.setFocusPainted(false);
                    linkBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                    linkBtn.addActionListener(e -> linkToEtsyListing(etsyCard));
                    etsyCard.add(linkBtn);

                    etsyCard.revalidate(); etsyCard.repaint();
                } catch (Exception e) { e.printStackTrace(); }
            }
        };
        worker.execute();
    }

    private void openEtsyListing(int listingId) {
        try {
            EtsyListing l = db.getListingById(listingId);
            if (l != null) new EtsyListingDialog(this, l, db, null).setVisible(true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void linkToEtsyListing(JPanel etsyCard) {
        // Build list of available refs
        List<String> refs;
        try { refs = db.getAllListingRefs(); }
        catch (Exception e) { refs = new java.util.ArrayList<>(); }

        if (refs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Aucune fiche Etsy disponible. Créez d'abord une fiche dans l'onglet Etsy.",
                "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JComboBox<String> refCombo = new JComboBox<>(refs.toArray(new String[0]));
        refCombo.setEditable(true);
        refCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JTextField notesField = new JTextField();
        notesField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel form = new JPanel(new GridLayout(4, 1, 4, 4));
        form.add(new JLabel("Référence de la fiche Etsy :"));
        form.add(refCombo);
        form.add(new JLabel("Note sur le rôle de cette pierre (optionnel) :"));
        form.add(notesField);

        int r = JOptionPane.showConfirmDialog(this, form, "Associer à une fiche Etsy",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        String selectedRef = (String) refCombo.getSelectedItem();
        if (selectedRef == null || selectedRef.isBlank()) return;

        try {
            EtsyListing target = db.getListingByRef(selectedRef);
            if (target == null) {
                JOptionPane.showMessageDialog(this, "Référence '" + selectedRef + "' introuvable.",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }
            db.linkGemstoneToListing(gem.getId(), target.getId(), notesField.getText().trim(), 1);
            refreshEtsySection(etsyCard);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erreur : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveStatus() {
        Gemstone.Status newStatus = (Gemstone.Status) statusCombo.getSelectedItem();
        if (newStatus == null) return;

        saveStatusBtn.setEnabled(false);
        saveStatusBtn.setText("Sauvegarde...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                db.updateStatus(gem.getId(), newStatus);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    gem.setStatus(newStatus);
                    updateStatusColors(newStatus);
                    saveStatusBtn.setText("✓ Enregistré");
                    saveStatusBtn.setBackground(new Color(34, 197, 94));
                    // Refresh after a moment
                    Timer t = new Timer(1500, ev -> {
                        saveStatusBtn.setEnabled(true);
                        saveStatusBtn.setText("Enregistrer");
                        saveStatusBtn.setBackground(ACCENT2);
                    });
                    t.setRepeats(false);
                    t.start();
                    // Callback to refresh the main table
                    if (onStatusChanged != null) onStatusChanged.run();
                } catch (Exception e) {
                    saveStatusBtn.setEnabled(true);
                    saveStatusBtn.setText("Enregistrer");
                    JOptionPane.showMessageDialog(GemstoneDetailDialog.this,
                        "Erreur lors de la sauvegarde:\n" + e.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void updateStatusColors(Gemstone.Status status) {
        statusDotLabel.setForeground(status.getColor());
        statusTextLabel.setForeground(status.getColor());
        statusTextLabel.setText(status.getLabel());
    }

    private JPanel buildCard(String title) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
            new LineBorder(BORDER, 1, true),
            new EmptyBorder(14, 16, 14, 16)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(ACCENT);
        lbl.setBorder(new EmptyBorder(0, 0, 10, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(lbl);

        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        card.add(sep);
        card.add(Box.createVerticalStrut(8));
        return card;
    }

    private void addField(JPanel card, String label, String value) {
        if (value == null || value.isBlank()) return;
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel lbl = new JLabel(label + " :");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(TEXT_SECONDARY);
        lbl.setPreferredSize(new Dimension(110, 22));
        JLabel val = new JLabel(escapeHtml(value));
        val.setFont(new Font("Segoe UI", Font.BOLD, 12));
        val.setForeground(TEXT_PRIMARY);
        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        card.add(row);
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 10));
        footer.setBackground(new Color(25, 25, 42));
        footer.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));
        if (gem.getCreatedAt() != null) {
            JLabel info = new JLabel("Ajouté le " + gem.getCreatedAt().format(DTF));
            info.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            info.setForeground(TEXT_SECONDARY);
            footer.add(info);
        }
        JButton closeBtn = new JButton("Fermer");
        closeBtn.setPreferredSize(new Dimension(100, 32));
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        return footer;
    }

    private void loadImage() {
        SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
            BufferedImage loaded = null;
            @Override
            protected ImageIcon doInBackground() throws Exception {
                if (gem.getLocalImagePath() != null && !gem.getLocalImagePath().isBlank()) {
                    File f = new File(gem.getLocalImagePath());
                    if (f.exists()) {
                        loaded = ImageIO.read(f);
                        if (loaded != null) { hasLocalImage = true; return scaleImage(loaded); }
                    }
                }
                if (gem.getImageUrl() != null && !gem.getImageUrl().isBlank()) {
                    try {
                        loaded = ImageIO.read(new URL(gem.getImageUrl()));
                        if (loaded != null) { hasUrlImage = true; return scaleImage(loaded); }
                    } catch (Exception ignored) {}
                }
                return null;
            }
            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        imageLabel.setIcon(icon);
                        imageLabel.setText(null);
                        imageLabel.setToolTipText("Cliquer pour ouvrir en haute résolution");
                    } else {
                        imageLabel.setText("<html><center>🖼<br><small>Image non disponible</small></center></html>");
                    }
                } catch (Exception ignored) { imageLabel.setText("❌ Erreur"); }
            }
        };
        worker.execute();
    }

    private ImageIcon scaleImage(BufferedImage original) {
        int maxW = 264, maxH = 264;
        int w = original.getWidth(), h = original.getHeight();
        double scale = Math.min((double) maxW / w, (double) maxH / h);
        int nw = (int)(w * scale), nh = (int)(h * scale);
        Image scaled = original.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
