# ESP32-S3-MINI-1U 3.3V 短路修复后的测试顺序

日期：2026-09-06

已知故障：ESP32-S3-MINI-1U 核心模组底部多处焊盘连锡，导致 3.3V 电源轨对 GND 硬短。短路已人工修复。

## 目标

短路消失不等于整板已合格。修复后先证明：

1. 3.3V 不再硬短；
2. ESP 模组周围不存在新的相邻信号桥连；
3. 5V→BQ24074→TPS63021→3.3V 电源链路正常；
4. ESP EN/BOOT/下载链路正常；
5. 之后再逐个启用外设。

## Step 1：完全断电复测

外设全部断开：Battery、Camera、B板/FPC、LRA、Bone、USB。

记录：

- 3V3→GND 稳定电阻；
- 5V_IN→GND；
- SYS_PWR→GND；
- 2V8→GND；
- 1V5→GND。

不得只凭蜂鸣档判定。3V3 不应再长期稳定在接近 0Ω。

## Step 2：ESP 模组焊点复核

因为根因是模组底部连锡，短路修复后仍需检查相邻不同网络之间是否存在 0Ω 级桥连，重点关注：

- 3V3 / GND；
- EN；
- BOOT；
- USB D+ / D-；
- UART TX / RX；
- 可访问的相邻 GPIO。

信号网络经过芯片内部 ESD 结构时可能存在二极管读数，因此以“近似 0Ω 的硬连通”为主要异常线索，不应把任意蜂鸣都当作桥连。

## Step 3：第一次修复后上电

优先使用可调电源从 5V_IN 注入：

- 5.0V；
- 初始限流 100mA；
- 外设仍全部断开。

观察 3~5 秒：

- 是否立即进入恒流；
- 输入电压是否塌陷；
- TPS63021、ESP 模组或其他 IC 是否快速升温。

100mA 无异常后可升到 200~300mA进行最小系统测试。若要让 ESP 完整启动，限流可能需要继续提高，但必须在确认无异常发热和电源轨稳定后进行。

## Step 4：测电源树

记录实际值：

| 节点 | 目标 |
|---|---|
| 5V_IN | 约 5.0V |
| SYS_PWR / BQ24074 OUT | 按当前 PowerPath 状态实测 |
| 3V3 / TPS63021 OUT | 约 3.3V |
| ESP 3V3 | 应与系统 3V3 一致 |
| EN | 正常启动应为高电平 |

2V8/1V5 是否出现取决于当前 LDO EN/电路状态；不可在未确认 EN 逻辑前把“未出现”直接判为故障。

## Step 5：只验证 ESP 最小系统

在 3.3V 正常后：

1. 检查 EN、BOOT 静态状态；
2. 尝试进入下载模式；
3. PC/下载工具识别 ESP；
4. 读取芯片信息；
5. 烧录最简单串口输出或 GPIO 翻转程序；
6. 暂时不要启用 Wi-Fi、Camera、音频或触觉。

如果下载失败，优先检查：3V3、EN、BOOT、USB D+/D- 连通性、22Ω 串阻、USBLC6，而不是直接怀疑 ESP 模组损坏。

## 2026-09-06 实测进展

已使用 Windows + esptool v5.2.0，通过原生 USB 在 `COM8` 成功连接 ESP32-S3，并执行：

```powershell
python -m esptool --chip esp32s3 chip-id
```

esptool 实测返回：

```text
Chip type: ESP32-S3 (QFN56) (revision v0.2)
Embedded Flash 4MB (XMC)
Embedded PSRAM 2MB (AP_3v3)
Crystal frequency: 40MHz
USB mode: USB-Serial/JTAG
Stub flasher running.
```

这一步确认：

- ESP32-S3 本体可进入 ROM 下载模式；
- EN / BOOT 操作有效；
- 原生 USB 枚举与下载链路工作；
- D+ / D-、22Ω 串联电阻、ESD 链路至少在当前测试下可工作；
- 当前模组实际内存配置与 `ESP32-S3-MINI-1U-N4R2` 的 4MB Flash + 2MB PSRAM 相符；
- `ESP32-S3 has no chip ID. Reading MAC address instead.` 为 esptool 的正常提示，不是故障。

下一步：执行 `flash-id`，随后烧录最小测试固件。当前不执行 `erase-flash`。

## Step 6：外设逐个恢复

ESP 最小系统通过后按顺序：

1. BMI270；
2. PCA9540B；
3. DRV2605L 左/右；
4. 数字麦克风；
5. MAX98357A（先无负载/低音量）；
6. Camera 电源与 SCCB；
7. Camera DVP；
8. LRA；
9. Bone；
10. B板/FPC；
11. Battery / charging / PowerPath。

每加一个负载都记录输入电流和 3.3V 是否稳定。

## 当前状态

- 3.3V 硬短：REPAIRED
- 已知根因：ESP 核心模组底部多焊盘连锡
- 3.3V 上电实测：约 3.4V，稳定
- EN 上电实测：约 3.4V，稳定
- BOOT/EN 下载模式：PASS
- ESP ROM：PASS
- USB 枚举/下载链路：PASS
- esptool chip-id：PASS
- 模组识别：ESP32-S3 QFN56 rev v0.2
- Flash：4MB，已由 esptool 识别；待 `flash-id` 进一步确认
- PSRAM：2MB，已由 esptool 识别
- ESP 最小系统：初步 PASS，待最小固件烧录
- 外设：NOT TESTED
