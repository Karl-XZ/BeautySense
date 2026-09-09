# 银龄智护 B1 测试程序索引

目的：避免依赖 GitHub 全局 Code Search。后续测试程序统一从本页直接进入。

## 当前测试程序

- [Test 5C OFFLINE｜20 次真实断电冷启动（无 Wi-Fi 环境）](./ESP32_TEST5C_OFFLINE_COLD_BOOT_20X_20260909.ino)
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
