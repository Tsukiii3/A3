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

        if (total > 0) {
            System.out.println(">>> Feeds: banco já populado com "
                + total + " URLs. Pulando carga inicial.");
            return;
        }
        System.out.println(">>> Feeds: banco vazio, iniciando carga completa...");
        carregarOpenPhish();
        urlHausLoader.carregar();

        System.out.println(">>> Carga total finalizada: "
            + urlRepo.count() + " URLs no banco.");
    }
    private void carregarOpenPhish() {
        System.out.println(">>> OpenPhish: iniciando...");
        try {
            RestTemplate rest = new RestTemplate();
            String feed = rest.getForObject(OPENPHISH_FEED, String.class);

            if (feed == null || feed.isBlank()) {
                System.out.println(">>> OpenPhish: feed indisponível.");
                return;
            }
            String[] linhas = feed.split("\n");
            System.out.println(">>> OpenPhish: " + linhas.length + " URLs no feed.");

            List<UrlPhishing> batch = new ArrayList<>();
            int salvas = 0;

            for (String linha : linhas) {
                String url = linha.trim();
                if (url.isBlank() || urlRepo.existsByUrl(url)) continue;

                String dominio = extrairDominio(url);
                if (dominio.isBlank()) continue;

                batch.add(new UrlPhishing(url, dominio, "OpenPhish"));

                if (batch.size() >= BATCH_SIZE) {
                    urlRepo.saveAll(batch);
                    salvas += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                urlRepo.saveAll(batch);
                salvas += batch.size();
            }

            System.out.println(">>> OpenPhish: " + salvas + " URLs salvas.");
        } catch (Exception e) {
            System.out.println(">>> OpenPhish erro: " + e.getMessage());
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