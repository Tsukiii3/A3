package com.phishguard.demo.service;

import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.*;

import java.util.*;

@Service
public class SafeBrowsingService {

    private final String API_KEY = "AIzaSyBBp9fEEE4BIw7CHqA4Gwt4wY38Kvha8_g";

    // Domínios que o Google Safe Browsing NÃO deve checar
    private final List<String> trustedDomains = List.of(
            "facebook.com", "facebookmail.com", "linkedin.com", 
            "google.com", "github.com", "microsoft.com"
    );

    public boolean isMalicious(List<String> urls) {
        if (urls == null || urls.isEmpty()) return false;

        // FILTRO: Remove links que pertencem a domínios conhecidos
        List<String> filteredUrls = urls.stream()
                .filter(url -> trustedDomains.stream().noneMatch(url::contains))
                .toList();

        // Se não sobrou nenhum link suspeito após o filtro, retorna seguro
        if (filteredUrls.isEmpty()) return false;

        String endpoint = "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=" + API_KEY;
        RestTemplate restTemplate = new RestTemplate();

        List<Map<String, String>> threatEntries = new ArrayList<>();
        for (String url : filteredUrls) {
            threatEntries.add(Map.of("url", url));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("client", Map.of("clientId", "phishguard", "clientVersion", "1.0"));
        body.put("threatInfo", Map.of(
                "threatTypes", List.of("MALWARE", "SOCIAL_ENGINEERING"),
                "platformTypes", List.of("ANY_PLATFORM"),
                "threatEntryTypes", List.of("URL"),
                "threatEntries", threatEntries
        ));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body), String.class);
            // O SafeBrowsing retorna {} quando está limpo. Se houver conteúdo, é malicioso.
            return response.getBody() != null && response.getBody().contains("matches");
        } catch (Exception e) {
            System.err.println("Erro SafeBrowsing: " + e.getMessage());
            return false; 
        }
    }
}