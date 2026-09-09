# 银龄智护 B1｜Test 5C OFFLINE 20x 冷启动结果

日期：2026-09-09

程序：`ESP32_TEST5C_OFFLINE_COLD_BOOT_20X_V2_20260909.ino`

## 结论

**PASS / STRONG**

在当前无目标 Wi-Fi AP 的环境下，使用 V2 离线版完成 20 次真实断电冷启动。最终持久计数：

```text
cold=20
pass=20
fail=0
```

V2 仅在 `RESET REASON: POWERON` 时计入冷启动，上传/Reset/OTHER 启动只作为 PRECHECK，不污染冷启动结果。

## 第 19 次实测

```text
RESET REASON: POWERON
TEST5C V2 COUNTERS: cold=19 pass=18 fail=0 target=20
MODE: REAL COLD BOOT #19 -> WILL BE COUNTED
PSRAM PASS
CAMERA PASS
AUDIO INIT PASS
MIC channel=LEFT
IMU CHIP_ID=0x27
BMI260 config bytes=8192
PCA/IMU INIT PASS
```

15 s 健康窗口结束：

```text
cameraFrames=7
imuReads=14
micBlocks=59
camErr=0
txErr=0
rxErr=0
imuErr=0
pcaErr=0
THIS REAL COLD BOOT #19: PASS
PERSISTENT V2 RESULT: cold=19 pass=19 fail=0
```

## 第 20 次实测

```text
RESET REASON: POWERON
TEST5C V2 COUNTERS: cold=20 pass=19 fail=0 target=20
MODE: REAL COLD BOOT #20 -> WILL BE COUNTED
PSRAM PASS
CAMERA PASS
AUDIO INIT PASS
MIC channel=LEFT
IMU CHIP_ID=0x27
BMI260 config bytes=8192
PCA/IMU INIT PASS
```

15 s 健康窗口结束：

```text
cameraFrames=7
imuReads=14
micBlocks=59
camErr=0
txErr=0
rxErr=0
imuErr=0
pcaErr=0
THIS REAL COLD BOOT #20: PASS
PERSISTENT V2 RESULT: cold=20 pass=20 fail=0
TEST5C OFFLINE V2 20x RESULT: PASS CANDIDATE
```

健康采样中 `heap=229436 B`、`psram=2077076 B` 保持稳定。第 20 次静置 IMU `|A|` 约 1.017~1.020 g，Gyro 接近 0 dps；MIC 持续有 RMS/Peak 数据，Camera/IMU/MIC 计数持续增长。

## 覆盖范围

本轮明确覆盖：

- ESP32-S3 真实 POWERON 冷启动
- PSRAM 初始化
- OV5640 初始化与 JPEG 帧抓取
- I2S MIC RX
- MAX98357A I2S TX
- PCA9540B 0x70
- 当前 BMI260-like 0x68，CHIP_ID=0x27，8192-byte config 与六轴读取
- 15 s 多模块并发健康窗口

本轮明确不覆盖：

- Wi-Fi：当前环境无目标 AP，状态仍为 `ENV BLOCKED`
- Haptic：仍为 `INTERMITTENT / UNRESOLVED`
- Battery/FPC：小板未到，仍 `BLOCKED`

## 下一步

进入机械扰动测试：系统持续运行时，对板边、连接器、FPC/线束与当前可接触焊点做轻微扰动，观察 Camera / Audio / I2C / IMU 是否出现错误、掉线、重启或数据停滞。