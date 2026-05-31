package com.phishguard.demo.loader;

import com.phishguard.demo.model.UrlPhishing;
import com.phishguard.demo.repository.UrlPhishingRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

@Component
public class OpenPhishLoader implements ApplicationRunner {
    private static final String OPENPHISH_FEED = "https://openphish.com/feed.txt";
    private static final int    BATCH_SIZE      = 100;
    private final UrlPhishingRepository urlRepo;
    private final UrlHausLoader         urlHausLoader;

    public OpenPhishLoader(UrlPhishingRepository urlRepo,
                           UrlHausLoader urlHausLoader) {
        this.urlRepo       = urlRepo;
        this.urlHausLoader = urlHausLoader;
    }
    @Override
public void run(ApplicationArguments args) {
    long total = urlRepo.count();
    if (total == 0) {
        // Roda em background pra não travar a inicialização
        new Thread(() -> {
            System.out.println(">>> PhishGuard: carregando base em background...");
            carregarOpenPhish();
            urlHausLoader.carregar();
            System.out.println(">>> PhishGuard: base pronta com " + urlRepo.count() + " URLs.");
        }, "phish-loader").start();
    }
}
   public int carregarOpenPhish() {
    try {
        RestTemplate rest = new RestTemplate();
        String feed = rest.getForObject(OPENPHISH_FEED, String.class);
        if (feed == null || feed.isBlank()) return 0;

        String[] linhas = feed.split("\n");
        List<UrlPhishing> novas = new ArrayList<>();

        for (String linha : linhas) {
            if (novas.size() >= 1000) break; // limite por recarga

            String url = linha.trim();
            if (url.isBlank() || urlRepo.existsByUrl(url)) continue;

            String dominio = extrairDominio(url);
            if (!dominio.isBlank()) {
                novas.add(new UrlPhishing(url, dominio, "OpenPhish"));
            }
        }
        if (novas.isEmpty()) return 0;

        for (int i = 0; i < novas.size(); i += BATCH_SIZE) {
            urlRepo.saveAll(novas.subList(i, Math.min(i + BATCH_SIZE, novas.size())));
        }

        return novas.size();

    } catch (Exception e) {
        System.out.println(">>> OpenPhish erro: " + e.getMessage());
        return 0;
    }
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