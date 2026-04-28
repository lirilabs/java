package com.example.LiveData.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Configuration
public class UpstoxConfig {

    @Value("${upstox.access.token}")
    private String accessToken;

    @Value("${upstox.instruments}")
    private String instruments;

   
    @Value("${upstox.chunk.size:100}")
    private int chunkSize;

    public String getAccessToken() { return accessToken; }

   
    public String getInstruments() { return instruments; }

    public List<List<String>> getInstrumentChunks() {
        List<String> all = Arrays.asList(instruments.split(","));
        List<List<String>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < all.size(); i += chunkSize) {
            chunks.add(all.subList(i, Math.min(i + chunkSize, all.size())));
        }
        return chunks;
    }

    public int getChunkSize() { return chunkSize; }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
