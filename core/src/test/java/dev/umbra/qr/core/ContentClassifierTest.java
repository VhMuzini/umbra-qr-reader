package dev.umbra.qr.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.umbra.qr.core.ScanContent.WifiSecurity;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ContentClassifierTest {

    @Test
    void classifiesHttpsUrl() {
        var url = assertInstanceOf(
                ScanContent.Url.class, ContentClassifier.classify("  https://Example.org/path?q=1 \n"));

        assertEquals("https://Example.org/path?q=1", url.link());
        assertEquals("example.org", url.host());
        assertTrue(url.warnings().isEmpty());
        assertEquals("  https://Example.org/path?q=1 \n", url.raw());
    }

    @Test
    void urlWithSpacesFallsBackToPlainText() {
        assertInstanceOf(
                ScanContent.PlainText.class, ContentClassifier.classify("https://exa mple.org"));
    }

    @Test
    void parsesWifiWithEscapes() {
        var wifi = assertInstanceOf(
                ScanContent.Wifi.class,
                ContentClassifier.classify("WIFI:T:WPA;S:My\\;Net;P:pa\\:ss\\\\word;H:true;;"));

        assertEquals("My;Net", wifi.ssid());
        assertEquals(Optional.of("pa:ss\\word"), wifi.password());
        assertEquals(WifiSecurity.WPA, wifi.security());
        assertTrue(wifi.hidden());
    }

    @Test
    void openWifiHasNoPassword() {
        var wifi = assertInstanceOf(
                ScanContent.Wifi.class, ContentClassifier.classify("WIFI:S:Cafe;T:nopass;P:;;"));

        assertEquals("Cafe", wifi.ssid());
        assertEquals(Optional.empty(), wifi.password());
        assertEquals(WifiSecurity.NONE, wifi.security());
        assertFalse(wifi.hidden());
    }

    @Test
    void wifiWithoutSsidIsPlainText() {
        assertInstanceOf(ScanContent.PlainText.class, ContentClassifier.classify("WIFI:T:WPA;P:x;;"));
    }

    @Test
    void parsesMailto() {
        var email = assertInstanceOf(
                ScanContent.Email.class,
                ContentClassifier.classify("mailto:crypt@example.org?subject=Hello%20there&body=a+b"));

        assertEquals("crypt@example.org", email.address());
        assertEquals(Optional.of("Hello there"), email.subject());
        assertEquals(Optional.of("a+b"), email.body());
    }

    @Test
    void parsesMatmsg() {
        var email = assertInstanceOf(
                ScanContent.Email.class,
                ContentClassifier.classify("MATMSG:TO:crypt@example.org;SUB:Hi;BODY:Line\\;two;;"));

        assertEquals("crypt@example.org", email.address());
        assertEquals(Optional.of("Hi"), email.subject());
        assertEquals(Optional.of("Line;two"), email.body());
    }

    @Test
    void parsesPhone() {
        var phone = assertInstanceOf(
                ScanContent.Phone.class, ContentClassifier.classify("tel:+55 11 99999-0000"));

        assertEquals("+55 11 99999-0000", phone.number());
    }

    @Test
    void parsesSmsTo() {
        var sms = assertInstanceOf(
                ScanContent.Sms.class, ContentClassifier.classify("SMSTO:+5511999990000:Boa noite: tudo bem?"));

        assertEquals("+5511999990000", sms.number());
        assertEquals(Optional.of("Boa noite: tudo bem?"), sms.message());
    }

    @Test
    void parsesSmsUri() {
        var sms = assertInstanceOf(
                ScanContent.Sms.class, ContentClassifier.classify("sms:+123?body=hi%20there"));

        assertEquals("+123", sms.number());
        assertEquals(Optional.of("hi there"), sms.message());
    }

    @Test
    void parsesGeo() {
        var geo = assertInstanceOf(
                ScanContent.Geo.class, ContentClassifier.classify("geo:-23.5505,-46.6333,10?q=Catedral%20da%20S%C3%A9"));

        assertEquals(-23.5505, geo.latitude());
        assertEquals(-46.6333, geo.longitude());
        assertEquals(Optional.of("Catedral da Sé"), geo.query());
    }

    @ParameterizedTest
    @ValueSource(strings = {"geo:91,0", "geo:abc,def", "geo:10", "geo:NaN,0"})
    void invalidGeoIsPlainText(String raw) {
        assertInstanceOf(ScanContent.PlainText.class, ContentClassifier.classify(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "hello", "mailto:", "tel:", "SMSTO:", "ftp://example.org", "javascript:alert(1)"})
    void unknownOrEmptyIsPlainText(String raw) {
        var text = assertInstanceOf(ScanContent.PlainText.class, ContentClassifier.classify(raw));

        assertEquals(raw, text.raw());
    }

    @Test
    void malformedPercentEncodingIsKeptVerbatim() {
        var phone = assertInstanceOf(ScanContent.Phone.class, ContentClassifier.classify("tel:%zz12"));

        assertEquals("%zz12", phone.number());
    }

    @Test
    void urlWarningsAreExposed() {
        var url = assertInstanceOf(
                ScanContent.Url.class, ContentClassifier.classify("http://user@10.0.0.1/login"));

        assertEquals(
                Set.of(
                        UrlSafety.Warning.INSECURE_HTTP,
                        UrlSafety.Warning.EMBEDDED_CREDENTIALS,
                        UrlSafety.Warning.IP_LITERAL_HOST),
                url.warnings());
    }
}
