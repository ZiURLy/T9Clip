# 九键修复·截图复制（T9Clip）

> An Xposed module for the Fcitx5 T9 build "小企鹅万象九键" (EyaaCai v0.1.0): keeps the T9 layout in web pages and search boxes, copies new screenshots to the clipboard with a Gboard-style toolbar thumbnail, and makes the toolbar customisable.

本模块用来改进小企鹅万象九键（EyaaCai v0.1.0），主要做三件事：修复九键布局、截图后自动复制、自定义工具栏。

## 功能

**九键修复**
- 修复在网页和搜索框里被强制切成 26 键的问题
- 密码框仍然保持 26 键

**截图复制**
- 截屏后自动把截图放进剪贴板
- 像 Gboard 一样，在输入法工具栏上显示最新截图的缩略图，点一下就发到输入框
- 输入框只能输入文字时会给出提示
- 剪贴板面板里有一张截图卡片，可以从剪贴板清除这张图

**验证码自动复制**
- Google 短信（`com.google.android.apps.messaging`）收到带验证码的通知时，自动把验证码放进剪贴板（小企鹅剪贴板里也会有）
- 工具栏出现蓝色「码」缩略图和验证码，点一下直接填进当前输入框，填完自动清除剪贴板；长按只清除
- 只认通知：短信 App 正开着对话页面、不弹通知时不会触发
- 不联网、不申请权限、不轮询，不额外耗电；作用域需要勾上「系统框架」，勾完要重启手机

**工具栏自定义**
- 长按工具栏按钮，可以左移、右移、移到最左或隐藏
- 可以把「更多」面板里的选项（例如翻译）固定到工具栏
- 随时可以恢复默认工具栏

## 要求

- 已 root，并装有 Xposed 框架（LSPosed、Vector 等）
- Android 11 及以上
- **小企鹅万象九键 v0.1.0（EyaaCai）**，包名 `org.fcitx.fcitx5.android`。九键修复只认这个版本，其他版本的小企鹅输入法没有测试过
- 验证码功能需要 Google 短信，并允许它弹通知
- **截图复制只支持 ColorOS / OxygenOS 的系统截屏**（`com.oplus.screenshot`）

作者的测试环境：一加 Ace 6T，OxygenOS 16，Vector。

## 安装

1. 从 [Releases](../../releases) 下载最新的 APK 并安装
2. 在 Xposed 管理器里启用本模块，作用域勾选「小企鹅输入法」「截屏」和「系统框架」（已默认推荐）
3. 强行停止这两个应用，或者直接重启手机

## 隐私

- 模块本身不申请任何权限，也不联网
- 截图会进入系统剪贴板。和你手动复制的内容一样，正在前台的应用可以读到剪贴板，不想留着的截图可以在剪贴板卡片里清除

## 遇到问题

请到 [Issues](../../issues) 反馈，并附上：手机型号和系统版本、Xposed 框架版本、输入法版本，以及 Xposed 管理器里的模块日志。

## 原理

- `FcitxT9Fix` / `InputTypeFix`：修正输入法根据输入框类型选择的键盘布局
- `ScreenshotClip`：在系统截屏进程里监听新截图，把它写入剪贴板
- `ToolbarImageChip` / `ClipboardImageCard`：在输入法工具栏和剪贴板面板里显示截图
- `ToolbarCustomizer`：调整工具栏按钮的顺序、隐藏按钮，以及固定「更多」面板里的选项

## 说明

原始工程丢失，本仓库的源码由发布版 APK 反编译整理而来，局部变量名是工具生成的，欢迎改进。

## 自己编译

需要 JDK 17 和 Android SDK，用 Android Studio 打开即可编译。编译 Release 包要用你自己的签名密钥，请不要把密钥文件提交到仓库。

## 许可证

[MIT](LICENSE)：可以自由使用、修改和再发布，只需保留版权声明。
