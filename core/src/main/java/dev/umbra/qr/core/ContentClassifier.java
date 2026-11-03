package dev.umbra.qr.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a raw QR payload into a {@link ScanContent}. Parsing is lenient: anything that cannot be
 * interpreted safely falls back to {@link ScanContent.PlainText}, never to an exception.
 */
public final class ContentClassifier {

    private ContentClassifier() {}

    public static ScanContent classify(String raw) {
        String text = raw.strip();
        String lower = text.toLowerCase(Locale.ROOT);

        Optional<? extends ScanContent> parsed;
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            parsed = parseUrl(raw, text);
        } else if (lower.startsWith("wifi:")) {
            parsed = parseWifi(raw, text.substring("wifi:".length()));
        } else if (lower.startsWith("mailto:")) {
            parsed = parseMailto(raw, text.substring("mailto:".length()));
        } else if (lower.startsWith("matmsg:")) {
            parsed = parseMatmsg(raw, text.substring("matmsg:".length()));
        } else if (lower.startsWith("tel:")) {
            parsed = parsePhone(raw, text.substring("tel:".length()));
        } else if (lower.startsWith("smsto:")) {
            parsed = parseSmsTo(raw, text.substring("smsto:".length()));
        } else if (lower.startsWith("sms:")) {
            parsed = parseSmsUri(raw, text.substring("sms:".length()));
        } else if (lower.startsWith("geo:")) {
            parsed = parseGeo(raw, text.substring("geo:".length()));
        } else {
            parsed = Optional.empty();
        }
        return parsed.map(ScanContent.class::cast).orElseGet(() -> new ScanContent.PlainText(raw));
    }

    private static Optional<ScanContent.Url> parseUrl(String raw, String text) {
        return UrlSafety.analyze(text)
                .map(analysis -> new ScanContent.Url(raw, text, analysis.host(), analysis.warnings()));
    }

    /** Parses {@code T:WPA;S:ssid;P:password;H:true;;} with backslash escapes. */
    private static Optional<ScanContent.Wifi> parseWifi(String raw, String body) {
        Map<String, String> fields = parseFields(body);
        String ssid = fields.get("S");
        if (ssid == null || ssid.isEmpty()) {
            return Optional.empty();
        }
        ScanContent.WifiSecurity security = ScanContent.WifiSecurity.parse(fields.get("T"));
        Optional<String> password =
                Optional.ofNullable(fields.get("P")).filter(p -> !p.isEmpty());
        if (security == ScanContent.WifiSecurity.NONE) {
            password = Optional.empty();
        }
        boolean hidden = "true".equalsIgnoreCase(fields.get("H"));
        return Optional.of(new ScanContent.Wifi(raw, ssid, password, security, hidden));
    }

    /** Parses {@code TO:address;SUB:subject;BODY:body;;}. */
    private static Optional<ScanContent.Email> parseMatmsg(String raw, String body) {
        Map<String, String> fields = parseFields(body);
        String to = fields.get("TO");
        if (to == null || to.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(
                new ScanContent.Email(
                        raw,
                        to.strip(),
                        nonBlank(fields.get("SUB")),
                        nonBlank(fields.get("BODY"))));
    }

    /** Parses {@code address?subject=...&body=...}. */
    private static Optional<ScanContent.Email> parseMailto(String raw, String rest) {
        int q = rest.indexOf('?');
        String address = decode(q >= 0 ? rest.substring(0, q) : rest).strip();
        if (address.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> query = q >= 0 ? parseQuery(rest.substring(q + 1)) : Map.of();
        return Optional.of(
                new ScanContent.Email(
                        raw, address, nonBlank(query.get("subject")), nonBlank(query.get("body"))));
    }

    private static Optional<ScanContent.Phone> parsePhone(String raw, String number) {
        String cleaned = decode(number).strip();
        return cleaned.isEmpty() ? Optional.empty() : Optional.of(new ScanContent.Phone(raw, cleaned));
    }

    /** Parses {@code number:message}. */
    private static Optional<ScanContent.Sms> parseSmsTo(String raw, String rest) {
        int colon = rest.indexOf(':');
        String number = (colon >= 0 ? rest.substring(0, colon) : rest).strip();
        if (number.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> message = colon >= 0 ? nonBlank(rest.substring(colon + 1)) : Optional.empty();
        return Optional.of(new ScanContent.Sms(raw, number, message));
    }

    /** Parses {@code number?body=message}. */
    private static Optional<ScanContent.Sms> parseSmsUri(String raw, String rest) {
        int q = rest.indexOf('?');
        String number = decode(q >= 0 ? rest.substring(0, q) : rest).strip();
        if (number.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> query = q >= 0 ? parseQuery(rest.substring(q + 1)) : Map.of();
        return Optional.of(new ScanContent.Sms(raw, number, nonBlank(query.get("body"))));
    }

    /** Parses {@code lat,lon[,alt][;params][?q=query]}. */
    private static Optional<ScanContent.Geo> parseGeo(String raw, String rest) {
        int q = rest.indexOf('?');
        String coordinates = q >= 0 ? rest.substring(0, q) : rest;
        int semicolon = coordinates.indexOf(';');
        if (semicolon >= 0) {
            coordinates = coordinates.substring(0, semicolon);
        }
        String[] parts = coordinates.split(",");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            double latitude = Double.parseDouble(parts[0].strip());
            double longitude = Double.parseDouble(parts[1].strip());
            if (!Double.isFinite(latitude)
                    || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90
                    || Math.abs(longitude) > 180) {
                return Optional.empty();
            }
            Map<String, String> query = q >= 0 ? parseQuery(rest.substring(q + 1)) : Map.of();
            return Optional.of(
                    new ScanContent.Geo(raw, latitude, longitude, nonBlank(query.get("q"))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Splits {@code K:value;K:value;;} into a map, honouring backslash escapes. Keys are
     * upper-cased; the first occurrence of a key wins.
     */
    static Map<String, String> parseFields(String body) {
        Map<String, String> fields = new HashMap<>();
        for (String field : splitUnescaped(body, ';')) {
            int colon = field.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = field.substring(0, colon).strip().toUpperCase(Locale.ROOT);
            fields.putIfAbsent(key, field.substring(colon + 1));
        }
        return fields;
    }

    /**
     * Splits on {@code separator} unless escaped with a backslash. Escape sequences are resolved
     * in the returned parts. Only the key prefix of a field can contain an unescaped colon, so
     * resolving escapes here is safe for {@link #parseFields(String)}.
     */
    private static List<String> splitUnescaped(String text, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                current.append(text.charAt(++i));
            } else if (c == separator) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, eq)).toLowerCase(Locale.ROOT);
            params.putIfAbsent(key, decode(pair.substring(eq + 1)));
        }
        return params;
    }

    /** Percent-decodes URI components; a literal {@code +} is kept as-is (RFC 6068). */
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Malformed percent-encoding: show the text exactly as scanned.
            return value;
        }
    }

    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }
}
