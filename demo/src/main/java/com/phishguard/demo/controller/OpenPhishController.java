package com.phishguard.demo.controller;

import com.phishguard.demo.loader.UrlHausLoader;
import com.phishguard.demo.model.EmailGolpistas;
import com.phishguard.demo.model.UrlPhishing;
import com.phishguard.demo.repository.EmailGolpistaRepository;
import com.phishguard.demo.repository.UrlPhishingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class OpenPhishController {

    private final UrlPhishingRepository   urlRepo;
    private final UrlHausLoader           urlHausLoader;
    private final EmailGolpistaRepository emailGolpistaRepo;

    public OpenPhishController(UrlPhishingRepository urlRepo,
                               UrlHausLoader urlHausLoader,
                               EmailGolpistaRepository emailGolpistaRepo) {
        this.urlRepo            = urlRepo;
        this.urlHausLoader      = urlHausLoader;
        this.emailGolpistaRepo  = emailGolpistaRepo;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(Map.of(
            "total_urls_phishing", urlRepo.count(),
            "total_emails_golpistas", emailGolpistaRepo.count()
        ));
    }

    @PostMapping("/popular-urls")
    public ResponseEntity<?> popularUrls() {
        try {
            long antes  = urlRepo.count();
            int novas   = urlHausLoader.carregar();
            long depois = urlRepo.count();

            return ResponseEntity.ok(Map.of(
                "status",      "OK",
                "novas_urls",  novas,
                "total_banco", depois
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/recarregar-openphish")
    public ResponseEntity<?> recarregarOpenPhish() {
        try {
            RestTemplate rest = new RestTemplate();
            String feed = rest.getForObject("https://openphish.com/feed.txt", String.class);

            if (feed == null || feed.isBlank()) {
                return ResponseEntity.ok(Map.of("status", "Feed indisponível"));
            }

            List<UrlPhishing> batch = new ArrayList<>();
            for (String linha : feed.split("\n")) {
                String url = linha.trim();
                if (url.isBlank() || urlRepo.existsByUrl(url)) continue;
                String dominio = extrairDominio(url);
                if (!dominio.isBlank()) {
                    batch.add(new UrlPhishing(url, dominio, "OpenPhish"));
                }
            }
            if (!batch.isEmpty()) urlRepo.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                "status",      "OK",
                "novas_urls",  batch.size(),
                "total_banco", urlRepo.count()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    @Transactional
    @DeleteMapping("/limpar-dominio")
    public ResponseEntity<?> limparDominio(@RequestParam String dominio) {
        emailGolpistaRepo.deleteByDominio(dominio);
        List<UrlPhishing> urls = urlRepo.findByDominio(dominio);
        urlRepo.deleteAll(urls);
        return ResponseEntity.ok(Map.of("status", "removido", "dominio", dominio));
    }

    @Transactional
    @DeleteMapping("/remover-remetente")
    public ResponseEntity<?> removerRemetente(@RequestParam String email) {
        if (emailGolpistaRepo.existsByRemetente(email)) {
            emailGolpistaRepo.deleteByRemetente(email);
            return ResponseEntity.ok(Map.of("status", "removido", "email", email));
        }
        return ResponseEntity.ok(Map.of("status", "não encontrado"));
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