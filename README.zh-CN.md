# Open iOS Keyboard

开源 Android 输入法，按 iOS 键盘的键位和输入节奏来做。

[English](README.md)

## 背景

不少人双持手机，但日常主力是 iPhone。换到 Android 打字时，键位、符号层、中英切换和九宫格入口往往都不在肌肉记忆里的位置，每次都要停一下找键，感觉很割裂。

这个项目就是为这类用户做的：在 Android 上保住 iOS 键盘的键位和输入节奏，减少换机时的停顿。

## 功能

- iOS 风格的 QWERTY、数字、符号、Emoji
- 中文 26 键拼音、中文 9 键/T9、英文做在同一套输入法里
- 离线中文候选：`librime` + 雾凇拼音
- 本机学习与自定义短语，只存在应用私有目录
- 无 `INTERNET`、无广告、无遥测、无云候选

当前是可运行的 V1 原型。想接近 iPhone 底部体验时，系统里只保留这一款键盘，否则 Android 会在导航栏画系统切换按钮。

## 构建

需要 Android SDK（`compileSdk 35`）、NDK `28.0.13004108`、CMake `3.31.6`。

```bash
./gradlew test
./gradlew assembleDebug
```

## 安装

1. 安装 APK，打开 **Open iOS Keyboard**。
2. 点击 **Enable keyboard**，再点 **Switch keyboard**。
3. 若要更接近 iOS 底栏，在系统设置里关掉其他输入法。

Android 对第三方键盘的隐私提示是系统正常行为。

## 许可证

本仓库和发布的 APK 按 [GPL-3.0-only](LICENSE) 授权，因为内置了雾凇拼音词库。

原创键盘代码另外按 [Apache-2.0](LICENSES/Apache-2.0.txt) 提供；不包含 GPL 词库时可以按 Apache-2.0 复用。

详见 [NOTICE](NOTICE) 和 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
