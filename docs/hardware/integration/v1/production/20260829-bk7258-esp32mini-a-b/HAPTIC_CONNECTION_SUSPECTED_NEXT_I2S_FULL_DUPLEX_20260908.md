# Haptic 间歇问题暂记 + 下一项 I2S 全双工测试（2026-09-08）

## Haptic 当前结论

- 两颗 DRV2605L 已重新焊接；PCA9540B CH0/CH1 分别选通时均能看到 0x5A。
- 曾出现 CH1 ROM effect 15 `GO timeout`，强 RTP 时两路曾出现 `OC_DETECT=1`。
- 随后在未重新烧录、未修改固件的情况下又恢复正常振动。
- 用户当前判断更像连接/接触问题。
- 工程状态仍记为 `INTERMITTENT / CONNECTION SUSPECTED / NOT CLOSED`，后续整机稳定性阶段补做机械扰动与重复性验证。

## 当前继续执行

Haptic 暂不继续消耗时间，进入 Test 4 的 I2S Full-Duplex：

```text
MIC -> GPIO38 -> ESP32-S3 I2S RX
                 ^
GPIO36 BCLK -----| shared
GPIO37 WS -------| shared
                 v
ESP32-S3 I2S TX -> GPIO39 -> MAX98357A -> Bone
GPIO40 -> AMP_SD_MODE
```

目标：验证 MIC RX 与 MAX98357A TX 在共享 GPIO36 BCLK / GPIO37 WS 的情况下可以同时稳定运行。

使用程序：
`ESP32_I2S_FULL_DUPLEX_MIC_AMP_TEST_20260908.ino`

PASS 条件：
- FULL-DUPLEX INIT PASS；
- 播放 1 kHz 测试音时骨传导持续正常发声；
- 同时 MIC RMS/Peak 持续更新、不冻结；
- 静音阶段说话时 MIC RMS 明显上升；
- 连续 1~2 分钟无 TX WRITE FAIL / RX READ FAIL / 重启 / 卡死。
