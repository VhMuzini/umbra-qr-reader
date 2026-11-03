package dev.umbra.qr.core;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Typed interpretation of the text stored in a QR code.
 *
 * <p>Every variant keeps the original {@link #raw()} payload so the user can always copy exactly
 * what was scanned, regardless of how it was interpreted.
 */
public sealed interface ScanContent {

    /** The untouched payload decoded from the QR code. */
    String raw();

    /**
     * An {@code http} or {@code https} link.
     *
     * @param link the payload without surrounding whitespace, ready to be opened
     */
    record Url(String raw, String link, String host, Set<UrlSafety.Warning> warnings)
            implements ScanContent {
        public Url {
            Objects.requireNonNull(raw);
            Objects.requireNonNull(link);
            Objects.requireNonNull(host);
            warnings = Set.copyOf(warnings);
        }
    }

    /** Wi-Fi credentials in the {@code WIFI:} format. */
    record Wifi(
            String raw,
            String ssid,
            Optional<String> password,
            WifiSecurity security,
            boolean hidden)
            implements ScanContent {
        public Wifi {
            Objects.requireNonNull(raw);
            Objects.requireNonNull(ssid);
            Objects.requireNonNull(password);
            Objects.requireNonNull(security);
        }
    }

    /** An e-mail draft from {@code mailto:} or {@code MATMSG:}. */
    record Email(String raw, String address, Optional<String> subject, Optional<String> body)
            implements ScanContent {
        public Email {
            Objects.requireNonNull(raw);
            Objects.requireNonNull(address);
            Objects.requireNonNull(subject);
            Objects.requireNonNull(body);
        }
    }

    /** A phone number from {@code tel:}. */
    record Phone(String raw, String number) implements ScanContent {
        public Phone {
            Objects.requireNonNull(raw);
            Objects.requireNonNull(number);
        }
    }

    /** A text message from {@code SMSTO:} or {@code sms:}. */
    record Sms(String raw, String number, Optional<String> message) implements ScanContent {
        public Sms {
            Objects.requireNonNull(raw);
            Objects.requireNonNull(number);
            Objects.requireNonNull(message);
        }
    }

    /** A location from {@code geo:}. */
    record Geo(String raw, double latitude, double longitude, Optional<String> query)
            implements ScanContent {
        public Geo {
            Objects.requireNonNull(raw);
            Objects.requireNonNull(query);
        }
    }

    /** Anything that does not match a known format. */
    record PlainText(String raw) implements ScanContent {
        public PlainText {
            Objects.requireNonNull(raw);
        }
    }

    /** Authentication type advertised by a Wi-Fi QR code. */
    enum WifiSecurity {
        NONE,
        WEP,
        WPA,
        SAE;

        static WifiSecurity parse(String value) {
            return switch (value == null ? "" : value.trim().toUpperCase(Locale.ROOT)) {
                case "WEP" -> WEP;
                case "WPA", "WPA2", "WPA/WPA2" -> WPA;
                case "SAE", "WPA3" -> SAE;
                default -> NONE;
            };
        }
    }
}
