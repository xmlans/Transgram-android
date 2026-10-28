# TransGram for Android

TransGram 是一个非官方 Telegram Android 客户端，基于 Telegram Android 源码开发。项目包含双语翻译、图片文字识别、读屏优化和低视力设置。

这个目录是从开发工作区导出的源码快照，包含当时未提交的改动；它不含 Git 历史。19 个上游原生依赖已作为普通目录放入。由于之前的 APK 没有记录完整源码快照，目前还不能保证本目录与那个 APK 的源码逐字对应。

## 构建环境

- Android Studio 2025.1.4 或兼容的版本
- Android SDK 36、Build Tools 36.0.0
- Android NDK 27.2.12479018
- Android Studio 自带 JDK

在 `local.properties` 中配置 Android SDK 路径，以及自己从 [Telegram](https://my.telegram.org/) 申请的 API 凭据：

```properties
sdk.dir=/path/to/Android/Sdk
TG_APP_ID=你的数字ID
TG_APP_HASH=你的API_HASH
```

打开项目后，可尝试构建 Android 调试版本：

```bash
./gradlew :TMessagesProj_App:assembleAfatDebug
```

这里提供的是上游示例 Google 服务配置。要使用推送等 Google 服务，需要为自己的应用包名配置 Firebase 并替换 `google-services.json`。正式发布需要使用你自己的签名密钥和配置；生产密钥及本地 API 凭据没有包含在这个目录中。第三方测试目录中保留了上游公开的证书和测试密钥样本；它们不是 TransGram 的正式签名密钥。

这个源码快照尚未在干净环境完成构建验证。如果构建失败，先检查本机 SDK、NDK、API 凭据、Firebase 配置和原生依赖；请勿把自己的密钥或 `local.properties` 上传到公开仓库。

## 许可与来源

本项目基于 [Telegram for Android](https://github.com/DrKLO/Telegram)，按照仓库中的 [LICENSE](LICENSE) 发布。上游构建说明保留在 [README-UPSTREAM.md](README-UPSTREAM.md)。其他组件及离线 OCR 的许可说明见 [NOTICE](NOTICE) 和应用内的第三方许可列表；子目录中的许可证也应一并保留。

源码目录当前只是待审核的本地导出，并未发布到网络。发布 APK 时，应同时提供与该 APK 对应的完整源码及构建材料。
