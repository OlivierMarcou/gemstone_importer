package com.gemstone.importer.parser;

import com.gemstone.importer.model.Gemstone;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Parser pour les fichiers Excel contenant les données de pierres précieuses
 */
public class ExcelParser {
    private static final Logger logger = LoggerFactory.getLogger(ExcelParser.class);
    
    /**
     * Parse un fichier Excel et retourne la liste des pierres précieuses
     */
    public List<Gemstone> parseExcelFile(File excelFile) throws IOException {
        List<Gemstone> gemstones = new ArrayList<>();
        
        logger.info("Début du parsing du fichier Excel: {}", excelFile.getName());
        
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = 0;
            
            // Ignorer la première ligne (en-têtes)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                
                try {
                    Gemstone gemstone = parseRow(row);
                    if (gemstone != null && gemstone.getArticleId() != null) {
                        gemstones.add(gemstone);
                        rowCount++;
                    }
                } catch (Exception e) {
                    logger.warn("Erreur lors du parsing de la ligne {}: {}", i, e.getMessage());
                }
            }
            
            logger.info("Parsing terminé. {} pierres précieuses extraites.", rowCount);
        }
        
        return gemstones;
    }
    
    /**
     * Parse une ligne du fichier Excel et crée un objet Gemstone
     */
    private Gemstone parseRow(Row row) {
        Gemstone gemstone = new Gemstone();
        
        // Column 0: ID Article
        gemstone.setArticleId(getCellValueAsString(row.getCell(0)));
        
        // Column 1: Titre
        gemstone.setTitle(getCellValueAsString(row.getCell(1)));
        
        // Column 2: Description
        gemstone.setDescription(getCellValueAsString(row.getCell(2)));
        
        // Column 3: Prix
        Double price = getCellValueAsDouble(row.getCell(3));
        gemstone.setPrice(price);
        
        // Column 4: Quantité
        Double quantity = getCellValueAsDouble(row.getCell(4));
        if (quantity != null) {
            gemstone.setQuantity(quantity.intValue());
        }
        
        // Column 5: Date d'achat
        LocalDateTime purchaseDate = getCellValueAsDate(row.getCell(5));
        gemstone.setPurchaseDate(purchaseDate);
        
        // Column 6: ID Commande
        gemstone.setOrderId(getCellValueAsString(row.getCell(6)));
        
        // Column 7: ID Transaction
        gemstone.setTransactionId(getCellValueAsString(row.getCell(7)));
        
        // Column 8: État
        gemstone.setStatus(getCellValueAsString(row.getCell(8)));
        
        // Column 9: URL Annonce
        gemstone.setListingUrl(getCellValueAsString(row.getCell(9)));
        
        // Column 10: URL Image
        gemstone.setImageUrl(getCellValueAsString(row.getCell(10)));
        
        // Column 11: Nb Images
        Double imageCount = getCellValueAsDouble(row.getCell(11));
        if (imageCount != null) {
            gemstone.setImageCount(imageCount.intValue());
        }
        
        // Column 12: Nb Vidéos
        Double videoCount = getCellValueAsDouble(row.getCell(12));
        if (videoCount != null) {
            gemstone.setVideoCount(videoCount.intValue());
        }
        
        return gemstone;
    }
    
    /**
     * Récupère la valeur d'une cellule sous forme de String
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                // Convertir le nombre en string sans notation scientifique
                double value = cell.getNumericCellValue();
                if (value == (long) value) {
                    return String.valueOf((long) value);
                } else {
                    return String.valueOf(value);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }
    
    /**
     * Récupère la valeur d'une cellule sous forme de Double
     */
    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                try {
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) {
                        return null;
                    }
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    logger.warn("Impossible de convertir '{}' en nombre", cell.getStringCellValue());
                    return null;
                }
            case FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (IllegalStateException e) {
                    return null;
                }
            default:
                return null;
        }
    }
    
    /**
     * Récupère la valeur d'une cellule sous forme de LocalDateTime
     */
    private LocalDateTime getCellValueAsDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            } else if (cell.getCellType() == CellType.STRING) {
                // Essayer de parser une date ISO
                String dateStr = cell.getStringCellValue().trim();
                if (!dateStr.isEmpty()) {
                    return LocalDateTime.parse(dateStr.replace("Z", ""));
                }
            }
        } catch (Exception e) {
            logger.warn("Impossible de parser la date: {}", e.getMessage());
        }
        
        return null;
    }
}
