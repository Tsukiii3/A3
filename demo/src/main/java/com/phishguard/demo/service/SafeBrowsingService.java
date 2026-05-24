package com.phishguard.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class SafeBrowsingService {

    @Value("${google.safebrowsing.api.key:}")
    private String apiKey;

    private static final String API_URL =
        "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=";

    private static final Set<String> KNOWN_MALICIOUS_PATTERNS = Set.of(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl",
        "phishing", "malware", "virus", "trojan"
    );

    /**
     * Verifica se algum dos links é malicioso.
     * Usa a API do Google Safe Browsing se a key estiver configurada,
     * caso contrário usa verificação local por padrões conhecidos.
     */
    public boolean isMalicious(List<String> urls) {
        if (urls == null || urls.isEmpty()) return false;

        if (apiKey != null && !apiKey.isBlank()) {
            return checkWithGoogleApi(urls);
        }

        return checkLocalPatterns(urls);
    }

    private boolean checkWithGoogleApi(List<String> urls) {
        try {
            RestTemplate rest = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            List<Map<String, Object>> threatEntries = new ArrayList<>();
            for (String url : urls) {
                threatEntries.add(Map.of("url", url));
            }

            Map<String, Object> body = Map.of(
                "client", Map.of(
                    "clientId", "phishguard",
                    "clientVersion", "1.0"
                ),
                "threatInfo", Map.of(
                    "threatTypes", List.of(
                        "MALWARE",
                        "SOCIAL_ENGINEERING",
                        "UNWANTED_SOFTWARE",
                        "POTENTIALLY_HARMFUL_APPLICATION"
                    ),
                    "platformTypes",    List.of("ANY_PLATFORM"),
                    "threatEntryTypes", List.of("URL"),
                    "threatEntries",    threatEntries
                )
            );

            ResponseEntity<Map> res = rest.postForEntity(
                API_URL + apiKey,
                new HttpEntity<>(body, headers),
                Map.class
            );

            // Se a resposta tiver "matches", há ameaça
            if (res.getBody() != null && res.getBody().containsKey("matches")) {
                return true;
            }

        } catch (Exception e) {
            System.out.println("SafeBrowsing API erro: " + e.getMessage());
            // Fallback para verificação local
            return checkLocalPatterns(urls);
        }

        return false;
    }

    /**
     * Fallback: verifica padrões suspeitos localmente sem precisar de API key.
     */
    private boolean checkLocalPatterns(List<String> urls) {
        for (String url : urls) {
            String lower = url.toLowerCase();
            for (String pattern : KNOWN_MALICIOUS_PATTERNS) {
                if (lower.contains(pattern)) return true;
            }
            // URL com IP direto é suspeita
            if (lower.matches("https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) {
                return true;
            }
        }
        return false;
    }
}