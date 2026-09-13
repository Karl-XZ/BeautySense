# 银龄智护 B1｜双 DRV2605L / LRA 随机模板长时间压力测试结果

日期：2026-09-09

程序：`ESP32_DUAL_DRV2605L_LRA_PATTERN_STRESS_20260909.ino`

## 结论

自动化/电气链路判定：**PASS / STRONG**。

Haptic 总体关闭仍保留一个人工边界：需要确认两颗 LRA 在整段测试中均无可感知掉振、忽强忽弱、异常发热或异响。若人工体感也正常，则可将此前 `INTERMITTENT / UNRESOLVED` 正式关闭为 `PASS / STRONG`。

## 日志结构

上传日志中包含两次测试运行：

- Run 1：运行到 `elapsed=1737 s`（约 28 min 57 s），未出现硬停条件；随后日志进入新的测试启动段，因此该次不作为 120 min 完整结果。
- Run 2：完整运行到 `elapsed=7200 s`，达到 120 min，并输出 `PASS CANDIDATE` 与 PASS 音效。

## 完整 Run 2 最终数据

```text
elapsed=7200s
rounds=3865
patterns=3865
extTrigChecks=39
ch0Effects=9484
ch1Effects=8938

i2cErr=0
goTimeout=0
OC=0
OT=0

statusReads=36944
diagBitSeen=0
heap=291076
```

程序最终输出：

```text
========== HAPTIC STRESS 120-MIN MILESTONE ==========
PASS SO FAR: no hard-stop condition observed.

================ HAPTIC STRESS RESULT ================
DUAL DRV2605L / LRA CHANNEL STRESS: PASS CANDIDATE
...
AUDIO CUE: PASS
```

## 覆盖范围

本轮实际覆盖：

- PCA9540B 0x70 分支切换
- CH0 / CH1 两颗 DRV2605L 0x5A 持续访问
- 两路 LRA Library 6 ROM effect 反复启停
- 12 个基础模板随机选取
- 每轮模板内部 effect shuffle
- CH0-only
- CH1-only
- A/B alternating
- dual near-simultaneous
- GPIO34 / GPIO35 External Trigger
- 每 100 轮周期性重新验证 External Trigger
- 启动阶段两路保守 RTP 梯度：0x08 -> 0x10 -> 0x18 -> 0x20 -> 0x30，每级 120 ms
- 持续 STATUS 检查与 OC/OT/GO timeout/I2C 硬停监控

## 关键意义

此前 Haptic 曾出现：

```text
CH1 GO timeout
CH0/CH1 OC_DETECT=1
```

而本轮完整 120 min 中：

```text
i2cErr=0
goTimeout=0
OC=0
OT=0
diagBitSeen=0
```

两路 effect 计数分别达到 9484 与 8938，说明两路控制/输出通道都被高频、反复、不同模式覆盖，且未复现此前的软件可见异常。

## 判定边界

这份结果证明当前焊接/连接状态下，PCA9540B + 双 DRV2605L + 双 LRA 的控制与驱动链路已通过 2 小时随机模板压力验证。

它不等同于执行器寿命认证、正式振动可靠性、最大幅度热极限或量产统计验证。程序有意排除 effect 14/15/16 和长时间 0x7F RTP，以避免把“通道稳定性测试”变成极限功率烧机。

若人工确认 2 小时内两颗 LRA 无掉振、异常热、异响或明显不一致，则 Haptic 可正式标记：`PASS / STRONG`。
