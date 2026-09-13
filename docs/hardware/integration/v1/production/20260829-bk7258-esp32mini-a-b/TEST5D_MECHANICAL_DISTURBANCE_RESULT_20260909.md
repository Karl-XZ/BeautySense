# 银龄智护 B1｜Test 5D 机械扰动测试结果

日期：2026-09-09

## 结论

状态：**PASS**

用户在 Test 5C OFFLINE V2 20/20 冷启动通过后，继续进行了此前约定的轻微机械扰动测试，反馈“测试了没有问题”。

## 本次扰动对象

按当前测试计划，对系统运行状态下可接触部位进行轻微扰动，重点包括：

- 板边
- 连接器
- 线束 / FPC
- 可接触焊点

## 观察链路

本次关注以下已运行功能链：

- OV5640 Camera
- I2S MIC RX
- MAX98357A Audio TX
- PCA9540B
- 当前 BMI260-like IMU
- ESP32-S3 系统运行状态

## PASS 判据

扰动期间未观察到用户可见异常，未出现已知的以下故障现象：

- Camera 停止或明显异常
- Audio / MIC 中断
- I2C / IMU 功能异常
- 系统卡死或异常重启
- 连接器 / 线束扰动导致功能掉线

因此本轮机械扰动测试记录为 **PASS**。

## 边界

- 本结果是当前手工轻微机械扰动下的功能性验证，不等同于正式振动、跌落、运输或寿命可靠性认证。
- Haptic 仍保持 `INTERMITTENT / UNRESOLVED`，本次 PASS 不用于关闭 DRV2605L / LRA 的既有间歇问题。
- Battery / FPC 电源链路仍等待相关小板到货后补测。
- Wi-Fi 冷启动维度当前因目标 AP 不在环境中保持 `ENV BLOCKED`。

## 当前 Test 5 状态

```text
Test 5A Wi-Fi+Camera+MIC+Audio long-run     PASS / STRONG
Test 5B + BMI260-like/PCA concurrency       PASS / STRONG
Test 5C OFFLINE 20x cold boot               PASS / STRONG
Test 5D light mechanical disturbance        PASS
Wi-Fi cold-boot dimension                   ENV BLOCKED
Haptic                                      INTERMITTENT / UNRESOLVED
Battery/FPC                                 BLOCKED
```
