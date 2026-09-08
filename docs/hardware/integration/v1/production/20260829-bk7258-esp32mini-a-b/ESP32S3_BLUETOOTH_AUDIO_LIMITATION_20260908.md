# ESP32-S3 蓝牙音频限制（2026-09-08）

## 结论

当前 A 板使用 ESP32-S3-MINI-1U-N4R2。该芯片支持 Bluetooth LE，但不支持 Bluetooth Classic（BR/EDR），也不支持 LE Audio。

因此不能通过普通手机“蓝牙音箱”方式，使用 A2DP Sink 接收手机正在播放的系统媒体音频。

目标链路：

```text
Phone media
  ↓ Bluetooth A2DP
ESP32-S3
  ↓ I2S
MAX98357A
  ↓
Bone conduction
```

在当前 ESP32-S3 上不可实现，阻塞点位于 A2DP / Bluetooth Classic 协议能力，不是 MAX98357A 或 I2S 引脚问题。

## 当前板可继续验证的音频链路

```text
ESP32-S3 本地生成 / Wi-Fi获取 PCM
  ↓
GPIO36 BCLK
GPIO37 WS
GPIO39 AMP_DOUT
GPIO40 AMP_SD_MODE
  ↓
MAX98357A
  ↓ BTL SPK+/SPK-
骨传导负载
```

此链路可以继续做功放、I2S、骨传导器件、温升和电流验证。

## 若产品需要“手机蓝牙直接播放”

需要增加支持 Bluetooth Classic A2DP Sink 的器件/SoC，或更换主控方案。原始 ESP32 系列支持 Classic Bluetooth A2DP；ESP32-S3 不支持。

不能用普通 BLE GATT 程序替代 A2DP 来接收手机系统媒体音频。