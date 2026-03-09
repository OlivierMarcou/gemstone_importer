package com.gemstone.viewer.etsy;

import com.gemstone.viewer.db.DatabaseManager;
import com.gemstone.viewer.etsy.EtsyApiClient.JsonObject;

import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service d'import : lit les JsonObject de l'API Etsy
 * et les insère/met à jour dans etsy_listings via UPSERT.
 *
 * Stratégie de correspondance :
 *   - Si etsy_listing_id existe déjà → UPDATE
 *   - Sinon → INSERT
 *   - listing_ref est auto-généré : ETY-{etsy_id}
 *   - Les liaisons listing_gemstones ne sont PAS écrasées (on préserve les associations manuelles)
 */
public class EtsyImportService {

    private final DatabaseManager db;
    private final Connection conn;

    // Compteurs
    private int inserted = 0;
    private int updated  = 0;
    private int skipped  = 0;
    private int errors   = 0;

    public EtsyImportService(DatabaseManager db) throws Exception {
        this.db   = db;
        this.conn = db.getConnection();
    }

    public void resetCounters() { inserted = 0; updated = 0; skipped = 0; errors = 0; }
    public int getInserted() { return inserted; }
    public int getUpdated()  { return updated; }
    public int getSkipped()  { return skipped; }
    public int getErrors()   { return errors; }

    /**
     * Importe un lot de fiches Etsy.
     * @param listings Liste de JsonObject renvoyés par l'API
     * @param log      Callback pour messages de log
     */
    public void importBatch(List<JsonObject> listings, Consumer<String> log) {
        for (JsonObject l : listings) {
            try {
                upsertListing(l, log);
            } catch (Exception e) {
                errors++;
                String id = l.getString("listing_id", "?");
                if (log != null) log.accept("  ❌ Erreur fiche #" + id + " : " + e.getMessage());
            }
        }
    }

    private void upsertListing(JsonObject l, Consumer<String> log) throws Exception {
        String etsyId    = String.valueOf(l.getLong("listing_id", 0));
        String title     = l.getString("title", "");
        String stateStr  = l.getString("state", "draft");
        String dbState   = EtsyApiClient.mapState(stateStr);
        String listingRef = "ETY-" + etsyId;

        // Vérifier si la fiche existe déjà
        Integer existingId = findByEtsyId(etsyId);

        // Extraire les champs
        BigDecimal price    = EtsyApiClient.extractPrice(l);
        String currency     = extractCurrency(l);
        String description  = l.getString("description", null);
        long   quantity     = l.getLong("quantity", 1);
        String etsyUrl      = l.getString("url", null);
        String imageUrl     = EtsyApiClient.extractMainImageUrl(l);
        int    processingMin= (int) l.getLong("processing_min", 1);
        int    processingMax= (int) l.getLong("processing_max", 3);
        int    processing   = Math.max(processingMin, 1);
        int    views        = (int) l.getLong("views", 0);
        int    numFavorers  = (int) l.getLong("num_favorers", 0);

        // Tags et matériaux
        List<String> tags      = l.getStringArray("tags");
        List<String> materials = l.getStringArray("materials");

        // Type de bijou — on tente de le deviner via taxonomy_path ou tags
        String jewelryType = guessJewelryType(l);

        // Timestamps
        long createdTs  = l.getLong("original_creation_timestamp", 0);
        long updatedTs  = l.getLong("last_modified_timestamp",     0);
        long listedTs   = l.getLong("creation_timestamp",          0); // date de mise en ligne

        if (existingId != null) {
            // UPDATE
            update(existingId, etsyId, listingRef, title, description, jewelryType, dbState,
                price, currency, (int)quantity, etsyUrl, imageUrl, processing,
                views, numFavorers, tags, materials, updatedTs, listedTs);
            updated++;
            if (log != null) log.accept("  ↺ MàJ : " + listingRef + " — " + truncate(title, 50));
        } else {
            // INSERT
            insert(etsyId, listingRef, title, description, jewelryType, dbState,
                price, currency, (int)quantity, etsyUrl, imageUrl, processing,
                views, numFavorers, tags, materials, createdTs, updatedTs, listedTs);
            inserted++;
            if (log != null) log.accept("  ✅ Import : " + listingRef + " — " + truncate(title, 50));
        }
    }

    private Integer findByEtsyId(String etsyId) throws SQLException {
        String sql = "SELECT id FROM etsy_listings WHERE etsy_listing_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, etsyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    private void insert(String etsyId, String ref, String title, String desc,
            String jewelryType, String status, BigDecimal price, String currency,
            int quantity, String etsyUrl, String imageUrl, int processingDays,
            int views, int favorites, List<String> tags, List<String> materials,
            long createdTs, long updatedTs, long listedTs) throws SQLException {

        String sql = "INSERT INTO etsy_listings " +
            "(etsy_listing_id, listing_ref, title, description, jewelry_type, status, " +
            "price, currency, quantity, etsy_url, main_image_url, processing_days, " +
            "views, favorites, tags, materials, " +
            "created_at, updated_at, listed_at) " +
            "VALUES (?,?,?,?,?::jewelry_type,?::etsy_listing_status,?,?,?,?,?,?,?,?,?,?," +
            "?,?,?) " +
            "ON CONFLICT (etsy_listing_id) DO UPDATE SET " +
            "listing_ref=EXCLUDED.listing_ref, title=EXCLUDED.title, " +
            "description=EXCLUDED.description, jewelry_type=EXCLUDED.jewelry_type, " +
            "status=EXCLUDED.status, price=EXCLUDED.price, currency=EXCLUDED.currency, " +
            "quantity=EXCLUDED.quantity, etsy_url=EXCLUDED.etsy_url, " +
            "main_image_url=EXCLUDED.main_image_url, processing_days=EXCLUDED.processing_days, " +
            "views=EXCLUDED.views, favorites=EXCLUDED.favorites, " +
            "tags=EXCLUDED.tags, materials=EXCLUDED.materials, " +
            "updated_at=EXCLUDED.updated_at, listed_at=EXCLUDED.listed_at";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, etsyId);
            ps.setString(2, ref);
            ps.setString(3, title);
            ps.setString(4, desc);
            ps.setString(5, jewelryType);
            ps.setString(6, status);
            ps.setBigDecimal(7, price);
            ps.setString(8, currency);
            ps.setInt(9, quantity);
            ps.setString(10, etsyUrl);
            ps.setString(11, imageUrl);
            ps.setInt(12, processingDays);
            ps.setInt(13, views);
            ps.setInt(14, favorites);
            ps.setArray(15, toArray(tags));
            ps.setArray(16, toArray(materials));
            ps.setTimestamp(17, ts(createdTs));
            ps.setTimestamp(18, ts(updatedTs));
            ps.setTimestamp(19, ts(listedTs));
            ps.executeUpdate();
        }
    }

    private void update(int id, String etsyId, String ref, String title, String desc,
            String jewelryType, String status, BigDecimal price, String currency,
            int quantity, String etsyUrl, String imageUrl, int processingDays,
            int views, int favorites, List<String> tags, List<String> materials,
            long updatedTs, long listedTs) throws SQLException {

        String sql = "UPDATE etsy_listings SET " +
            "listing_ref=?, title=?, description=?, jewelry_type=?::jewelry_type, " +
            "status=?::etsy_listing_status, price=?, currency=?, quantity=?, etsy_url=?, " +
            "main_image_url=COALESCE(main_image_url,?), " +   // ne pas écraser une image locale
            "processing_days=?, views=?, favorites=?, tags=?, materials=?, " +
            "updated_at=?, listed_at=? " +
            "WHERE id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ref);
            ps.setString(2, title);
            ps.setString(3, desc);
            ps.setString(4, jewelryType);
            ps.setString(5, status);
            ps.setBigDecimal(6, price);
            ps.setString(7, currency);
            ps.setInt(8, quantity);
            ps.setString(9, etsyUrl);
            ps.setString(10, imageUrl);
            ps.setInt(11, processingDays);
            ps.setInt(12, views);
            ps.setInt(13, favorites);
            ps.setArray(14, toArray(tags));
            ps.setArray(15, toArray(materials));
            ps.setTimestamp(16, ts(updatedTs));
            ps.setTimestamp(17, ts(listedTs));
            ps.setInt(18, id);
            ps.executeUpdate();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Devine le type de bijou à partir du taxonomy_path et des tags */
    private String guessJewelryType(JsonObject l) {
        // Essayer taxonomy_path
        List<String> taxPath = l.getStringArray("taxonomy_path");
        if (taxPath != null) {
            String joined = String.join(" ", taxPath).toLowerCase();
            if (contains(joined, "ring","bague"))              return "ring";
            if (contains(joined, "earring","boucle"))          return "earrings";
            if (contains(joined, "pendant","pendentif"))       return "pendant";
            if (contains(joined, "necklace","collier"))        return "necklace";
            if (contains(joined, "bracelet"))                  return "bracelet";
            if (contains(joined, "brooch","broche"))           return "brooch";
            if (contains(joined, "anklet","cheville"))         return "anklet";
            if (contains(joined, "tiara","couronne","diadem")) return "tiara";
        }
        // Fallback sur les tags
        List<String> tags = l.getStringArray("tags");
        if (tags != null) {
            String joined = String.join(" ", tags).toLowerCase();
            if (contains(joined, "ring","bague"))              return "ring";
            if (contains(joined, "earring","boucle"))          return "earrings";
            if (contains(joined, "pendant","pendentif"))       return "pendant";
            if (contains(joined, "necklace","collier"))        return "necklace";
            if (contains(joined, "bracelet"))                  return "bracelet";
            if (contains(joined, "brooch"))                    return "brooch";
        }
        // Fallback sur le titre
        String title = l.getString("title","").toLowerCase();
        if (contains(title, "ring","bague"))              return "ring";
        if (contains(title, "earring","boucle","oreille"))return "earrings";
        if (contains(title, "pendant","pendentif"))       return "pendant";
        if (contains(title, "necklace","collier"))        return "necklace";
        if (contains(title, "bracelet"))                  return "bracelet";
        if (contains(title, "brooch","broche"))           return "brooch";
        return "other";
    }

    private boolean contains(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    private String extractCurrency(JsonObject l) {
        JsonObject price = l.getObject("price");
        if (price != null) { String c = price.getString("currency_code"); if (c!=null) return c; }
        return l.getString("currency_code", "EUR");
    }

    private Array toArray(List<String> list) throws SQLException {
        if (list == null || list.isEmpty()) return null;
        return conn.createArrayOf("TEXT", list.toArray(new String[0]));
    }

    private Timestamp ts(long epochSeconds) {
        if (epochSeconds <= 0) return null;
        return new Timestamp(epochSeconds * 1000L);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
