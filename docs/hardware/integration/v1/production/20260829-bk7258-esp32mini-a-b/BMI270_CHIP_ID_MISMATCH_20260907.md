# ESP32-S3 首板 BMI270 身份异常记录（2026-09-07）

## 实板映射

```text
SENSOR_I2C_SDA = GPIO21
SENSOR_I2C_SCL = GPIO18
PCA9540B = 0x70
```

两颗 DRV2605L 当前因返修检查发现连锡而已拆除，因此 PCA9540B 两个下游分支当前均无 0x5A 器件。

## 身份与 mux 隔离实测

分别测试 PCA9540B：

```text
OFF = 0x00
CH0 = 0x04
CH1 = 0x05
```

三个状态结果完全一致：

```text
0x68 ACK
0x70 ACK
0x69 NO ACK
0x5A not detected
0x68 register 0x00 -> 0x27, 10/10, no read failure
```

因此：

```text
0x68 位于 PCA9540B 上游主总线
PCA CH0/CH1 与 0x27 无关
DRV2605L 当前拆除状态符合扫描结果
```

预期项目器件 BMI270：

```text
CHIP_ID register = 0x00
expected CHIP_ID = 0x24
```

当前实测：

```text
CHIP_ID = 0x27
```

因此项目 BMI270 身份当前仍为 FAIL / unresolved，不得标 PASS。

## I2C 焊接压力测试实测

测试程序：

`ESP32_BMI_I2C_SOLDER_STRESS_20260907.ino`

条件：PCA9540B 两个下游通道关闭，直接测试上游 0x68。

100 kHz：

```text
OK      = 1000
FAIL    = 0
0x24    = 0
0x27    = 1000
OTHER   = 0
```

400 kHz：

```text
OK      = 1000
FAIL    = 0
0x24    = 0
0x27    = 1000
OTHER   = 0
```

共 2000 次原始 CHIP_ID 读取全部成功，全部稳定返回 0x27，无随机字节、无读失败。

## 供电实测

2026-09-07 测得：

```text
BMI VDD   = 3.415 V
BMI VDDIO = 3.415 V
```

TPS63021 的 PS/SYNC 在当前设计中直接接 GND，因此允许进入 Power Save Mode。当前 3.3V 轨在轻载时高于 3.300 V 的现象可以由该模式解释；3.415 V 仍处于 BMI270/BMI260 的 VDD 与 VDDIO 允许范围内。当前不把 0x27 归因于供电超限。

## 焊接状态判定

当前结果可以较强地说明以下活动路径具备稳定电气连接：

```text
BMI 供电 / GND（至少足以启动和持续稳定响应 I2C）
SDA
SCL
地址选择状态（当前稳定为 0x68）
```

100 kHz 与 400 kHz 共 2000 次无失败，使“严重或间歇性 SDA/SCL 虚焊导致 0x27”的可能性很低。

但是当前测试不能证明 BMI LGA-14 的所有底部焊点均完整合格，仍未覆盖：

```text
INT1 / INT2
未使用数字脚
所有底部焊盘的长期机械可靠性
温度 / 弯曲 / 振动条件下的间歇故障
```

当前生产状态：

```text
BMI I2C active solder connectivity: PASS / STRONG
BMI complete LGA solder qualification: NOT FULLY VERIFIED
BMI270 identity: FAIL / unresolved (0x27 != 0x24)
```

## 下一步：做替换对照，不再继续围绕同一颗芯片反复读寄存器

当前这颗 IMU 顶部丝印在焊接过程中已经不可辨认。由于其在 100 kHz/400 kHz 下共 2000 次都稳定返回 0x27，继续重复相同读取的诊断收益已经很低。

下一步采用 A/B 对照：

1. 记录当前芯片为 `OLD_IMU: stable CHIP_ID=0x27`，不要再把它当作已确认 BMI270。
2. 从同一采购批次取一颗未焊的新 BMI270，先拍照记录原始顶标和 Pin1 方向。
3. 拆除旧 IMU，清理焊盘并检查无桥连、无焊盘脱落。
4. 焊接新 IMU，控制热量，避免再次把顶标烧毁。
5. 上电前检查 3V3-GND 是否短路，并确认 SDA/SCL 不互短、不对地硬短。
6. 上电后只做最小原始 I2C 身份测试：扫描 0x68/0x69，并读取寄存器 0x00。
7. 若新器件得到 `0x24`：说明 PCB/I2C/供电设计本身没有导致 0x27，旧器件应按身份异常或热损伤嫌疑件隔离；随后再跑 7Semi BMI270 完整初始化与六轴测试。
8. 若新器件仍稳定得到 `0x27`：问题升级为采购批次/实装料号异常或其它系统性身份问题，再取第二颗新料交叉验证，不继续返修 PCB 总线。

## BOM 目标

```text
Bosch BMI270
LCSC/JLCPCB: C2836813
LGA-14 2.5x3 mm
```
