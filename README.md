# Open iOS Keyboard

An open-source Android keyboard that follows the iOS keyboard layout and typing rhythm.

[中文说明](README.zh-CN.md)

## Why

Many people carry two phones but use an iPhone as their daily driver. On Android, the keys, symbol layers, language switch, and T9 entry are often not where muscle memory expects them. Typing feels split between two systems.

This project is for those users: keep the iOS keyboard’s positions and rhythm on Android, so switching devices costs less.

## Features

- iOS-style QWERTY, number, symbol, and emoji layers
- Chinese 26-key pinyin, Chinese 9-key/T9, and English in one IME
- Offline Chinese candidates via `librime` and Rime Ice
- Local learning and custom phrases in app-private storage
- No `INTERNET` permission, ads, telemetry, or cloud candidates

This is a working V1 prototype. Keep only this keyboard enabled if you want to hide Android’s system IME switcher.

## Build

Requires Android SDK (`compileSdk 35`), NDK `28.0.13004108`, and CMake `3.31.6`.

```bash
./gradlew test
./gradlew assembleDebug
```

## Install

1. Install the APK and open **Open iOS Keyboard**.
2. Tap **Enable keyboard**, then **Switch keyboard**.
3. For an iOS-like bottom row, disable other system keyboards.

Android’s third-party keyboard privacy warning is normal.

## License

The repository and released APKs are [GPL-3.0-only](LICENSE) because they include Rime Ice dictionaries.

Original keyboard code is also available under [Apache-2.0](LICENSES/Apache-2.0.txt) if you do not include those GPL dictionaries.

See [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
