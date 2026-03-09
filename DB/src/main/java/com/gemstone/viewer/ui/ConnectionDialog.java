package com.gemstone.viewer.ui;

import com.gemstone.viewer.db.DatabaseManager;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;

public class ConnectionDialog extends JDialog {

    private JTextField hostField;
    private JTextField portField;
    private JTextField dbNameField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton connectBtn;
    private JButton cancelBtn;
    private JLabel statusLabel;

    private DatabaseManager dbManager;
    private boolean connected = false;

    private static final Preferences PREFS = Preferences.userNodeForPackage(ConnectionDialog.class);

    public ConnectionDialog(Frame parent) {
        super(parent, "Connexion à PostgreSQL", true);
        initUI();
        loadPrefs();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 30, 46));
        header.setBorder(new EmptyBorder(24, 28, 24, 28));
        JLabel titleLabel = new JLabel("💎 Gemstone Viewer");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        JLabel subLabel = new JLabel("Connexion à la base de données");
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subLabel.setForeground(new Color(180, 180, 200));
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subLabel);
        header.add(titlePanel, BorderLayout.CENTER);
        mainPanel.add(header, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(24, 28, 16, 28));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 12);
        gbc.anchor = GridBagConstraints.WEST;

        hostField = new JTextField("localhost", 20);
        portField = new JTextField("5432", 6);
        dbNameField = new JTextField("gemstones", 20);
        usernameField = new JTextField("postgres", 20);
        passwordField = new JPasswordField(20);

        styleField(hostField);
        styleField(portField);
        styleField(dbNameField);
        styleField(usernameField);
        styleField(passwordField);

        int row = 0;

        // Host + Port on same line
        addLabel(form, gbc, "Hôte :", row, 0);
        gbc.gridx = 1; gbc.gridy = row; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        form.add(hostField, gbc);
        addLabel(form, gbc, "Port :", row, 2);
        gbc.gridx = 3; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(portField, gbc);
        row++;

        addLabel(form, gbc, "Base de données :", row, 0);
        gbc.gridx = 1; gbc.gridy = row; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1; gbc.gridwidth = 3;
        form.add(dbNameField, gbc);
        gbc.gridwidth = 1;
        row++;

        addLabel(form, gbc, "Utilisateur :", row, 0);
        gbc.gridx = 1; gbc.gridy = row; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1; gbc.gridwidth = 3;
        form.add(usernameField, gbc);
        gbc.gridwidth = 1;
        row++;

        addLabel(form, gbc, "Mot de passe :", row, 0);
        gbc.gridx = 1; gbc.gridy = row; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1; gbc.gridwidth = 3;
        form.add(passwordField, gbc);
        gbc.gridwidth = 1;
        row++;

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(220, 80, 80));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(statusLabel, gbc);

        mainPanel.add(form, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        btnPanel.setBorder(new MatteBorder(1, 0, 0, 0, new Color(60, 60, 80)));

        cancelBtn = new JButton("Annuler");
        cancelBtn.setPreferredSize(new Dimension(100, 36));
        cancelBtn.addActionListener(e -> dispose());

        connectBtn = new JButton("Connexion");
        connectBtn.setPreferredSize(new Dimension(120, 36));
        connectBtn.setBackground(new Color(99, 102, 241));
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setFocusPainted(false);
        connectBtn.addActionListener(e -> tryConnect());

        btnPanel.add(cancelBtn);
        btnPanel.add(connectBtn);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        // Enter key
        getRootPane().setDefaultButton(connectBtn);

        add(mainPanel);
        setMinimumSize(new Dimension(480, 0));
    }

    private void addLabel(JPanel panel, GridBagConstraints gbc, String text, int row, int col) {
        gbc.gridx = col; gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.add(lbl, gbc);
    }

    private void styleField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 34));
    }

    private void tryConnect() {
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String db = dbNameField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;

        connectBtn.setEnabled(false);
        connectBtn.setText("Connexion...");
        statusLabel.setText(" ");

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                dbManager = new DatabaseManager(url, user, pass);
                return dbManager.connect();
            }

            @Override
            protected void done() {
                try {
                    connected = get();
                    if (connected) {
                        savePrefs(host, port, db, user);
                        dispose();
                    } else {
                        statusLabel.setText("❌ Connexion impossible. Vérifiez les paramètres.");
                        connectBtn.setEnabled(true);
                        connectBtn.setText("Connexion");
                    }
                } catch (Exception e) {
                    statusLabel.setText("❌ Erreur : " + e.getMessage());
                    connectBtn.setEnabled(true);
                    connectBtn.setText("Connexion");
                }
            }
        };
        worker.execute();
    }

    private void savePrefs(String host, String port, String db, String user) {
        PREFS.put("host", host);
        PREFS.put("port", port);
        PREFS.put("db", db);
        PREFS.put("user", user);
    }

    private void loadPrefs() {
        hostField.setText(PREFS.get("host", "localhost"));
        portField.setText(PREFS.get("port", "5432"));
        dbNameField.setText(PREFS.get("db", "gemstones"));
        usernameField.setText(PREFS.get("user", "postgres"));
    }

    public DatabaseManager getDbManager() { return dbManager; }
    public boolean isConnected() { return connected; }
}
