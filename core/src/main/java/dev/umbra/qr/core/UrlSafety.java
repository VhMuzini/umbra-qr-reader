package dev.umbra.qr.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Offline heuristics that flag links commonly used for phishing. Nothing here contacts the
 * network: the goal is to give the user enough information to decide before opening a link.
 */
public final class UrlSafety {

    /** A reason to look twice at a link before opening it. */
    public enum Warning {
        /** Plain {@code http}: traffic can be read or altered in transit. */
        INSECURE_HTTP,
        /** Host contains {@code xn--} labels, which may disguise a look-alike domain. */
        PUNYCODE_HOST,
        /** Host contains non-ASCII characters, which may disguise a look-alike domain. */
        NON_ASCII_HOST,
        /** Host is a raw IP address instead of a domain name. */
        IP_LITERAL_HOST,
        /** The link embeds a user name or password ({@code user@host}), a classic spoofing trick. */
        EMBEDDED_CREDENTIALS
    }

    /** Parsed pieces of an http(s) link that the UI needs. */
    public record Analysis(String host, Set<Warning> warnings) {
        public Analysis {
            warnings = Set.copyOf(warnings);
        }
    }

    private static final Pattern IPV4 =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");

    private UrlSafety() {}

    /**
     * Analyses an http(s) link.
     *
     * @return empty when the text is not a well-formed http(s) URL with a host
     */
    public static Optional<Analysis> analyze(String url) {
        final URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return Optional.empty();
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.empty();
        }

        // getHost() returns null for non-ASCII hosts, so parse the authority ourselves.
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty()) {
            return Optional.empty();
        }

        EnumSet<Warning> warnings = EnumSet.noneOf(Warning.class);
        if (scheme.equals("http")) {
            warnings.add(Warning.INSECURE_HTTP);
        }

        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            warnings.add(Warning.EMBEDDED_CREDENTIALS);
            authority = authority.substring(at + 1);
        }

        String host = stripPort(authority).toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            return Optional.empty();
        }

        if (host.startsWith("[") || IPV4.matcher(host).matches()) {
            warnings.add(Warning.IP_LITERAL_HOST);
        }
        if (host.startsWith("xn--") || host.contains(".xn--")) {
            warnings.add(Warning.PUNYCODE_HOST);
        }
        if (!host.chars().allMatch(c -> c < 0x80)) {
            warnings.add(Warning.NON_ASCII_HOST);
        }

        return Optional.of(new Analysis(host, warnings));
    }

    private static String stripPort(String authority) {
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            return end > 0 ? authority.substring(0, end + 1) : authority;
        }
        int colon = authority.lastIndexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }
}
