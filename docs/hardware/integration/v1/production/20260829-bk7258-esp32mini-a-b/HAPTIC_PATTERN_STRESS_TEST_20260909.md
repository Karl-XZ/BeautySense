# 银龄智护 B1｜双 DRV2605L / 双 LRA 通道完整性与长时间压力测试

日期：2026-09-09

状态：READY / NOT RUN YET

程序：`ESP32_DUAL_DRV2605L_LRA_PATTERN_STRESS_20260909.ino`

## 目的

当前 Haptic 历史状态为 `INTERMITTENT / UNRESOLVED`：两颗 DRV2605L 的 I2C 地址均可通过 PCA9540B 分别访问，但曾出现 CH1 `GO timeout`，以及强 RTP 时 CH0/CH1 都出现过 `OC_DETECT=1`，随后同一固件下又恢复正常。用户判断更像连接/接触类问题。

本轮不再做单次“能不能振”的验证，而是做通道完整性 + 多模板随机组合 + 长时间重复播放压力测试。

## 测试结构

### Phase A｜保守 RTP 电气完整性筛查

两路分别执行：

```text
0x08 -> 0x10 -> 0x18 -> 0x20 -> 0x30
```

每级仅驱动 120 ms。任何 `OC_DETECT` / `OVER_TEMP` 立即停机。

### Phase B｜外部触发链路

- GPIO34 -> CH0 / LRA A
- GPIO35 -> CH1 / LRA B

两路分别配置 DRV2605L External Edge Trigger 后触发一次，并读取 STATUS。

### Phase C｜Library 6 随机 ROM 模板长压测

使用 DRV2605L LRA Library 6。

定义 12 个基础模板，覆盖 effect 1~13 的短促点击 / bump / double / triple / fuzz 组合。每轮：

1. 随机选 1 个基础模板；
2. 随机打乱模板内部 effect 顺序；
3. 四种通道策略循环覆盖：
   - CH0 only
   - CH1 only
   - CH0/CH1 交替
   - 双路近同时启动
4. 每 100 轮重新执行一次 GPIO34/GPIO35 External Trigger sanity check。

由于每个模板内部会 shuffle，实际播放序列远多于 12 种。

## 为什么不直接做长时间 0x7F RTP

本板历史上强 RTP 已观察到两路 `OC_DETECT=1`。因此这份压力测试不把“最大振幅连续硬顶”当作默认稳定性测试方式。

当前程序明确排除：

- RTP `0x7F` 长时间连续驱动
- effect 14 Strong Buzz 100%
- effect 15 750 ms Alert 100%
- effect 16 1000 ms Alert 100%

本轮重点是通道、PCA 切换、DRV I2C、GO engine、GPIO trigger、重复短波形以及连接稳定性，而不是执行器最大振幅极限。

## 默认时长

```text
120 min
```

程序在 10 / 30 / 60 / 120 min 打印 milestone。

## 自动硬停条件

任何一项发生即判 FAIL 并停止两颗 DRV：

- PCA9540B 选择失败
- DRV2605L I2C 读写失败
- `GO timeout`
- `OC_DETECT=1`
- `OVER_TEMP=1`

## HEALTH 字段

```text
elapsed
rounds
patterns
extTrigChecks
ch0Effects
ch1Effects
i2cErr
goTimeout
OC
OT
statusReads
diagBitSeen
heap
```

其中正式安全门槛重点看：

```text
i2cErr=0
goTimeout=0
OC=0
OT=0
```

并要求 CH0 / CH1 effect 计数持续增长。

## 声音提示

使用 MAX98357A：

```text
START: 660 -> 880 -> 1175 Hz
PASS : 1047 -> 1568 Hz
FAIL : 660 -> 440 -> 220 Hz
```

声音只做阶段提示，不代替串口日志和错误计数。

## 最终人工检查

即使固件输出 `PASS CANDIDATE`，还要人工确认：

- 两颗 LRA 整个测试过程中都确实有正常体感输出；
- 没有明显忽强忽弱、失振、异响；
- DRV2605L / LRA / 焊点没有异常发热；
- 轻微触碰板边/线束/马达连接时没有出现瞬断。

只有自动错误计数为 0 + 人工体感正常，才把 Haptic 从 `INTERMITTENT / UNRESOLVED` 关闭为 PASS。
