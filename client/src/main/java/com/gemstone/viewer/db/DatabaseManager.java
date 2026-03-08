package com.gemstone.viewer.db;

import com.gemstone.viewer.model.Gemstone;
import com.gemstone.viewer.model.SearchCriteria;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    private static final String SELECT_COLS =
        "SELECT id, article_id, title, description, price, quantity, purchase_date, " +
        "order_id, transaction_id, listing_url, image_url, local_image_path, " +
        "gem_type, carats, dimensions, shape, color, clarity, treatment, origin, " +
        "status, created_at, updated_at FROM gemstones";

    public DatabaseManager(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public boolean connect() {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(url, username, password);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public boolean isConnected() {
        try { return connection != null && !connection.isClosed() && connection.isValid(2); }
        catch (SQLException e) { return false; }
    }

    /** Ensure the status column exists (graceful if it doesn't yet) */
    public boolean hasStatusColumn() {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, "gemstones", "status")) {
            return rs.next();
        } catch (SQLException e) { return false; }
    }

    /** Run the migration SQL to add the status column */
    public void runStatusMigration() throws SQLException {
        String[] statements = {
            // Create type if not exists
            "DO $$ BEGIN CREATE TYPE gem_status AS ENUM ('in_stock','used','damaged','unavailable'); " +
            "EXCEPTION WHEN duplicate_object THEN null; END $$",
            // Add column
            "ALTER TABLE gemstones ADD COLUMN IF NOT EXISTS status gem_status",
            // Initialize values
            "UPDATE gemstones SET status = CASE WHEN price IS NULL OR price = 0 " +
            "THEN 'unavailable'::gem_status ELSE 'in_stock'::gem_status END WHERE status IS NULL",
            // Set default + not null
            "ALTER TABLE gemstones ALTER COLUMN status SET DEFAULT 'in_stock'",
            "ALTER TABLE gemstones ALTER COLUMN status SET NOT NULL"
        };
        for (String sql : statements) {
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
            }
        }
    }

    public List<Gemstone> searchGemstones(SearchCriteria criteria) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_COLS + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(description) LIKE ?)");
            String kw = "%" + criteria.getKeyword().toLowerCase() + "%";
            params.add(kw); params.add(kw);
        }
        if (criteria.getGemType() != null && !criteria.getGemType().isBlank()) {
            sql.append(" AND LOWER(gem_type) = LOWER(?)");
            params.add(criteria.getGemType());
        }
        if (criteria.getColor() != null && !criteria.getColor().isBlank()) {
            sql.append(" AND LOWER(color) = LOWER(?)");
            params.add(criteria.getColor());
        }
        if (criteria.getShape() != null && !criteria.getShape().isBlank()) {
            sql.append(" AND LOWER(shape) = LOWER(?)");
            params.add(criteria.getShape());
        }
        if (criteria.getClarity() != null && !criteria.getClarity().isBlank()) {
            sql.append(" AND LOWER(clarity) = LOWER(?)");
            params.add(criteria.getClarity());
        }
        if (criteria.getTreatment() != null && !criteria.getTreatment().isBlank()) {
            sql.append(" AND LOWER(treatment) = LOWER(?)");
            params.add(criteria.getTreatment());
        }
        if (criteria.getOrigin() != null && !criteria.getOrigin().isBlank()) {
            sql.append(" AND LOWER(origin) = LOWER(?)");
            params.add(criteria.getOrigin());
        }
        if (criteria.getStatus() != null) {
            sql.append(" AND status::text = ?");
            params.add(criteria.getStatus().getDbValue());
        }
        if (criteria.getMinCarats() != null) { sql.append(" AND carats >= ?"); params.add(criteria.getMinCarats()); }
        if (criteria.getMaxCarats() != null) { sql.append(" AND carats <= ?"); params.add(criteria.getMaxCarats()); }
        if (criteria.getMinPrice() != null) { sql.append(" AND price >= ?"); params.add(criteria.getMinPrice()); }
        if (criteria.getMaxPrice() != null) { sql.append(" AND price <= ?"); params.add(criteria.getMaxPrice()); }

        String col = switch (criteria.getSortColumn()) {
            case "title" -> "title";
            case "gem_type" -> "gem_type";
            case "carats" -> "carats";
            case "price" -> "price";
            case "origin" -> "origin";
            case "color" -> "color";
            case "shape" -> "shape";
            case "status" -> "status";
            default -> "id";
        };
        sql.append(" ORDER BY ").append(col).append(criteria.isSortAscending() ? " ASC" : " DESC").append(" NULLS LAST");

        List<Gemstone> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    public Gemstone getGemstoneById(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_COLS + " WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public void updateStatus(int gemstoneId, Gemstone.Status status) throws SQLException {
        String sql = "UPDATE gemstones SET status = ?::gem_status, updated_at = NOW() WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.getDbValue());
            ps.setInt(2, gemstoneId);
            ps.executeUpdate();
        }
    }

    public Map<String, Object> getStatistics() throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) as total, COALESCE(SUM(price),0) as total_value, " +
                "COALESCE(AVG(price),0) as avg_price, COALESCE(SUM(carats),0) as total_carats, " +
                "COUNT(DISTINCT gem_type) as gem_types, COUNT(DISTINCT origin) as origins " +
                "FROM gemstones")) {
            if (rs.next()) {
                stats.put("total", rs.getInt("total"));
                stats.put("total_value", rs.getBigDecimal("total_value"));
                stats.put("avg_price", rs.getBigDecimal("avg_price"));
                stats.put("total_carats", rs.getBigDecimal("total_carats"));
                stats.put("gem_types", rs.getInt("gem_types"));
                stats.put("origins", rs.getInt("origins"));
            }
        }
        // Status breakdown
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT status::text, COUNT(*) FROM gemstones GROUP BY status")) {
            while (rs.next()) statusCounts.put(rs.getString(1), rs.getInt(2));
        }
        stats.put("statusCounts", statusCounts);
        return stats;
    }

    public List<String> getDistinctValues(String column) throws SQLException {
        List<String> values = new ArrayList<>();
        values.add("");
        String sql = "SELECT DISTINCT " + column + " FROM gemstones WHERE " + column +
                     " IS NOT NULL AND " + column + " != '' ORDER BY " + column;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) { String v = rs.getString(1); if (v != null) values.add(v); }
        }
        return values;
    }

    private Gemstone mapRow(ResultSet rs) throws SQLException {
        Gemstone g = new Gemstone();
        g.setId(rs.getInt("id"));
        g.setArticleId(rs.getString("article_id"));
        g.setTitle(rs.getString("title"));
        g.setDescription(rs.getString("description"));
        g.setPrice(rs.getBigDecimal("price"));
        g.setQuantity((Integer) rs.getObject("quantity"));
        Timestamp pd = rs.getTimestamp("purchase_date");
        if (pd != null) g.setPurchaseDate(pd.toLocalDateTime());
        g.setOrderId(rs.getString("order_id"));
        g.setTransactionId(rs.getString("transaction_id"));
        g.setListingUrl(rs.getString("listing_url"));
        g.setImageUrl(rs.getString("image_url"));
        g.setLocalImagePath(rs.getString("local_image_path"));
        g.setGemType(rs.getString("gem_type"));
        g.setCarats(rs.getBigDecimal("carats"));
        g.setDimensions(rs.getString("dimensions"));
        g.setShape(rs.getString("shape"));
        g.setColor(rs.getString("color"));
        g.setClarity(rs.getString("clarity"));
        g.setTreatment(rs.getString("treatment"));
        g.setOrigin(rs.getString("origin"));
        // Status: handle if column might not exist yet
        try { g.setStatus(Gemstone.Status.fromDb(rs.getString("status"))); }
        catch (SQLException ignored) {}
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) g.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) g.setUpdatedAt(ua.toLocalDateTime());
        return g;
    }
}
