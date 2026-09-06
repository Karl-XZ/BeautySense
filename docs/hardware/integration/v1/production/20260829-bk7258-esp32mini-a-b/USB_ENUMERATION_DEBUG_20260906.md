# ESP32-S3 USB 枚举故障 Bring-up 记录

日期：2026-09-06

## 当前状态

- 前序 3V3 硬短已修复，根因是 ESP32 核心模组底部多焊盘连锡。
- 3V3 实测约 3.4V且在 Windows USB 连接/断开提示音出现时不掉压。
- EN 实测约 3.4V且稳定。
- 电脑持续出现 USB 设备连接/断开提示音。
- 已去掉杜邦飞线，改用 USB 数据线线头直接接板端 D+/D-/GND，故障现象不变。
- 断电 D+/D- 基本短路检查无异常；两颗串联电阻均实测约 22Ω。

## 当前判断

电源掉压和飞线质量优先级下降。当前高优先级：

1. ESP32-S3-MINI-1U 底部 GPIO19(D-)/GPIO20(D+) 焊接异常；
2. USBLC6-2SC6 方向、焊接或器件损坏；
3. D+/D- 实际网络映射错误/接反；
4. 未稳定处于 ROM 下载模式，Flash 固件导致 USB 重配；
5. 主机端口/驱动作为后续排除项。

## 下一步

### 1. ROM 下载模式稳定性测试

BOOT 接 GND保持；EN 短接 GND 0.5~1s后释放；BOOT继续保持低约10~20s，同时观察 Windows 设备管理器。

- 稳定：优先查 Flash 固件/启动逻辑。
- 仍循环断连：优先查 USB 物理链路。

### 2. 真正端到端核对

```text
ESP GPIO20(D+) → 22Ω → USBLC6-2SC6 → cable D+
ESP GPIO19(D-) → 22Ω → USBLC6-2SC6 → cable D-
```

不得仅依赖丝印或网络名称。

### 3. ESD 隔离测试

USBLC6-2SC6 标准内部 I/O 对为 1↔6 与 3↔4；Pin2=GND，Pin5=VBUS。若具备返修能力，可临时拆 ESD 并直通 D+/D- I/O 路径以隔离 ESD 器件因素，同时保留 22Ω 串阻。调试状态无 ESD 保护。

### 4. 设备管理器记录

记录提示音对应的设备条目及错误，如 `Unknown USB Device` / `Device Descriptor Request Failed`，用于判断是否已进入描述符阶段。
