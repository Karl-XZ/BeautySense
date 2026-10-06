# 妆伴 Android 客户端

本模块是妆伴 1.2.1 原生 Android 客户端，语音助手名为「小妆」。

- 前置摄像头魔镜：持续预览、完整画幅、默认收起的半透明对话区。
- 普通增强与无障碍辅助模式、挂颈佩戴校准和语音唤醒。
- 文字与语音对话、视觉找物、物品位置记忆。
- 妆容方案、分步指导、步骤复核与妆后预览。
- 肤质分析、护肤建议、运行记录和服务地址设置。

客户端通过 HTTP 接口调用妆伴服务端。默认服务地址为 `https://openl.work/BeautySense`，也可在 App 设置中修改。云端模型密钥由服务端保存。

## 构建与测试

在仓库根目录执行：

```powershell
.\gradlew.bat :beautysense:testDebugUnitTest :beautysense:assembleDebug --no-daemon
```

输出：`beautysense/build/outputs/apk/debug/beautysense-debug.apk`。支持 Android 8.0（API 26）及以上，编译 SDK 为 35，JDK 为 17。

构建环境变量 `BEAUTYSENSE_SERVER_URL` 可指定服务地址；自托管服务启用鉴权时，通过 `BEAUTYSENSE_CLIENT_TOKEN` 提供客户端访问凭证。修改环境变量后需重新构建。

连接有前置摄像头的设备后，可运行魔镜界面测试：

```powershell
.\gradlew.bat :beautysense:connectedDebugAndroidTest --no-daemon
```

调试签名 APK 用于体验及联调。实体设备的摄像头方向、长时间运行、功耗、发热和安全提醒需要另外实测。
