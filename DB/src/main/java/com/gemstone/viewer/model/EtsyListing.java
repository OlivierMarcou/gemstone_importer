package com.gemstone.viewer.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EtsyListing {

    public enum JewelryType {
        RING("ring", "Bague", "💍"),
        EARRINGS("earrings", "Boucles d'oreilles", "✨"),
        PENDANT("pendant", "Pendentif", "🔮"),
        NECKLACE("necklace", "Collier", "📿"),
        BRACELET("bracelet", "Bracelet", "⌚"),
        BROOCH("brooch", "Broche", "🌸"),
        ANKLET("anklet", "Bracelet cheville", "🦶"),
        TIARA("tiara", "Tiare", "👑"),
        OTHER("other", "Autre", "💎");

        private final String dbValue, label, emoji;
        JewelryType(String db, String label, String emoji) { this.dbValue=db; this.label=label; this.emoji=emoji; }
        public String getDbValue() { return dbValue; }
        public String getLabel()   { return label; }
        public String getEmoji()   { return emoji; }
        public static JewelryType fromDb(String v) {
            if (v == null) return OTHER;
            for (JewelryType t : values()) if (t.dbValue.equals(v)) return t;
            return OTHER;
        }
        @Override public String toString() { return emoji + " " + label; }
    }

    public enum ListingStatus {
        DRAFT("draft", "Brouillon", new java.awt.Color(107,114,128)),
        ACTIVE("active", "Active", new java.awt.Color(52,211,153)),
        SOLD("sold", "Vendue", new java.awt.Color(99,102,241)),
        INACTIVE("inactive", "Désactivée", new java.awt.Color(251,191,36)),
        EXPIRED("expired", "Expirée", new java.awt.Color(239,68,68));

        private final String dbValue, label;
        private final java.awt.Color color;
        ListingStatus(String db, String label, java.awt.Color color) { this.dbValue=db; this.label=label; this.color=color; }
        public String getDbValue()     { return dbValue; }
        public String getLabel()       { return label; }
        public java.awt.Color getColor() { return color; }
        public static ListingStatus fromDb(String v) {
            if (v == null) return DRAFT;
            for (ListingStatus s : values()) if (s.dbValue.equals(v)) return s;
            return DRAFT;
        }
        @Override public String toString() { return label; }
    }

    private int id;
    private String listingRef;
    private String etsyListingId;
    private String title;
    private String description;
    private JewelryType jewelryType = JewelryType.OTHER;
    private ListingStatus status = ListingStatus.DRAFT;
    private BigDecimal price;
    private String currency = "EUR";
    private Integer quantity;
    private String etsyUrl;
    private String[] tags;
    private String[] materials;
    private String metalType;
    private String metalColor;
    private String ringSize;
    private BigDecimal weightGrams;
    private String mainImageUrl;
    private String mainImagePath;
    private Integer processingDays;
    private Integer views;
    private Integer favorites;
    private Integer salesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime listedAt;
    private LocalDateTime soldAt;

    // Joined fields from view
    private int gemstoneCount;
    private String gemTypesUsed;

    // Linked gemstones (loaded separately)
    private List<Gemstone> gemstones = new ArrayList<>();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getListingRef() { return listingRef; }
    public void setListingRef(String listingRef) { this.listingRef = listingRef; }
    public String getEtsyListingId() { return etsyListingId; }
    public void setEtsyListingId(String v) { this.etsyListingId = v; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public JewelryType getJewelryType() { return jewelryType; }
    public void setJewelryType(JewelryType jewelryType) { this.jewelryType = jewelryType != null ? jewelryType : JewelryType.OTHER; }
    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status != null ? status : ListingStatus.DRAFT; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getEtsyUrl() { return etsyUrl; }
    public void setEtsyUrl(String etsyUrl) { this.etsyUrl = etsyUrl; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public String[] getMaterials() { return materials; }
    public void setMaterials(String[] materials) { this.materials = materials; }
    public String getMetalType() { return metalType; }
    public void setMetalType(String metalType) { this.metalType = metalType; }
    public String getMetalColor() { return metalColor; }
    public void setMetalColor(String metalColor) { this.metalColor = metalColor; }
    public String getRingSize() { return ringSize; }
    public void setRingSize(String ringSize) { this.ringSize = ringSize; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public void setWeightGrams(BigDecimal weightGrams) { this.weightGrams = weightGrams; }
    public String getMainImageUrl() { return mainImageUrl; }
    public void setMainImageUrl(String mainImageUrl) { this.mainImageUrl = mainImageUrl; }
    public String getMainImagePath() { return mainImagePath; }
    public void setMainImagePath(String mainImagePath) { this.mainImagePath = mainImagePath; }
    public Integer getProcessingDays() { return processingDays; }
    public void setProcessingDays(Integer processingDays) { this.processingDays = processingDays; }
    public Integer getViews() { return views; }
    public void setViews(Integer views) { this.views = views; }
    public Integer getFavorites() { return favorites; }
    public void setFavorites(Integer favorites) { this.favorites = favorites; }
    public Integer getSalesCount() { return salesCount; }
    public void setSalesCount(Integer salesCount) { this.salesCount = salesCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getListedAt() { return listedAt; }
    public void setListedAt(LocalDateTime listedAt) { this.listedAt = listedAt; }
    public LocalDateTime getSoldAt() { return soldAt; }
    public void setSoldAt(LocalDateTime soldAt) { this.soldAt = soldAt; }
    public int getGemstoneCount() { return gemstoneCount; }
    public void setGemstoneCount(int gemstoneCount) { this.gemstoneCount = gemstoneCount; }
    public String getGemTypesUsed() { return gemTypesUsed; }
    public void setGemTypesUsed(String gemTypesUsed) { this.gemTypesUsed = gemTypesUsed; }
    public List<Gemstone> getGemstones() { return gemstones; }
    public void setGemstones(List<Gemstone> gemstones) { this.gemstones = gemstones != null ? gemstones : new ArrayList<>(); }

    @Override public String toString() { return listingRef + " — " + (title != null ? title : ""); }
}
