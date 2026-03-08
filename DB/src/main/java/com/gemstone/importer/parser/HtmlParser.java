package com.gemstone.importer.parser;

import com.gemstone.importer.model.Gemstone;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser pour les fichiers HTML contenant les rapports de pierres précieuses
 */
public class HtmlParser {
    private static final Logger logger = LoggerFactory.getLogger(HtmlParser.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    private final File htmlDirectory;
    
    public HtmlParser(File htmlDirectory) {
        this.htmlDirectory = htmlDirectory;
    }
    
    /**
     * Parse un fichier HTML et retourne la liste des pierres précieuses
     */
    public List<Gemstone> parseHtmlFile(File htmlFile) throws IOException {
        List<Gemstone> gemstones = new ArrayList<>();
        
        logger.info("Début du parsing du fichier HTML: {}", htmlFile.getName());
        
        Document doc = Jsoup.parse(htmlFile, "UTF-8");
        Elements itemCards = doc.select("div.item-card");
        
        logger.info("Nombre d'articles trouvés: {}", itemCards.size());
        
        for (Element card : itemCards) {
            try {
                Gemstone gemstone = parseItemCard(card);
                if (gemstone != null && gemstone.getArticleId() != null) {
                    gemstones.add(gemstone);
                }
            } catch (Exception e) {
                logger.warn("Erreur lors du parsing d'un article: {}", e.getMessage());
            }
        }
        
        logger.info("Parsing terminé. {} pierres précieuses extraites.", gemstones.size());
        
        return gemstones;
    }
    
    /**
     * Parse une carte d'article HTML et crée un objet Gemstone
     */
    private Gemstone parseItemCard(Element card) {
        Gemstone gemstone = new Gemstone();
        
        // Extraire le titre
        Element titleElement = card.selectFirst("h3.item-title");
        if (titleElement != null) {
            gemstone.setTitle(titleElement.text().trim());
        }
        
        // Extraire le prix
        Element priceElement = card.selectFirst("div.item-price");
        if (priceElement != null) {
            try {
                String priceText = priceElement.text().trim();
                gemstone.setPrice(Double.parseDouble(priceText));
            } catch (NumberFormatException e) {
                logger.warn("Impossible de parser le prix: {}", priceElement.text());
            }
        }
        
        // Extraire la date
        Element dateElement = card.selectFirst("div.item-date");
        if (dateElement != null) {
            try {
                String dateText = dateElement.text().trim();
                LocalDate date = LocalDate.parse(dateText, DATE_FORMATTER);
                gemstone.setPurchaseDate(date.atStartOfDay());
            } catch (Exception e) {
                logger.warn("Impossible de parser la date: {}", dateElement.text());
            }
        }
        
        // Extraire les détails
        Elements detailElements = card.select("div.item-detail");
        for (Element detail : detailElements) {
            String text = detail.text();
            
            if (text.startsWith("ID:")) {
                String id = text.substring(3).trim();
                gemstone.setArticleId(id);
            } else if (text.startsWith("Quantité:")) {
                try {
                    String quantityStr = text.substring(9).trim();
                    gemstone.setQuantity(Integer.parseInt(quantityStr));
                } catch (NumberFormatException e) {
                    logger.warn("Impossible de parser la quantité: {}", text);
                }
            } else if (text.startsWith("Commande:")) {
                String orderId = text.substring(9).trim();
                gemstone.setOrderId(orderId);
            }
        }
        
        // Extraire l'URL de l'annonce
        Element linkElement = card.selectFirst("a.btn-primary");
        if (linkElement != null) {
            gemstone.setListingUrl(linkElement.attr("href"));
        }
        
        // Extraire l'URL de l'image
        Element imageElement = card.selectFirst("div.item-images img");
        if (imageElement != null) {
            String imageSrc = imageElement.attr("src");
            
            // Si c'est un chemin local, essayer de trouver l'image
            if (imageSrc.startsWith("images/")) {
                File imageFile = new File(htmlDirectory, imageSrc);
                if (imageFile.exists()) {
                    gemstone.setLocalImagePath(imageFile.getAbsolutePath());
                }
            } else {
                gemstone.setImageUrl(imageSrc);
            }
        }
        
        return gemstone;
    }
}
