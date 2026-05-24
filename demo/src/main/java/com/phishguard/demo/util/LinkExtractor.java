package com.phishguard.demo.util;

import java.util.*;
import java.util.regex.*;

public class LinkExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?:https?://|www\\.)[\\w\\d.\\-#@:/?=!%&+_~]+|" +
        "(?<![\\w@])[\\w\\d\\-]+\\.(?:com\\.br|com|net|org|gov|edu|io|co)(?:/[\\w\\d./?=&%\\-]*)?(?=[\\s\"'<>]|$)",
        Pattern.CASE_INSENSITIVE
    );

    public static List<String> extrairLinks(String texto) {
        if (texto == null || texto.isBlank()) return List.of();

        Set<String> links = new LinkedHashSet<>(); // LinkedHashSet mantém ordem e sem duplicatas
        Matcher matcher = URL_PATTERN.matcher(texto);

        while (matcher.find()) {
            String url = normalizar(matcher.group());
            if (url != null && !url.isBlank()) {
                links.add(url);
            }
        }

        return new ArrayList<>(links);
    }

    private static String normalizar(String url) {
        if (url == null) return null;
        url = url.trim().replaceAll("[),.;!>\"']+$", ""); // remove pontuação no final
        if (url.toLowerCase().startsWith("www.")) {
            url = "http://" + url;
        }
        return url.isBlank() ? null : url;
    }
}