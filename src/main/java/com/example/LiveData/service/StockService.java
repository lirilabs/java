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

    private final ConcurrentHashMap<String, StockData> stockCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedList<double[]>> priceHistoryMap = new ConcurrentHashMap<>();
    private static final int ADX_PERIOD = 14;

    private static final String UPSTOX_QUOTE_URL = "https://api.upstox.com/v2/market-quote/quotes?symbol=";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    @Scheduled(fixedRateString = "${upstox.refresh.interval}")
    public void fetchUpdateAndBroadcast() {
        try {
            List<StockData> stocks = fetchAllChunks();
            if (!stocks.isEmpty()) {
                stockRepository.saveAll(stocks);
                stocks.forEach(s -> stockCache.put(s.getInstrumentKey(), s));
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
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return parseResponse(response.getBody());
        } catch (HttpClientErrorException.Unauthorized e) {
            System.err.println("🔑 Token Expired.");
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
            String instrumentKey = entry.getKey();
            JsonNode node = entry.getValue();

            double ltp   = node.path("last_price").asDouble();
            double open  = node.path("ohlc").path("open").asDouble();
            double high  = node.path("ohlc").path("high").asDouble();
            double low   = node.path("ohlc").path("low").asDouble();
            double netChangeApi = node.path("net_change").asDouble();
            double close = (netChangeApi != 0) ? (ltp - netChangeApi) : node.path("ohlc").path("close").asDouble();
            if (close == 0) close = ltp;

            // FIX: Using LTP for history ensures ADX sees 5-second movements
            priceHistoryMap.putIfAbsent(instrumentKey, new LinkedList<>());
            LinkedList<double[]> history = priceHistoryMap.get(instrumentKey);
            history.add(new double[]{ltp, ltp, ltp});

            if (history.size() > ADX_PERIOD + 1) history.removeFirst();
            double liveAdxValue = calculateLiveADX(history);

            double rocValue = (open != 0) ? ((ltp - open) / open) * 100 : 0;
            double tr = Math.max(high - low, Math.max(Math.abs(high - close), Math.abs(low - close)));

            StockData s = new StockData();
            s.setInstrumentKey(instrumentKey);
            s.setSymbol(node.path("symbol").asText());
            s.setCurrentPrice(ltp);
            s.setVolume(node.path("volume").asLong());
            s.setAtr(Math.round(tr * 100.0) / 100.0);
            s.setAdx(liveAdxValue);
            s.setRoc(Math.round(rocValue * 100.0) / 100.0);
            s.setTimestamp(now);
            s.setOpenPrice(open);
            s.setHighPrice(high);
            s.setLowPrice(low);
            s.setClosePrice(close);
            s.setChange(netChangeApi);
            s.setChangePercent(close != 0 ? Math.round((netChangeApi / close) * 10000.0) / 100.0 : 0);
            s.setStatus(netChangeApi >= 0 ? "UP" : "DOWN");

            stocks.add(s);
        }
        return stocks;
    }

    private double calculateLiveADX(LinkedList<double[]> history) {
        if (history.size() < ADX_PERIOD) return 25.0; // Wait for buffer

        double trSum = 0, plusDmSum = 0, minusDmSum = 0;
        for (int i = 1; i < history.size(); i++) {
            double[] curr = history.get(i);
            double[] prev = history.get(i - 1);

            double tr = Math.max(curr[0] - curr[1], Math.max(Math.abs(curr[0] - prev[2]), Math.abs(curr[1] - prev[2])));
            trSum += (tr == 0) ? 0.01 : tr;

            double upMove = curr[0] - prev[0];
            double downMove = prev[1] - curr[1];
            if (upMove > downMove && upMove > 0) plusDmSum += upMove;
            if (downMove > upMove && downMove > 0) minusDmSum += downMove;
        }

        if (trSum <= 0.1) return 25.0; // Stagnant market safety

        double plusDI = 100 * (plusDmSum / trSum);
        double minusDI = 100 * (minusDmSum / trSum);
        double sum = plusDI + minusDI;
        double dx = (sum == 0) ? 0 : 100 * (Math.abs(plusDI - minusDI) / sum);

        double finalAdx = Math.round(dx * 100.0) / 100.0;
        return (finalAdx <= 0 || finalAdx >= 99) ? 25.0 : finalAdx;
    }

    public List<StockData> getLatestStocks() { return new ArrayList<>(stockCache.values()); }

    // This method resolves the "cannot find symbol" error
    public List<StockData> search(String query) {
        if (query == null || query.isEmpty()) return getLatestStocks();
        String q = query.toLowerCase();
        return stockCache.values().stream()
                .filter(s -> s.getSymbol().toLowerCase().contains(q)
                        || s.getInstrumentKey().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }
}
