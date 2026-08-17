# Android 系统输入法 UI 边界

更新日期：2026-05-16

## 结论

Open Phone Layout 可以绘制和控制自己的键盘主体、候选栏、底部地球仪和麦克风按钮，但不能从 APK 内删除 Android 系统导航栏上的控件。

## 已验证行为

在模拟器中，同时启用以下两个输入法时：

- `dev.openkeyboard.applelayout/.KeyboardImeService`
- `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`

Android 会在导航栏右下角显示系统输入法切换图标。这个图标不是 `IosAccessoryBarView` 绘制的，也不在本项目 View 树里。

禁用 Google LatinIME 后，`enabled_input_methods` 只保留 Open Phone Layout，右下角系统输入法切换图标消失。

```bash
adb shell ime disable com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
adb shell settings get secure enabled_input_methods
```

## 产品策略

V1.0 既然已经内置中文、英文、九宫格、全键盘、符号和 Emoji，推荐用户只启用 Open Phone Layout。这样可以最大限度接近 iOS 底部布局，并避免 Android 系统额外的输入法切换按钮造成误触。

Android 导航栏的收起键盘按钮同样属于系统 UI，第三方输入法不能删除。项目只能通过缩短自己的底部 accessory bar、调整地球仪和麦克风位置，降低系统控件对键盘布局的干扰。
