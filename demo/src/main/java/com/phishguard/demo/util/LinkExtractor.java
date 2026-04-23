package com.phishguard.demo.util;

import java.util.*;
import java.util.regex.*;

public class LinkExtractor {
    public static List<String> extrairLinks(String texto) {
        Set<String> links = new HashSet<>();
        if (texto == null || texto.isEmpty()) return new ArrayList<>();

        // Captura protocolos e domínios comuns (www. ou .com / .com.br)
        String urlPatternString = "(?:https?://|www\\.)[\\w\\d.#@/?=!%&-]+|(?<=\\s|^)[\\w\\d-]+\\.(?:com|com\\.br|net|org|gov|edu)(?=\\s|$)";
        Pattern pattern = Pattern.compile(urlPatternString, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(texto);

        while (matcher.find()) {
            links.add(normalizar(matcher.group()));
        }

        return new ArrayList<>(links);
    }
    private static String normalizar(String url) {
        if (url == null) return "";
        url = url.trim().replaceAll("[),.;!]+$", "");
        if (url.toLowerCase().startsWith("www.")) url = "http://" + url;
        return url;
    }
}