# ESP32-S3 Arduino Bring-up

日期：2026-09-06

目标板：`ESP32-S3-MINI-1U-N4R2`

## 已验证硬件状态

- ESP32-S3 ROM 下载：PASS
- 原生 USB-Serial/JTAG：PASS
- COM 端口：COM8
- Flash：4MB Quad SPI，PASS
- PSRAM：2MB，已由 esptool 识别
- `flash-id`：PASS

## 当前 Arduino IDE 状态

Arduino IDE 2.3.8 能看到：

```text
COM8 Serial Port (USB)
```

但开发板搜索 `esp32s3` 时没有结果，说明 PC 端尚未安装 Arduino-ESP32 board package；这不是板子故障。

## 下一步

安装 `esp32 by Espressif Systems` 后：

```text
Board: ESP32S3 Dev Module
Port: COM8
Flash Size: 4MB
PSRAM: QSPI PSRAM
USB CDC On Boot: Enabled
```

`ESP32-S3-MINI-1U-N4R2` 官方存储配置为 4MB Quad SPI Flash + 2MB Quad SPI PSRAM，所以不要选 OPI/Octal PSRAM。

首个固件只做原生 USB 串口周期输出，确认 Flash 写入、复位、从 Flash 启动和 USB 串口稳定后，再进入外设 Bring-up。
