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
    private BigDecimal minCarats, maxCarats;
    private BigDecimal minPrice, maxPrice;
    private Gemstone.Status status;
    private String etsyListingRef;
    private String sortColumn;
    private boolean sortAscending;

    public SearchCriteria() { sortColumn = "id"; sortAscending = true; }

    public String getKeyword()           { return keyword; }
    public void setKeyword(String v)     { this.keyword = v; }
    public String getGemType()           { return gemType; }
    public void setGemType(String v)     { this.gemType = v; }
    public String getColor()             { return color; }
    public void setColor(String v)       { this.color = v; }
    public String getShape()             { return shape; }
    public void setShape(String v)       { this.shape = v; }
    public String getClarity()           { return clarity; }
    public void setClarity(String v)     { this.clarity = v; }
    public String getTreatment()         { return treatment; }
    public void setTreatment(String v)   { this.treatment = v; }
    public String getOrigin()            { return origin; }
    public void setOrigin(String v)      { this.origin = v; }
    public BigDecimal getMinCarats()     { return minCarats; }
    public void setMinCarats(BigDecimal v) { this.minCarats = v; }
    public BigDecimal getMaxCarats()     { return maxCarats; }
    public void setMaxCarats(BigDecimal v) { this.maxCarats = v; }
    public BigDecimal getMinPrice()      { return minPrice; }
    public void setMinPrice(BigDecimal v){ this.minPrice = v; }
    public BigDecimal getMaxPrice()      { return maxPrice; }
    public void setMaxPrice(BigDecimal v){ this.maxPrice = v; }
    public Gemstone.Status getStatus()   { return status; }
    public void setStatus(Gemstone.Status v) { this.status = v; }
    public String getEtsyListingRef()    { return etsyListingRef; }
    public void setEtsyListingRef(String v) { this.etsyListingRef = v; }
    public String getSortColumn()        { return sortColumn; }
    public void setSortColumn(String v)  { this.sortColumn = v; }
    public boolean isSortAscending()     { return sortAscending; }
    public void setSortAscending(boolean v) { this.sortAscending = v; }
}
