# TPS63021 3V3 轻载电压实测（2026-09-07）

## 实板配置

```text
TPS63021 fixed 3.3V
PS/SYNC -> GND
```

当前设计按既定方案将 `PS/SYNC` 直接接地，因此允许 TPS63021 在轻载时进入 Power Save Mode。

## 实测

```text
BMI VDD   = 3.415V
BMI VDDIO = 3.415V
```

两者均来自系统 3V3 电源轨。3.415V 相对标称 3.3V 约高 3.5%。当前板处于较轻负载状态，且两颗 DRV2605L 已拆除，因此该现象与 Power Save Mode 轻载输出抬高相符。

## 判定

```text
TPS63021 3V3 rail: BASIC PASS
3.415V under present light load: ACCEPT / expected with PS/SYNC low
Do not treat this measurement alone as regulator failure.
```

此测量也说明 BMI 当前 VDD/VDDIO 并不存在明显欠压。BMI 身份异常 `CHIP_ID=0x27` 应继续按器件身份/实装料调查，而不是优先返修 3V3 电源。
