package com.gemstone.viewer.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

/**
 * Viewer haute résolution pour les images de gemmes.
 * Supporte le zoom molette, le pan par drag, et le zoom par double-clic.
 */
public class ImageViewerDialog extends JDialog {

    private static final Color BG = new Color(10, 10, 18);
    private static final Color TOOLBAR_BG = new Color(20, 20, 36);
    private static final Color TEXT = new Color(200, 200, 220);
    private static final Color BORDER_C = new Color(50, 50, 70);

    private BufferedImage originalImage;
    private final ImagePanel imagePanel;
    private JLabel infoLabel;
    private JLabel zoomLabel;

    public ImageViewerDialog(Window parent, String localPath, String imageUrl, String title) {
        super(parent, "🔍 Image haute résolution — " + (title != null ? title : ""), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        imagePanel = new ImagePanel();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);

        // Toolbar
        root.add(buildToolbar(), BorderLayout.NORTH);

        // Image panel in scroll
        imagePanel.setBackground(BG);
        JScrollPane scroll = new JScrollPane(imagePanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setBackground(BG);
        scroll.getHorizontalScrollBar().setBackground(TOOLBAR_BG);
        scroll.getVerticalScrollBar().setBackground(TOOLBAR_BG);
        root.add(scroll, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout(16, 0));
        statusBar.setBackground(TOOLBAR_BG);
        statusBar.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_C),
            new EmptyBorder(6, 14, 6, 14)
        ));
        infoLabel = new JLabel("Chargement de l'image...");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        infoLabel.setForeground(TEXT);
        zoomLabel = new JLabel("100%");
        zoomLabel.setFont(new Font("Segoe UI Mono", Font.BOLD, 11));
        zoomLabel.setForeground(new Color(139, 92, 246));
        statusBar.add(infoLabel, BorderLayout.WEST);
        statusBar.add(zoomLabel, BorderLayout.EAST);
        root.add(statusBar, BorderLayout.SOUTH);

        add(root);
        setSize(900, 720);
        setLocationRelativeTo(parent);

        // Load image async
        loadImage(localPath, imageUrl);

        // Keyboard shortcuts
        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().registerKeyboardAction(e -> dispose(), escape, JComponent.WHEN_IN_FOCUSED_WINDOW);
        KeyStroke plus = KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0);
        getRootPane().registerKeyboardAction(e -> { imagePanel.zoomIn(); updateZoomLabel(); }, plus, JComponent.WHEN_IN_FOCUSED_WINDOW);
        KeyStroke minus = KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0);
        getRootPane().registerKeyboardAction(e -> { imagePanel.zoomOut(); updateZoomLabel(); }, minus, JComponent.WHEN_IN_FOCUSED_WINDOW);
        KeyStroke zero = KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0);
        getRootPane().registerKeyboardAction(e -> { imagePanel.resetZoom(); updateZoomLabel(); }, zero, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bar.setBackground(TOOLBAR_BG);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_C));

        bar.add(makeBtn("🔍+", "Zoom avant (+)", e -> { imagePanel.zoomIn(); updateZoomLabel(); }));
        bar.add(makeBtn("🔍-", "Zoom arrière (-)", e -> { imagePanel.zoomOut(); updateZoomLabel(); }));
        bar.add(makeBtn("⬛ 1:1", "Taille réelle (0)", e -> { imagePanel.resetZoom(); updateZoomLabel(); }));
        bar.add(makeBtn("⊡ Fit", "Ajuster à la fenêtre", e -> { imagePanel.fitToWindow(); updateZoomLabel(); }));

        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setForeground(BORDER_C);
        sep.setPreferredSize(new Dimension(1, 24));
        bar.add(sep);

        JLabel hint = new JLabel("Molette: zoom  •  Drag: déplacer  •  Dbl-clic: zoom in");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(new Color(100, 100, 130));
        bar.add(hint);

        return bar;
    }

    private JButton makeBtn(String text, String tooltip, ActionListener al) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBackground(new Color(35, 35, 55));
        btn.setForeground(TEXT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 10, 28));
        btn.addActionListener(al);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(55, 55, 80)); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(new Color(35, 35, 55)); }
        });
        return btn;
    }

    private void loadImage(String localPath, String imageUrl) {
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            String source = "";
            @Override
            protected BufferedImage doInBackground() throws Exception {
                if (localPath != null && !localPath.isBlank()) {
                    File f = new File(localPath);
                    if (f.exists()) {
                        source = "Fichier local: " + f.getName();
                        return ImageIO.read(f);
                    }
                }
                if (imageUrl != null && !imageUrl.isBlank()) {
                    source = "URL: " + imageUrl;
                    return ImageIO.read(new URL(imageUrl));
                }
                return null;
            }
            @Override
            protected void done() {
                try {
                    BufferedImage img = get();
                    if (img != null) {
                        originalImage = img;
                        imagePanel.setImage(img);
                        imagePanel.fitToWindow();
                        infoLabel.setText(source + "  •  " + img.getWidth() + " × " + img.getHeight() + " px");
                        updateZoomLabel();
                    } else {
                        infoLabel.setText("❌ Image non disponible");
                    }
                } catch (Exception e) {
                    infoLabel.setText("❌ Erreur de chargement: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void updateZoomLabel() {
        zoomLabel.setText(String.format("%.0f%%", imagePanel.getZoom() * 100));
    }

    // ========================= Inner ImagePanel =========================

    class ImagePanel extends JPanel {
        private BufferedImage image;
        private double zoom = 1.0;
        private double minZoom = 0.05;
        private double maxZoom = 10.0;
        private int panX = 0, panY = 0;
        private Point dragStart;

        ImagePanel() {
            setOpaque(false);

            // Mouse wheel zoom
            addMouseWheelListener(e -> {
                double factor = e.getWheelRotation() < 0 ? 1.15 : (1.0 / 1.15);
                double oldZoom = zoom;
                zoom = Math.min(maxZoom, Math.max(minZoom, zoom * factor));

                // Zoom toward cursor
                Point mouse = e.getPoint();
                panX = (int) (mouse.x - (mouse.x - panX) * zoom / oldZoom);
                panY = (int) (mouse.y - (mouse.y - panY) * zoom / oldZoom);

                updateZoomLabel();
                repaint();
            });

            // Pan drag
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) dragStart = e.getPoint();
                }
                @Override public void mouseReleased(MouseEvent e) { dragStart = null; }
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        zoom = Math.min(maxZoom, zoom * 2);
                        updateZoomLabel();
                        repaint();
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragStart != null && SwingUtilities.isLeftMouseButton(e)) {
                        panX += e.getX() - dragStart.x;
                        panY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        public void setImage(BufferedImage img) {
            this.image = img;
            zoom = 1.0;
            panX = 0; panY = 0;
            repaint();
        }

        public double getZoom() { return zoom; }

        public void zoomIn() { zoom = Math.min(maxZoom, zoom * 1.25); repaint(); }
        public void zoomOut() { zoom = Math.max(minZoom, zoom / 1.25); repaint(); }
        public void resetZoom() { zoom = 1.0; centerImage(); repaint(); }

        public void fitToWindow() {
            if (image == null) return;
            double zw = (double) getWidth() / image.getWidth();
            double zh = (double) getHeight() / image.getHeight();
            zoom = Math.min(zw, zh) * 0.95;
            centerImage();
            repaint();
        }

        private void centerImage() {
            if (image == null) return;
            panX = (int) ((getWidth() - image.getWidth() * zoom) / 2);
            panY = (int) ((getHeight() - image.getHeight() * zoom) / 2);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(BG);
            g.fillRect(0, 0, getWidth(), getHeight());

            if (image == null) {
                g.setColor(new Color(100, 100, 130));
                g.setFont(new Font("Segoe UI", Font.ITALIC, 16));
                FontMetrics fm = g.getFontMetrics();
                String msg = "Chargement...";
                g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                zoom > 1 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                          : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            AffineTransform at = new AffineTransform();
            at.translate(panX, panY);
            at.scale(zoom, zoom);
            g2.drawRenderedImage(image, at);
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            if (image == null) return new Dimension(600, 500);
            return new Dimension(
                Math.max(getParent() != null ? getParent().getWidth() : 600, (int)(image.getWidth() * zoom) + 40),
                Math.max(getParent() != null ? getParent().getHeight() : 500, (int)(image.getHeight() * zoom) + 40)
            );
        }
    }
}
