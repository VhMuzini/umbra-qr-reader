# Umbra

A gothic, privacy-first QR code reader for Android. It reads codes and stays offline: it has no
network access, no analytics and no tracking.

Written in Java 21. Available in English, Brazilian Portuguese and French.

## Features

- **Live scanning** through the camera, inside a gothic lancet-arch viewfinder.
- **Read from image**: decode a QR code from a picture via the system photo picker.
- **Torch** toggle on devices with a flash.
- **Typed results** with suitable actions:

  | Content | Shown | Action |
  | --- | --- | --- |
  | Link (`http`, `https`) | Domain, full link, safety warnings | Open in browser (after confirmation) |
  | Wi-Fi (`WIFI:`) | Network, security, masked password | Copy password |
  | E-mail (`mailto:`, `MATMSG:`) | Address, subject, message | Write e-mail |
  | Phone (`tel:`) | Number | Open dialer |
  | SMS (`SMSTO:`, `sms:`) | Number, message | Open messaging app |
  | Location (`geo:`) | Coordinates, place | Open map |
  | Anything else | Text | Copy, share |

- **Link safety checks**, done offline: flags plain `http`, raw IP addresses, `xn--` (punycode)
  and non-ASCII domains, and credentials hidden before an `@`.
- **Per-app language**: pick English, Português (Brasil), Français or the system default from the app.

## Privacy

| Guarantee | How |
| --- | --- |
| No network access | The `INTERNET` permission is removed from the merged manifest with `tools:node="remove"`, so no library can add it back. |
| One permission only | `CAMERA`. The image picker needs no storage permission. |
| No history | Results exist only in memory while shown. Nothing is written to disk. |
| No backups | `allowBackup="false"` and data extraction rules exclude everything. |
| No third-party services | No analytics, no crash reporting, no Google Play Services, no Firebase. |
| No font downloads | Fonts are bundled, not fetched with Downloadable Fonts. |
| No dependency metadata blob | The Google-encrypted dependency info block is left out of the APK. |
| Sensitive clipboard | Copied Wi-Fi passwords are flagged as sensitive, which hides them from clipboard previews. |
| Explicit hand-off | Links open in your browser only after a confirmation dialog that shows the destination. |
| Local-only logs | Logs go to logcat on the device and never contain scanned content. Release builds strip debug logs. |

Check the permissions yourself:

```bash
$ANDROID_HOME/build-tools/<version>/aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

The only system permission listed is `android.permission.CAMERA`. The
`dev.umbra.qr.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` entry is an AndroidX-internal,
signature-level permission that grants no capabilities.

## Build with Docker

The only requirement is Docker. No JDK or Android SDK on the host.

```bash
docker/build.sh                  # tests, lint, debug APK and release APK
docker/build.sh assembleDebug    # any Gradle tasks
```

The script builds a toolchain image (`docker/Dockerfile`: JDK 21 and the Android SDK, with the
command-line tools pinned by checksum), mounts the repository, and runs Gradle as your user. APKs
land in `app/build/outputs/apk/` as usual, and Gradle caches persist in `.docker-cache/`, so later
runs are fast.

The image is `linux/amd64` because Android build tools only ship x86_64 Linux binaries; on ARM
hosts (such as Apple Silicon) Docker emulates it, which is slower.

## Requirements (local build)

- JDK 21 (Gradle runs on it; make sure `JAVA_HOME` points to it)
- Android SDK with platform 37 (Gradle downloads it if missing and its license is accepted)
- Device or emulator running Android 8.0 (API 26) or newer

## Build and run

```bash
./gradlew assembleDebug                  # build the debug APK
./gradlew installDebug                   # install on a connected device
./gradlew assembleRelease                # minified, unsigned release APK
```

## Tests

```bash
./gradlew :core:test                     # JVM tests: decoding, parsing, link safety
./gradlew :app:testDebugUnitTest         # Android module unit tests
./gradlew :app:lintDebug                 # lint, including API checks on :core
./gradlew :app:connectedDebugAndroidTest # instrumented tests (needs a device or emulator)
```

## Project structure

```
core/   Pure Java 21 library with no Android dependencies, tested on the JVM.
        QrDecoder (ZXing wrapper), ContentClassifier, ScanContent, UrlSafety.
app/    Android app (Java): CameraX scanning, result sheet, gothic theme, translations.
```

Logic that does not need Android lives in `core`, so most behaviour is covered by fast JVM tests.

## Adding a language

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<qualifier>/strings.xml`
   and translate every string that is not marked `translatable="false"`.
2. Add the locale to `app/src/main/res/xml/locales_config.xml`.
3. Add the qualifier to `localeFilters` in `app/build.gradle.kts`.
4. Add an entry to `app/src/main/res/menu/language.xml` and handle it in
   `ScannerActivity#showLanguageMenu`.

## License

Umbra is licensed under the [GNU General Public License v2.0](LICENSE).

### Third-party components

| Component | License |
| --- | --- |
| [ZXing](https://github.com/zxing/zxing) | Apache 2.0 |
| AndroidX (AppCompat, CameraX) and Material Components | Apache 2.0 |
| [Cinzel](https://github.com/NDISCOVER/Cinzel-Font), [EB Garamond](https://github.com/octaviopardo/EBGaramond12), [UnifrakturCook](https://unifraktur.sourceforge.net/) fonts | SIL Open Font License 1.1 (texts in `app/src/main/assets/licenses/`) |

The fonts are subset to Latin characters and, for the variable fonts, instanced to static weights.
