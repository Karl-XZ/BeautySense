# 银龄智护 V1 — 人工重画 A/B 双板原理图与 PCB 清单

日期：2026-08-26

## 1. 当前决定

当前 `银龄智护_V1_核心网络-working.eprj2` 不再直接作为最终 PCB 更新源，而作为：

- 已验证电气关系参考；
- 器件/位号参考；
- Net / Pin Matrix 参考；
- BOM / Connector 参考。

后续由项目负责人**手工整理/绘制原理图与 PCB**。

当前工程结构实际仍是：一个 Board、一个 Schematic、一个 Sheet、一个 PCB，因此它尚未形成真正的双 PCB 工程。

A/B 仍是工程分区名，当前不强制绑定左/右镜腿；机械结构确定后再映射成 LEFT / RIGHT。

---

## 2. 总体目标

最终建立三个层次：

```text
SYSTEM / HARNESS
├── BOARD_A schematic → PCB_A
└── BOARD_B schematic → PCB_B

J_INTER_A
   ║ 12Pin FPC
J_INTER_B
```

要求：

- Board A 与 Board B 可独立生成 PCB；
- 任一 Board Netlist 不包含另一块板的器件；
- 跨板关系只通过 J_INTER_A / J_INTER_B 与 Harness Pin Map 表达；
- 不允许一条普通全局 Net/Wire 绕过 Connector 把两块 PCB 合并；
- 当前旧 PCB1 仅参考，不作为最终 PCB。

---

# 3. BOARD A — MAIN / SENSING SIDE

## 3.1 核心器件

| Reference | 器件 | 主要职责 |
|---|---|---|
| U1 | BQ24074 | 1S LiPo 充电 / Power Path |
| U2 | TPS63021 | SYS_3V3 Buck-Boost |
| U3 | ESP32-S3-MINI-1U-N4R2 | 主 MCU / Wi-Fi / BLE / USB / Camera / Audio / Sensor 控制 |
| U4 | TLV75728PDBVR | Camera 2.8V LDO |
| U5 | TLV75515PDBVR | Camera Core 1.5V LDO |
| U6 | MAX98357A | I2S 单声道 Class-D，驱动两只 Bone |
| U7 | ICS-43434 | I2S 数字麦克风 |
| U8 | BMI270 | 六轴 IMU |
| U9 | DRV2605L A | A 侧 LRA Driver |
| U10 | PCA9540B | 两颗固定 0x5A DRV2605L 的 I2C 隔离 |
| J2 | OV5640 24Pin FPC | Camera 接口 |
| J_MAG_USB | 4Pin Magnetic | 5V / GND / USB D+ / D- |
| J_BONE_A | Bone A | 本侧骨传导输出 |
| J_LRA_A | LRA A | 本侧触觉执行器 |
| J_INTER_A | 12Pin FPC | A→B 跨镜腿边界 |

## 3.2 A 侧必须跟随核心器件一起画的外围

### U1 BQ24074

- R1：ILIM；
- R2：ISET；
- R3：TS/NTC 相关可选网络；
- R4：PGOOD pull-up；
- R5：CHG pull-up；
- C3：BAT pin local bypass；
- C4：PowerPath OUT bulk。

### U2 TPS63021

- L1；
- C5/C6/C7：输入/模拟供电去耦；
- C8/C9/C10：3.3V 输出 bulk；
- R6：PG pull-up。

### U3 ESP32-S3

- C11/C12：3.3V local decoupling；
- C13：EN/reset timing；
- R7：EN pull-up；
- R8：GPIO0/BOOT pull-up；
- TP_EN / TP_BOOT / TP_UART_TX / TP_UART_RX；
- TP_3V3_A / TP_GND_A。

### U4/U5 Camera LDO

- U4：C14/C15 + R9；
- U5：C16/C17 + R10。

### Camera J2

- R11/R12：SCCB SDA/SCL pull-up；
- C25/C26：2.8V local decoupling；
- C27：Camera Core local decoupling。

### U6 MAX98357A

- C18/C19：VDD bulk / HF bypass；
- R13：SD_MODE 相关网络；
- J_BONE_A；
- SPK_P / SPK_N 同时通过 J_INTER_A 去 B 侧 Bone。

### U7 ICS-43434

- C20：VDD bypass；
- R14：当前 MIC data/default 配置外围，手工重画时需结合当前网络与器件资料再次确认用途。

### U8 BMI270 / SENSOR I2C

- C21/C22：VDD/VDDIO bypass；
- R15/R16：SENSOR I2C upstream pull-up。

### U10 PCA9540B + U9 DRV2605L A

- C28：PCA9540B bypass；
- R17/R18：HAPTIC_A CH0 SDA/SCL pull-up；
- C23：DRV A VDD bypass；
- C24：DRV A REG bypass；
- J_LRA_A。

## 3.3 A 侧手工绘图重点

1. 先画 Power：Magnetic 5V → protection → BQ24074 → SYS_PWR → TPS63021 → SYS_3V3；
2. 再画 MCU + BOOT/EN/UART/USB；
3. Camera DVP/SCCB 与两路 Camera Power 全部留 A；
4. MIC / AMP I2S 按当前 Pin Matrix；
5. BMI270 + PCA9540B 上游使用 SENSOR_I2C；
6. PCA9540B CH0 只在 A 内终止到 U9；
7. PCA9540B CH1 只画到 J_INTER_A；
8. B 侧 Battery 的 BAT+/NTC 只通过 J_INTER_A 进入 U1；
9. MAX98357A SPK_P/N：一路到 J_BONE_A，一路只到 J_INTER_A；
10. 每个 Core IC 周围把自己的去耦、pull-up、programming resistor 成组画在一起。

---

# 4. BOARD B — BATTERY / REMOTE ACTUATOR SIDE

## 4.1 核心器件

| Reference | 器件 | 主要职责 |
|---|---|---|
| J_BAT | Battery Connector | 1S LiPo + NTC 接口 |
| U11 | DRV2605L B | B 侧独立 LRA Driver |
| J_LRA_B | LRA B | B 侧触觉执行器 |
| J_BONE_B | Bone B | B 侧骨传导输出 |
| J_INTER_B | 12Pin FPC | B→A 跨镜腿边界 |
| TP_3V3_B | Test Point | B 侧 3.3V bring-up |
| TP_GND_B | Test Point | B 侧 GND bring-up |

B 侧没有主 MCU、Camera、USB、RF、MAX98357A 和主稳压器。

## 4.2 B 侧外围

### U11 DRV2605L B

- C29：实际网络应为 SYS_3V3 ↔ GND，属于 U11 VDD bypass；
- C30：实际网络应为 DRV_B_REG ↔ GND，属于 U11 REG bypass；
- R19/R20：HAPTIC_B 下游 I2C SDA/SCL pull-up；
- J_LRA_B。

注意：旧 BOM 中 C29/C30 Comment 写反，手工重画按实际 Net / Datasheet 处理。

### B 侧供电

- C31：B 侧 SYS_3V3 bulk；
- C32：ProjectSpec 曾要求 SYS_3V3 HF bypass，但最终 Live BOM/Netlist 缺失；手工重画前确认是否补回。默认建议按 B 侧远端 Driver local bypass 需求重新核对后决定；
- TP_3V3_B / TP_GND_B。

### Bone B

J_BONE_B 只接从 J_INTER_B 进入的 SPK_P / SPK_N。

### Battery

J_BAT 本地接电池：

- BAT+；
- GND；
- BAT_NTC（如果最终使用）。

BAT+/GND/NTC 再通过 J_INTER_B 去 A 侧 Charger / Power Path。

## 4.3 B 侧手工绘图重点

1. J_INTER_B 作为所有跨板网络的唯一入口/出口；
2. SYS_3V3 → C31/(C32) → U11；
3. HAPTIC_B SDA/SCL → R19/R20 → U11；
4. HAPTIC_B_TRIG → U11 IN/TRIG；
5. U11 OUT+/OUT- → J_LRA_B；
6. SPK_P/N → J_BONE_B；
7. J_BAT → BAT+/GND/NTC → J_INTER_B；
8. B 板至少保留 TP_3V3_B 和 TP_GND_B。

---

# 5. 12Pin Inter-Temple FPC 手工处理

当前 baseline：

| Pin | 功能 |
|---:|---|
| 1 | BAT+ |
| 2 | BAT+ |
| 3 | GND |
| 4 | GND |
| 5 | SYS_3V3 |
| 6 | HAPTIC_B_SCL |
| 7 | HAPTIC_B_SDA |
| 8 | HAPTIC_B_TRIG |
| 9 | SPK_P |
| 10 | SPK_N |
| 11 | BAT_NTC / conditional |
| 12 | SPARE |

关键规则：

- Board A 的这些 Net 只终止在 J_INTER_A；
- Board B 的对应 Net 只从 J_INTER_B 开始；
- Harness 表负责 A.PinN ↔ B.PinN；
- 不在一个普通板级 NetGraph 中把 J_INTER_A 和 J_INTER_B 再用 Wire 连起来；
- 若 EasyEDA 同名 Net Label 是全局作用域，则两块板最好使用独立 schematic/PCB design unit，避免自动合并。

---

# 6. 当前旧工程必须人工复核的问题

## P0 / 原理图与 PCB 前必须处理

1. 当前工程仍是单 Board / 单 PCB；
2. J_INTER_A / J_INTER_B 当前不能继续作为同一 Board NetGraph 的直接连续连接；
3. J_BONE_A/B、J_LRA_A/B 所用连接器 Symbol/Footprint 的 1/2 电气触点与 3/4 mechanical mounting pad 必须逐一核对；机械 Pad 禁止被当作普通信号 Pin；
4. C29/C30 Comment 与实际网络相反；
5. C32 SSOT / Live 不一致；
6. 所有 R/C/L/D/F/TP 必须按 `component-ownership.csv` 跟随核心器件；
7. MAX98357A SPK_P/N 为 BTL，任何一端禁止接 GND；
8. 两颗 DRV2605L 地址相同，必须保持 PCA9540B 隔离；
9. Camera FPC electrical contacts 与 mechanical pads 必须区分；
10. MCU Pin Matrix 按仓库最新 Plan A / Plan B 文件，不从旧图面重新猜。

## P1 / 人工可读性

1. 删除大量无可见文字意义的 NetPort；
2. 跨模块使用可见 Named Net Label；
3. 本地外围使用短线；
4. 禁止大面积长斜线；
5. 位号、数值、网络名不要互相覆盖；
6. 原理图分功能页/功能块；
7. 每画完一块先人工 ERC + Netlist 对照，再进入 PCB。

---

# 7. 建议的人工工作顺序

```text
STEP 1  确认 A/B 最终物理左右对应关系（可暂不定）
STEP 2  核对所有 Connector 的真实 Electrical / Mechanical Pin Role
STEP 3  确认 C32 是否保留
STEP 4  新建/整理 Board A 独立原理图 design unit
STEP 5  按 Core + Local Support Group 画 Board A
STEP 6  新建/整理 Board B 独立原理图 design unit
STEP 7  按 Core + Local Support Group 画 Board B
STEP 8  单独维护 12Pin Harness Map / System Overview
STEP 9  分别运行 A/B ERC
STEP 10 分别导出 A/B Netlist 并对照 component-ownership / Pin Matrix / Harness
STEP 11 分别转 PCB_A / PCB_B
STEP 12 PCB Placement 按 Core + Local Support Group，先功率/去耦/敏感信号，再其它器件
```

---

# 8. 当前状态

```text
CURRENT_EPRJ2 = ELECTRICAL_REFERENCE_ONLY
MANUAL_SCHEMATIC_REDRAW = ACTIVE
BOARD_A_SCHEMATIC = TODO
BOARD_B_SCHEMATIC = TODO
HARNESS_MAP = BASELINE_AVAILABLE
PCB_A = TODO
PCB_B = TODO
```
