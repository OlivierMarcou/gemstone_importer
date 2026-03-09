package com.gemstone.viewer.ui;

import com.gemstone.viewer.model.Gemstone;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GemstoneTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "#", "Statut", "Type", "Titre", "Carats", "Forme", "Couleur", "Clarté",
        "Traitement", "Origine", "Prix (€)", "Dimensions"
    };

    private List<Gemstone> data = new ArrayList<>();

    public void setData(List<Gemstone> data) {
        this.data = data != null ? data : new ArrayList<>();
        fireTableDataChanged();
    }

    public Gemstone getGemstoneAt(int row) { return data.get(row); }

    @Override public int getRowCount() { return data.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override
    public Class<?> getColumnClass(int col) {
        return switch (col) {
            case 0 -> Integer.class;
            case 4, 10 -> BigDecimal.class;
            case 1 -> Gemstone.Status.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int col) {
        Gemstone g = data.get(row);
        return switch (col) {
            case 0 -> g.getId();
            case 1 -> g.getStatus();
            case 2 -> nvl(g.getGemType());
            case 3 -> nvl(g.getTitle());
            case 4 -> g.getCarats();
            case 5 -> nvl(g.getShape());
            case 6 -> nvl(g.getColor());
            case 7 -> nvl(g.getClarity());
            case 8 -> nvl(g.getTreatment());
            case 9 -> nvl(g.getOrigin());
            case 10 -> g.getPrice();
            case 11 -> nvl(g.getDimensions());
            default -> "";
        };
    }

    private String nvl(String s) { return s != null ? s : ""; }

    /** Renderer for the Status badge cell */
    public static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final Color BG_DARK = new Color(18, 18, 30);
        private static final Color BG_ALT = new Color(22, 22, 38);
        private static final Color SEL = new Color(80, 55, 150);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
            panel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

            if (!isSelected) {
                panel.setBackground(row % 2 == 0 ? BG_DARK : BG_ALT);
            } else {
                panel.setBackground(SEL);
            }

            if (value instanceof Gemstone.Status status) {
                // Colored dot
                JLabel dot = new JLabel("●");
                dot.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                dot.setForeground(status.getColor());

                JLabel lbl = new JLabel(status.getLabel());
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
                lbl.setForeground(status.getColor());

                panel.add(dot);
                panel.add(lbl);
            }
            return panel;
        }
    }
}
