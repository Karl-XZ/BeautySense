# BMI260 功能身份验证计划（2026-09-07）

## 背景

当前首板 IMU 在 0x68 地址稳定响应，寄存器 0x00 连续读取得到 0x27。100 kHz 与 400 kHz 共 2000 次读取均无失败。PCA9540B OFF/CH0/CH1 三种状态均不影响结果。VDD/VDDIO 实测 3.415 V，处于 BMI260/BMI270 允许范围。

当前不再优先拆换 IMU，而先做无损的 BMI260 功能身份验证。

## 为什么需要真正的 BMI260 初始化

不能只把 BMI270 程序中的 `0x24` 改成 `0x27`。BMI260 与 BMI270 使用不同的初始化配置数据。BMI260 需要加载其专用配置文件（>8 KB），完成后检查 `INTERNAL_STATUS(0x21)` 低 4 位是否为 `0x01 init_ok`，再开启 accelerometer/gyroscope 并读取六轴数据。

Linux/ChromiumOS BMI260 驱动的关键流程：

```text
CHIP_ID 0x00 -> 0x27
soft reset 0x7E <- 0xB6
PWR_CONF 0x7C -> disable advanced power save for config load
INIT_CTRL 0x59 <- 0
chunk upload BMI260 config through INIT_ADDR_0/1 (0x5B/0x5C) + INIT_DATA (0x5E)
INIT_CTRL 0x59 <- 1
wait <= ~150 ms for INTERNAL_STATUS message == 0x01
then enable ACC/GYR and read raw data
```

## PASS 条件

只有同时满足以下条件，才把“当前 silicon 按 BMI260 工作”记为 STRONG PASS：

```text
CHIP_ID = 0x27
BMI260 dedicated config upload = PASS
INTERNAL_STATUS[3:0] = 0x01
ACC data continuously changes with tilt
GYRO data continuously changes with rotation
static acceleration magnitude roughly 1 g
```

如果 BMI260 专用初始化失败，不能单独据此判定它不是 BMI260，因为还需要排除 Arduino 端 config upload/分块传输实现问题。

## 当前策略修正

之前计划优先拆换一颗新 BMI270 做 A/B 对照。现修正为：

```text
优先级1：无损 BMI260 专用初始化 + 六轴功能测试
优先级2：如果结果仍不确定，再拆换新 BMI270 做 A/B 对照
```

这样可以避免在已有稳定 I2C 连接的情况下过早返修 LGA 器件。
