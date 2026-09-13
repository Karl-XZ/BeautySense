# 银龄智护 V1 — 8Pin 双镜腿硬件架构冻结

日期：2026-08-27
状态：`ARCHITECTURE_FROZEN`

本文件记录 2026-08-27 人工确认的当前首版原型硬件架构。它用于替代此前 12Pin / B 侧主动 Haptic Driver 的镜腿分区基线。

## 1. 冻结目标

当前首版原型优先级：

1. 中央跨镜框 FPC 导体数量尽量少；
2. 高速/敏感数字信号不跨镜腿；
3. 电池与主逻辑分居两侧，用电池作为主要配重；
4. B 板尽量被动化，降低远端板故障点和 Bring-up 难度；
5. 保留双 Bone、双 LRA、双 DRV2605L 独立控制、Camera、MIC、IMU、USB/充电等既定核心功能；
6. A/B 为工程分区名，当前不绑定物理左/右镜腿。

## 2. 当前板级形态

仍并行保留两套 A 板：

```text
A-ESP      = ESP32-S3-MINI-1U-N4R2 主控版本
A-BK       = BK7258QN88616 主控版本
B-COMMON   = 两套主控共用的被动/端点板
```

### A 板 — MAIN / SENSING / POWER / DRIVER

A 板集中放置主动电子器件和高速接口：

- MCU / SoC：ESP32-S3 或 BK7258；
- RF / antenna interface；
- OV5640 + Camera FPC；
- Camera 2.8V / Core LDO；
- ICS-43434；
- BMI270；
- PCA9540B；
- DRV2605L A；
- DRV2605L B；
- LRA A；
- MAX98357A；
- Bone A；
- BQ24074 Charger / PowerPath；
- TPS63021 SYS_3V3；
- 4Pin Magnetic USB/Charge 接口及 USB/5V 保护；
- BOOT / EN / UART / Debug TP；
- J_INTER_A 8Pin FPC connector。

### B 板 — BATTERY / REMOTE ENDPOINT

B 板不再放主动 Haptic Driver，也不再需要 SYS_3V3/I2C/Trigger：

- 1S LiPo Battery；
- Battery connector；
- Bone B；
- LRA B；
- J_INTER_B 8Pin FPC connector；
- 可选 BAT/GND 测试点。

B 板当前无 MCU、无 DRV2605L、无 PCA9540B、无 Audio AMP、无 Camera 电源、无 3V3 逻辑电源。

## 3. 8Pin Inter-Temple FPC — 已冻结

逻辑 Pin Map：

| Pin | Net | 方向 | 用途 |
|---:|---|---|---|
| 1 | BAT+ | B → A | 电池主电源，与 Pin2 并联 |
| 2 | BAT+ | B → A | 电池主电源，与 Pin1 并联 |
| 3 | GND | A ↔ B | 主回流，与 Pin4 并联 |
| 4 | GND | A ↔ B | 主回流，与 Pin3 并联 |
| 5 | SPK_P | A → B | MAX98357A 差分输出到 Bone B |
| 6 | SPK_N | A → B | MAX98357A 差分输出到 Bone B |
| 7 | LRA_B_P | A → B | A 板 DRV2605L-B 差分输出到远端 LRA B |
| 8 | LRA_B_N | A → B | A 板 DRV2605L-B 差分输出到远端 LRA B |

当前 8Pin 中明确不包含：

- SYS_3V3；
- HAPTIC_B_SCL；
- HAPTIC_B_SDA；
- HAPTIC_B_TRIG；
- BAT_NTC；
- USB D+/D-；
- Camera DVP；
- I2S；
- Spare。

FPC **Pin 数量与逻辑网络已冻结**；实际 FPC/连接器型号、Pitch、长度、铜厚、动态弯折寿命和机械方向仍为 `MECHANICAL_TBD`。

## 4. Haptic 架构 — 两颗 Driver 都在 A

```text
SENSOR_I2C
├── BMI270 @0x68
└── PCA9540B @0x70
      ├── CH0 → DRV2605L A @0x5A → LRA A（本地）
      └── CH1 → DRV2605L B @0x5A → FPC LRA_B_P/N → LRA B（远端）

MCU GPIO → DRV A IN/TRIG
MCU GPIO → DRV B IN/TRIG
```

因此 I2C、Trigger、DRV 去耦和 REG 电容全部留在 A 板，不跨镜框。

主要新增验证风险：DRV2605L-B 到 LRA-B 的差分驱动路径经过 FPC，首板需验证线阻、Auto Calibration、Back-EMF/谐振跟踪和振动强度。

## 5. Audio 架构 — AMP 留 A

```text
MCU I2S → MAX98357A A
               ├── SPK_P/N → Bone A
               └── SPK_P/N → 8Pin FPC → Bone B
```

两个 Bone 继续播放相同单声道并联输出。`SPK_P / SPK_N` 均为 BTL/Class-D 差分端，禁止任一端接 GND。

## 6. 电源与充电接口 — A 侧冻结

当前冻结：

```text
4Pin Magnetic USB/Charge → A
BQ24074 Charger/PowerPath → A
TPS63021 SYS_3V3 → A
Battery → B
```

磁吸接口逻辑功能维持：

- 5V；
- GND；
- USB D+；
- USB D-。

USB D+/D- 保持 A 板本地连接 MCU，不经过中央 FPC。

磁吸接口的**具体器件型号、尺寸、封装和机械开口仍未冻结**，状态为 `MECHANICAL_FOOTPRINT_TBD`；只有其逻辑位置与功能冻结在 A。

Battery BAT+ 通过 8Pin FPC 的 Pin1/Pin2 并联送至 A；GND 通过 Pin3/Pin4 并联回流。

V1 8Pin 基线不把 Battery NTC 跨到 A。若 BQ24074 TS 功能在首版不使用电池包 NTC，应按 BQ24074 数据手册在 A 板进行合法本地偏置/处理，不允许 TS 悬空。

## 7. 与旧 12Pin 方案的关系

本文件明确替代旧方案中的：

- 12Pin inter-temple baseline；
- B 侧 DRV2605L B；
- B 侧 Haptic I2C pull-up；
- B 侧 3V3 rail / decoupling；
- HAPTIC_B SDA/SCL/TRIG 跨 FPC；
- BAT_NTC 跨 FPC；
- FPC Spare。

旧文档保留作为历史设计记录，不再作为当前 PCB 输入。

## 8. 当前首板主要风险 / Gate

### Gate A — FPC 电源载流

必须在选择具体 8Pin FPC/连接器后确认：

- 单 Pin 额定电流；
- Pin1+Pin2 的 BAT+ 总载流；
- Pin3+Pin4 的 GND 总载流；
- 峰值压降；
- 接触电阻与温升。

### Gate B — 远端 LRA 驱动

首板验证：

- DRV2605L-B Auto Calibration；
- LRA 启动/制动；
- 反电动势检测；
- 预设波形强度；
- 连续运行温升；
- FPC 长度/线阻对触觉强度的影响。

### Gate C — Class-D 远端 Bone

检查 SPK_P/N 的 FPC 布线、EMI、串扰和远端 Bone 输出，不与 BAT 电源线形成不必要的长距离敏感耦合。

### Gate D — 磁吸接口机械件

当前仅冻结在 A 侧。未找到合适实际器件前，不冻结孔位、外壳开口、焊盘和板边位置。

## 9. 当前结论

```text
BOARD_ARCHITECTURE       = FROZEN
A_ACTIVE_ELECTRONICS     = FROZEN
B_PASSIVE_ENDPOINT       = FROZEN
INTER_TEMPLE_PIN_COUNT   = 8 FROZEN
INTER_TEMPLE_LOGIC_MAP   = FROZEN
MAGNETIC_PORT_SIDE       = A FROZEN
MAGNETIC_PORT_FOOTPRINT  = TBD
FPC_PART/PITCH/LENGTH    = TBD
A/B LEFT-RIGHT BINDING   = TBD
```

本文件与 `common/decision-log.md`、`common/inter-temple-fpc.csv`、`common/temple-partition.csv` 共同作为当前人工重画原理图/PCB的最高优先级输入。