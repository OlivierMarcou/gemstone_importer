package com.gemstone.importer.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Représente une pierre précieuse avec toutes ses informations
 */
public class Gemstone {
    private String articleId;
    private String title;
    private String description;
    private Double price;
    private Integer quantity;
    private LocalDateTime purchaseDate;
    private String orderId;
    private String transactionId;
    private String status;
    private String listingUrl;
    private String imageUrl;
    private String localImagePath;
    private Integer imageCount;
    private Integer videoCount;
    
    // Informations extraites du titre
    private String gemType;        // Type de pierre (Sapphire, Ruby, Emerald, etc.)
    private Double carats;          // Poids en carats
    private String dimensions;      // Dimensions (ex: 8.8x7.5mm)
    private String shape;           // Forme (Heart, Oval, Pear, etc.)
    private String color;           // Couleur
    private String clarity;         // Clarté (VS, VVS, etc.)
    private String treatment;       // Traitement (Heated, Unheated, etc.)
    private String origin;          // Origine (Burma, Ceylon, Madagascar, etc.)
    
    public Gemstone() {}
    
    public Gemstone(String articleId) {
        this.articleId = articleId;
    }
    
    // Parse le titre pour extraire les informations
    public void parseTitle() {
        if (title == null || title.isEmpty()) {
            return;
        }
        
        String cleanTitle = title.replaceAll("💎", "").trim();
        
        // Extraction des carats (ex: 1.97ct, 0.33ct)
        if (cleanTitle.matches(".*\\d+\\.\\d+ct.*")) {
            String[] parts = cleanTitle.split("ct");
            if (parts.length > 0) {
                String caratStr = parts[0].replaceAll(".*?(\\d+\\.\\d+)$", "$1");
                try {
                    this.carats = Double.parseDouble(caratStr);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        
        // Extraction des dimensions (ex: 8.8x7.5mm)
        if (cleanTitle.matches(".*\\d+(\\.\\d+)?x\\d+(\\.\\d+)?(x\\d+(\\.\\d+)?)?mm.*")) {
            String dimPattern = "(\\d+(\\.\\d+)?x\\d+(\\.\\d+)?(x\\d+(\\.\\d+)?)?)mm";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(dimPattern);
            java.util.regex.Matcher matcher = pattern.matcher(cleanTitle);
            if (matcher.find()) {
                this.dimensions = matcher.group(1) + "mm";
            }
        }
        
        // Extraction de la forme
        String[] shapes = {"Heart", "Oval", "Pear", "Round", "Cushion", "Emerald Cut", "Princess", 
                          "Marquise", "Radiant", "Asscher", "Baguette", "Trillion", "Square"};
        for (String shape : shapes) {
            if (cleanTitle.toLowerCase().contains(shape.toLowerCase())) {
                this.shape = shape;
                break;
            }
        }
        
        // Extraction du type de pierre
        String[] gemTypes = {"Sapphire", "Ruby", "Emerald", "Diamond", "Spinel", "Tourmaline", 
                            "Topaz", "Aquamarine", "Garnet", "Amethyst", "Citrine", "Peridot",
                            "Zircon", "Tanzanite", "Opal", "Jade", "Turquoise", "Paraiba"};
        for (String type : gemTypes) {
            if (cleanTitle.toLowerCase().contains(type.toLowerCase())) {
                this.gemType = type;
                break;
            }
        }
        
        // Extraction de la clarté
        String[] clarities = {"FL", "IF", "VVS1", "VVS2", "VVS", "VS1", "VS2", "VS", "SI1", "SI2", "SI", "I1", "I2", "I3"};
        for (String clarity : clarities) {
            if (cleanTitle.contains(clarity)) {
                this.clarity = clarity;
                break;
            }
        }
        
        // Extraction du traitement
        if (cleanTitle.toLowerCase().contains("unheated")) {
            this.treatment = "Unheated";
        } else if (cleanTitle.toLowerCase().contains("heated")) {
            this.treatment = "Heated";
        } else if (cleanTitle.toLowerCase().contains("natural")) {
            this.treatment = "Natural";
        }
        
        // Extraction de l'origine
        String[] origins = {"Burma", "Myanmar", "Ceylon", "Sri Lanka", "Madagascar", "Tanzania", 
                           "Mozambique", "Colombia", "Brazil", "Africa", "Australia", "Thailand",
                           "Vietnam", "Cambodia", "Afghanistan", "Pakistan", "Kashmir"};
        for (String origin : origins) {
            if (cleanTitle.toLowerCase().contains(origin.toLowerCase())) {
                this.origin = origin;
                break;
            }
        }
        
        // Extraction de la couleur (simplifiée)
        if (cleanTitle.toLowerCase().contains("blue")) {
            this.color = "Blue";
        } else if (cleanTitle.toLowerCase().contains("red") || cleanTitle.toLowerCase().contains("ruby")) {
            this.color = "Red";
        } else if (cleanTitle.toLowerCase().contains("green")) {
            this.color = "Green";
        } else if (cleanTitle.toLowerCase().contains("yellow")) {
            this.color = "Yellow";
        } else if (cleanTitle.toLowerCase().contains("pink")) {
            this.color = "Pink";
        } else if (cleanTitle.toLowerCase().contains("purple")) {
            this.color = "Purple";
        } else if (cleanTitle.toLowerCase().contains("orange")) {
            this.color = "Orange";
        } else if (cleanTitle.toLowerCase().contains("champagne")) {
            this.color = "Champagne";
        } else if (cleanTitle.toLowerCase().contains("white")) {
            this.color = "White";
        }
    }
    
    // Getters et Setters
    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { 
        this.title = title;
        parseTitle(); // Parse automatiquement le titre
    }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    
    public LocalDateTime getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDateTime purchaseDate) { this.purchaseDate = purchaseDate; }
    
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getListingUrl() { return listingUrl; }
    public void setListingUrl(String listingUrl) { this.listingUrl = listingUrl; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getLocalImagePath() { return localImagePath; }
    public void setLocalImagePath(String localImagePath) { this.localImagePath = localImagePath; }
    
    public Integer getImageCount() { return imageCount; }
    public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }
    
    public Integer getVideoCount() { return videoCount; }
    public void setVideoCount(Integer videoCount) { this.videoCount = videoCount; }
    
    public String getGemType() { return gemType; }
    public void setGemType(String gemType) { this.gemType = gemType; }
    
    public Double getCarats() { return carats; }
    public void setCarats(Double carats) { this.carats = carats; }
    
    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }
    
    public String getShape() { return shape; }
    public void setShape(String shape) { this.shape = shape; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public String getClarity() { return clarity; }
    public void setClarity(String clarity) { this.clarity = clarity; }
    
    public String getTreatment() { return treatment; }
    public void setTreatment(String treatment) { this.treatment = treatment; }
    
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Gemstone gemstone = (Gemstone) o;
        return Objects.equals(articleId, gemstone.articleId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(articleId);
    }
    
    @Override
    public String toString() {
        return "Gemstone{" +
                "articleId='" + articleId + '\'' +
                ", title='" + title + '\'' +
                ", gemType='" + gemType + '\'' +
                ", carats=" + carats +
                ", shape='" + shape + '\'' +
                ", color='" + color + '\'' +
                ", price=" + price +
                ", origin='" + origin + '\'' +
                '}';
    }
}
