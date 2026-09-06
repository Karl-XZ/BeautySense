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

因此项目 BMI270 身份当前仍为 FAIL，不得标 PASS。

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

## 2026-09-07 BMI260 专用初始化与六轴功能实测

使用 BMI260 专用初始化流程与 `bmi260_config_file`，程序只有在以下步骤均成功后才会进入 `LIVE DATA`：

```text
CHIP_ID = 0x27
soft reset
BMI260 config upload
INTERNAL_STATUS init_ok
ACC/GYRO enable
continuous 6-axis read
```

实板已经连续输出六轴数据。

运动时示例：

```text
ACC[g] X=-0.685 Y=-0.080 Z=-1.452 |A|=1.608   GYRO[dps] X=197.02 Y=-79.83 Z=239.93
ACC[g] X=0.641 Y=0.413 Z=0.780 |A|=1.091      GYRO[dps] X=18.55 Y=295.90 Z=-324.95
ACC[g] X=-1.546 Y=0.427 Z=-0.429 |A|=1.660    GYRO[dps] X=447.27 Y=-373.78 Z=-41.38
```

静止后示例：

```text
ACC[g] X=-0.019 Y=-0.306 Z=0.972 |A|=1.020   GYRO[dps] X=0.49 Y=0.12 Z=0.31
ACC[g] X=-0.017 Y=-0.298 Z=0.975 |A|=1.020   GYRO[dps] X=0.49 Y=0.12 Z=0.43
ACC[g] X=-0.016 Y=-0.289 Z=0.977 |A|=1.019   GYRO[dps] X=0.18 Y=0.06 Z=0.31
```

判读：

```text
运动时加速度/角速度随动作显著变化      PASS
静止时 |A| 约 1.02 g                    PASS
静止时 Gyro 接近 0 dps                  PASS
BMI260 专用初始化后持续六轴输出          PASS
```

静止时 Y≈-0.29g、Z≈0.98g 主要反映板子并非完全水平；|A|≈1.02g 才是更关键的静止重力一致性指标。运动过程中 |A| 明显大于或小于 1g 属于动态线加速度叠加，属于正常现象。

## 当前生产判定

```text
BMI I2C active solder connectivity: PASS / STRONG
BMI six-axis data path: PASS
BMI260-compatible behavior: PASS / STRONG CONFIRMATION
BMI270 identity: FAIL
Observed silicon behavior: strongly consistent with BMI260
```

这已经不再只是“0x27 看起来像 BMI260”。当前器件不仅返回 BMI260 CHIP_ID，还能接受 BMI260 专用配置并正常输出合理的 Accel/Gyro 数据，因此工程上应按“板上实装器件表现为 BMI260”处理。

但购买记录的目标料仍是 BMI270；这不能反推购买记录错误。可能原因仍包括实装/来料混料、错料或其它供应链身份异常。

## 下一步

1. 不再为当前 0x27 做 I2C/电源返修。
2. 当前器件隔离标记为 `BMI260-like / wrong identity for BOM`。
3. 项目正式 BOM 仍保持 BMI270，不把当前器件标为 BMI270 PASS。
4. 后续需要恢复正式设计时，更换一颗确认顶标的 BMI270，并要求原始 CHIP_ID=0x24，再跑 BMI270 完整初始化与六轴测试。
5. 若要追溯供应问题，可对同采购批次未焊新料做顶标记录和独立 CHIP_ID 抽检。

## BOM 目标

```text
Bosch BMI270
LCSC/JLCPCB: C2836813
LGA-14 2.5x3 mm
```
