# 安全与隐私规范

## V1 隐私承诺

Open Phone Layout Keyboard V1 是纯离线键盘：

- 不请求 `INTERNET` 权限。
- 不包含联网服务、云候选、云同步、遥测、广告 SDK 或崩溃上报 SDK。
- 不上传用户输入内容、候选选择、词频、常用短语或设置。
- 用户输入习惯和自定义短语只存储在本机应用私有存储中。
- 默认关闭 Android 自动备份和设备迁移导出，避免词库随系统备份离开设备。

## 本地存储范围

V1 允许保存两类本地数据：

- 输入习惯：候选词被选择的次数，用于本机候选排序。
- 自定义短语：用户手动添加的 `拼音 -> 短语`。
- Rime 用户数据：Rime 在应用私有目录中生成的用户词库、学习数据和部署产物。

V1 不保存：

- 原始全文输入历史。
- 密码输入框内容。
- App 名称与输入内容的组合记录。
- 联系人、剪贴板、定位、账号、设备标识符。
- 任何网络地址、令牌、Cookie 或远程配置。

## 敏感场景规则

在密码类输入框中：

- 禁止学习词频。
- 禁止写入用户词库。
- 清空当前拼音组合态。

当输入框设置 `IME_FLAG_NO_PERSONALIZED_LEARNING` 时，也必须禁止学习。邮箱、手机号、姓名、网址、地址等隐私敏感输入类型默认不学习，除非后续提供明确的用户开关。

## 权限规范

Manifest 必须保持最小权限：

- 允许：`android.permission.BIND_INPUT_METHOD`，这是 Android 输入法服务必需权限。
- 禁止：`INTERNET`、`ACCESS_NETWORK_STATE`、读取联系人、读取短信、读取外部存储、定位、录音、相机。

每次新增依赖或功能都必须检查 manifest diff，确认没有新增联网或敏感权限。

## 本地数据控制

设置页必须提供：

- 添加自定义短语。
- 查看本地自定义短语。
- 清空本地数据。

清空本地数据应删除：

- 自定义短语。
- 候选词频。
- Rime 用户目录中的学习数据与用户词库。
- 后续新增的任何本地学习数据。

## 实现约束

- 词库随 APK 打包或由用户手动导入，不自动下载。
- 所有用户数据使用应用私有存储，禁止写入公共外部存储。
- 禁止日志输出用户输入内容、候选词选择、组合态拼音。
- 禁止引入广告、分析、远程配置、推送、A/B 实验 SDK。
- 禁止把输入内容写入崩溃报告。
- 调试日志在 release 构建中必须关闭或不包含用户文本。
- 语音识别必须使用本地离线引擎；接入前不得请求 `RECORD_AUDIO`，不得调用可能联网的系统语音识别服务。

## 审计清单

发布前检查：

- `AndroidManifest.xml` 没有 `INTERNET`。
- `android:allowBackup="false"`。
- `android:usesCleartextTraffic="false"`。
- `data_extraction_rules.xml` 排除 app 数据备份和迁移。
- 依赖树不包含网络、广告、分析或崩溃上报 SDK。
- 敏感输入框不触发学习。
- 设置页可以清空本地学习数据。

## 参考资料

- Android Developers: [Create an input method](https://developer.android.google.cn/develop/ui/views/touch-and-input/creating-input-method?hl=en)
- Android Developers: [Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup?hl=en)
- Android Developers: [Security recommendations for backups](https://developer.android.com/privacy-and-security/risks/backup-best-practices)
- Android Developers: [Cleartext communications risk](https://developer.android.com/privacy-and-security/risks/cleartext-communications?hl=en)
- Android Developers: [SharedPreferences](https://developer.android.google.cn/training/data-storage/shared-preferences?hl=en)
