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

## 焊接状态判定

当前 I2C 结果可以较强地说明以下路径至少具备稳定电气连接：

```text
BMI 供电 / GND（至少足以启动和稳定响应 I2C）
SDA
SCL
地址选择状态（当前地址稳定为 0x68）
```

原因：PCA OFF / CH0 / CH1 三种状态下，对 0x68 连续读取均 10/10 返回同一字节 0x27，且无 READ FAIL。

因此当前不符合“典型严重 SDA/SCL 虚焊导致间歇 ACK、随机字节或频繁读失败”的表现。

但是当前测试不能证明 BMI270 LGA-14 的所有底部焊点均合格，尤其以下功能尚未覆盖：

```text
INT1 / INT2
未使用接口脚
所有底部焊盘的机械可靠性
温度/弯曲/振动条件下的间歇虚焊
```

当前生产状态应记录为：

```text
BMI I2C basic solder connectivity: PASS / BASIC
BMI complete LGA solder qualification: NOT FULLY VERIFIED
BMI270 identity: FAIL / unresolved (0x27 != 0x24)
```

## 后续处理

1. 暂停 7Semi BMI270 完整初始化结论，不因 `imu.begin()` 失败直接判定焊坏。
2. 100kHz / 400kHz 分别进行长时间 CHIP_ID 连续读取，统计 ACK/READ FAIL。
3. 测试期间轻压 PCB、轻微弯曲边缘，观察 0x68 是否掉线或读值改变。
4. 有条件时用逻辑分析仪/示波器确认 SDA/SCL 上升沿、ACK、重复启动和高电平幅值。
5. 检查板上实装 IMU 顶标、Pin1 方向与同批未焊器件。
6. 身份问题解决后再做 Accel/Gyro/Temperature 连续数据测试；若项目使用 INT1/INT2，再独立验证中断脚。

## BOM 目标

```text
Bosch BMI270
LCSC/JLCPCB: C2836813
LGA-14 2.5x3 mm
```
