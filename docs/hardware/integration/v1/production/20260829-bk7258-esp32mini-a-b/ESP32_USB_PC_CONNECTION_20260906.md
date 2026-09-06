# ESP32-S3 USB D+/D- 连接电脑首测

适用：银龄智护 A-ESP 首板，ESP32-S3-MINI-1U-N4R2。

## 推荐首测接法

如果主板已经由可调电源从 5V_IN 供电，则电脑 USB 仅接：

```text
PC USB D+  -> 板上 USB D+
PC USB D-  -> 板上 USB D-
PC USB GND -> 板上 GND
PC USB 5V  -> 不接
```

必须共地，D+/D- 不可对调。数据线尽量短，D+/D- 贴近并行/双绞，避免长飞线。

## USB-A 2.0 线常见颜色

- 红：+5V
- 黑：GND
- 白：D-
- 绿：D+

颜色不是强制保证，接线前应用万用表通断档确认插头引脚。

USB-A 插头标准引脚：Pin1 VBUS, Pin2 D-, Pin3 D+, Pin4 GND。

## 如果由电脑 USB 同时供电

只有在确认板上 5V 输入无短路、BQ24074/TPS63021 电源链已通过限流测试后，才可把 USB 5V 接到板上 5V_IN，同时接 GND/D-/D+。此时不要再并联另一个 5V 电源。

## 进入 ROM 下载模式

1. BOOT 接 GND并保持；
2. EN 接 GND约0.5~1秒；
3. 先释放 EN；
4. 再释放 BOOT；
5. 观察电脑是否出现新的 USB/COM 设备。

若没有枚举，优先检查：D+/D- 是否接反、GND 是否共地、22Ω串阻连续性、USBLC6-2SC6焊接、ESP 模组 USB 焊盘是否仍有桥连/虚焊。

记录日期：2026-09-06
