package com.example.LiveData.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.LiveData.config.UpstoxConfig;
import com.example.LiveData.entity.StockData;
import com.example.LiveData.repository.StockRepository;
import com.example.LiveData.websocket.StockWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class StockService {

    @Autowired
    private UpstoxConfig upstoxConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockWebSocketHandler webSocketHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache for quick UI lookups and WebSocket broadcasts
    private final ConcurrentHashMap<String, StockData> stockCache = new ConcurrentHashMap<>();

    private static final String UPSTOX_QUOTE_URL = "https://api.upstox.com/v2/market-quote/quotes?symbol=";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    /**
     * Core loop: Fetches data, calculates indicators, saves to DB, and broadcasts via WS.
     */
    @Scheduled(fixedRateString = "${upstox.refresh.interval}")
    public void fetchUpdateAndBroadcast() {
        try {
            List<StockData> stocks = fetchAllChunks();

            if (!stocks.isEmpty()) {
                // 1. Persist to PostgreSQL
                stockRepository.saveAll(stocks);

                // 2. Update Local Cache
                stocks.forEach(s -> stockCache.put(s.getInstrumentKey(), s));

                // 3. Broadcast to Web Clients
                String json = objectMapper.writeValueAsString(new ArrayList<>(stockCache.values()));
                webSocketHandler.broadcastStockData(json);

                System.out.println("✅ Sync Success: " + stocks.size() + " stocks updated at " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
        } catch (Exception e) {
            System.err.println("❌ Service Error: " + e.getMessage());
        }
    }

    private List<StockData> fetchAllChunks() {
        List<StockData> all = new ArrayList<>();
        List<List<String>> chunks = upstoxConfig.getInstrumentChunks();

        for (List<String> chunk : chunks) {
            try {
                String keys = String.join(",", chunk);
                all.addAll(fetchChunk(keys));
            } catch (Exception e) {
                System.err.println("⚠️ Chunk Error: " + e.getMessage());
            }
        }
        return all;
    }

    private List<StockData> fetchChunk(String instrumentKeys) throws Exception {
        String url = UPSTOX_QUOTE_URL + instrumentKeys;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + upstoxConfig.getAccessToken());
        headers.set("Accept", "application/json");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return parseResponse(response.getBody());
        } catch (HttpClientErrorException.Unauthorized e) {
            System.err.println("🔑 Token Expired: Update upstox.access.token in properties.");
            return Collections.emptyList();
        }
    }

    private List<StockData> parseResponse(String body) throws Exception {
        List<StockData> stocks = new ArrayList<>();
        JsonNode data = objectMapper.readTree(body).path("data");
        String now = LocalDateTime.now().format(FORMATTER);

        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode node = entry.getValue();

            // Mapping from API
            double ltp   = node.path("last_price").asDouble();
            double open  = node.path("ohlc").path("open").asDouble();
            double high  = node.path("ohlc").path("high").asDouble();
            double low   = node.path("ohlc").path("low").asDouble();

            // ADDED: Use net_change from API to calculate true Previous Close
            double netChangeApi = node.path("net_change").asDouble();
            double close = (netChangeApi != 0) ? (ltp - netChangeApi) : node.path("ohlc").path("close").asDouble();

            // Final fallback if close is still 0
            if (close == 0) {
                close = ltp;
            }

            long volume  = node.path("volume").asLong();

            // Calculations
            double rocValue = (open != 0) ? ((ltp - open) / open) * 100 : 0;
            double tr = Math.max(high - low, Math.max(Math.abs(high - close), Math.abs(low - close)));

            // Fixed calculation using the API net change
            double netChange = netChangeApi;
            double pctChange = (close != 0) ? (netChange / close) * 100 : 0;

            StockData s = new StockData();
            s.setInstrumentKey(entry.getKey());
            s.setSymbol(node.path("symbol").asText());
            s.setCurrentPrice(ltp);
            s.setVolume(volume);
            s.setAtr(Math.round(tr * 100.0) / 100.0);
            s.setAdx(25.0);
            s.setRoc(Math.round(rocValue * 100.0) / 100.0);
            s.setTimestamp(now);

            // Extended Price Data
            s.setOpenPrice(open);
            s.setHighPrice(high);
            s.setLowPrice(low);
            s.setClosePrice(close);
            s.setChange(netChange);
            s.setChangePercent(Math.round(pctChange * 100.0) / 100.0);
            s.setStatus(netChange >= 0 ? "UP" : "DOWN");

            stocks.add(s);
        }
        return stocks;
    }

    // --- Helper Methods for Controllers ---

    public List<StockData> getLatestStocks() {
        return new ArrayList<>(stockCache.values());
    }

    public Optional<StockData> getByInstrumentKey(String key) {
        return Optional.ofNullable(stockCache.get(key));
    }

    public List<StockData> getTopGainers(int limit) {
        return stockCache.values().stream()
                .sorted((a, b) -> Double.compare(b.getChangePercent(), a.getChangePercent()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<StockData> getTopLosers(int limit) {
        return stockCache.values().stream()
                .sorted(Comparator.comparingDouble(StockData::getChangePercent))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<StockData> search(String query) {
        if (query == null || query.isEmpty()) return getLatestStocks();
        String q = query.toLowerCase();
        return stockCache.values().stream()
                .filter(s -> s.getSymbol().toLowerCase().contains(q)
                        || s.getInstrumentKey().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }
}
