# 妆伴 · BeautySense

妆伴是一款语音与视觉协作的美妆助手，支持前置摄像头魔镜、妆容方案、分步化妆指导、肤质分析和化妆品查找。语音助手名为「小妆」。

## 下载与体验

- [Android App 1.2.1 下载](https://github.com/Karl-XZ/BeautySense/releases/tag/v1.2.1)：Android 8.0 及以上，调试签名测试版。
- [Web 创空间](https://modelscope.cn/studios/Karlzhy/BeautySense)：上传图片、使用摄像头或选择预设画面体验。
- [Web 页面](https://openl.work/BeautySense/)。

Web 版本具备全部功能体验，但是为了最好的使用效果，建议下载 App 使用。也可联系我们试用自研硬件「妆伴魔镜」。

## 功能

- **魔镜模式**：持续显示前置摄像头，画幅按原始比例完整显示；底部对话区默认收起，点击后半透明展开。
- **语音和文字对话**：语音输入、播报、佩戴模式下的「小妆」唤醒。
- **妆造与分步指导**：选择风格与色号、生成方案、逐步指导、视觉复核和微调。
- **肤质分析**：面部区域检测、肤质观察、护肤建议及妆后预览。
- **找物与记忆**：识别桌面化妆品、相对方位指引和物品位置记录。
- **运行记录**：服务连接、脱敏日志及安全提醒。

云端分析由妆伴服务端提供；摄像头与麦克风需要用户授权，模型密钥不存放在客户端源码中。硬件安全提醒及长期稳定性仍需在目标设备上验证。

## 源码结构

本仓库主分支提供已发布的原生 Android 客户端源码，基于 Kotlin、Jetpack Compose、CameraX 和 ML Kit。

```text
beautysense/
  src/main/java/com/beautysense/app/
    audio/     # 录音、语音播报和唤醒
    model/     # 方案、步骤与复核数据
    network/   # 妆伴服务接口
    ui/        # Compose 界面与魔镜预览
    vision/    # 人脸裁切与帧缓存
    wear/      # 佩戴服务与设备接入
  src/test/          # JVM 单元测试
  src/androidTest/   # 魔镜设备界面测试
  build.gradle
docs/images/         # 妆伴界面演示截图
gradle/              # Gradle Wrapper
.github/workflows/   # Android 构建与测试
```

Web 与模型服务端通过上方体验链接提供；它们的源码不包含在当前主分支。

## 构建 Android App

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0；Gradle 通过仓库内的 Wrapper 运行。

```powershell
.\gradlew.bat :beautysense:testDebugUnitTest :beautysense:assembleDebug --no-daemon
```

Linux/macOS：

```bash
chmod +x gradlew
./gradlew :beautysense:testDebugUnitTest :beautysense:assembleDebug --no-daemon
```

APK 输出：`beautysense/build/outputs/apk/debug/beautysense-debug.apk`。

服务地址默认使用 `https://openl.work/BeautySense`，也可在 App 设置中修改，或通过 `BEAUTYSENSE_SERVER_URL` 在构建时配置。自托管服务如启用客户端鉴权，需要设置 `BEAUTYSENSE_CLIENT_TOKEN`；模型密钥由服务端保管。请勿将本机配置、令牌或签名文件提交到仓库。

## 界面示例

| 首页 | 妆造工作室 | 分步指导 |
| --- | --- | --- |
| ![首页](docs/images/01_home_dashboard.png) | ![妆造](docs/images/03_makeup_studio_styling.png) | ![指导](docs/images/07_step_guidance_execution.png) |

更多客户端说明见 [beautysense/README.md](beautysense/README.md)。
