package dev.umbra.qr.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.umbra.qr.core.UrlSafety.Warning;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlSafetyTest {

    @Test
    void cleanHttpsHasNoWarnings() {
        var analysis = UrlSafety.analyze("https://example.org:8443/a").orElseThrow();

        assertEquals("example.org", analysis.host());
        assertEquals(Set.of(), analysis.warnings());
    }

    @Test
    void flagsPunycode() {
        var analysis = UrlSafety.analyze("https://xn--pple-43d.com/").orElseThrow();

        assertEquals(Set.of(Warning.PUNYCODE_HOST), analysis.warnings());
    }

    @Test
    void flagsNonAsciiHost() {
        var analysis = UrlSafety.analyze("https://аpple.com/").orElseThrow();

        assertTrue(analysis.warnings().contains(Warning.NON_ASCII_HOST));
    }

    @Test
    void flagsIpv6Literal() {
        var analysis = UrlSafety.analyze("https://[::1]:8080/").orElseThrow();

        assertEquals("[::1]", analysis.host());
        assertEquals(Set.of(Warning.IP_LITERAL_HOST), analysis.warnings());
    }

    @Test
    void credentialsHideTheRealHost() {
        var analysis = UrlSafety.analyze("https://bank.com@evil.example/").orElseThrow();

        assertEquals("evil.example", analysis.host());
        assertEquals(Set.of(Warning.EMBEDDED_CREDENTIALS), analysis.warnings());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.org", "https://", "example.org", "https://exa mple.org", "mailto:a@b.c"})
    void rejectsNonHttpOrMalformed(String url) {
        assertTrue(UrlSafety.analyze(url).isEmpty());
    }
}
