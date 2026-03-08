package com.gemstone.importer;

import com.gemstone.importer.database.DatabaseManager;
import com.gemstone.importer.model.Gemstone;
import com.gemstone.importer.parser.ExcelParser;
import com.gemstone.importer.parser.HtmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;

/**
 * Application principale pour importer des données de pierres précieuses
 * depuis des fichiers Excel et HTML vers une base de données PostgreSQL
 */
public class GemstoneImporter {
    private static final Logger logger = LoggerFactory.getLogger(GemstoneImporter.class);
    
    private static final String CONFIG_FILE = "config.properties";
    private static final String DEFAULT_CONFIG_FILE = "default-config.properties";
    
    public static void main(String[] args) {
        logger.info("=== Gemstone Importer - Démarrage ===");
        
        try {
            // Charger la configuration
            Properties config = loadConfiguration();
            
            // Paramètres de la base de données
            String dbUrl = config.getProperty("db.url");
            String dbUsername = config.getProperty("db.username");
            String dbPassword = config.getProperty("db.password");
            String imagesDirectory = config.getProperty("images.directory", "images");
            
            // Répertoires des fichiers à importer
            String excelDirectory = config.getProperty("import.excel.directory", ".");
            String htmlDirectory = config.getProperty("import.html.directory", ".");
            
            // Mode de fonctionnement
            boolean createSchema = Boolean.parseBoolean(
                config.getProperty("db.create.schema", "true")
            );
            
            // Créer le gestionnaire de base de données
            DatabaseManager dbManager = new DatabaseManager(
                dbUrl, dbUsername, dbPassword, imagesDirectory
            );
            
            // Créer le schéma si nécessaire
            if (createSchema) {
                logger.info("Création du schéma de la base de données...");
                dbManager.createSchema();
                logger.info("Schéma créé avec succès!");
            }
            
            // Parser et importer les fichiers
            List<Gemstone> allGemstones = new ArrayList<>();
            
            // Importer depuis Excel
            File excelDir = new File(excelDirectory);
            if (excelDir.exists()) {
                logger.info("Recherche de fichiers Excel dans: {}", excelDir.getAbsolutePath());
                List<Gemstone> excelGemstones = importFromExcel(excelDir);
                allGemstones.addAll(excelGemstones);
                logger.info("{} pierres trouvées dans les fichiers Excel", excelGemstones.size());
            } else {
                logger.warn("Répertoire Excel non trouvé: {}", excelDir.getAbsolutePath());
            }
            
            // Importer depuis HTML
            File htmlDir = new File(htmlDirectory);
            if (htmlDir.exists()) {
                logger.info("Recherche de fichiers HTML dans: {}", htmlDir.getAbsolutePath());
                List<Gemstone> htmlGemstones = importFromHtml(htmlDir);
                allGemstones.addAll(htmlGemstones);
                logger.info("{} pierres trouvées dans les fichiers HTML", htmlGemstones.size());
            } else {
                logger.warn("Répertoire HTML non trouvé: {}", htmlDir.getAbsolutePath());
            }
            
            // Fusionner les données (Excel + HTML)
            Map<String, Gemstone> mergedGemstones = mergeGemstones(allGemstones);
            logger.info("Total de {} pierres uniques après fusion", mergedGemstones.size());
            
            // Insérer dans la base de données
            if (!mergedGemstones.isEmpty()) {
                int insertedCount = dbManager.insertGemstones(
                    new ArrayList<>(mergedGemstones.values())
                );
                logger.info("{} nouvelles pierres insérées dans la base de données", insertedCount);
            } else {
                logger.warn("Aucune pierre à importer!");
            }
            
            // Afficher les statistiques
            dbManager.printStatistics();
            
            logger.info("=== Gemstone Importer - Terminé ===");
            
        } catch (Exception e) {
            logger.error("Erreur fatale lors de l'importation", e);
            System.exit(1);
        }
    }
    
    /**
     * Charge la configuration depuis un fichier properties
     */
    private static Properties loadConfiguration() throws IOException {
        Properties config = new Properties();
        
        // Essayer de charger le fichier de configuration utilisateur
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            logger.info("Chargement de la configuration depuis: {}", CONFIG_FILE);
            try (InputStream input = new FileInputStream(configFile)) {
                config.load(input);
            }
        } else {
            // Charger la configuration par défaut depuis les resources
            logger.info("Fichier de configuration non trouvé, utilisation de la configuration par défaut");
            try (InputStream input = GemstoneImporter.class.getClassLoader()
                    .getResourceAsStream(DEFAULT_CONFIG_FILE)) {
                if (input != null) {
                    config.load(input);
                } else {
                    logger.warn("Configuration par défaut non trouvée, utilisation de valeurs codées en dur");
                    config.setProperty("db.url", "jdbc:postgresql://localhost:5432/gemstones");
                    config.setProperty("db.username", "postgres");
                    config.setProperty("db.password", "postgres");
                    config.setProperty("images.directory", "images");
                    config.setProperty("import.excel.directory", ".");
                    config.setProperty("import.html.directory", ".");
                    config.setProperty("db.create.schema", "true");
                }
            }
        }
        
        return config;
    }
    
    /**
     * Importe les pierres depuis les fichiers Excel
     */
    private static List<Gemstone> importFromExcel(File directory) {
        List<Gemstone> gemstones = new ArrayList<>();
        ExcelParser parser = new ExcelParser();
        
        // Trouver tous les fichiers Excel
        File[] excelFiles = directory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls")
        );
        
        if (excelFiles == null || excelFiles.length == 0) {
            logger.warn("Aucun fichier Excel trouvé dans {}", directory.getAbsolutePath());
            return gemstones;
        }
        
        logger.info("Fichiers Excel trouvés: {}", excelFiles.length);
        
        for (File file : excelFiles) {
            try {
                logger.info("Traitement du fichier: {}", file.getName());
                List<Gemstone> parsed = parser.parseExcelFile(file);
                gemstones.addAll(parsed);
                logger.info("{} pierres extraites de {}", parsed.size(), file.getName());
            } catch (IOException e) {
                logger.error("Erreur lors du parsing de {}", file.getName(), e);
            }
        }
        
        return gemstones;
    }
    
    /**
     * Importe les pierres depuis les fichiers HTML
     */
    private static List<Gemstone> importFromHtml(File directory) {
        List<Gemstone> gemstones = new ArrayList<>();
        HtmlParser parser = new HtmlParser(directory);
        
        // Trouver tous les fichiers HTML
        File[] htmlFiles = directory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".html") || name.toLowerCase().endsWith(".htm")
        );
        
        if (htmlFiles == null || htmlFiles.length == 0) {
            logger.warn("Aucun fichier HTML trouvé dans {}", directory.getAbsolutePath());
            return gemstones;
        }
        
        logger.info("Fichiers HTML trouvés: {}", htmlFiles.length);
        
        for (File file : htmlFiles) {
            try {
                logger.info("Traitement du fichier: {}", file.getName());
                List<Gemstone> parsed = parser.parseHtmlFile(file);
                gemstones.addAll(parsed);
                logger.info("{} pierres extraites de {}", parsed.size(), file.getName());
            } catch (IOException e) {
                logger.error("Erreur lors du parsing de {}", file.getName(), e);
            }
        }
        
        return gemstones;
    }
    
    /**
     * Fusionne les pierres précieuses en combinant les données d'Excel et HTML
     * Excel a priorité pour les données complètes
     */
    private static Map<String, Gemstone> mergeGemstones(List<Gemstone> gemstones) {
        Map<String, Gemstone> mergedMap = new HashMap<>();
        
        for (Gemstone gemstone : gemstones) {
            String articleId = gemstone.getArticleId();
            if (articleId == null || articleId.isEmpty()) {
                continue;
            }
            
            if (!mergedMap.containsKey(articleId)) {
                mergedMap.put(articleId, gemstone);
            } else {
                // Fusionner les données (garder les données non nulles)
                Gemstone existing = mergedMap.get(articleId);
                mergeGemstoneData(existing, gemstone);
            }
        }
        
        return mergedMap;
    }
    
    /**
     * Fusionne les données de deux pierres précieuses
     * Les données de 'source' complètent celles de 'target'
     */
    private static void mergeGemstoneData(Gemstone target, Gemstone source) {
        if (target.getTitle() == null && source.getTitle() != null) {
            target.setTitle(source.getTitle());
        }
        if (target.getDescription() == null && source.getDescription() != null) {
            target.setDescription(source.getDescription());
        }
        if (target.getPrice() == null && source.getPrice() != null) {
            target.setPrice(source.getPrice());
        }
        if (target.getQuantity() == null && source.getQuantity() != null) {
            target.setQuantity(source.getQuantity());
        }
        if (target.getPurchaseDate() == null && source.getPurchaseDate() != null) {
            target.setPurchaseDate(source.getPurchaseDate());
        }
        if (target.getOrderId() == null && source.getOrderId() != null) {
            target.setOrderId(source.getOrderId());
        }
        if (target.getTransactionId() == null && source.getTransactionId() != null) {
            target.setTransactionId(source.getTransactionId());
        }
        if (target.getListingUrl() == null && source.getListingUrl() != null) {
            target.setListingUrl(source.getListingUrl());
        }
        if (target.getImageUrl() == null && source.getImageUrl() != null) {
            target.setImageUrl(source.getImageUrl());
        }
        if (target.getLocalImagePath() == null && source.getLocalImagePath() != null) {
            target.setLocalImagePath(source.getLocalImagePath());
        }
    }
}
