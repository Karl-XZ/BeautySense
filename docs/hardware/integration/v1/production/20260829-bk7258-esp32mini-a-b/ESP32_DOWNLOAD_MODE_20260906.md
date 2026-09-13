# ESP32-S3-MINI-1U 下载模式进入与首个烧录测试

适用：银龄智护 V1 A-ESP，`ESP32-S3-MINI-1U-N4R2`。

## 1. BOOT/EN 手动进入 ROM Download Mode

测试点：`BOOT`、`EN`、`GND`。

操作顺序：

1. 板子正常供电，确认 3V3 正常。
2. 将 `BOOT` 临时短接到 `GND`。
3. 将 `EN` 短接到 `GND` 约 0.5~1 s。
4. 先松开 `EN`，让 EN 回到高电平。
5. 再松开 `BOOT`。

等价逻辑：在复位释放瞬间让 BOOT/GPIO0 保持低电平，ESP32-S3 就会进入 ROM 下载模式。

## 2. USB 下载路径

若板上原生 USB D+/D- 已正确连接 ESP32-S3，并通过 22Ω 串阻和 ESD 到接口，可直接将 USB 数据线接电脑。

进入下载模式后，电脑应出现新的 USB/串口类设备。若不识别，优先检查：3V3、EN、BOOT、USB D+/D- 连通性、22Ω 串阻、USB ESD、数据线。

## 3. UART 下载备用路径

若原生 USB 暂时不能用，可通过 `UART_TX` / `UART_RX` / `GND` 测试点接 USB-UART：

- USB-UART TX -> 板上 UART_RX
- USB-UART RX -> 板上 UART_TX
- GND -> GND

板子若已由自身 5V 输入供电，不要再由 USB-UART 的 VCC 给板供电。

## 4. esptool 读取芯片信息

安装 esptool 后，识别串口并执行：

```bash
python -m esptool --chip esp32s3 chip_id
```

若新版 esptool 使用连字符命令，可使用：

```bash
esptool --chip esp32s3 chip-id
```

成功时应能读到 ESP32-S3 芯片信息，说明 ROM 下载链路基本成立。

## 5. 首个程序原则

首个程序只做最小功能，例如串口周期打印，不要一开始启用 Camera、Wi-Fi、音频、LRA 等全部外设。

目标：先证明电源、ESP 模组、BOOT/EN、USB/UART 下载链路和 Flash 基本正常。

记录日期：2026-09-06
