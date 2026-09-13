# Android → 眼镜音频链路澄清（2026-09-08）

## 结论

此前 V1 架构中的 `Android 手机 → Wi-Fi / BLE → 主控` 是**通用通信链路描述**，不是 Bluetooth Classic A2DP 音频接收链路。

对 ESP32-S3 Plan A，当前硬件可直接支持并且与既有架构一致的音频闭环应为：

```text
Android App / TTS / 云端音频
        ↓
      Wi-Fi
        ↓
ESP32-S3 接收/缓冲/解码 PCM/WAV/压缩音频
        ↓
I2S TX
GPIO36 BCLK
GPIO37 WS
GPIO39 AMP_DOUT
GPIO40 AMP_SD_MODE
        ↓
MAX98357A
        ↓
SPK+/SPK-
        ↓
两只 8Ω Bone（单声道并联）
```

BLE 在 Plan A 中更适合作为控制、状态、配网、命令等低带宽通道，不应把 `Wi-Fi / BLE → Android` 简写理解为“手机系统媒体通过 A2DP 直接推到 ESP32-S3”。

## 与旧文档的关系

旧 `README.md` 与 `plan-a/.../architecture.md` 中：

- `Android 手机 / AI / ASR / LLM / TTS / App → Wi-Fi / BLE → 主控`
- `主控 I2S TX → MAX98357A → Bone`

这两段定义了“手机/应用与主控通信”和“主控到骨传导的音频输出”两部分，但没有冻结中间的音频传输协议，也没有声明 A2DP。

因此此前真正未闭合的是**软件传输协议层**，不是 I2S / MAX98357A / Bone 硬件链路。

## 当前建议

首版优先采用 Wi-Fi 音频流完成产品功能验证：

1. Android App 或云端生成 TTS/提示音；
2. 通过 TCP / WebSocket / HTTP 流发送到 ESP32-S3；
3. ESP32-S3 在 PSRAM 中做环形缓冲；
4. PCM/WAV 可直接送 I2S；若使用压缩格式则先解码；
5. MAX98357A 输出到两只骨传导单元。

如果产品需求是“任意手机系统媒体像蓝牙音箱一样直接播放”，则该需求与当前 ESP32-S3 Plan A 的通用 Wi-Fi/BLE架构不是同一件事，需要单独新增对应音频接收方案。