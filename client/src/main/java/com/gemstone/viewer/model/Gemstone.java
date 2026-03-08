package com.gemstone.viewer.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Gemstone {

    public enum Status {
        IN_STOCK("in_stock", "En stock", new java.awt.Color(52, 211, 153)),
        USED("used", "Utilisée", new java.awt.Color(251, 191, 36)),
        DAMAGED("damaged", "Abîmée", new java.awt.Color(239, 68, 68)),
        UNAVAILABLE("unavailable", "Indisponible", new java.awt.Color(107, 114, 128));

        private final String dbValue;
        private final String label;
        private final java.awt.Color color;

        Status(String dbValue, String label, java.awt.Color color) {
            this.dbValue = dbValue;
            this.label = label;
            this.color = color;
        }

        public String getDbValue() { return dbValue; }
        public String getLabel() { return label; }
        public java.awt.Color getColor() { return color; }

        public static Status fromDb(String dbVal) {
            if (dbVal == null) return IN_STOCK;
            return switch (dbVal) {
                case "used" -> USED;
                case "damaged" -> DAMAGED;
                case "unavailable" -> UNAVAILABLE;
                default -> IN_STOCK;
            };
        }

        @Override
        public String toString() { return label; }
    }

    private int id;
    private String articleId;
    private String title;
    private String description;
    private BigDecimal price;
    private Integer quantity;
    private LocalDateTime purchaseDate;
    private String orderId;
    private String transactionId;
    private String listingUrl;
    private String imageUrl;
    private String localImagePath;
    private String gemType;
    private BigDecimal carats;
    private String dimensions;
    private String shape;
    private String color;
    private String clarity;
    private String treatment;
    private String origin;
    private Status status = Status.IN_STOCK;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Gemstone() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDateTime getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDateTime purchaseDate) { this.purchaseDate = purchaseDate; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getListingUrl() { return listingUrl; }
    public void setListingUrl(String listingUrl) { this.listingUrl = listingUrl; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getLocalImagePath() { return localImagePath; }
    public void setLocalImagePath(String localImagePath) { this.localImagePath = localImagePath; }
    public String getGemType() { return gemType; }
    public void setGemType(String gemType) { this.gemType = gemType; }
    public BigDecimal getCarats() { return carats; }
    public void setCarats(BigDecimal carats) { this.carats = carats; }
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
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status != null ? status : Status.IN_STOCK; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return title != null ? title : "Gemstone #" + id; }
}
