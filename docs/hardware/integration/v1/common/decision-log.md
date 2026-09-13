# 银龄智护 V1 设计决策记录

本文件记录已经由项目负责人明确确认或在原厂资料基础上完成工程冻结的决定，用于防止后续 Skill / Codex 重复读取过期结论。

## 2026-08-10 当前有效决策

### D-001 主控并行两套

- Plan A：ESP32-S3-MINI-1U-N4R2；
- Plan B：BK7258QN88616（8+16 供应商候选，采购前核对完整料号）。

状态：`CONFIRMED`。

### D-002 MIC

V1 当前原理图和采购基线采用 `ICS-43434 ×1`。

INMP441 保留为历史实验验证资料，不要求当前版本换回 INMP441。

状态：`CONFIRMED`。

### D-003 Bone 数量与声道

- 8Ω Bone ×2；
- 每只标称约 1~1.5 W；
- 两只播放完全相同的单声道；
- 不要求左右立体声；
- MAX98357A ×1；
- 两只 Bone 并联跨接 MAX98357A `SPK+ / SPK-`；
- 禁止任一端接 GND。

状态：`CONFIRMED`。

### D-004 Haptic 数量

- DRV2605L ×2；
- 0809 X-axis LRA ×2；
- 左右分别控制；
- LEFT / RIGHT 也允许同时触发。

状态：`CONFIRMED`。

### D-005 双 DRV2605L 总线架构

两颗 DRV2605L 均使用固定 `0x5A` 地址。V1 两套主控统一增加：

- `PCA9540B ×1`，上游固定地址 `0x70`；
- 上游接 `SENSOR_I2C`；
- CH0 接 `DRV2605L LEFT @0x5A`；
- CH1 接 `DRV2605L RIGHT @0x5A`；
- BMI270 `0x68` 留在 MUX 上游；
- 左右 DRV 的 `IN/TRIG` 分别连接独立 MCU GPIO。

这样 Camera 可以继续占用另一套独立 SCCB/I²C，Plan A / Plan B 使用同一外围架构。

PCA9540B 同一时刻只选通一个下游 I²C 通道，因此 V1 的同步触觉策略为：先分别配置 LEFT / RIGHT，再通过两个 `IN/TRIG` GPIO 触发预设波形。严格双路高频 RTP 同步更新不作为 V1 硬要求。

DRV2605L EN 不用于地址隔离。

状态：`CONFIRMED / SCHEMATIC_REQUIRED`。

### D-006 TP / Recovery

开发阶段需要测试点，但数量必须精简。

Mandatory：

- TP_GND；
- TP_3V3；
- TP_EN / RESET；
- TP_BOOT / DOWNLOAD。

Recommended：

- TP_UART_TX；
- TP_UART_RX。

Optional：TP_5V、TP_VBAT、关键 Bus/Rail。

状态：`CONFIRMED`。

### D-007 4Pin 磁吸

V1 逻辑接口继续使用：

- 5V；
- GND；
- USB D+；
- USB D-。

用于充电、Native USB 烧录、日志和开发数据。

2026-08-27 进一步冻结：接口位于 A 板，靠近 MCU USB / BQ24074；具体磁吸器件型号、尺寸、封装、板边位置和外壳开口仍待找到合适实际器件后确定。

状态：`LOGIC_AND_SIDE_CONFIRMED / MECHANICAL_FOOTPRINT_PENDING`。

### D-008 Camera FPC

当前 24Pin 电气映射审查未发现“大量 Pin 全部接 GND”的问题。FPC 接触面 / Pin1 / 排线机械方向属于首板前机械确认项，不作为当前重新绘制的主要阻塞问题。

状态：`ELECTRICAL_ACCEPTED / MECHANICAL_PENDING`。

### D-009 Camera RESET / PWDN

控制电平是否需要额外处理尚未最终确认。依据最终 OV5640 模组资料判断，资料不足时保持 TBD，不自动增加电平转换。

状态：`PENDING`。

### D-010 ESP32 4MB + 2MB 内存理解

ESP32-S3-MINI-1U-N4R2 为 4 MB Flash + 2 MB PSRAM；两者不是 6 MB 通用 RAM。

2 MB PSRAM 是否足够整机并发属于系统压力测试，不作为当前原理图网络错误。

状态：`SYSTEM_VALIDATION_PENDING`。

### D-011 电源峰值预算

可以做 Datasheet 估算，但暂不作为重新绘制原理图的硬阻塞项；首板阶段再结合实测峰值电流、压降和温升验证。

状态：`SYSTEM_VALIDATION_PENDING`。

### D-012 Plan A 音频引脚策略

ESP32-S3 采用一个 I²S 控制器的标准全双工结构，让 ICS-43434 与 MAX98357A 共享 BCLK / WS，使用独立 DIN / DOUT。两者均采用标准 I²S，当前按相同采样率 / frame timing 设计。

目的：减少 GPIO 占用，同时保留第二套 I²S 控制器作为扩展余量。

状态：`PIN_MATRIX_FROZEN / BENCH_VALIDATION_PENDING`。

### D-013 Plan B 音频引脚策略

BK7258 为降低首版 SDK 配置风险，MIC 与 AMP 暂使用不同 I²S Group：

- ICS-43434 → I²S Group 0；
- MAX98357A → I²S Group 2。

BK7258 GPIO 余量足够，不需要为了省 2 个 GPIO 强制共用同一组时钟。

状态：`PIN_MATRIX_FROZEN`。

### D-014 双镜腿 A/B 物理分区（历史基线）

产品最终为眼镜形态，电子器件必须分布在两个镜腿，通过跨镜框 FPC / 排线互连。此前首版基线为 A 主逻辑、B 电池 + 远端 DRV2605L，中央使用 12 conductor FPC。

该决策中的“双板边界、Battery 与主逻辑分居、高速信号不跨镜腿”原则继续有效；其 **12Pin FPC、B 侧 DRV2605L、B 侧 SYS_3V3/I2C/Trigger** 已被 D-015 替代。

状态：`SUPERSEDED_BY_D-015`。

## 2026-08-27 新冻结决策

### D-015 8Pin 双镜腿 + B 被动端点架构

Supersedes：`D-014` 中的 12Pin FPC 与 B 侧主动 Haptic Driver 分区。

当前首版固定为：

**A — MAIN / SENSING / POWER / DRIVER**

- MCU / SoC + RF / 启动 / 下载；
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
- 4Pin Magnetic USB/Charge + ESD/5V protection；
- 主 Debug / Recovery TP；
- J_INTER_A 8Pin。

**B — BATTERY / REMOTE ENDPOINT**

- 1S LiPo；
- Battery connector；
- Bone B；
- LRA B；
- J_INTER_B 8Pin；
- 可选 BAT/GND TP。

B 板不再放 DRV2605L、PCA9540B、3V3 logic、I2C pull-up 或 Trigger logic。

**中央 8Pin FPC 逻辑 Pin Map：**

1. BAT+
2. BAT+
3. GND
4. GND
5. SPK_P
6. SPK_N
7. LRA_B_P
8. LRA_B_N

其中 BAT+ 两 Pin 并联、GND 两 Pin 并联用于提高载流和降低压降；SPK_P/N 为 MAX98357A 到远端 Bone B 的差分 Class-D 输出；LRA_B_P/N 为 A 板 DRV2605L-B 到远端 LRA B 的差分驱动。

当前明确不跨中央 FPC：

- SYS_3V3；
- HAPTIC_B SDA/SCL/TRIG；
- BAT_NTC；
- USB D+/D-；
- I2S；
- Camera DVP；
- RF。

BQ24074 与磁吸 USB/Charge 接口固定在 A。磁吸接口逻辑为 5V/GND/USB D+/D-，具体器件/尺寸/Footprint 继续 `TBD`。

V1 8Pin 基线不传 Battery NTC；BQ24074 TS 若不使用电池包 NTC，必须按器件数据手册在 A 板合法本地偏置，不得悬空。

风险门禁：

1. 选择具体 FPC/连接器后核算 BAT+/GND 双 Pin 的额定电流、压降、接触电阻和温升；
2. 验证 DRV2605L-B 经 FPC 远端驱动 LRA-B 时的 Auto Calibration、Back-EMF/谐振跟踪、振动强度和连续运行；
3. 验证 SPK_P/N 跨 FPC 的 EMI / 串扰；
4. A/B 当前仍是工程名，不冻结实际左/右镜腿。

状态：`ARCHITECTURE_FROZEN / FPC_MECHANICAL_PENDING / MAGNETIC_FOOTPRINT_PENDING`。

详细文件：

- `reviews/20260827-8pin-two-temple-architecture-freeze.md`
- `common/dual-temple-partition.md`
- `common/temple-partition.csv`
- `common/inter-temple-fpc.csv`

## 规则

1. 新决策如果替代旧决策，必须新增条目并注明 `Supersedes`；
2. 自动化工具不得自行删除本文件中的人工确认决定；
3. 审计发现冲突时，应报告 `DOCUMENT_OUT_OF_SYNC`，而不是静默覆盖；
4. `design-requirements.md` 与本文件共同构成重新绘制原理图前的最高优先级输入。