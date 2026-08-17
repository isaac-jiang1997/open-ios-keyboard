# Open Phone Layout Keyboard

An open-source Android keyboard focused on matching the native iPhone keyboard's key positions and typing flow as closely as Android allows.

[中文说明](README.zh-CN.md)

## Why This Project Exists

Android keyboards vary widely across brands, ROMs, screen sizes, and input methods. That flexibility is powerful, but it also means users who move between iPhone and Android often pay a small switching cost every time they type.

Open Phone Layout Keyboard takes a different route: it treats the native iOS keyboard layout as the reference design. The goal is not to clone Apple assets, but to preserve familiar key positions, row rhythm, language switching, symbol layers, and Chinese/English typing flow so users can move across devices with less friction.

## Goals

- Match the iPhone key layout: every visible key is represented by a layout spec with fixed relative row geometry.
- Keep the visual style similar, while using original code and original assets.
- Recreate core iPhone typing behavior: shift, caps lock, delete repeat, symbol layers, space, return, and input-method switching.
- Stay fully open source and easy to audit.

## Highlights

- iPhone-style key geometry for Android, including QWERTY rows, bottom-row rhythm, number/symbol layers, and an iOS-style accessory bar.
- Chinese and English modes in one keyboard, reducing the need to keep multiple Android keyboards enabled.
- Simplified Chinese 26-key pinyin and 9-key/T9 pinyin layouts.
- Offline Chinese input through `librime`/Rime with bundled dictionaries, plus a small legacy TSV fallback.
- Local candidate learning and custom phrases stored only in app-private storage.
- No network permission, telemetry, ads, cloud candidates, or remote configuration.
- Original Android implementation and original launcher assets; no copied Apple code, fonts, private assets, or artwork.

## Current Status

This repository contains the first Android IME prototype:

- Native Android input method service.
- Custom drawn keyboard view.
- iPhone-style English QWERTY rows.
- V1 Chinese/English input modes.
- Chinese input routed through a replaceable engine boundary; the current APK uses offline `librime`/Rime with Rime Ice (`rime-ice`) as the primary simplified Chinese dictionary. The old TSV pinyin engine is kept only as a fallback.
- Candidate strip for Chinese pinyin.
- Number and symbol layers.
- Shift, double-tap caps lock, backspace repeat, space, return, and globe/input-method switching.
- Simple launcher screen for enabling and switching to the keyboard.
- Android system IME switcher avoidance: because Open Phone Layout already contains Chinese, English, 9-key, full-keyboard, emoji, and symbol modes, the recommended setup is to keep only this keyboard enabled. If another Android keyboard remains enabled, Android may draw its own system input-method switcher in the navigation bar; third-party IMEs cannot remove that system control from inside the APK.

The layout currently uses iPhone-like relative geometry. Exact device-by-device matching should be calibrated with screenshots and measured key frames before calling it pixel-perfect.

See [V1 Chinese/English Architecture](docs/V1_CHINESE_ENGLISH_ARCHITECTURE.zh-CN.md) and [Chinese Input Engine Research](docs/CHINESE_INPUT_ENGINE_RESEARCH.zh-CN.md) for the Chinese input engine decision and development logic.

See [Security and Privacy](docs/SECURITY_PRIVACY.zh-CN.md) for the offline-only privacy rules, local learning policy, and release audit checklist.

## Build

This project is a standard Android Gradle project.

Recommended local environment:

- Android Studio or Android SDK command-line tools.
- Android Gradle Plugin compatible with `compileSdk 35`.
- Android NDK `28.0.13004108`.
- CMake `3.31.6`.

With a Gradle wrapper:

```bash
./gradlew test
./gradlew assembleDebug
```

Without a Gradle wrapper:

```bash
gradle test
gradle assembleDebug
```

If you do not have a Gradle wrapper yet, open the project in Android Studio and let it sync, or generate a wrapper with a compatible local Gradle install before enabling GitHub Actions.

## Install And Enable

1. Install the debug APK on an Android device.
2. Open **Open Phone Layout Keyboard**.
3. Tap **Enable keyboard** and enable **Open Phone Layout**.
4. Tap **Switch keyboard** and select it.
5. For iPhone-like bottom-row behavior, disable other Android keyboards in system input settings. This removes Android's extra system input-method switcher when the device has no other keyboard to switch to.

Android will show its normal third-party keyboard privacy warning. That warning appears for all custom keyboards.

## GitHub Publishing Plan

Recommended repository flow:

1. Keep source, docs, licenses, and third-party notices in Git.
2. Keep generated artifacts out of Git: `*.apk`, `*.aab`, `.gradle/`, `build/`, `app/build/`, `local.properties`, signing keys, and local screenshots.
3. Before the first public push, run the privacy checklist in [Security and Privacy](docs/SECURITY_PRIVACY.zh-CN.md).
4. Push the source repository to GitHub.
5. Build release APKs from a clean checkout.
6. Attach APKs to GitHub Releases instead of committing them to the repository.
7. Tag releases with semantic versions such as `v0.1.0`.

Example:

```bash
git remote add origin https://github.com/<owner>/<repo>.git
git push -u origin master
git tag v0.1.0
git push origin v0.1.0
```

If you do not want your real name or email address to appear on GitHub commits, rewrite the local Git history or publish from a clean repository before the first push.

## Public Distribution Checklist

- Confirm `AndroidManifest.xml` has no `INTERNET`, recording, contacts, SMS, storage, or location permissions.
- Confirm `android:allowBackup="false"` and `android:usesCleartextTraffic="false"`.
- Confirm `data_extraction_rules.xml` excludes app files, databases, shared preferences, and external data from backup and device transfer.
- Confirm no signing keys, `.env` files, API tokens, personal local paths, local screenshots, or generated APKs are tracked by Git.
- Confirm bundled third-party dictionaries and native libraries are covered by their licenses and notices.
- For GitHub Releases, publish source and license notices together with binary artifacts.

## Layout Calibration Plan

To make the layout truly match iPhone key positions:

1. Capture reference screenshots from the target iPhone models and iOS versions.
2. Measure every key rectangle in points: `x`, `y`, `width`, `height`, row gaps, side insets, and bottom safe-area spacing.
3. Convert those values to normalized ratios in `IosKeyboardLayout`.
4. Add screenshot comparison tests for common Android widths.
5. Keep every calibrated layout version documented by iPhone model, iOS version, orientation, locale, and display zoom setting.

## Legal Note

This project should not copy Apple artwork, private assets, trademarks, or proprietary code. The implementation should remain original and open-source, with compatibility focused on layout and interaction behavior.

## License

Project source code is licensed under Apache License 2.0. See [LICENSE](LICENSE).

Bundled third-party dictionaries and libraries keep their own licenses. In
particular, the bundled Rime Ice dictionary files are GPL-3.0-only. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
