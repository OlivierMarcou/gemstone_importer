package com.gemstone.importer.database;

import com.gemstone.importer.model.Gemstone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Gestionnaire de base de données PostgreSQL pour les pierres précieuses
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    private final String url;
    private final String username;
    private final String password;
    private final String imagesDirectory;
    
    public DatabaseManager(String url, String username, String password, String imagesDirectory) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.imagesDirectory = imagesDirectory;
        
        // Créer le répertoire des images s'il n'existe pas
        File dir = new File(imagesDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * Établit une connexion à la base de données
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
    
    /**
     * Crée le schéma de la base de données
     */
    public void createSchema() throws SQLException {
        logger.info("Création du schéma de la base de données...");
        
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS gemstones (
                id SERIAL PRIMARY KEY,
                article_id VARCHAR(50) UNIQUE NOT NULL,
                title TEXT,
                description TEXT,
                price DECIMAL(10, 2),
                quantity INTEGER,
                purchase_date TIMESTAMP,
                order_id VARCHAR(50),
                transaction_id VARCHAR(50),
                status VARCHAR(50),
                listing_url TEXT,
                image_url TEXT,
                local_image_path TEXT,
                image_count INTEGER,
                video_count INTEGER,
                
                -- Informations extraites
                gem_type VARCHAR(50),
                carats DECIMAL(10, 2),
                dimensions VARCHAR(50),
                shape VARCHAR(50),
                color VARCHAR(50),
                clarity VARCHAR(20),
                treatment VARCHAR(50),
                origin VARCHAR(100),
                
                -- Métadonnées
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            -- Index pour améliorer les performances
            CREATE INDEX IF NOT EXISTS idx_article_id ON gemstones(article_id);
            CREATE INDEX IF NOT EXISTS idx_gem_type ON gemstones(gem_type);
            CREATE INDEX IF NOT EXISTS idx_origin ON gemstones(origin);
            CREATE INDEX IF NOT EXISTS idx_purchase_date ON gemstones(purchase_date);
            CREATE INDEX IF NOT EXISTS idx_price ON gemstones(price);
            
            -- Vue pour les statistiques
            CREATE OR REPLACE VIEW gemstone_statistics AS
            SELECT 
                COUNT(*) as total_gemstones,
                SUM(price) as total_value,
                AVG(price) as average_price,
                SUM(carats) as total_carats,
                COUNT(DISTINCT gem_type) as distinct_types,
                COUNT(DISTINCT origin) as distinct_origins
            FROM gemstones;
            
            -- Vue pour les pierres par type
            CREATE OR REPLACE VIEW gemstones_by_type AS
            SELECT 
                gem_type,
                COUNT(*) as count,
                SUM(price) as total_value,
                AVG(price) as avg_price,
                SUM(carats) as total_carats,
                AVG(carats) as avg_carats
            FROM gemstones
            WHERE gem_type IS NOT NULL
            GROUP BY gem_type
            ORDER BY count DESC;
            
            -- Vue pour les pierres par origine
            CREATE OR REPLACE VIEW gemstones_by_origin AS
            SELECT 
                origin,
                COUNT(*) as count,
                SUM(price) as total_value,
                AVG(price) as avg_price
            FROM gemstones
            WHERE origin IS NOT NULL
            GROUP BY origin
            ORDER BY count DESC;
        """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createTableSQL);
            logger.info("Schéma créé avec succès!");
            
        } catch (SQLException e) {
            logger.error("Erreur lors de la création du schéma", e);
            throw e;
        }
    }
    
    /**
     * Vérifie si une pierre existe déjà dans la base de données
     */
    public boolean gemstoneExists(String articleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM gemstones WHERE article_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, articleId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        
        return false;
    }
    
    /**
     * Insère une pierre précieuse dans la base de données
     */
    public void insertGemstone(Gemstone gemstone) throws SQLException {
        // Télécharger l'image si nécessaire
        if (gemstone.getImageUrl() != null && !gemstone.getImageUrl().isEmpty()) {
            try {
                String localPath = downloadImage(gemstone.getImageUrl(), gemstone.getArticleId());
                gemstone.setLocalImagePath(localPath);
            } catch (IOException e) {
                logger.warn("Impossible de télécharger l'image pour {}: {}", 
                           gemstone.getArticleId(), e.getMessage());
            }
        }
        
        String sql = """
            INSERT INTO gemstones (
                article_id, title, description, price, quantity, purchase_date,
                order_id, transaction_id, status, listing_url, image_url,
                local_image_path, image_count, video_count,
                gem_type, carats, dimensions, shape, color, clarity,
                treatment, origin
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, gemstone.getArticleId());
            pstmt.setString(2, gemstone.getTitle());
            pstmt.setString(3, gemstone.getDescription());
            
            if (gemstone.getPrice() != null) {
                pstmt.setDouble(4, gemstone.getPrice());
            } else {
                pstmt.setNull(4, Types.DECIMAL);
            }
            
            if (gemstone.getQuantity() != null) {
                pstmt.setInt(5, gemstone.getQuantity());
            } else {
                pstmt.setNull(5, Types.INTEGER);
            }
            
            if (gemstone.getPurchaseDate() != null) {
                pstmt.setTimestamp(6, Timestamp.valueOf(gemstone.getPurchaseDate()));
            } else {
                pstmt.setNull(6, Types.TIMESTAMP);
            }
            
            pstmt.setString(7, gemstone.getOrderId());
            pstmt.setString(8, gemstone.getTransactionId());
            pstmt.setString(9, gemstone.getStatus());
            pstmt.setString(10, gemstone.getListingUrl());
            pstmt.setString(11, gemstone.getImageUrl());
            pstmt.setString(12, gemstone.getLocalImagePath());
            
            if (gemstone.getImageCount() != null) {
                pstmt.setInt(13, gemstone.getImageCount());
            } else {
                pstmt.setNull(13, Types.INTEGER);
            }
            
            if (gemstone.getVideoCount() != null) {
                pstmt.setInt(14, gemstone.getVideoCount());
            } else {
                pstmt.setNull(14, Types.INTEGER);
            }
            
            pstmt.setString(15, gemstone.getGemType());
            
            if (gemstone.getCarats() != null) {
                pstmt.setDouble(16, gemstone.getCarats());
            } else {
                pstmt.setNull(16, Types.DECIMAL);
            }
            
            pstmt.setString(17, gemstone.getDimensions());
            pstmt.setString(18, gemstone.getShape());
            pstmt.setString(19, gemstone.getColor());
            pstmt.setString(20, gemstone.getClarity());
            pstmt.setString(21, gemstone.getTreatment());
            pstmt.setString(22, gemstone.getOrigin());
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Erreur lors de l'insertion de la pierre {}", gemstone.getArticleId(), e);
            throw e;
        }
    }
    
    /**
     * Insère plusieurs pierres précieuses en évitant les doublons
     */
    public int insertGemstones(List<Gemstone> gemstones) throws SQLException {
        int insertedCount = 0;
        int skippedCount = 0;
        
        logger.info("Insertion de {} pierres précieuses...", gemstones.size());
        
        for (Gemstone gemstone : gemstones) {
            if (gemstone.getArticleId() == null || gemstone.getArticleId().isEmpty()) {
                logger.warn("Pierre sans ID, ignorée: {}", gemstone.getTitle());
                skippedCount++;
                continue;
            }
            
            if (gemstoneExists(gemstone.getArticleId())) {
                logger.debug("Pierre {} déjà existante, ignorée", gemstone.getArticleId());
                skippedCount++;
                continue;
            }
            
            try {
                insertGemstone(gemstone);
                insertedCount++;
                logger.info("Pierre insérée: {} - {}", gemstone.getArticleId(), gemstone.getTitle());
            } catch (SQLException e) {
                logger.error("Erreur lors de l'insertion de {}", gemstone.getArticleId(), e);
                skippedCount++;
            }
        }
        
        logger.info("Insertion terminée: {} nouvelles pierres, {} ignorées", insertedCount, skippedCount);
        
        return insertedCount;
    }
    
    /**
     * Télécharge une image depuis une URL et la sauvegarde localement
     */
    private String downloadImage(String imageUrl, String articleId) throws IOException {
        logger.debug("Téléchargement de l'image pour l'article {}", articleId);
        
        // Extraire l'extension du fichier
        String extension = ".jpg";
        if (imageUrl.contains(".png")) {
            extension = ".png";
        } else if (imageUrl.contains(".gif")) {
            extension = ".gif";
        }
        
        String fileName = articleId + extension;
        Path targetPath = Paths.get(imagesDirectory, fileName);
        
        // Si l'image existe déjà, ne pas la retélécharger
        if (Files.exists(targetPath)) {
            logger.debug("Image déjà existante: {}", fileName);
            return targetPath.toString();
        }
        
        try {
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Image téléchargée: {}", fileName);
            }
            
            return targetPath.toString();
            
        } catch (IOException e) {
            logger.warn("Erreur lors du téléchargement de l'image {}: {}", imageUrl, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Affiche les statistiques de la base de données
     */
    public void printStatistics() throws SQLException {
        logger.info("=== Statistiques de la base de données ===");
        
        String sql = "SELECT * FROM gemstone_statistics";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                logger.info("Nombre total de pierres: {}", rs.getInt("total_gemstones"));
                logger.info("Valeur totale: {:.2f}", rs.getDouble("total_value"));
                logger.info("Prix moyen: {:.2f}", rs.getDouble("average_price"));
                logger.info("Total de carats: {:.2f}", rs.getDouble("total_carats"));
                logger.info("Types distincts: {}", rs.getInt("distinct_types"));
                logger.info("Origines distinctes: {}", rs.getInt("distinct_origins"));
            }
        }
        
        logger.info("=== Top 10 types de pierres ===");
        sql = "SELECT * FROM gemstones_by_type LIMIT 10";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                logger.info("{}: {} pierres, valeur totale: {:.2f}", 
                           rs.getString("gem_type"),
                           rs.getInt("count"),
                           rs.getDouble("total_value"));
            }
        }
    }
}
