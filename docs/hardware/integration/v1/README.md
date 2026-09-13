# 银龄智护 V1 双主控硬件方案

本目录是银龄智护 V1 眼镜端硬件集成的当前工程基线。当前阶段并行保留两套主控路线；除主控及其必需外围差异外，Camera、IMU、MIC、Audio、Haptic、USB/充电、供电和 Android 侧功能定义尽量保持一致。

> 当前边界：比赛 / 初期开发版本，外壳采用 **3D 打印**。

## 方案 A — ESP32-S3

**主控：ESP32-S3-MINI-1U-N4R2**

- 4 MB Flash + 2 MB PSRAM；
- 15.4 × 15.4 mm；
- 外置 2.4 GHz 天线；
- 现有 ESP32 实验代码最容易迁移；
- 当前业务 GPIO / Pin Matrix 已冻结；
- 2 MB PSRAM 的全功能并发能力属于后续系统验证项。

## 方案 B — BK7258

**主控候选采购料号：BK7258QN88616（供应商标注 8 MB Flash + 16 MB PSRAM）**

- 资源和媒体能力更宽裕；
- 当前业务 GPIO / GPIO Group Pin Matrix 已冻结；
- 完整订货码、Flash/PSRAM 配置、封装和温度等级仍需采购前确认；
- QFN88 的 Reset / Boot / RF / 下载等封装级外围仍需按 Beken Hardware Reference Design 复核；
- Camera + Audio + Wi-Fi + BLE 并发能力与 SDK 成熟度属于系统验证项。

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
                           ├→ 8Ω Bone A
                           └→ 8Pin FPC → 8Ω Bone B
                         （同一单声道）

触觉控制：
SENSOR_I2C
├── BMI270 @0x68
└── PCA9540B @0x70
      ├── CH0 → DRV2605L A @0x5A → LRA A
      └── CH1 → DRV2605L B @0x5A → 8Pin FPC → LRA B

两颗 DRV2605L 均位于 A 板，左右 Driver 分别使用独立 MCU IN/TRIG GPIO。
```

### 当前冻结数量

- OV5640 ×1；
- BMI270 ×1；
- ICS-43434 ×1；
- MAX98357A ×1；
- 8Ω 骨传导单元 ×2；
- PCA9540B ×1；
- DRV2605L ×2；
- 0809 X 轴 LRA ×2；
- BQ24074 ×1；
- TPS63021 ×1；
- 1S LiPo ×1；
- 4Pin Magnetic USB/Charge 逻辑接口 ×1：5V / GND / USB D+ / USB D-；
- 中央 8Pin FPC ×1 套。

## 2026-08-27 当前架构冻结：8Pin 双镜腿

当前 PCB 目标：

```text
A-ESP      → ESP32-S3 主控 A 板
A-BK       → BK7258 主控 A 板
B-COMMON   → 两套主控共用的被动端点 B 板
```

### A 板

集中主动电子和高速接口：

- MCU / RF；
- Camera / Camera Power；
- MIC；
- IMU；
- PCA9540B；
- DRV2605L A + DRV2605L B；
- LRA A；
- MAX98357A + Bone A；
- BQ24074；
- TPS63021；
- Magnetic USB/Charge；
- USB/5V Protection；
- Debug / Recovery；
- J_INTER_A 8Pin。

### B 板

当前为被动/端点板：

- 1S LiPo；
- Battery connector；
- Bone B；
- LRA B；
- J_INTER_B 8Pin；
- 可选 BAT/GND TP。

B 板当前无 DRV2605L、无 SYS_3V3、无 I2C/Trigger logic。

### 中央 8Pin 固定映射

```text
Pin1  BAT+
Pin2  BAT+
Pin3  GND
Pin4  GND
Pin5  SPK_P
Pin6  SPK_N
Pin7  LRA_B_P
Pin8  LRA_B_N
```

中央不再传：SYS_3V3、HAPTIC SDA/SCL/TRIG、BAT_NTC、USB、I2S、Camera DVP、RF。

详细冻结说明：[`reviews/20260827-8pin-two-temple-architecture-freeze.md`](./reviews/20260827-8pin-two-temple-architecture-freeze.md)。

## Magnetic USB/Charge — 逻辑位置已冻结、机械件待定

接口固定放在 A 板，以保持 USB D+/D- 本地连接 MCU，并与 BQ24074 同侧。

逻辑定义继续为：

- 5V；
- GND；
- USB D+；
- USB D-。

但当前尚未找到满足机械需求的具体磁吸接口，因此以下仍为 TBD：型号、尺寸、Footprint、板边位置、外壳开口和安装方向。

## 双路触觉总线 — 已冻结

两颗 DRV2605L 均为固定 `0x5A`，统一采用 `PCA9540B` 进行地址隔离：

```text
SENSOR_I2C → PCA9540B @0x70
               ├── CH0 → DRV_A @0x5A
               └── CH1 → DRV_B @0x5A
```

两颗 Driver 现在都位于 A 板。B 侧 LRA 通过中央 FPC 的 `LRA_B_P/N` 直接连接 A 板 DRV-B 输出。

首板需要重点验证远端 LRA 的线阻、Auto Calibration、Back-EMF/谐振跟踪和振动强度。

## 两套 GPIO Matrix

### Plan A

已冻结：Camera DVP + SCCB、Native USB、SENSOR_I2C、BMI270 INT1、LEFT/RIGHT Haptic Trigger、ICS-43434 + MAX98357A I²S、UART0 Recovery、EN/BOOT。

详见：[`plan-a-esp32-s3-mini-1u-n4r2/pin-matrix.csv`](./plan-a-esp32-s3-mini-1u-n4r2/pin-matrix.csv)。

### Plan B

已完成业务 GPIO / GPIO Group 冻结：Camera CIS/JPEG DVP、Camera I2C1、Sensor I2C0、MIC I²S Group0、AMP I²S Group2、USB、BMI INT、双 Haptic Trigger。

BK7258 QFN88 的 Reset / Boot / RF / 下载等封装级专用脚继续按原厂 Hardware Reference Design 逐 Pin 核对。

详见：[`plan-b-bk7258qn88616/pin-matrix.csv`](./plan-b-bk7258qn88616/pin-matrix.csv)。

## 开发阶段测试 / Recovery

A 板 Mandatory：

- TP_GND；
- TP_3V3；
- TP_EN / RESET；
- TP_BOOT / DOWNLOAD。

Recommended：TP_UART_TX / RX。

B 板当前为被动端点板，如空间允许只保留 TP_BAT_B / TP_GND_B。

## 当前执行方式

本轮正式原理图和 PCB 人工绘制/检查。旧自动生成 `.eprj2` 仅作为电气参考和问题样本，不直接作为最终 PCB 生成源。

执行节奏：

1. 8Pin A/B 架构已冻结；
2. 完成 A-ESP、A-BK、B-COMMON 人工原理图；
3. 完成三块 PCB、生产检查、BOM/CPL并下单；
4. PCB等待期重点完成3D结构和PPG实验方案；
5. 目标 2026-09-02 到货；未到则依赖实板任务顺延；
6. 到货后两天完成焊接、烧录、通讯、外围、FPC和装壳测试。

完整执行计划：[`reviews/20260826-prototype-sprint-plan.md`](./reviews/20260826-prototype-sprint-plan.md)。

## 当前权威输入

当前原理图/PCB优先读取：

1. [`common/decision-log.md`](./common/decision-log.md) — D-015；
2. [`common/dual-temple-partition.md`](./common/dual-temple-partition.md)；
3. [`common/inter-temple-fpc.csv`](./common/inter-temple-fpc.csv)；
4. [`common/temple-partition.csv`](./common/temple-partition.csv)；
5. [`common/component-ownership.csv`](./common/component-ownership.csv)；
6. [`reviews/20260827-8pin-two-temple-architecture-freeze.md`](./reviews/20260827-8pin-two-temple-architecture-freeze.md)。

历史 12Pin 文档若与以上文件冲突，以 D-015 和 20260827 Freeze 为准。