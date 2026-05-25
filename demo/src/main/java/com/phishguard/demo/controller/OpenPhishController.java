package com.phishguard.demo.controller;

import com.phishguard.demo.loader.UrlHausLoader;
import com.phishguard.demo.repository.UrlPhishingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.phishguard.demo.model.UrlPhishing;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class OpenPhishController {

    private final UrlPhishingRepository urlRepo;
    private final UrlHausLoader         urlHausLoader;

    public OpenPhishController(UrlPhishingRepository urlRepo,
                           UrlHausLoader urlHausLoader) {
        this.urlRepo       = urlRepo;
        this.urlHausLoader = urlHausLoader;
    }
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_urls_phishing", urlRepo.count());
        return ResponseEntity.ok(stats);
    }
    @PostMapping("/recarregar-openphish")
    public ResponseEntity<?> recarregarOpenPhish() {
        try {
            RestTemplate rest = new RestTemplate();
            String feed = rest.getForObject(
                "https://openphish.com/feed.txt", String.class);

            if (feed == null || feed.isBlank()) {
                return ResponseEntity.ok(Map.of("status", "Feed indisponível"));
            }
            String[] linhas = feed.split("\n");
            List<UrlPhishing> batch = new ArrayList<>();
            int novas = 0;

            for (String linha : linhas) {
                String url = linha.trim();
                if (url.isBlank() || urlRepo.existsByUrl(url)) continue;

                String dominio = extrairDominio(url);
                if (!dominio.isBlank()) {
                    batch.add(new UrlPhishing(url, dominio, "OpenPhish"));
                    novas++;
                }
            }
            if (!batch.isEmpty()) urlRepo.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                "status", "OK",
                "novas_urls", novas,
                "total_banco", urlRepo.count()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of("erro", e.getMessage()));
        }
    }
    @PostMapping("/popular-urls")
    public ResponseEntity<?> recarregarUrlHaus() {
        long antes = urlRepo.count();
        urlHausLoader.carregar();
        long depois = urlRepo.count();

        return ResponseEntity.ok(Map.of(
            "status",    "OK",
            "novas_urls", depois - antes,
            "total_banco", depois
        ));
    }
    private String extrairDominio(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) return "";
            host = host.replace("www.", "");
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }
            return host;
        } catch (Exception e) { return ""; }
    }
}