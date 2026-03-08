package com.gemstone.viewer.model;

import java.math.BigDecimal;

public class SearchCriteria {
    private String keyword;
    private String gemType;
    private String color;
    private String shape;
    private String clarity;
    private String treatment;
    private String origin;
    private BigDecimal minCarats;
    private BigDecimal maxCarats;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Gemstone.Status status; // null = all
    private String sortColumn;
    private boolean sortAscending;

    public SearchCriteria() {
        sortColumn = "id";
        sortAscending = true;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getGemType() { return gemType; }
    public void setGemType(String gemType) { this.gemType = gemType; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getShape() { return shape; }
    public void setShape(String shape) { this.shape = shape; }
    public String getClarity() { return clarity; }
    public void setClarity(String clarity) { this.clarity = clarity; }
    public String getTreatment() { return treatment; }
    public void setTreatment(String treatment) { this.treatment = treatment; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public BigDecimal getMinCarats() { return minCarats; }
    public void setMinCarats(BigDecimal minCarats) { this.minCarats = minCarats; }
    public BigDecimal getMaxCarats() { return maxCarats; }
    public void setMaxCarats(BigDecimal maxCarats) { this.maxCarats = maxCarats; }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    public Gemstone.Status getStatus() { return status; }
    public void setStatus(Gemstone.Status status) { this.status = status; }
    public String getSortColumn() { return sortColumn; }
    public void setSortColumn(String sortColumn) { this.sortColumn = sortColumn; }
    public boolean isSortAscending() { return sortAscending; }
    public void setSortAscending(boolean sortAscending) { this.sortAscending = sortAscending; }
}
