# 银龄智护 V1 双主控硬件方案

本目录是银龄智护 V1 眼镜端硬件集成的当前工程基线。当前阶段并行保留两套主控路线；除主控及其必需外围差异外，Camera、IMU、MIC、Audio、Haptic、USB/磁吸接口、供电和 Android 侧功能定义尽量保持一致。

> 当前边界：比赛 / 初期开发版本，外壳采用 **3D 打印**。本项目不引入其它项目中的金属外壳、金属触控等约束。

## 方案 A — ESP32-S3

**主控：ESP32-S3-MINI-1U-N4R2**

- 4 MB Flash + 2 MB PSRAM；
- 15.4 × 15.4 mm；
- 外置 2.4 GHz 天线；
- 现有 ESP32 实验代码最容易迁移；
- 当前业务 GPIO / Pin Matrix 已冻结；
- 2 MB PSRAM 的全功能并发能力属于后续系统验证项，不作为当前原理图网络是否正确的判据。

## 方案 B — BK7258

**主控候选采购料号：BK7258QN88616（供应商标注 8 MB Flash + 16 MB PSRAM）**

- 资源和媒体能力更宽裕；
- 当前业务 GPIO / GPIO Group Pin Matrix 已冻结；
- 完整订货码、Flash/PSRAM 配置、封装和温度等级仍需采购前向供应商/原厂确认；
- QFN88 的 Reset / Boot / RF / 下载等封装级外围仍需按 Beken Hardware Reference Design 复核；
- Camera + Audio + Wi-Fi + BLE 的并发能力与 SDK 成熟度属于系统验证项。

## V1 当前统一硬件基线

```text
                           Android 手机
                  AI / ASR / LLM / TTS / App
                               │
                         Wi-Fi / BLE
                               │
                    ┌──────────▼──────────┐
                    │   Plan A / Plan B  │
                    │   主控 MCU / SoC   │
                    └──────────┬──────────┘
             ┌─────────────────┼─────────────────┐
             │                 │                 │
          OV5640           ICS-43434          BMI270
        DVP + SCCB           I²S RX          I²C + INT1

主控 I²S TX → MAX98357A → SPK+/SPK-
                           ├→ 8Ω Bone LEFT
                           └→ 8Ω Bone RIGHT
                         （同一单声道，并联）

触觉控制：
SENSOR_I2C
├── BMI270 @0x68
└── PCA9540B @0x70
      ├── CH0 → DRV2605L LEFT  @0x5A → 0809 LRA LEFT
      └── CH1 → DRV2605L RIGHT @0x5A → 0809 LRA RIGHT

主控独立 GPIO → LEFT / RIGHT DRV2605L IN/TRIG
```

### 当前冻结数量

- OV5640 ×1；
- BMI270 ×1；
- ICS-43434 ×1；
- MAX98357A ×1；
- 8Ω 骨传导单元 ×2，标称约 1~1.5 W/只，播放相同单声道；
- PCA9540B ×1；
- DRV2605L ×2；
- 0809 X 轴 LRA ×2；
- 1S LiPo ×1；
- 4 Pin 磁吸接口 ×1：5V / GND / USB D+ / USB D-。

## 双路触觉总线 — 已冻结

两颗 DRV2605L 均为固定 `0x5A`。V1 两套主控统一采用 `PCA9540B` 进行地址隔离：

```text
SENSOR_I2C → PCA9540B @0x70
               ├── CH0 → DRV_LEFT  @0x5A
               └── CH1 → DRV_RIGHT @0x5A
```

左右 Driver 另各有一根独立 `IN/TRIG` GPIO。同步预设效果的策略是：分别通过 MUX 配置 LEFT / RIGHT，再通过两个 Trigger GPIO 触发。

PCA9540B 是 1-of-2 MUX，因此严格双路高刷新率 RTP 同步更新不作为当前 V1 硬要求；如后续需要，再升级总线或 Driver 架构。

详细设计见 [`common/dual-haptic-bus.md`](./common/dual-haptic-bus.md)。

## 两套 GPIO Matrix — 已完成

### Plan A

已完成并冻结：

- Camera DVP + SCCB；
- Native USB；
- SENSOR_I2C；
- BMI270 INT1；
- LEFT / RIGHT Haptic Trigger；
- ICS-43434 + MAX98357A I²S；
- UART0 Recovery；
- EN / BOOT；
- 4 路 clean spare GPIO。

详见：[`plan-a-esp32-s3-mini-1u-n4r2/pin-matrix.csv`](./plan-a-esp32-s3-mini-1u-n4r2/pin-matrix.csv)。

### Plan B

已完成业务 GPIO / GPIO Group 冻结：

- Camera CIS/JPEG DVP fixed group；
- Camera I2C1；
- Sensor I2C0；
- MIC I²S Group0；
- AMP I²S Group2；
- USB；
- BMI INT / 双 Haptic Trigger；
- 大量 GPIO 余量。

BK7258 QFN88 的 Reset / Boot / RF / 下载等**封装级专用脚**继续在正式原理图阶段按原厂 Hardware Reference Design 逐 Pin 核对。

详见：[`plan-b-bk7258qn88616/pin-matrix.csv`](./plan-b-bk7258qn88616/pin-matrix.csv)。

## 开发阶段测试 / 恢复接口

V1 开发板采用“最少但可救板”的测试点策略：

**Mandatory：**
- TP_GND；
- TP_3V3；
- TP_EN / RESET；
- TP_BOOT / DOWNLOAD。

**Recommended：**
- TP_UART_TX；
- TP_UART_RX。

**Optional（空间允许）：**
- TP_5V；
- TP_VBAT；
- 关键 I²C / Camera Rail 测试点。

禁止给所有网络无差别增加 TP。

## 当前文档结构

- [`common/`](./common/)：当前统一 BOM、需求、决策、双 Haptic 总线、接口、问题台账、主控比较；
- [`plan-a-esp32-s3-mini-1u-n4r2/`](./plan-a-esp32-s3-mini-1u-n4r2/)：ESP32-S3 方案；
- [`plan-b-bk7258qn88616/`](./plan-b-bk7258qn88616/)：BK7258 方案；
- [`references/`](./references/)：原厂资料和历史实验资料。

## 当前执行方式（2026-08-26更新）

当前产品原型进入人工PCB冲刺阶段。旧自动生成 `.eprj2` 保留为电气参考和问题样本，但不直接作为最终PCB生成源。

本轮并行保留：

```text
A-ESP      → ESP32-S3主控A板
A-BK       → BK7258主控A板
B-COMMON   → 两套主控尽量共用的B板
```

执行节奏：

1. 8/26晚冻结三板边界、FPC、连接器和关键机械边界；
2. 8/27完成三份人工原理图；
3. 8/28完成三块PCB、生产检查、BOM/CPL并下单；
4. 8/29–9/1等待PCB期间重点完成3D眼镜结构和PPG心率/血氧实验方案；
5. 目标9/2 PCB到货；若未到，依赖实板的测试任务整体顺延1天；
6. 到货后用两天完成焊接、烧录、基础通讯、关键外围、FPC/双板和装壳测试；
7. 测试后再根据真实结果冻结后续实验计划。

完整当前计划：[`reviews/20260826-prototype-sprint-plan.md`](./reviews/20260826-prototype-sprint-plan.md)。

此前的双PCB边界、外围Ownership和Harness审查仍作为工程规则继续有效：[`reviews/20260810-next-step-two-board-schematic-plan.md`](./reviews/20260810-next-step-two-board-schematic-plan.md)。
