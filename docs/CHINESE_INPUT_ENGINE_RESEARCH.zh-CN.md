# 中文输入引擎调研与接入决策

更新日期：2026-05-16

## 目标

当前中文输入的核心问题不是键位，而是候选词引擎太弱：

- 候选词不够准。
- 9 键数字序列对应的拼音候选太少。
- 每次输入都由应用层临时查小 TSV，缺少成熟输入法需要的组词、词频、用户词典和候选分页。

因此后续不再继续扩写项目内的自研 `PinyinEngine` 作为主方案。当前 `PinyinEngine` 只保留为临时 fallback，用于保持 APK 可运行。

## 参考项目结论

### Rime / librime + Trime

Rime 的核心库 `librime` 是成熟的离线输入法引擎，支持拼音、形码、输入方案、用户词典和候选管理。Trime 是 Android 前端，已经证明了 Android IME + librime 的可行路径。

本地源码重点参考点：

- Trime 在 `TrimeInputMethodService` 创建 `RimeSession`，输入法服务只负责生命周期、组合态、候选 UI 与提交文本。
- Trime 的 JNI 层通过 `select_candidate`、`candidate_list_from_index`、`sync_user_data` 等 librime API 处理候选选择、候选列表和本地用户数据。
- Trime 的用户词典管理通过 native 方法做导入、导出、备份和恢复，说明用户学习数据不需要联网。

适合本项目的原因：

- 符合纯离线要求。
- 候选精度和用户学习交给成熟引擎。
- 前端可以继续保持 iOS 键位布局，不需要采用 Trime 的皮肤或 UI。
- schema/dictionary 可随 APK 打包，也可后续允许用户手动导入。
- `librime` 本身是 BSD-3-Clause；Trime 是 GPL-3.0-or-later，只能作为架构参考，不能把 Trime 源码直接复制进当前 Apache-2.0 项目。

### Fcitx5 Android / libime

Fcitx5 Android 把 Fcitx5 框架和引擎移植到了 Android，中文能力由 `fcitx5-chinese-addons` 和 `libime` 支撑。它的架构更接近完整输入法框架：native daemon、addon、事件回调、候选分页、preedit 更新。

本地源码重点参考点：

- native 层设置 `LIBIME_MODEL_DIRS`、Fcitx 配置目录和用户数据目录。
- 输入事件进入 native engine 后，候选列表通过 `setCandidateList` 更新，preedit 通过 `setPreedit` 或 `setClientPreedit` 更新。
- Java/Kotlin 层消费 candidate/preedit/paged candidate 事件，UI 不直接做拼音匹配。

适合参考，但不作为当前首选的原因：

- 能力很强，但接入的是完整 Fcitx5 框架，构建与运行时复杂度高于 Rime。
- 对本项目来说，首要任务是保持 iOS 布局，中文引擎只需要稳定离线能力；Rime 的前端接入面更窄。
- Fcitx5/libime 生态的许可证和 addon 组合也需要逐项审计，不能直接整包搬入。

## 本项目决策

中文输入主路线改为：

1. 保持 `KeyboardImeService`、`IosKeyboardView`、`CandidateStripView` 作为我们自己的 iOS 布局前端。
2. 通过 `ChineseInputEngine` 接口隔离中文引擎。
3. 当前 APK 已新增 `RimeChineseInputEngine`，用 JNI 接入 librime，并保留 `LegacyPinyinInputEngine` 作为 native 初始化失败时的 fallback：
   - 初始化本地 Rime 数据目录。
   - 部署全拼与 9 键相关 schema。
   - 按键转发到 librime。
   - 从 librime 读取 preedit、候选、comment 和分页状态。
   - 候选选择调用 librime selection API。
   - 用户学习数据保留在 app 私有目录，并在敏感输入框禁用学习。
4. OpenPhone 的全拼和九宫格 schema 默认使用简体中文输出；官方 `luna_pinyin` 词典经 `t2s.json` 转换，避免默认候选出现繁体字。

## 9 键要求

9 键不能继续用简单的 `2=ABC` 字符串前缀扫描。成熟实现需要：

- 输入数字序列后由引擎展开可能拼音。
- 候选排序结合基础词频、用户词频和上下文组合。
- 候选栏优先展示汉字词候选，而不是只展示匹配到的拼音表项。
- 保留“选拼音”入口，用于在必要时从数字序列切到具体拼音。
- 九宫格候选栏只展示中文候选，不展示原始数字序列，避免把 `7487832` 这类输入码误认为第一候选。

这部分应由 Rime schema 与引擎承担；Android UI 只保留 iOS 键位和交互。

## 隐私边界

引擎接入后仍必须满足：

- 不请求 `INTERNET`。
- 不使用云候选。
- 不上传输入内容、词频、用户词典或自定义短语。
- 用户数据只放在 app 私有目录。
- 密码等敏感输入框不学习、不写入用户词典。

## 参考资料

- [rime/librime](https://github.com/rime/librime)
- [osfans/trime](https://github.com/osfans/trime)
- [fcitx5-android/fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)
- [fcitx/libime](https://github.com/fcitx/libime)
