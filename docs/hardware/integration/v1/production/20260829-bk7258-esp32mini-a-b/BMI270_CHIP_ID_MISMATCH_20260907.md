# ESP32-S3 首板 BMI270 身份异常记录（2026-09-07）

## 实测

SENSOR_I2C：

```text
SDA = GPIO21
SCL = GPIO18
```

I2C 扫描：

```text
0x68 ACK
0x70 ACK
```

0x70 为 PCA9540B。

对 0x68 的寄存器 `0x00` 直接读取：

```text
Raw CHIP_ID = 0x27
```

预期 BMI270：

```text
CHIP_ID = 0x24
```

## 关键判断

`0x27` 不是 BMI270 的合法 CHIP_ID；公开 Linux BMI270/BMI260 驱动明确区分：

```text
BMI260 CHIP_ID = 0x27
BMI270 CHIP_ID = 0x24
```

因此当前 0x68 器件表现与 BMI260 高度一致，而不是 BMI270。

这也解释了为何 7Semi BMI270 库 `imu.begin()` 失败：BMI260 与 BMI270 使用不同的初始化配置数据，不能把 BMI270 的初始化数据直接发送给 BMI260。

## 当前状态

```text
ESP32 SENSOR_I2C        PASS
PCA9540B 0x70           PASS
0x68 ACK                 PASS
BMI270 身份              FAIL
实测身份                 SUSPECT BMI260
7Semi BMI270 init        FAIL（符合身份不匹配现象）
```

## 处理原则

1. 暂停把该器件标记为 BMI270 PASS。
2. 不因 `imu.begin()` 失败立即返修 I2C 线路；基础总线已经工作。
3. 用原始 Wire 连续读取 `0x00` 多次，并分别在 100kHz/400kHz 下验证结果是否稳定为 `0x27`。
4. 检查实物顶标、采购来源、包装标签和入库记录。
5. 项目 BOM 目标仍保持 BMI270；若确认安装件实际为 BMI260，则属于来料/装配型号不一致，应更换正确 BMI270。
6. 若仅为临时板级 Bring-up，可另用 BMI260 驱动验证六轴，但不得据此将 BMI270 项目项判 PASS。

## BOM 参考

当前项目 BOM 目标：

```text
Bosch BMI270
LCSC/JLCPCB: C2836813
LGA-14 2.5x3 mm
```

公开 LCSC/JLCPCB 条目将 C2836813 标为 BMI270，因此若安装件来自该料号但稳定读取 0x27，需要进一步追踪实物料号与装配来源。
