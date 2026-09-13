# 银龄智护 V1 双镜腿分区架构

## 当前状态

`ARCHITECTURE_FROZEN / FPC_MECHANICAL_PENDING / MAGNETIC_FOOTPRINT_PENDING`

2026-08-27 起，本文件采用 **8Pin 中央 FPC + A 侧主动电子集中 + B 侧被动端点** 架构。此前 12Pin / B 侧 DRV2605L 方案保留在 Git 历史和 D-014 中，不再作为当前 PCB 输入。

A / B 当前仍是工程分区名，不强制绑定左/右镜腿。

## 设计目标

1. 电池与主逻辑分居两侧，用电池作为主要配重；
2. 中央 FPC 导体数量尽可能少；
3. Camera DVP、USB、I2S、I2C、RF 等高速/敏感数字信号不跨镜腿；
4. B 板尽量被动化，减少远端供电、总线和主动 IC 故障点；
5. 保留双 Bone、双 LRA、双 DRV2605L 独立控制等既定功能；
6. BAT+/GND 用双 Pin 并联保证原型阶段的电源可靠性。

---

## A 区 — MAIN / SENSING / POWER / DRIVER

```text
A TEMPLE
├── MCU / SoC
│   ├── A-ESP: ESP32-S3-MINI-1U-N4R2
│   └── A-BK : BK7258QN88616
├── RF / antenna interface
├── OV5640 + Camera FPC
├── Camera 2.8V / Core LDO
├── ICS-43434
├── BMI270
├── PCA9540B
├── DRV2605L A
├── DRV2605L B
├── LRA A
├── MAX98357A
├── Bone A
├── BQ24074 Charger / PowerPath
├── TPS63021 SYS_3V3
├── 4Pin Magnetic USB/Charge interface
├── USB ESD / 5V protection
├── BOOT / EN / UART / Debug TP
└── J_INTER_A 8Pin
```

A 侧承担所有主动控制、高速接口和电源管理。

### A 侧明确不跨镜腿的网络

- Camera DVP / SCCB；
- USB D+ / D-；
- MCU RF；
- MIC I2S；
- MAX98357A I2S；
- SENSOR_I2C；
- PCA9540B CH0 / CH1 I2C；
- 双 DRV2605L IN/TRIG；
- SYS_3V3。

---

## B 区 — BATTERY / REMOTE ENDPOINT

```text
B TEMPLE
├── 1S LiPo Battery
├── Battery connector
├── Bone B
├── LRA B
├── optional TP_BAT_B / TP_GND_B
└── J_INTER_B 8Pin
```

B 侧当前不放：

- MCU；
- Camera；
- MAX98357A；
- PCA9540B；
- DRV2605L；
- 3V3 regulator；
- I2C pull-up；
- Trigger logic；
- USB / Magnetic interface。

B 板因此成为 **Battery + Remote Endpoint Board**。

---

## 8Pin Inter-Temple FPC — 冻结 Pin Map

| Pin | Net | 方向 | 说明 |
|---:|---|---|---|
| 1 | BAT+ | B → A | 与 Pin2 并联 |
| 2 | BAT+ | B → A | 与 Pin1 并联 |
| 3 | GND | A ↔ B | 与 Pin4 并联 |
| 4 | GND | A ↔ B | 与 Pin3 并联 |
| 5 | SPK_P | A → B | MAX98357A → Bone B |
| 6 | SPK_N | A → B | MAX98357A → Bone B |
| 7 | LRA_B_P | A → B | DRV2605L-B → LRA B |
| 8 | LRA_B_N | A → B | DRV2605L-B → LRA B |

逻辑 Pin 数和网络已经冻结。

尚未冻结：

- 具体 FPC 型号；
- Pitch；
- 长度；
- 铜厚；
- 连接器系列；
- 动态弯折寿命；
- 机械方向 / Pin1 朝向。

这些属于 `MECHANICAL_PENDING`。

---

## 为什么从 12Pin 改为 8Pin

旧 12Pin 方案把 DRV2605L-B 放在 B，因此中央需要额外传：

```text
SYS_3V3
HAPTIC_B_SCL
HAPTIC_B_SDA
HAPTIC_B_TRIG
```

新方案把 DRV2605L-B 搬回 A，只跨：

```text
LRA_B_P
LRA_B_N
```

因此取消 4 根逻辑/控制线，增加 2 根 LRA 差分驱动线，净减少 2 根；同时 BAT_NTC 和 Spare 也不再占中央 Pin，最终由 12Pin 收敛为 8Pin。

主要收益：

- I2C 不跨镜框；
- Trigger 不跨镜框；
- B 板不再需要 3V3；
- B 板主动器件减少；
- FPC 更窄、连接器更容易小型化；
- Bring-up 故障点减少。

主要代价：

- DRV2605L-B 到 LRA-B 的驱动线变长；
- 不再有 BAT_NTC 中央通道；
- 无 Spare Pin；
- 后续跨镜腿扩展能力下降。

---

## Haptic 架构

```text
A SENSOR_I2C
├── BMI270 @0x68
└── PCA9540B @0x70
      ├── CH0 → DRV2605L A @0x5A → LRA A
      └── CH1 → DRV2605L B @0x5A → 8Pin FPC → LRA B

MCU GPIO → DRV A IN/TRIG
MCU GPIO → DRV B IN/TRIG
```

两颗 DRV2605L 的 I2C、Trigger、VDD bypass 和 REG capacitor 全部留在 A。

### 远端 LRA 风险 Gate

首板必须验证：

- Auto Calibration；
- Back-EMF / resonance tracking；
- 启动/制动；
- 波形库效果；
- 连续运行；
- FPC 线阻导致的振动强度变化。

若 8Pin FPC 实际长度/线宽导致明显性能下降，再讨论 Driver 是否需要重新远端化；首版不提前回退。

---

## Bone Audio 架构

```text
MCU I2S → MAX98357A A
               ├── SPK_P/N → Bone A
               └── SPK_P/N → FPC → Bone B
```

两个 Bone 继续播放相同单声道。

`SPK_P / SPK_N` 为 BTL/Class-D 差分输出，禁止任一端接 GND。

---

## 电源与充电架构

当前冻结：

```text
4Pin Magnetic USB/Charge → A
BQ24074                  → A
TPS63021                  → A
Battery                   → B
```

电池通过中央 FPC：

```text
BAT+ ×2 → A
GND  ×2 → A/B主回流
```

### 为什么磁吸接口放 A

磁吸接口逻辑功能保持：

```text
5V
GND
USB D+
USB D-
```

放 A 可以让 USB D+/D- 直接连接 MCU，避免高速 USB 跨镜框。

### 当前磁吸接口仍未冻结的内容

由于尚未找到满足机械需求的具体磁吸连接器，目前不冻结：

- 型号；
- 长宽高；
- Footprint；
- PCB边缘开口；
- 外壳开孔；
- 安装方向。

其**逻辑位置在 A**已经冻结。

### Battery NTC

当前 8Pin 不包含 BAT_NTC。

如果首版 BQ24074 不读取电池包 NTC，则 TS 必须在 A 板按数据手册做合法本地偏置/处理，不允许悬空。

---

## FPC 电源可靠性 Gate

BAT+ 和 GND 各使用两个并联 Pin 的原因是载流和接触可靠性，而不是不同电压。

选择具体 FPC/连接器后必须核算：

- 单 Pin 额定电流；
- BAT+ 两 Pin 总载流；
- GND 两 Pin 总回流；
- 铜阻；
- Connector contact resistance；
- 峰值压降；
- 温升。

若具体器件证明 8Pin 无法满足峰值载流，应优先更换更高额定电流的 8Pin FPC/连接器或加宽铜导体，而不是未经评估直接改回 12Pin。

---

## A/B 重量原则

功能数量不要求左右相等。

A 放主动电子和高速信号；B 放 Battery + Bone B + LRA B。由于 Battery 通常是最大的单体质量块，重量平衡由机械样机实际称重决定，不用 IC 数量判断。

后续通过：

- Battery 容量 / 形状；
- Battery 在镜腿前后位置；
- PCB 长度；
- 外壳体积分布；

微调重心。

---

## 原理图 / PCB 强制要求

原理图必须保持两个独立板级设计单元：

```text
[A-ESP or A-BK]
       │
   J_INTER_A
       ║ 8Pin FPC
   J_INTER_B
       │
 [B-COMMON]
```

禁止 A/B 元件继续处在同一 PCB NetGraph。

Board B 当前无 SYS_3V3、I2C、Trigger，因此旧 B 侧这些器件/网络不得被旧模板重新带入。

任何自动化或人工检查都应以：

- `decision-log.md` D-015；
- `inter-temple-fpc.csv`；
- `temple-partition.csv`；
- `reviews/20260827-8pin-two-temple-architecture-freeze.md`；

作为当前最高优先级架构输入。