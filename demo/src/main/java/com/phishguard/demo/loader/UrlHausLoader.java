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
    private static final int BATCH_SIZE = 400;

    private final UrlPhishingRepository urlRepo;

    public UrlHausLoader(UrlPhishingRepository urlRepo) {
        this.urlRepo = urlRepo;
    }
    public void carregar() {
        System.out.println(">>> URLhaus: iniciando carga...");
        try {
            RestTemplate rest = new RestTemplate();
            byte[] zipBytes = rest.getForObject(URLHAUS_CSV, byte[].class);

            if (zipBytes == null) {
                System.out.println(">>> URLhaus: download falhou.");
                return;
            }
            System.out.println(">>> URLhaus: ZIP baixado ("
                + zipBytes.length / 1024 + " KB), descompactando...");
            List<UrlPhishing> batch = new ArrayList<>();
            int salvas     = 0;
            int ignoradas  = 0;
            int duplicadas = 0;

            try (ZipInputStream zis = new ZipInputStream(
                     new ByteArrayInputStream(zipBytes))) {

                zis.getNextEntry();

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zis));
                String linha;
                while ((linha = reader.readLine()) != null) {

                    if (linha.startsWith("#") || linha.isBlank()) continue;
                    String[] cols = parseCsvLine(linha);

                    if (cols.length < 3) continue;
                    String url    = cols[2].replace("\"", "").trim();
                    String status = cols.length > 3
                        ? cols[3].replace("\"", "").trim() : "";
                    if (!status.equalsIgnoreCase("online")) {
                        ignoradas++;
                        continue;
                    }
                    if (url.isBlank() || !url.startsWith("http")) {
                        ignoradas++;
                        continue;
                    }
                    // Evita duplicata
                    if (urlRepo.existsByUrl(url)) {
                        duplicadas++;
                        continue;
                    }
                    String dominio = extrairDominio(url);
                    if (dominio.isBlank()) continue;

                    String threat = cols.length > 5
                        ? cols[5].replace("\"", "").trim() : "URLhaus";

                    batch.add(new UrlPhishing(url, dominio, "URLhaus:" + threat));

                    if (batch.size() >= BATCH_SIZE) {
                        urlRepo.saveAll(batch);
                        salvas += batch.size();
                        batch.clear();
                        System.out.println(">>> URLhaus: " + salvas + " URLs salvas...");
                    }
                }
            }
            if (!batch.isEmpty()) {
                urlRepo.saveAll(batch);
                salvas += batch.size();
            }
            System.out.println(">>> URLhaus: carga concluída!");
            System.out.println(">>> Salvas: "     + salvas);
            System.out.println(">>> Ignoradas: "  + ignoradas);
            System.out.println(">>> Duplicadas: " + duplicadas);

        } catch (Exception e) {
            System.out.println(">>> URLhaus erro: " + e.getMessage());
            e.printStackTrace();
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
            host = host.replace("www.", "");
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }
            return host;
        } catch (Exception e) { return ""; }
    }
}