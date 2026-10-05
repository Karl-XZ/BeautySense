# 妆伴 Android App

`beautysense` 是妆伴的原生 Android 客户端，语音助手叫小妆。手机挂在胸前后，后置摄像头、麦克风、扬声器/耳机、振动器、加速度计和陀螺仪共同模拟头戴设备，并复用 `web-simulator` 的真实 Agent 与模型服务。

## 已接通能力

- 普通增强和无障碍辅助两种模式；
- 镜面、桌面、前方三段挂颈校准；
- CameraX 后台取帧和 ML Kit 本地人脸检测裁切；
- 妆伴魔镜模式使用前置摄像头持续预览，上方画面作为镜子、下方三分之一用于对话；切换到其他功能页仍保持摄像头取帧，退出模式后释放摄像头；
- 阿里云 ASR/TTS 服务端代理，用户开始新一轮输入时会中断旧播报；
- 佩戴模式持续监听“小妆”；只说名字会回应，同一句带请求会直接执行，结束任务后重新等待唤醒；
- DeepSeek 对话/意图路由、Qwen VL 视觉理解、方案生成、调整、分步指导与前后脸复核；
- `PASS / REPAIR / BLOCKED / UNCERTAIN` 复核状态、可选微调和随时跳过；
- 无障碍找物、相对方位、物品位置记忆和自由规划；
- 皮肤分析、护肤建议、欧莱雅产品推荐、保存询问和 AI 妆效预览；
- 加速度计与陀螺仪跌倒候选、安全抢占、本机振动；
- 脱敏运行日志和服务地址设置。

云端密钥不会打包进 APK，只由 `web-simulator` 服务读取。

## 运行

先启动服务端：

```powershell
cd web-simulator
npm install
npm run dev
```

再构建 Android App：

```powershell
.\gradlew.bat :beautysense:testDebugUnitTest :beautysense:assembleDebug --no-daemon
```

Debug APK：

```text
beautysense/build/outputs/apk/debug/beautysense-debug.apk
release/妆伴-1.2.0-魔镜-debug.apk
```

远程服务部署要求在构建时设置 `BEAUTYSENSE_CLIENT_TOKEN`。该客户端访问凭证与模型密钥不同；模型密钥继续只由服务端保存。修改构建环境变量后，请使用 `--rerun-tasks` 重新构建 APK，避免 Kotlin 增量编译沿用旧常量。

Android 模拟器默认使用 `http://10.0.2.2:5176`。实体手机需要在“设置”中改成运行服务端电脑的局域网 HTTPS 地址；开发调试可使用局域网 HTTP，正式发布构建默认禁止明文网络。

如果 Windows 上的仓库路径包含多层非 ASCII 字符，而 JDK 21 无法加载 Gradle 单元测试类，可从纯英文目录联接运行同一命令；APK 编译本身不受影响。

## 验证

```powershell
cd web-simulator
npm test

cd ..
.\gradlew.bat :beautysense:testDebugUnitTest :beautysense:assembleDebug :beautysense:lintDebug --no-daemon
```

魔镜模式已在 Android 模拟器验证前置摄像头取帧、切换到日志页继续取帧、退出后释放相机，并通过设备界面测试。连接本地服务后，使用模拟器前置摄像头画面完成真实视觉提问并收到模型回复。Web 全套 80 项测试通过。实体手机的摄像头方向、长时间运行、功耗和发热仍需在目标设备上核验。

## 生产边界

当前 APK 是可真实联调的 MVP，不等于已完成医疗器械或人身安全产品认证。发布前仍需在目标实体手机完成锁屏一小时稳定性、功耗/温升、弱网、来电、TalkBack、不同相机方向、真实跌倒误报率和隐私合规测试。安全提醒不能替代紧急救援。
