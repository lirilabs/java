package com.example.LiveData.controller;


import com.example.LiveData.entity.StockData;
import com.example.LiveData.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*") // Allows your frontend to connect without CORS issues
public class StockController {

    @Autowired
    private StockService stockService;

    /**
     * GET /api/stocks
     * Returns the full list of stocks with ATR, ADX, and ROC.
     */
    @GetMapping
    public List<StockData> getStocks() {
        return stockService.getLatestStocks();
    }

    /**
     * GET /api/stocks/filter?status=UP
     * Filters live stocks by status (UP/DOWN).
     */
    @GetMapping("/filter")
    public List<StockData> filterByStatus(@RequestParam String status) {
        return stockService.getLatestStocks().stream()
                .filter(s -> s.getStatus() != null && s.getStatus().equalsIgnoreCase(status))
                .toList();
    }

    /**
     * GET /api/stocks/search?q=RELIANCE
     * Search by symbol or instrument key.
     */
    @GetMapping("/search")
    public List<StockData> search(@RequestParam String q) {
        return stockService.search(q);
    }

    /**
     * GET /api/stocks/symbol/RELIANCE
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<StockData> getBySymbol(@PathVariable String symbol) {
        return stockService.getLatestStocks().stream()
                .filter(s -> s.getSymbol().equalsIgnoreCase(symbol))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/stocks/gainers?limit=5
     * Returns top N performing stocks based on change percentage.
     */
    @GetMapping("/gainers")
    public List<StockData> topGainers(@RequestParam(defaultValue = "10") int limit) {
        return stockService.getLatestStocks().stream()
                .sorted((a, b) -> Double.compare(b.getChangePercent(), a.getChangePercent()))
                .limit(limit)
                .toList();
    }

    /**
     * GET /api/stocks/losers?limit=5
     */
    @GetMapping("/losers")
    public List<StockData> topLosers(@RequestParam(defaultValue = "10") int limit) {
        return stockService.getLatestStocks().stream()
                .sorted((a, b) -> Double.compare(a.getChangePercent(), b.getChangePercent()))
                .limit(limit)
                .toList();
    }

    /**
     * GET /api/stocks/summary
     * Provides a high-level overview of market breadth.
     */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<StockData> all = stockService.getLatestStocks();
        long up   = all.stream().filter(s -> "UP".equals(s.getStatus())).count();
        long down = all.stream().filter(s -> "DOWN".equals(s.getStatus())).count();
        double avgPct = all.stream().mapToDouble(StockData::getChangePercent).average().orElse(0);

        return Map.of(
                "totalStocks", all.size(),
                "advancing", up,
                "declining", down,
                "marketSentiment", up > down ? "BULLISH" : "BEARISH",
                "avgMarketMove", Math.round(avgPct * 100.0) / 100.0 + "%"
        );
    }
}