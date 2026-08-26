# 银龄智护 V1 — 双主控实验原型冲刺计划

日期：2026-08-26
状态：`CURRENT EXECUTION PLAN`

## 1. 本阶段目标

当前目标不是继续开放式优化，而是在尽可能短的时间内完成一套具有完整外形、完整双板结构、可烧录程序和可验证核心功能的实验型眼镜原型。

主控路线继续并行：

- Plan A：ESP32-S3-MINI-1U-N4R2；
- Plan B：BK7258QN88616。

两套路线共享外围功能定义、A/B双镜腿结构、Android/上层协议方向和测试方法。原则上只让主控最小系统、PinMux/SDK/下载等主控相关部分分叉，避免把整套产品完全复制成两份。

本阶段优先级：

1. 2.5 天完成原理图、PCB、生产检查并下单；
2. PCB制造等待期集中完成3D结构和PPG（心率/血氧）实验方案；
3. 目标 2026-09-02 到货；
4. 到货后两天完成焊接、烧录、基础通讯和关键外围测试；
5. 根据真实测试结果再决定后续实验安排。

---

## 2. PCB形态

当前目标采用三块PCB设计目标：

```text
A-ESP       = ESP32-S3 主控版本 A 板
A-BK        = BK7258 主控版本 A 板
B-COMMON    = 两套主控尽量共用的 B 板
```

A板承担主控、高速感知/接口和本侧执行；B板承担电池/远端执行，并尽量把与主控无关的功能做成公共板。

### 架构冻结前必须明确的P0项目

- A/B功能边界；
- BQ24074 + Charger/PowerPath 最终属于 A 还是 B；
- 12Pin FPC最终Pin Map；
- A-ESP / A-BK / B-COMMON的板框最大尺寸；
- Camera、FPC、磁吸接口、天线等强制机械位置；
- Bone/LRA连接器真实 electrical pin 与 mechanical pad 对应关系；
- C29/C30说明纠正；
- C32是否保留；
- 所有R/C/L/TP的Owner Core与Board归属。

上述项目冻结后不再在本轮PCB冲刺中反复重分配。

---

## 3. 手工原理图策略

本轮正式原理图和PCB由人工绘制/检查。此前自动生成的 `.eprj2` 只作为电气参考和问题样本，不作为最终PCB直接更新源。

必须保留的规则：

- 两个PCB设计单元必须真正分开；
- A侧跨板网络终止于 `J_INTER_A`；
- B侧跨板网络从 `J_INTER_B` 开始；
- A/B之间仅通过12Pin FPC/Harness Pin Map对应，不通过同一板级Wire或全局Net把两块PCB重新合成一个NetGraph；
- 外围器件按 `Core IC + Local Support Group` 绘制和布局；
- Connector的electrical pin、mechanical pad、shield/mounting pad必须逐项确认；
- MAX98357A BTL `SPK+ / SPK-` 均不得接GND；
- 两颗DRV2605L固定地址0x5A，继续按当前冻结的地址隔离方案实现，除非新的人工决策明确替代；
- PCB前必须检查Footprint、Pad Number、Pin Mapping、Board Ownership和FPC Pin Mapping。

---

## 4. 2.5天PCB冲刺

### 8月26日晚上 — Sprint 0.5 Day

目标：冻结架构并进入正式绘制。

完成：

1. 冻结A/B功能边界；
2. 冻结A-ESP / A-BK / B-COMMON三板定义；
3. 冻结BQ24074位置；
4. 冻结12Pin FPC；
5. 确认主要连接器Pin/Pad角色；
6. 确定PCB最大尺寸与关键机械位置；
7. 开始B-COMMON与公共电源/接口原理图。

输出：`ARCHITECTURE_FROZEN`。

### 8月27日 — Sprint Day 1

目标：三套原理图功能闭环。

A-ESP：

- ESP32-S3最小系统；
- Power / USB / EN / BOOT / UART；
- OV5640；
- Camera Rails；
- ICS-43434；
- BMI270；
- MAX98357A；
- PCA9540B / DRV2605L A；
- J_INTER_A。

A-BK：

- BK7258最小系统；
- Power / Reset / Boot / Download / RF；
- 按BK Pin Matrix接入相同公共外围；
- J_INTER_A。

B-COMMON：

- Battery；
- Charger/PowerPath（若冻结到B）；
- DRV2605L B；
- Bone B；
- LRA B；
- J_INTER_B；
- 本地去耦/上拉/TP。

输出：三份原理图均具备PCB转换条件。

### 8月28日 — Sprint Day 2

目标：完成三块PCB并下单。

顺序：

1. 三份原理图最终人工审查；
2. B-COMMON Placement / Routing；
3. A-ESP Placement / Routing；
4. A-BK Placement / Routing；
5. DRC/ERC/Footprint/Connector/FPC检查；
6. 生产文件检查；
7. BOM/CPL与缺料检查；
8. PCB和需要补充的元器件下单。

本阶段第一硬里程碑：

```text
M1 = PCB_ORDERED
目标时间：2026-08-28 晚，允许跨到 8/29 凌晨完成最后下单动作。
```

---

## 5. PCB制造等待期：8月29日—9月1日

这一阶段不把完整软件开发作为主任务。

主任务只有两条：

### Track 1 — 3D结构（第一优先级）

目标是在PCB到货前形成可直接试装的完整眼镜结构。

需要完成：

- A-ESP / A-BK / B-COMMON真实尺寸占位；
- Battery；
- Camera；
- Bone ×2；
- LRA ×2；
- FPC路径；
- 磁吸接口；
- 天线Keepout；
- PPG预留位置；
- 前框；
- A/B镜腿；
- 铰链/FPC通道；
- PCB固定；
- 电池固定；
- Camera/Bone/LRA安装位；
- 可拆盖；
- 第一版打印；
- 试装与第二版修正。

目标状态：`ENCLOSURE_READY_FOR_BOARD_INSTALL`。

### Track 2 — PPG心率/血氧实验方案

该功能当前定义为实验型生理参数采集，不以医疗级精度作为本轮要求。

需要完成：

- PPG候选传感器/模块；
- 红光/红外数据链；
- Heart Rate路径；
- SpO2估算路径；
- 测量位置比较：镜腿接触区 / 耳后 / 耳廓等；
- 接触压力与遮光；
- 环境光与运动伪影；
- Raw RED/IR数据记录；
- 与指夹血氧仪的对照实验方法；
- 静止/轻微运动测试；
- 数据记录格式和成功判据；
- 3D结构需要预留的传感器位置。

原则：本轮先完成模块级/台架验证，不强制把PPG集成进本次主PCB，以免延误主硬件。

### 等待期软件边界

只做最低Bring-up准备：

- ESP32工程可编译；
- BK7258 SDK/最小工程可编译；
- 两套下载/烧录方式确认；
- 串口日志方式确认；
- 历史Camera/MIC/Haptic测试代码整理；
- 准备上电测试脚本/清单。

完整应用软件和AI闭环不是这一等待期的第一优先级。

---

## 6. PCB到货与顺延规则

目标到货日：

```text
2026-09-02
```

9月2日上午执行收货检查：

- A-ESP PCB；
- A-BK PCB；
- B-COMMON PCB；
- 关键元器件；
- FPC/连接器/电池等装配关键件。

### 顺延规则

如果 9月2日 PCB 未到：

```text
测试 Day 1 +1天
测试 Day 2 +1天
依赖测试结论的后续实验安排 +1天
```

仍然优先继续3D/PPG收尾，不在没有实物时强行启动板级测试。

---

## 7. 到货后两天测试

### Test Day 1 — 焊接、烧录、基础通讯

顺序：

1. PCB目检；
2. 短路/阻值初检；
3. 焊接B-COMMON；
4. 焊接A-ESP；
5. 焊接A-BK；
6. 逐板电源Rail检查；
7. ESP32-S3最小程序开发/适配；
8. BK7258最小程序开发/适配；
9. 烧录；
10. UART / USB基础通讯；
11. A/B板基础互连检查。

Day 1完成标准：

- 两套主控至少能正确上电；
- 能进入烧录/下载；
- 能运行最小程序；
- 有稳定日志；
- 基础通讯通道建立。

### Test Day 2 — 外围联调、双板与装壳

重点：

- Camera；
- IMU；
- MIC；
- Audio / Bone；
- Haptic A/B；
- PCA9540B / 双DRV；
- FPC；
- 双板供电；
- 远端执行；
- 基础结构装配。

目标：

1. 得到真实成功/失败清单；
2. 判断ESP32-S3与BK7258各自当前成熟度；
3. 识别硬件返修/飞线/二版PCB需求；
4. 判断完整程序与功能联调的下一步优先级；
5. 基于实测结果安排后续实验，而不是提前把后续任务锁死。

两天测试后输出：`BOARD_BRINGUP_REPORT` + `NEXT_EXPERIMENT_PLAN`。

---

## 8. 当前里程碑

| Milestone | 目标日期 | 完成定义 |
|---|---|---|
| M0 Architecture Freeze | 8/26 晚 | 三板边界、FPC、连接器、关键机械边界冻结 |
| M1 PCB Ordered | 8/28 晚 | A-ESP/A-BK/B-COMMON完成生产检查并下单 |
| M2 Enclosure Ready | 9/1 | 第一版/修正版3D结构可用于板级试装 |
| M3 PPG Experiment Ready | 9/1 | 传感器、位置、采集/算法/对照方案明确 |
| M4 PCB Arrival Target | 9/2 | PCB与关键件到齐；未到则后续整体顺延1天 |
| M5 Bring-up Day 1 | 9/2 | 焊接、上电、烧录、基础通讯 |
| M6 Bring-up Day 2 | 9/3 | 关键外围、FPC、双板、装壳测试并形成后续实验计划 |

---

## 9. 当前禁止事项

为了保证原型速度，本轮在M6之前避免：

- 再次大范围改变产品功能定义；
- 同时维护两套完全不同的外围架构；
- 让旧自动生成原理图直接驱动最终PCB；
- 为视觉美观无限延长原理图时间；
- 在Connector Pin/Pad角色不明确时下板；
- PPG未经台架验证就强制塞入主PCB；
- 在PCB未到货前投入大量时间做依赖实板的完整软件联调；
- 把ESP32-S3和BK7258变成两套完全独立的上层应用协议。

---

## 10. 与旧计划的关系

`20260810-next-step-two-board-schematic-plan.md` 仍保留其双板边界、Ownership和Connector/Harness审查结论作为工程参考；但从2026-08-26开始，执行方式变为：

```text
人工重画/人工PCB
+ 双主控并行
+ 三板目标
+ 2.5天PCB冲刺
+ 4~5天3D/PPG等待期
+ 9/2目标到货
+ 2天板级测试
```

如果旧文件中的“继续由Skill增量修改现有 `.eprj2`”与本计划冲突，以本文件的当前人工原型冲刺策略为准。Skill后续继续作为方法论/自动化能力改进项目，不阻塞当前产品原型。