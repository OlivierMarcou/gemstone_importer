package com.gemstone.viewer.db;

import com.gemstone.viewer.model.EtsyListing;
import com.gemstone.viewer.model.Gemstone;
import com.gemstone.viewer.model.SearchCriteria;

import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final String url, username, password;
    private Connection connection;

    private static final String GEMSTONE_COLS =
        "id, article_id, title, description, price, quantity, purchase_date, " +
        "order_id, transaction_id, listing_url, image_url, local_image_path, " +
        "gem_type, carats, dimensions, shape, color, clarity, treatment, origin, " +
        "status, created_at, updated_at";

    public DatabaseManager(String url, String username, String password) {
        this.url = url; this.username = username; this.password = password;
    }

    public boolean connect() {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(url, username, password);
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public void disconnect() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { e.printStackTrace(); }
    }

    public boolean isConnected() {
        try { return connection != null && !connection.isClosed() && connection.isValid(2); }
        catch (SQLException e) { return false; }
    }

    public boolean hasStatusColumn() {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, "gemstones", "status")) {
            return rs.next();
        } catch (SQLException e) { return false; }
    }

    public boolean hasEtsyTables() {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, "etsy_listings", null)) {
            return rs.next();
        } catch (SQLException e) { return false; }
    }

    public void runStatusMigration() throws SQLException {
        String[] stmts = {
            "DO $$ BEGIN CREATE TYPE gem_status AS ENUM ('in_stock','used','damaged','unavailable'); " +
            "EXCEPTION WHEN duplicate_object THEN null; END $$",
            "ALTER TABLE gemstones ADD COLUMN IF NOT EXISTS status gem_status",
            "UPDATE gemstones SET status = CASE WHEN price IS NULL OR price=0 " +
            "THEN 'unavailable'::gem_status ELSE 'in_stock'::gem_status END WHERE status IS NULL",
            "ALTER TABLE gemstones ALTER COLUMN status SET DEFAULT 'in_stock'",
            "ALTER TABLE gemstones ALTER COLUMN status SET NOT NULL"
        };
        for (String s : stmts) { try (Statement st = connection.createStatement()) { st.execute(s); } }
    }

    // ================================================================
    //  GEMSTONES
    // ================================================================

    public List<Gemstone> searchGemstones(SearchCriteria c) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + GEMSTONE_COLS + " FROM gemstones WHERE 1=1");
        List<Object> p = new ArrayList<>();

        if (c.getKeyword() != null && !c.getKeyword().isBlank()) {
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(description) LIKE ?)");
            String kw = "%" + c.getKeyword().toLowerCase() + "%";
            p.add(kw); p.add(kw);
        }
        if (c.getGemType()   != null && !c.getGemType().isBlank())   { sql.append(" AND LOWER(gem_type)=LOWER(?)"); p.add(c.getGemType()); }
        if (c.getColor()     != null && !c.getColor().isBlank())     { sql.append(" AND LOWER(color)=LOWER(?)");    p.add(c.getColor()); }
        if (c.getShape()     != null && !c.getShape().isBlank())     { sql.append(" AND LOWER(shape)=LOWER(?)");    p.add(c.getShape()); }
        if (c.getClarity()   != null && !c.getClarity().isBlank())   { sql.append(" AND LOWER(clarity)=LOWER(?)"); p.add(c.getClarity()); }
        if (c.getTreatment() != null && !c.getTreatment().isBlank()) { sql.append(" AND LOWER(treatment)=LOWER(?)"); p.add(c.getTreatment()); }
        if (c.getOrigin()    != null && !c.getOrigin().isBlank())    { sql.append(" AND LOWER(origin)=LOWER(?)");   p.add(c.getOrigin()); }
        if (c.getStatus()    != null)                                 { sql.append(" AND status::text=?"); p.add(c.getStatus().getDbValue()); }
        if (c.getMinCarats() != null) { sql.append(" AND carats>=?"); p.add(c.getMinCarats()); }
        if (c.getMaxCarats() != null) { sql.append(" AND carats<=?"); p.add(c.getMaxCarats()); }
        if (c.getMinPrice()  != null) { sql.append(" AND price>=?");  p.add(c.getMinPrice()); }
        if (c.getMaxPrice()  != null) { sql.append(" AND price<=?");  p.add(c.getMaxPrice()); }

        // Filtre par référence Etsy
        if (c.getEtsyListingRef() != null && !c.getEtsyListingRef().isBlank()) {
            sql.append(" AND id IN (" +
                "SELECT lg.gemstone_id FROM listing_gemstones lg " +
                "JOIN etsy_listings el ON el.id=lg.listing_id " +
                "WHERE LOWER(el.listing_ref) LIKE LOWER(?))");
            p.add("%" + c.getEtsyListingRef() + "%");
        }

        String col = switch (c.getSortColumn()) {
            case "title"->"title"; case "gem_type"->"gem_type"; case "carats"->"carats";
            case "price"->"price"; case "origin"->"origin"; case "color"->"color";
            case "shape"->"shape"; case "status"->"status"; default->"id";
        };
        sql.append(" ORDER BY ").append(col).append(c.isSortAscending() ? " ASC" : " DESC").append(" NULLS LAST");

        List<Gemstone> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i=0; i<p.size(); i++) ps.setObject(i+1, p.get(i));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(mapGemstone(rs)); }
        }
        return out;
    }

    public Gemstone getGemstoneById(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + GEMSTONE_COLS + " FROM gemstones WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return mapGemstone(rs); }
        }
        return null;
    }

    public void updateStatus(int gemstoneId, Gemstone.Status status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE gemstones SET status=?::gem_status, updated_at=NOW() WHERE id=?")) {
            ps.setString(1, status.getDbValue()); ps.setInt(2, gemstoneId); ps.executeUpdate();
        }
    }

    /** Retourne les références Etsy liées à une pierre */
    public List<EtsyListing> getListingsForGemstone(int gemstoneId) throws SQLException {
        String sql = "SELECT el.id, el.listing_ref, el.title, el.jewelry_type::text, el.status::text, " +
            "el.price, el.etsy_url, el.main_image_path, el.main_image_url, lg.notes, lg.position " +
            "FROM etsy_listings el JOIN listing_gemstones lg ON lg.listing_id=el.id " +
            "WHERE lg.gemstone_id=? ORDER BY el.listing_ref";
        List<EtsyListing> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, gemstoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EtsyListing l = new EtsyListing();
                    l.setId(rs.getInt("id"));
                    l.setListingRef(rs.getString("listing_ref"));
                    l.setTitle(rs.getString("title"));
                    l.setJewelryType(EtsyListing.JewelryType.fromDb(rs.getString("jewelry_type")));
                    l.setStatus(EtsyListing.ListingStatus.fromDb(rs.getString("status")));
                    l.setPrice(rs.getBigDecimal("price"));
                    l.setEtsyUrl(rs.getString("etsy_url"));
                    l.setMainImagePath(rs.getString("main_image_path"));
                    l.setMainImageUrl(rs.getString("main_image_url"));
                    out.add(l);
                }
            }
        }
        return out;
    }

    /** Ajoute une liaison pierre ↔ fiche Etsy */
    public void linkGemstoneToListing(int gemstoneId, int listingId, String notes, int position) throws SQLException {
        String sql = "INSERT INTO listing_gemstones (listing_id, gemstone_id, notes, position) " +
            "VALUES (?,?,?,?) ON CONFLICT (listing_id, gemstone_id) DO UPDATE SET notes=EXCLUDED.notes, position=EXCLUDED.position";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, listingId); ps.setInt(2, gemstoneId);
            ps.setString(3, notes); ps.setInt(4, position);
            ps.executeUpdate();
        }
    }

    /** Supprime une liaison pierre ↔ fiche Etsy */
    public void unlinkGemstoneFromListing(int gemstoneId, int listingId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM listing_gemstones WHERE gemstone_id=? AND listing_id=?")) {
            ps.setInt(1, gemstoneId); ps.setInt(2, listingId); ps.executeUpdate();
        }
    }

    // ================================================================
    //  ETSY LISTINGS
    // ================================================================

    public List<EtsyListing> searchListings(String keyword, EtsyListing.JewelryType type,
            EtsyListing.ListingStatus status) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT id, listing_ref, etsy_listing_id, title, description, jewelry_type::text, " +
            "status::text, price, currency, quantity, etsy_url, metal_type, metal_color, ring_size, " +
            "weight_grams, main_image_url, main_image_path, processing_days, views, favorites, " +
            "sales_count, tags, materials, created_at, updated_at, listed_at, sold_at, " +
            "(SELECT COUNT(*) FROM listing_gemstones lg WHERE lg.listing_id=etsy_listings.id) AS gemstone_count, " +
            "(SELECT STRING_AGG(DISTINCT g.gem_type, ', ') FROM listing_gemstones lg " +
            " JOIN gemstones g ON g.id=lg.gemstone_id WHERE lg.listing_id=etsy_listings.id) AS gem_types_used " +
            "FROM etsy_listings WHERE 1=1");
        List<Object> p = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOWER(listing_ref) LIKE ? OR LOWER(title) LIKE ? OR LOWER(description) LIKE ?)");
            String kw = "%" + keyword.toLowerCase() + "%";
            p.add(kw); p.add(kw); p.add(kw);
        }
        if (type   != null) { sql.append(" AND jewelry_type::text=?"); p.add(type.getDbValue()); }
        if (status != null) { sql.append(" AND status::text=?");       p.add(status.getDbValue()); }
        sql.append(" ORDER BY listing_ref");

        List<EtsyListing> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i=0; i<p.size(); i++) ps.setObject(i+1, p.get(i));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(mapListing(rs)); }
        }
        return out;
    }

    public EtsyListing getListingById(int id) throws SQLException {
        String sql = "SELECT id, listing_ref, etsy_listing_id, title, description, jewelry_type::text, " +
            "status::text, price, currency, quantity, etsy_url, metal_type, metal_color, ring_size, " +
            "weight_grams, main_image_url, main_image_path, processing_days, views, favorites, " +
            "sales_count, tags, materials, created_at, updated_at, listed_at, sold_at, " +
            "(SELECT COUNT(*) FROM listing_gemstones lg WHERE lg.listing_id=etsy_listings.id) AS gemstone_count, " +
            "(SELECT STRING_AGG(DISTINCT g.gem_type, ', ') FROM listing_gemstones lg " +
            " JOIN gemstones g ON g.id=lg.gemstone_id WHERE lg.listing_id=etsy_listings.id) AS gem_types_used " +
            "FROM etsy_listings WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EtsyListing l = mapListing(rs);
                    l.setGemstones(getGemstonesForListing(id));
                    return l;
                }
            }
        }
        return null;
    }

    public EtsyListing getListingByRef(String ref) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM etsy_listings WHERE LOWER(listing_ref)=LOWER(?)")) {
            ps.setString(1, ref);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return getListingById(rs.getInt(1));
            }
        }
        return null;
    }

    public List<Gemstone> getGemstonesForListing(int listingId) throws SQLException {
        String sql = "SELECT g." + GEMSTONE_COLS.replace(",", ", g.") +
            " FROM gemstones g JOIN listing_gemstones lg ON lg.gemstone_id=g.id " +
            "WHERE lg.listing_id=? ORDER BY lg.position, g.id";
        // Fix: don't prefix the first column
        sql = "SELECT " + GEMSTONE_COLS.replace(",", ", g.").replaceFirst("^", "g.") +
            " FROM gemstones g JOIN listing_gemstones lg ON lg.gemstone_id=g.id " +
            "WHERE lg.listing_id=? ORDER BY lg.position, g.id";
        List<Gemstone> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(mapGemstone(rs)); }
        }
        return out;
    }

    public int createListing(EtsyListing l) throws SQLException {
        String sql = "INSERT INTO etsy_listings (listing_ref, etsy_listing_id, title, description, " +
            "jewelry_type, status, price, currency, quantity, etsy_url, metal_type, metal_color, " +
            "ring_size, weight_grams, main_image_url, main_image_path, processing_days, tags, materials) " +
            "VALUES (?,?,?,?,?::jewelry_type,?::etsy_listing_status,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, l.getListingRef());
            ps.setString(2, l.getEtsyListingId());
            ps.setString(3, l.getTitle());
            ps.setString(4, l.getDescription());
            ps.setString(5, l.getJewelryType().getDbValue());
            ps.setString(6, l.getStatus().getDbValue());
            ps.setBigDecimal(7, l.getPrice());
            ps.setString(8, l.getCurrency() != null ? l.getCurrency() : "EUR");
            ps.setObject(9, l.getQuantity());
            ps.setString(10, l.getEtsyUrl());
            ps.setString(11, l.getMetalType());
            ps.setString(12, l.getMetalColor());
            ps.setString(13, l.getRingSize());
            ps.setBigDecimal(14, l.getWeightGrams());
            ps.setString(15, l.getMainImageUrl());
            ps.setString(16, l.getMainImagePath());
            ps.setObject(17, l.getProcessingDays());
            ps.setArray(18, l.getTags() != null ? connection.createArrayOf("TEXT", l.getTags()) : null);
            ps.setArray(19, l.getMaterials() != null ? connection.createArrayOf("TEXT", l.getMaterials()) : null);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    public void updateListing(EtsyListing l) throws SQLException {
        String sql = "UPDATE etsy_listings SET listing_ref=?, etsy_listing_id=?, title=?, description=?, " +
            "jewelry_type=?::jewelry_type, status=?::etsy_listing_status, price=?, currency=?, quantity=?, " +
            "etsy_url=?, metal_type=?, metal_color=?, ring_size=?, weight_grams=?, " +
            "main_image_url=?, main_image_path=?, processing_days=?, tags=?, materials=?, updated_at=NOW() " +
            "WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, l.getListingRef());
            ps.setString(2, l.getEtsyListingId());
            ps.setString(3, l.getTitle());
            ps.setString(4, l.getDescription());
            ps.setString(5, l.getJewelryType().getDbValue());
            ps.setString(6, l.getStatus().getDbValue());
            ps.setBigDecimal(7, l.getPrice());
            ps.setString(8, l.getCurrency() != null ? l.getCurrency() : "EUR");
            ps.setObject(9, l.getQuantity());
            ps.setString(10, l.getEtsyUrl());
            ps.setString(11, l.getMetalType());
            ps.setString(12, l.getMetalColor());
            ps.setString(13, l.getRingSize());
            ps.setBigDecimal(14, l.getWeightGrams());
            ps.setString(15, l.getMainImageUrl());
            ps.setString(16, l.getMainImagePath());
            ps.setObject(17, l.getProcessingDays());
            ps.setArray(18, l.getTags() != null ? connection.createArrayOf("TEXT", l.getTags()) : null);
            ps.setArray(19, l.getMaterials() != null ? connection.createArrayOf("TEXT", l.getMaterials()) : null);
            ps.setInt(20, l.getId());
            ps.executeUpdate();
        }
    }

    public List<String> getAllListingRefs() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT listing_ref FROM etsy_listings ORDER BY listing_ref")) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    // ================================================================
    //  STATISTICS
    // ================================================================

    public Map<String, Object> getStatistics() throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) as total, COALESCE(SUM(price),0) as total_value, " +
                "COALESCE(AVG(price),0) as avg_price, COALESCE(SUM(carats),0) as total_carats, " +
                "COUNT(DISTINCT gem_type) as gem_types, COUNT(DISTINCT origin) as origins FROM gemstones")) {
            if (rs.next()) {
                stats.put("total", rs.getInt("total"));
                stats.put("total_value", rs.getBigDecimal("total_value"));
                stats.put("avg_price", rs.getBigDecimal("avg_price"));
                stats.put("total_carats", rs.getBigDecimal("total_carats"));
                stats.put("gem_types", rs.getInt("gem_types"));
                stats.put("origins", rs.getInt("origins"));
            }
        }
        Map<String, Integer> sc = new LinkedHashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT status::text, COUNT(*) FROM gemstones GROUP BY status")) {
            while (rs.next()) sc.put(rs.getString(1), rs.getInt(2));
        }
        stats.put("statusCounts", sc);
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

    // ================================================================
    //  MAPPERS
    // ================================================================

    private Gemstone mapGemstone(ResultSet rs) throws SQLException {
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
        try { g.setStatus(Gemstone.Status.fromDb(rs.getString("status"))); } catch (SQLException ignored) {}
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) g.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) g.setUpdatedAt(ua.toLocalDateTime());
        return g;
    }

    private EtsyListing mapListing(ResultSet rs) throws SQLException {
        EtsyListing l = new EtsyListing();
        l.setId(rs.getInt("id"));
        l.setListingRef(rs.getString("listing_ref"));
        l.setEtsyListingId(rs.getString("etsy_listing_id"));
        l.setTitle(rs.getString("title"));
        l.setDescription(rs.getString("description"));
        l.setJewelryType(EtsyListing.JewelryType.fromDb(rs.getString("jewelry_type")));
        l.setStatus(EtsyListing.ListingStatus.fromDb(rs.getString("status")));
        l.setPrice(rs.getBigDecimal("price"));
        l.setCurrency(rs.getString("currency"));
        l.setQuantity((Integer) rs.getObject("quantity"));
        l.setEtsyUrl(rs.getString("etsy_url"));
        l.setMetalType(rs.getString("metal_type"));
        l.setMetalColor(rs.getString("metal_color"));
        l.setRingSize(rs.getString("ring_size"));
        l.setWeightGrams(rs.getBigDecimal("weight_grams"));
        l.setMainImageUrl(rs.getString("main_image_url"));
        l.setMainImagePath(rs.getString("main_image_path"));
        l.setProcessingDays((Integer) rs.getObject("processing_days"));
        l.setViews((Integer) rs.getObject("views"));
        l.setFavorites((Integer) rs.getObject("favorites"));
        l.setSalesCount((Integer) rs.getObject("sales_count"));
        Array tags = rs.getArray("tags");
        if (tags != null) l.setTags((String[]) tags.getArray());
        Array mats = rs.getArray("materials");
        if (mats != null) l.setMaterials((String[]) mats.getArray());
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) l.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) l.setUpdatedAt(ua.toLocalDateTime());
        Timestamp la = rs.getTimestamp("listed_at");
        if (la != null) l.setListedAt(la.toLocalDateTime());
        Timestamp sa = rs.getTimestamp("sold_at");
        if (sa != null) l.setSoldAt(sa.toLocalDateTime());
        l.setGemstoneCount(rs.getInt("gemstone_count"));
        l.setGemTypesUsed(rs.getString("gem_types_used"));
        return l;
    }
}
