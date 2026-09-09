# 银龄智护 B1｜当前仍可在单板完成的功能性测试

日期：2026-09-09

## 已完成，不再重复

- ESP32-S3 最小系统 / Flash / PSRAM / USB：PASS
- Wi-Fi 基础联网、DNS/TCP/HTTP：PASS
- OV5640 SCCB / DVP / JPEG / 浏览器实时视频：PASS
- MIC 录音：PASS
- MAX98357A 基础播放：PASS
- I2S Full-Duplex：PASS / STRONG
- BMI260-like 当前器件六轴与 INT1：PASS / STRONG
- PCA9540B：PASS
- Test5A 长时间并发：PASS / STRONG
- Test5B + IMU/PCA 并发：PASS / STRONG
- Test5C OFFLINE 20x 真冷启动：PASS / STRONG
- Test5D 轻微机械扰动：PASS
- BLE GATT iPhone/nRF Connect：PASS / STRONG

## 不依赖另一半板 / 小板，仍建议完成

### 1. Haptic 重新关闭测试
状态：INTERMITTENT / UNRESOLVED。

当前两颗 DRV2605L I2C 均能访问，但历史上 CH1 有 GO timeout，两路强 RTP 曾出现 OC_DETECT=1。需要重新做单路低/中强度短脉冲循环、STATUS 读取和轻微扰动，确认连接修复后能稳定关闭问题。

### 2. Wi-Fi 冷启动 + 断线自动重连
当前原工作室 AP 不在环境中，因此 Wi-Fi cold-boot 维度仍为 ENV BLOCKED。可改用手机热点或当前可控 AP，验证真实 POWERON 后自动入网，以及 AP 临时关闭/恢复后的自动重连。

### 3. Wi-Fi + BLE 共存压力
现有 Test5A/B 验证了 Wi-Fi + Camera/MIC/Audio/IMU，但 BLE 是单独测试。还需要验证 ESP32-S3 2.4GHz 共存场景：BLE 保持连接/Notify，同时 Wi-Fi 联网并运行 Camera、MIC、Audio、IMU/PCA，观察掉线、吞吐、错误计数、Heap/PSRAM。

### 4. 实际产品网络音频链路
当前只验证了本地产生测试音和 I2S TX，没有验证“手机/电脑网络音频 -> Wi-Fi -> ESP32-S3 buffer -> I2S -> MAX98357A -> 骨传导”的真实产品链。建议先用 PC/手机发送 PCM/WAV 流，验证连续播放、缓冲欠载、断流恢复和 30 min 稳定性。

### 5. BLE 实际控制命令 / Wi-Fi 配网
当前 BLE GATT 只验证 PING/ECHO/Notify。若产品计划用 BLE 做配网、状态、音量、模式控制，还需把 BLE 从“通信链路 PASS”升级成“产品命令 PASS”：例如写入 Wi-Fi 凭据、查询状态、控制音量/播放、请求 IMU 状态等。

### 6. MAX98357A 实际负载连续播放 / 温升 / 电流
如果当前桌面上能直接接到最终等效负载，不依赖 B 板，则可现在完成：连续播放 30~60 min、听感失真、芯片温升、整机电流。若最终两只 8Ω 并联路径必须经过另一半板/FPC，则该项延期到整板组装。

## 另外一个非“当前功能失效”问题

正式 BOM 目标仍是 BMI270，但当前安装器件 CHIP_ID=0x27，功能表现为 BMI260-like。当前六轴功能已经 PASS；若要关闭“正式 BMI270 身份”问题，需要换成真实 CHIP_ID=0x24 的 BMI270 再做一次初始化/六轴/INT1 验证。

## 建议执行顺序

1. Wi-Fi + BLE 共存压力
2. 实际产品网络音频链路
3. BLE 实际控制/配网
4. Wi-Fi cold boot / AP loss-reconnect
5. Haptic 重新关闭测试
6. MAX98357A 最终负载热/电流（若当前条件允许）
