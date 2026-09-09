# 银龄智护 B1 测试程序索引

目的：避免依赖 GitHub 全局 Code Search。后续测试程序统一从本页直接进入。

## 当前测试程序

- [Test 5C OFFLINE V2｜20 次真实断电冷启动（无 Wi-Fi 环境，推荐）](./ESP32_TEST5C_OFFLINE_COLD_BOOT_20X_V2_20260909.ino)
- [Test 5C OFFLINE V1｜旧版，仅保留追溯](./ESP32_TEST5C_OFFLINE_COLD_BOOT_20X_20260909.ino)
- [Test 5C｜20 次真实冷启动验证（含 Wi-Fi）](./ESP32_TEST5C_COLD_BOOT_20X_20260909.ino)
- [Test 5B｜Wi-Fi + Camera + MIC + Audio + IMU/PCA](./ESP32_TEST5B_WIFI_CAMERA_AUDIO_IMU_STRESS_20260909.ino)
- [Test 5A｜Wi-Fi + Camera + MIC + Audio](./ESP32_TEST5A_WIFI_CAMERA_AUDIO_STRESS_20260908.ino)
- [I2S Full-Duplex｜MIC + MAX98357A](./ESP32_I2S_FULL_DUPLEX_MIC_AMP_TEST_20260908.ino)
- [OV5640 实时视频](./ESP32_OV5640_VIDEO_STREAM_20260908.ino)
- [I2S MIC WAV 录音](./ESP32_I2S_MIC_WAV_RECORDER_20260908.ino)
- [MAX98357A 基础播放](./ESP32_MAX98357A_TONE_TEST_20260908.ino)
- [BMI260-like 完整功能测试](./ESP32_BMI260_DIRECT_FULL_TEST_20260907.ino)
- [BMI260-like INT1 Data Ready](./ESP32_BMI260_INT1_DRDY_TEST_20260908.ino)
- [PCA9540B + 双 DRV2605L](./ESP32_PCA9540B_DUAL_DRV2605L_LRA_TEST_20260908.ino)
- [DRV2605L 单通道 OC 诊断](./ESP32_DRV2605L_SINGLE_CHANNEL_OC_DIAG_20260908.ino)

## 使用规则

- 后续新增测试程序时同步更新本索引。
- 对话里给测试程序时，优先直接给该文件 GitHub 链接，不再让用户依赖全局 Code Search。
- Wi-Fi 凭据由 ESP32 已保存凭据 / NVS 复用，不在仓库里保存真实密码。
- 无目标 AP/Wi-Fi 环境时，不应把 `WIFI INIT FAIL` 解释成板级冷启动失败；改用 Test 5C OFFLINE 验证其他硬件链路。
- Test 5C OFFLINE V1 存在显示逻辑歧义：上传/复位后的非 POWERON 启动虽然不会写入 pass/fail 计数，但 15 秒后仍会打印 `THIS OFFLINE BOOT: PASS`，且之后持续输出日志。V2 已修正：非 POWERON 只打印 `PRECHECK ... NOT COUNTED`；只有 `RESET REASON: POWERON` 才能判冷启动 PASS/FAIL；15 秒判定后停止前台刷屏并等待下一次真实断电上电。

## 测试程序声音提示约定（2026-09-09 起）

后续新测试程序在 MAX98357A / I2S Audio 可用时，默认加入不同的开始/结束提示音，方便不盯串口也能判断测试阶段。

```text
START：短促上升三音，表示“测试开始”
PASS：清晰高音双响/上升音，表示“测试完成且通过”
FAIL：低音下降三音，表示“测试结束但失败”
```

实现原则：

- 优先直接用 I2S 合成短音，不依赖外部 WAV 文件，避免额外 Flash/文件资源。
- 提示音总长度尽量控制在约 0.3~1.0 s，不干扰正式采样。
- START 音在 Audio 初始化成功后播放；若当前测试本身就是在验证 Audio 初始化，则先以串口日志作为最早阶段提示，Audio 一旦可用再播放 START 音。
- PASS/FAIL 音只在最终判定完成后播放一次，不循环。
- 无 Audio 硬件、Audio 本身初始化失败、或该测试明确要求静音时，不强行播放声音，以 Serial 状态为准。
- 长时间压力测试仍以日志/错误计数为正式判据，声音提示只用于阶段通知，不代替测试数据。
