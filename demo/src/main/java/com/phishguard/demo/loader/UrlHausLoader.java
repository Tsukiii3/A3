package com.phishguard.demo.loader;

import com.phishguard.demo.model.UrlPhishing;
import com.phishguard.demo.repository.UrlPhishingRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.ZipInputStream;

@Component
public class UrlHausLoader {

    private static final String URLHAUS_CSV =
        "https://urlhaus.abuse.ch/downloads/csv_recent/";
    private static final int BATCH_SIZE = 500;

    // Domínios genéricos que não devem ser salvos
    private static final Set<String> DOMINIOS_IGNORADOS = Set.of(
        "com.br", "org.br", "net.br", "gov.br", "edu.br",
        "com", "net", "org", "gov", "edu", "io", "co"
    );

    private final UrlPhishingRepository urlRepo;

    public UrlHausLoader(UrlPhishingRepository urlRepo) {
        this.urlRepo = urlRepo;
    }

    public int carregar() {
        try {
            RestTemplate rest = new RestTemplate();
            byte[] zipBytes = rest.getForObject(URLHAUS_CSV, byte[].class);
            if (zipBytes == null) return 0;

            List<UrlPhishing> novas = new ArrayList<>();
            Set<String> urlsNoBanco = new HashSet<>(urlRepo.findAllUrls());

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                zis.getNextEntry();
                BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                String linha;

                while ((linha = reader.readLine()) != null) {
                    if (novas.size() >= 2000) break;
                    if (linha.startsWith("#") || linha.isBlank()) continue;

                    String[] cols = parseCsvLine(linha);
                    if (cols.length < 3) continue;

                    String url    = cols[2].replace("\"", "").trim();
                    String status = cols.length > 3 ? cols[3].replace("\"", "").trim() : "";

                    if (!status.equalsIgnoreCase("online")) continue;
                    if (url.isBlank() || !url.startsWith("http")) continue;
                    if (urlsNoBanco.contains(url)) continue;

                    String dominio = extrairDominio(url);
                    if (dominio.isBlank() || DOMINIOS_IGNORADOS.contains(dominio)) continue;
                    if (dominio.length() < 4) continue; // evita domínios inválidos

                    String threat = cols.length > 5
                        ? cols[5].replace("\"", "").trim() : "URLhaus";

                    novas.add(new UrlPhishing(url, dominio, "URLhaus:" + threat));
                    urlsNoBanco.add(url);
                }
            }

            if (novas.isEmpty()) return 0;

            for (int i = 0; i < novas.size(); i += BATCH_SIZE) {
                urlRepo.saveAll(novas.subList(i, Math.min(i + BATCH_SIZE, novas.size())));
            }

            return novas.size();

        } catch (Exception e) {
            System.out.println(">>> URLhaus erro: " + e.getMessage());
            return 0;
        }
    }

    private String[] parseCsvLine(String linha) {
        List<String> cols   = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean dentroAspas = false;

        for (char c : linha.toCharArray()) {
            if (c == '"') {
                dentroAspas = !dentroAspas;
            } else if (c == ',' && !dentroAspas) {
                cols.add(campo.toString());
                campo.setLength(0);
            } else {
                campo.append(c);
            }
        }
        cols.add(campo.toString());
        return cols.toArray(new String[0]);
    }

    private String extrairDominio(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) return "";
            host = host.replace("www.", "").toLowerCase();
            String[] parts = host.split("\\.");
            if (parts.length < 2) return "";

            String ultDois = parts[parts.length-2] + "." + parts[parts.length-1];
            if (Set.of("com.br","org.br","net.br","gov.br","edu.br").contains(ultDois)) {
                if (parts.length >= 3) {
                    return parts[parts.length-3] + "." + ultDois;
                }
                return ""; 
            }

            return ultDois;
        } catch (Exception e) { return ""; }
    }
}