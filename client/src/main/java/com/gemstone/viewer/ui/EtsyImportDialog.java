package com.gemstone.viewer.ui;

import com.gemstone.viewer.db.DatabaseManager;
import com.gemstone.viewer.etsy.EtsyApiClient;
import com.gemstone.viewer.etsy.EtsyImportService;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.prefs.Preferences;

/**
 * Dialogue d'import des fiches Etsy.
 *
 * Étapes :
 *   1. Saisir la clé API (Keystring)
 *   2. Cliquer "Se connecter à Etsy" → OAuth2 PKCE dans le navigateur
 *   3. Choisir les états à importer
 *   4. Lancer l'import avec barre de progression + log
 */
public class EtsyImportDialog extends JDialog {

    // Colors
    private static final Color BG       = new Color(18, 18, 30);
    private static final Color CARD_BG  = new Color(28, 28, 45);
    private static final Color ETSY     = new Color(245, 140, 50);
    private static final Color ETSY2    = new Color(226, 90, 36);
    private static final Color TEXT_PRI = new Color(230, 230, 245);
    private static final Color TEXT_SEC = new Color(150, 150, 175);
    private static final Color BORDER_C = new Color(50, 50, 70);
    private static final Color SUCCESS  = new Color(52, 211, 153);
    private static final Color WARN     = new Color(251, 191, 36);
    private static final Color ERROR    = new Color(239, 68, 68);

    private final DatabaseManager db;
    private EtsyApiClient apiClient;
    private Runnable onImportDone;

    // Step 1: API Key
    private JTextField apiKeyField;
    private JLabel     authStatusLabel;
    private JButton    connectBtn;
    private JButton    disconnectBtn;

    // Step 2: Shop info
    private JLabel shopLabel;

    // Step 3: Options
    private JCheckBox cbActive, cbInactive, cbDraft, cbExpired, cbSoldOut;

    // Step 4: Progress
    private JTextArea  logArea;
    private JProgressBar progressBar;
    private JLabel     progressLabel;
    private JButton    importBtn;
    private JButton    cancelBtn;
    private volatile boolean importing = false;
    private volatile boolean cancelled = false;

    public EtsyImportDialog(Window parent, DatabaseManager db, Runnable onImportDone) {
        super(parent, "Import Etsy — Synchronisation des fiches produit", ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.onImportDone = onImportDone;
        this.apiClient = new EtsyApiClient();
        initUI();
        setSize(760, 680);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onClose(); }
        });
        updateAuthUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(),   BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        add(root);
    }

    // ── Header ───────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(25, 25, 42));
        p.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,BORDER_C), new EmptyBorder(16,24,16,24)));

        JPanel left = new JPanel(); left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel ico = new JLabel("🛍️  Import Etsy");
        ico.setFont(new Font("Segoe UI Emoji", Font.BOLD, 20)); ico.setForeground(ETSY);
        JLabel sub = new JLabel("Synchronise toutes vos fiches produit depuis votre boutique Etsy");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12)); sub.setForeground(TEXT_SEC);
        left.add(ico); left.add(Box.createVerticalStrut(4)); left.add(sub);
        p.add(left, BorderLayout.WEST);

        // Etsy logo badge
        JLabel badge = new JLabel("via Etsy API v3");
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setForeground(ETSY); badge.setBorder(new CompoundBorder(
            new LineBorder(ETSY, 1, true), new EmptyBorder(4,8,4,8)));
        p.add(badge, BorderLayout.EAST);
        return p;
    }

    // ── Body ─────────────────────────────────────────────────────────
    private JScrollPane buildBody() {
        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20,24,12,24));

        // ── ÉTAPE 1: Clé API ──────────────────────────────────────────
        panel.add(stepCard("① Clé API Etsy (Keystring)",
            buildApiKeyStep()));
        panel.add(Box.createVerticalStrut(14));

        // ── ÉTAPE 2: Connexion OAuth ──────────────────────────────────
        panel.add(stepCard("② Authentification OAuth2",
            buildOAuthStep()));
        panel.add(Box.createVerticalStrut(14));

        // ── ÉTAPE 3: Options d'import ─────────────────────────────────
        panel.add(stepCard("③ Fiches à importer",
            buildOptionsStep()));
        panel.add(Box.createVerticalStrut(14));

        // ── ÉTAPE 4: Log d'import ─────────────────────────────────────
        panel.add(stepCard("④ Progression de l'import",
            buildProgressStep()));

        panel.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setBackground(BG);
        return scroll;
    }

    private JPanel buildApiKeyStep() {
        JPanel p = new JPanel(); p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // ── Guide de configuration ─────────────────────────────────────
        JPanel guideCard = new JPanel();
        guideCard.setLayout(new BoxLayout(guideCard, BoxLayout.Y_AXIS));
        guideCard.setBackground(new Color(20, 30, 20));
        guideCard.setBorder(new CompoundBorder(
            new LineBorder(new Color(52, 130, 80), 1, true),
            new EmptyBorder(10, 12, 10, 12)
        ));
        guideCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        guideCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel guideTitle = new JLabel("📋  Configuration requise sur le portail Etsy");
        guideTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        guideTitle.setForeground(new Color(100, 220, 130));
        guideTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] steps = {
            "1.  Allez sur → https://www.etsy.com/developers/your-apps",
            "2.  Cliquez « Create a new app » (ou éditez votre app existante)",
            "3.  Remplissez : App Name, Description, Website URL (n'importe quelle URL valide)",
            "4.  Dans « Callback URLs » ajoutez EXACTEMENT :",
            "         http://localhost:8765/callback",
            "5.  Cochez le scope :  listings_r  (Read Listings)",
            "6.  Enregistrez — copiez le « Keystring » (PAS le Shared Secret) ci-dessous"
        };

        guideCard.add(guideTitle);
        guideCard.add(Box.createVerticalStrut(8));
        for (String s : steps) {
            JLabel l = new JLabel(s);
            l.setFont(new Font("Consolas", Font.PLAIN, 11));
            l.setForeground(s.contains("http://localhost") || s.contains("listings_r")
                ? new Color(255, 200, 80) : new Color(180, 220, 180));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setBorder(new EmptyBorder(1, 0, 1, 0));
            guideCard.add(l);
        }

        // Bouton ouvrir portail
        JButton openPortalBtn = new JButton("🌐  Ouvrir le portail développeur Etsy");
        openPortalBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        openPortalBtn.setBackground(new Color(40, 80, 50)); openPortalBtn.setForeground(new Color(100, 220, 130));
        openPortalBtn.setBorder(new CompoundBorder(new LineBorder(new Color(52,130,80),1,true), new EmptyBorder(4,10,4,10)));
        openPortalBtn.setFocusPainted(false); openPortalBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        openPortalBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        openPortalBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("https://www.etsy.com/developers/your-apps")); }
            catch (Exception ex) { logLine("Ouvrez manuellement : https://www.etsy.com/developers/your-apps", WARN); }
        });
        guideCard.add(Box.createVerticalStrut(8)); guideCard.add(openPortalBtn);

        // Bouton copier redirect URI
        JPanel uriRow = new JPanel(new BorderLayout(8, 0));
        uriRow.setOpaque(false); uriRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        uriRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        uriRow.setBorder(new EmptyBorder(4, 0, 0, 0));
        JLabel uriLabel = new JLabel("Callback URI (à copier dans Etsy) :");
        uriLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        uriLabel.setForeground(TEXT_SEC);
        JTextField uriField = new JTextField(EtsyApiClient.REDIRECT_URI);
        uriField.setEditable(false);
        uriField.setFont(new Font("Consolas", Font.BOLD, 12));
        uriField.setBackground(new Color(15,15,25)); uriField.setForeground(new Color(255,200,80));
        uriField.setBorder(new CompoundBorder(new LineBorder(BORDER_C,1), new EmptyBorder(3,6,3,6)));
        JButton copyUriBtn = etsyBtn("📋 Copier", new Color(40,60,40));
        copyUriBtn.setForeground(new Color(100,220,130)); copyUriBtn.setPreferredSize(new Dimension(80,28));
        copyUriBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(EtsyApiClient.REDIRECT_URI), null);
            copyUriBtn.setText("✅ Copié"); Timer t = new Timer(2000, ev -> copyUriBtn.setText("📋 Copier")); t.setRepeats(false); t.start();
        });
        uriRow.add(uriLabel, BorderLayout.WEST); uriRow.add(uriField, BorderLayout.CENTER); uriRow.add(copyUriBtn, BorderLayout.EAST);
        guideCard.add(uriRow);

        p.add(guideCard); p.add(Box.createVerticalStrut(14));

        // ── Saisie clé ─────────────────────────────────────────────────
        JLabel keyLabel = new JLabel("Keystring (clé publique de l'app Etsy) :");
        keyLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        keyLabel.setForeground(TEXT_PRI); keyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel keyHint = new JLabel("⚠️  Utilisez bien le « Keystring », PAS le « Shared Secret »");
        keyHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        keyHint.setForeground(WARN); keyHint.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel row = new JPanel(new BorderLayout(8,0));
        row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        apiKeyField = new JTextField(apiClient.hasApiKey() ? apiClient.getApiKey() : "");
        apiKeyField.setFont(new Font("Consolas", Font.PLAIN, 12));
        apiKeyField.setBackground(new Color(35,35,55)); apiKeyField.setForeground(TEXT_PRI);
        apiKeyField.setCaretColor(TEXT_PRI);
        apiKeyField.setBorder(new CompoundBorder(new LineBorder(BORDER_C,1), new EmptyBorder(4,8,4,8)));
        apiKeyField.putClientProperty("JTextField.placeholderText", "Collez ici le Keystring de votre app Etsy…");

        JButton saveKeyBtn = etsyBtn("Sauvegarder", ETSY2);
        saveKeyBtn.setPreferredSize(new Dimension(110, 34));
        saveKeyBtn.addActionListener(e -> {
            String key = apiKeyField.getText().trim();
            if (key.length() < 10) { logLine("⚠️ Clé API trop courte.", WARN); return; }
            apiClient = new EtsyApiClient(key);
            apiClient.savePrefs();
            logLine("✅ Clé API sauvegardée. Passez à l'étape ②.", SUCCESS);
            updateAuthUI();
        });
        row.add(apiKeyField, BorderLayout.CENTER); row.add(saveKeyBtn, BorderLayout.EAST);

        p.add(keyLabel); p.add(Box.createVerticalStrut(4));
        p.add(keyHint);  p.add(Box.createVerticalStrut(6));
        p.add(row);
        return p;
    }

    private JPanel buildOAuthStep() {
        JPanel p = new JPanel(); p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        authStatusLabel = new JLabel("◌  Non connecté");
        authStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        authStatusLabel.setForeground(TEXT_SEC); authStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        shopLabel = new JLabel("");
        shopLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        shopLabel.setForeground(TEXT_SEC); shopLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false); btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        connectBtn = etsyBtn("🔗 Se connecter à Etsy", ETSY2);
        connectBtn.setPreferredSize(new Dimension(200, 34));
        connectBtn.addActionListener(e -> startOAuth());

        disconnectBtn = etsyBtn("Déconnecter", new Color(100,40,40));
        disconnectBtn.setPreferredSize(new Dimension(120, 34));
        disconnectBtn.addActionListener(e -> disconnect());

        btnRow.add(connectBtn); btnRow.add(disconnectBtn);

        JLabel hint = new JLabel("Une page s'ouvrira dans votre navigateur pour autoriser l'accès à votre boutique.");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11)); hint.setForeground(TEXT_SEC);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(authStatusLabel); p.add(Box.createVerticalStrut(6));
        p.add(shopLabel);       p.add(Box.createVerticalStrut(8));
        p.add(btnRow);          p.add(Box.createVerticalStrut(6));
        p.add(hint);
        return p;
    }

    private JPanel buildOptionsStep() {
        JPanel p = new JPanel(); p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel lbl = new JLabel("Sélectionnez les états à importer :");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12)); lbl.setForeground(TEXT_PRI);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        cbActive   = check("🟢  Active      — fiches en vente",          SUCCESS, true);
        cbDraft    = check("🔵  Brouillon   — fiches non publiées",      ETSY,    true);
        cbInactive = check("🟡  Désactivée  — fiches temporairement off",WARN,    true);
        cbExpired  = check("🔴  Expirée     — fiches ayant expiré",      ERROR,   false);
        cbSoldOut  = check("🟣  Épuisée     — quantité = 0",             new Color(150,100,220), false);

        JPanel grid = new JPanel(new GridLayout(5,1,0,4));
        grid.setOpaque(false); grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        grid.add(cbActive); grid.add(cbDraft); grid.add(cbInactive); grid.add(cbExpired); grid.add(cbSoldOut);

        JLabel note = new JLabel("💡 Les associations pierre ↔ fiche existantes sont préservées lors des mises à jour.");
        note.setFont(new Font("Segoe UI", Font.ITALIC, 11)); note.setForeground(TEXT_SEC);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(lbl); p.add(Box.createVerticalStrut(10));
        p.add(grid); p.add(Box.createVerticalStrut(10));
        p.add(note);
        return p;
    }

    private JPanel buildProgressStep() {
        JPanel p = new JPanel(new BorderLayout(0,8)); p.setOpaque(false);

        progressLabel = new JLabel("En attente du lancement…");
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressLabel.setForeground(TEXT_SEC);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(ETSY); progressBar.setBackground(CARD_BG);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        progressBar.setString("Prêt");
        progressBar.setPreferredSize(new Dimension(0, 22));

        logArea = new JTextArea(10, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logArea.setBackground(new Color(10, 10, 20));
        logArea.setForeground(new Color(200, 255, 200));
        logArea.setBorder(new EmptyBorder(6,8,6,8));
        logArea.setLineWrap(true); logArea.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new LineBorder(BORDER_C,1));
        scroll.setBackground(new Color(10,10,20));

        JPanel top = new JPanel(new BorderLayout(0,4)); top.setOpaque(false);
        top.add(progressLabel, BorderLayout.NORTH);
        top.add(progressBar, BorderLayout.CENTER);

        p.add(top,    BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // ── Footer ───────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(25,25,42));
        footer.setBorder(new CompoundBorder(
            new MatteBorder(1,0,0,0,BORDER_C), new EmptyBorder(12,24,12,24)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        importBtn = etsyBtn("⬇  Lancer l'import", ETSY2);
        importBtn.setPreferredSize(new Dimension(180, 36));
        importBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        importBtn.addActionListener(e -> startImport());

        cancelBtn = etsyBtn("Annuler l'import", new Color(100,40,40));
        cancelBtn.setPreferredSize(new Dimension(140, 36));
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> { cancelled = true; cancelBtn.setEnabled(false); });

        left.add(importBtn); left.add(cancelBtn);
        footer.add(left, BorderLayout.WEST);

        JButton closeBtn = new JButton("Fermer");
        closeBtn.setPreferredSize(new Dimension(90, 36));
        closeBtn.addActionListener(e -> onClose());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false); right.add(closeBtn);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    // ── Logic ────────────────────────────────────────────────────────

    private void updateAuthUI() {
        SwingUtilities.invokeLater(() -> {
            boolean hasKey     = apiClient.hasApiKey();
            boolean hasToken   = apiClient.hasToken();
            boolean tokenValid = apiClient.isTokenValid();

            if (!hasKey) {
                authStatusLabel.setText("◌  Clé API non configurée"); authStatusLabel.setForeground(ERROR);
                shopLabel.setText("");
                connectBtn.setEnabled(false); disconnectBtn.setEnabled(false);
                importBtn.setEnabled(false);
            } else if (!hasToken) {
                authStatusLabel.setText("◌  Non connecté — cliquez « Se connecter à Etsy »");
                authStatusLabel.setForeground(WARN);
                shopLabel.setText("");
                connectBtn.setEnabled(true); disconnectBtn.setEnabled(false);
                importBtn.setEnabled(false);
            } else if (tokenValid) {
                authStatusLabel.setText("✅  Connecté"); authStatusLabel.setForeground(SUCCESS);
                String name = apiClient.getShopName();
                String id   = apiClient.getShopId();
                shopLabel.setText(name != null ? "Boutique : " + name + " (ID: " + id + ")" : "");
                shopLabel.setForeground(ETSY);
                connectBtn.setEnabled(false); disconnectBtn.setEnabled(true);
                importBtn.setEnabled(true);
            } else {
                authStatusLabel.setText("⚠️  Token expiré — reconnectez-vous"); authStatusLabel.setForeground(WARN);
                shopLabel.setText("");
                connectBtn.setEnabled(true); disconnectBtn.setEnabled(true);
                importBtn.setEnabled(false);
            }
        });
    }

    private void startOAuth() {
        String keyText = apiKeyField.getText().trim();
        if (keyText.length() < 10) {
            logLine("⚠️ Saisissez d'abord votre clé API Etsy (étape 1).", WARN);
            return;
        }
        apiClient = new EtsyApiClient(keyText);
        connectBtn.setEnabled(false);
        logLine("🔐 Démarrage du flux OAuth2 PKCE…", ETSY);

        new Thread(() -> {
            try {
                String authUrl = apiClient.startOAuthFlow();
                SwingUtilities.invokeLater(() -> {
                    logLine("🌐 Ouverture du navigateur…", TEXT_PRI);
                    try { Desktop.getDesktop().browse(new URI(authUrl)); }
                    catch (Exception ex) {
                        logLine("⚠️ Impossible d'ouvrir le navigateur automatiquement.", WARN);
                        logLine("📋 Copiez cette URL dans votre navigateur :", WARN);
                        logLine("    " + authUrl, TEXT_SEC);
                    }
                    logLine("⏳ Attente de l'autorisation Etsy (120 secondes)…", TEXT_SEC);
                });

                String code = apiClient.waitForAuthCode(120);
                logLine("✅ Code d'autorisation reçu.", SUCCESS);

                logLine("🔄 Échange du code contre un token…", TEXT_PRI);
                apiClient.exchangeCode(code);
                logLine("✅ Authentification réussie !", SUCCESS);

                logLine("📡 Chargement des infos de la boutique…", TEXT_PRI);
                apiClient.loadShopInfo(msg -> logLine("   " + msg, TEXT_SEC));
                apiClient.savePrefs();

                SwingUtilities.invokeLater(this::updateAuthUI);
            } catch (Exception e) {
                logLine("❌ Erreur OAuth : " + e.getMessage(), ERROR);
                SwingUtilities.invokeLater(() -> connectBtn.setEnabled(true));
            }
        }, "etsy-oauth").start();
    }

    private void disconnect() {
        apiClient.clearAuth();
        logLine("🔌 Déconnecté d'Etsy.", WARN);
        updateAuthUI();
    }

    private void startImport() {
        if (!apiClient.isTokenValid() && !apiClient.hasToken()) {
            JOptionPane.showMessageDialog(this, "Connectez-vous d'abord à Etsy.", "Non connecté", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!db.hasEtsyTables()) {
            JOptionPane.showMessageDialog(this, "Exécutez d'abord migrate_etsy.sql.", "Tables manquantes", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> states = new ArrayList<>();
        if (cbActive.isSelected())   states.add("active");
        if (cbInactive.isSelected()) states.add("inactive");
        if (cbDraft.isSelected())    states.add("draft");
        if (cbExpired.isSelected())  states.add("expired");
        if (cbSoldOut.isSelected())  states.add("sold_out");

        if (states.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Sélectionnez au moins un état.", "Sélection vide", JOptionPane.WARNING_MESSAGE);
            return;
        }

        importing = true; cancelled = false;
        importBtn.setEnabled(false); cancelBtn.setEnabled(true);
        progressBar.setIndeterminate(true); progressBar.setString("Import en cours…");
        logArea.setText("");
        logLine("🚀 Démarrage de l'import — états : " + String.join(", ", states), ETSY);

        new Thread(() -> {
            try {
                EtsyImportService service = new EtsyImportService(db);

                int[] batchNum = {0};
                int total = apiClient.fetchAllListings(states,
                    batch -> {
                        if (cancelled) return;
                        batchNum[0]++;
                        logLine("📦 Lot #" + batchNum[0] + " — " + batch.size() + " fiche(s)…", TEXT_PRI);
                        service.importBatch(batch, msg -> logLine(msg, null));
                    },
                    msg -> logLine("📡 " + msg, TEXT_SEC)
                );

                // Résumé
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    if (cancelled) {
                        progressBar.setString("Annulé"); progressBar.setValue(0);
                        logLine("\n⛔ Import annulé.", WARN);
                    } else {
                        progressBar.setString("Terminé"); progressBar.setValue(100);
                        progressBar.setForeground(SUCCESS);
                        logLine("\n══════════════════════════════════", TEXT_SEC);
                        logLine("✅ Import terminé !", SUCCESS);
                        logLine("   Fiches traitées : " + total, TEXT_PRI);
                        logLine("   Nouvelles       : " + service.getInserted(), SUCCESS);
                        logLine("   Mises à jour    : " + service.getUpdated(), ETSY);
                        logLine("   Erreurs         : " + service.getErrors(),  service.getErrors() > 0 ? ERROR : SUCCESS);
                        progressLabel.setText("Import terminé — " + service.getInserted() + " nouvelles, "
                            + service.getUpdated() + " mises à jour, " + service.getErrors() + " erreurs");
                        progressLabel.setForeground(SUCCESS);
                    }
                    importing = false;
                    importBtn.setEnabled(true); cancelBtn.setEnabled(false);
                    if (!cancelled && onImportDone != null) onImportDone.run();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Erreur"); progressBar.setForeground(ERROR);
                    logLine("❌ Erreur d'import : " + e.getMessage(), ERROR);
                    importing = false;
                    importBtn.setEnabled(true); cancelBtn.setEnabled(false);
                });
            }
        }, "etsy-import").start();
    }

    private void onClose() {
        if (importing) {
            int c = JOptionPane.showConfirmDialog(this,
                "Un import est en cours. Voulez-vous l'annuler et fermer ?",
                "Import en cours", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (c != JOptionPane.YES_OPTION) return;
            cancelled = true;
        }
        dispose();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void logLine(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private JPanel stepCard(String title, JPanel content) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
            new LineBorder(BORDER_C, 1, true),
            new EmptyBorder(14, 16, 14, 16)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(ETSY); lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setBorder(new EmptyBorder(0,0,10,0));

        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_C); sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(lbl); card.add(sep); card.add(Box.createVerticalStrut(10)); card.add(content);
        return card;
    }

    private JCheckBox check(String text, Color color, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        cb.setForeground(color); cb.setBackground(CARD_BG);
        cb.setFocusPainted(false); cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        return cb;
    }

    private JButton etsyBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg); btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false); btn.setBorderPainted(false); btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
