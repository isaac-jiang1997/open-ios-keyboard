# Open Phone Layout Keyboard

一个开源 Android 输入法项目，目标是在 Android 上尽可能复现 iPhone 原生键盘的键位位置与输入节奏。

## 项目定位

市面上的 Android 键盘在不同品牌、ROM、屏幕尺寸和输入法之间差异很大。对同时使用 iPhone 和 Android 的用户来说，这些差异会变成持续的操作成本：同一个字符、符号、语言切换或九宫格入口，在两台设备上的位置和交互经常不同。

Open Phone Layout Keyboard 的不同点是：它以 iOS 原生键盘的键位为参考，而不是重新发明一套 Android 键盘布局。项目希望让用户在切换设备时保留熟悉的肌肉记忆，减少“我知道要按什么，但手指先找一下”的停顿。

这个项目只追求键位、层级、输入节奏和交互习惯的兼容，不复制 Apple 的专有代码、字体、图标、私有资源或商标化素材。

## 核心亮点

- iOS 原生键盘式键位：英文 QWERTY 行、底部功能行、数字层、符号层、删除、Shift、空格和回车都按 iPhone 风格组织。
- 统一中英文体验：内置中文 26 键拼音、中文 9 键拼音、英文、符号和 Emoji 模式，减少对多个 Android 键盘的依赖。
- 降低跨设备成本：对于习惯 iPhone 键盘的用户，Android 端不需要重新适应完全不同的键位体系。
- 离线中文输入：当前主路线使用 `librime`/Rime 与内置词库，候选词不需要联网请求。
- 九宫格拼音支持：支持 T9 数字序列解析和候选展示，适配中文用户常用输入习惯。
- 本地学习与自定义短语：候选频次和自定义短语只保存在应用私有存储中，并提供清空入口。
- 隐私优先：不请求 `INTERNET` 权限，不包含遥测、广告、云候选、云同步或崩溃上报 SDK。
- 可审计开源实现：键盘绘制、输入法服务、候选栏、Rime 桥接和本地存储逻辑都在仓库中可查看。

## 当前状态

这是一个可运行的 Android IME 原型，已经包含：

- Android `InputMethodService` 输入法服务。
- 自绘 iOS 风格键盘视图。
- 英文 QWERTY 布局。
- 中文 26 键拼音与 9 键拼音。
- Rime/librime 离线中文候选。
- 中文候选栏与展开候选面板。
- 数字层、符号层、更多符号层和 Emoji 入口。
- Shift、Caps Lock、长按删除、空格、回车和输入模式切换。
- 启用键盘、切换键盘、添加自定义短语、清空本地数据的 launcher 页面。

当前布局已使用 iPhone 风格的相对几何规则。要宣称像素级一致，还需要按具体 iPhone 机型、iOS 版本、横竖屏、显示缩放和语言环境继续校准。

## 构建要求

- Android Studio 或 Android SDK 命令行工具。
- Android Gradle Plugin 对应的 Gradle 环境。
- `compileSdk 35`。
- Android NDK `28.0.13004108`。
- CMake `3.31.6`。

如果仓库中已有 Gradle Wrapper：

```bash
./gradlew test
./gradlew assembleDebug
```

如果当前检出没有 Gradle Wrapper：

```bash
gradle test
gradle assembleDebug
```

也可以先用 Android Studio 打开项目并完成同步，或用本机兼容的 Gradle 安装生成 wrapper。建议在接入 GitHub Actions 前提交 Gradle Wrapper。

## 安装与启用

1. 构建或下载 APK。
2. 安装到 Android 设备。
3. 打开 `Open Phone Layout Keyboard`。
4. 点击 `Enable keyboard`，在系统输入法设置中启用 `Open Phone Layout`。
5. 点击 `Switch keyboard`，切换到该输入法。
6. 为了更接近 iPhone 的底部输入体验，建议在系统输入法设置中暂时只保留这一款键盘。否则 Android 可能会在导航栏区域额外显示系统输入法切换按钮，这是第三方输入法无法在应用内部移除的系统控件。

Android 会对所有第三方键盘显示系统级隐私提示，这属于系统正常行为。

## GitHub 部署方案

推荐把 GitHub 仓库作为源码、文档、Issue 和 Release 的公开入口，APK 通过 GitHub Releases 分发，不直接提交到源码仓库。

### 1. 发布前整理

- 保留源码、文档、许可证、第三方声明和测试。
- 不提交 `*.apk`、`*.aab`、`.gradle/`、`build/`、`app/build/`、`local.properties`、签名密钥、本地截图和私有配置。
- 确认 `.gitignore` 覆盖上述生成文件。
- 如果不希望公开真实姓名或邮箱，首次推送前先重写 Git 历史，或用干净仓库重新导入代码。
- 确认第三方词库与 native 库的许可证声明完整。当前项目源码为 Apache-2.0，但内置 Rime Ice 词库为 GPL-3.0-only，发布 APK 时需要一起发布对应源码和许可证声明；如果未来要做 Apache-only 发行包，需要替换或移除 GPL 词库。

### 2. 推送源码

```bash
git remote add origin https://github.com/<owner>/<repo>.git
git push -u origin master
```

### 3. 建议的仓库结构

```text
.
├── README.md
├── README.zh-CN.md
├── LICENSE
├── THIRD_PARTY_NOTICES.md
├── docs/
├── app/
├── build.gradle
└── settings.gradle
```

### 4. 发布版本

```bash
./gradlew test
./gradlew assembleDebug
git tag v0.1.0
git push origin v0.1.0
```

然后在 GitHub Releases 中创建 `v0.1.0`，上传 APK，并在 release note 中写清楚：

- 支持的 Android 版本与 ABI。
- 当前功能范围。
- 已知限制。
- 隐私承诺。
- 第三方许可证说明。

### 5. 后续自动化

后续可以添加 GitHub Actions：

- PR 时运行单元测试。
- tag 发布时构建 APK。
- 将 APK 作为 workflow artifact 保存。
- 手动审核后再发布到 GitHub Releases。

建议在加入 CI 前先提交 Gradle Wrapper，避免 GitHub Actions 依赖 runner 上的系统 Gradle。

## 隐私与安全

V1 的隐私原则是纯离线：

- 不请求 `INTERNET`。
- 不上传输入内容、候选选择、词频、自定义短语或设置。
- 不包含广告、分析、远程配置或崩溃上报 SDK。
- 用户词库、候选学习和 Rime 用户数据只保存在应用私有目录。
- 默认关闭 Android 自动备份和设备迁移导出。
- 密码框、禁止个性化学习的输入框和敏感输入类型中不应写入学习数据。

更多细节见 [安全与隐私规范](docs/SECURITY_PRIVACY.zh-CN.md)。

## 公开发布检查清单

发布前建议检查：

```bash
git status --short
git ls-files '*.jks' '*.keystore' '*.p12' '*.pem' '*.key' '*.apk' '*.aab' '*.env' 'local.properties'
git grep -n -i -E '(api[_-]?key|secret|password|authorization|bearer|cookie|private key)'
git grep -n -E '(/Users/|/Volumes/|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})'
```

如果这些命令命中真实密钥、签名文件、个人路径、个人邮箱或本地构建产物，应在首次公开推送前清理。

## 开源边界

本项目实现的是键位兼容性和输入交互，不应复制 Apple 的专有代码、字体、图形资源、商标、未公开接口或受保护素材。

项目源码使用 Apache License 2.0。第三方词库和 native 库保留其原始许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
