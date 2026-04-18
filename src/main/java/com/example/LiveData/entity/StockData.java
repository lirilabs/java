package com.example.LiveData.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Entity
@Table(name = "stocks")
// This ensures the JSON output follows your exact requested order
@JsonPropertyOrder({ "currentPrice", "volume", "atr", "adx", "roc", "timestamp" })
public class StockData {

    @Id
    private String instrumentKey;
    private String symbol;

    // --- Requested Order Fields ---
    private double currentPrice;
    private long volume;
    private double atr;
    private double adx;
    private double roc;
    private String timestamp;

    // --- Additional Price Data ---
    private double highPrice;
    private double lowPrice;
    private double openPrice;
    private double closePrice;
    private double change;
    private double changePercent;
    private String status;

    // Standard no-args constructor for Hibernate
    public StockData() {
    }

    // -------- Getters & Setters --------

    public String getInstrumentKey() { return instrumentKey; }
    public void setInstrumentKey(String instrumentKey) { this.instrumentKey = instrumentKey; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public double getAtr() { return atr; }
    public void setAtr(double atr) { this.atr = atr; }

    public double getAdx() { return adx; }
    public void setAdx(double adx) { this.adx = adx; }

    public double getRoc() { return roc; }
    public void setRoc(double roc) { this.roc = roc; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public double getHighPrice() { return highPrice; }
    public void setHighPrice(double highPrice) { this.highPrice = highPrice; }

    public double getLowPrice() { return lowPrice; }
    public void setLowPrice(double lowPrice) { this.lowPrice = lowPrice; }

    public double getOpenPrice() { return openPrice; }
    public void setOpenPrice(double openPrice) { this.openPrice = openPrice; }

    public double getClosePrice() { return closePrice; }
    public void setClosePrice(double closePrice) { this.closePrice = closePrice; }

    public double getChange() { return change; }
    public void setChange(double change) { this.change = change; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "StockData [Symbol=" + symbol + ", Price=" + currentPrice +
                ", Volume=" + volume + ", ATR=" + atr + ", ADX=" + adx +
                ", ROC=" + roc + "%, Time=" + timestamp + "]";
    }
}