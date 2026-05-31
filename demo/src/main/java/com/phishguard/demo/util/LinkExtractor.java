package com.phishguard.demo.util;

import java.util.*;
import java.util.regex.*;

public class LinkExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?:https?://|www\\.)[\\w\\d.\\-#@:/?=!%&+_~]+|" +
        "(?<![\\w@])[\\w\\d\\-]+\\.(?:com\\.br|com|net|org|gov|edu|io|co)(?:/[\\w\\d./?=&%\\-]*)?(?=[\\s\"'<>]|$)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> IGNORE_DOMAINS = Set.of(
        "w3.org", "xmlsoap.org", "schemas.microsoft.com",
        "googleapis.com", "gstatic.com", "googleusercontent.com",
        "mimecast.com", "spf.protection.outlook.com",
        "purl.org", "dublincore.org", "ogp.me"
    );
    public static List<String> extrairLinks(String texto) {
        if (texto == null || texto.isBlank()) return List.of();

        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(texto);

        while (matcher.find()) {
            String url = normalizar(matcher.group());
            if (url != null && !url.isBlank() && !deveIgnorar(url)) {
                links.add(url);
            }
        }

        return new ArrayList<>(links);
    }
    private static boolean deveIgnorar(String url) {
        String lower = url.toLowerCase();
        return IGNORE_DOMAINS.stream().anyMatch(lower::contains);
    }
    private static String normalizar(String url) {
        if (url == null) return null;
        url = url.trim().replaceAll("[),.;!>\"']+$", "");
        if (url.toLowerCase().startsWith("www.")) {
            url = "http://" + url;
        }
        return url.isBlank() ? null : url;
    }
}