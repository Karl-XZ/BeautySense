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

## 后续处理

1. 当前不因 0x27 重新回流/吹焊 IMU。
2. 断电重上电后可再重复一次 100 kHz / 400 kHz 压力测试作为冷启动复核。
3. 测量并记录 IMU VDD、VDDIO 对 GND 实际电压。
4. 继续在原始 I2C 层读取更多身份/状态寄存器。
5. 若仍稳定为 0x27，临时尝试 BMI260 驱动完整初始化和 Accel/Gyro 数据读取，验证当前 silicon 行为。
6. 若 BMI260 驱动正常，则按来料/实装身份异常处理；项目正式 BOM 仍保持 BMI270，不能据此判 BMI270 PASS。

## BOM 目标

```text
Bosch BMI270
LCSC/JLCPCB: C2836813
LGA-14 2.5x3 mm
```
