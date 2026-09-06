# ESP32-S3 Arduino 上传成功但原生 USB 串口无输出

日期：2026-09-06

## 当前状态

ESP32-S3-MINI-1U-N4R2：

- esptool chip-id：PASS
- esptool flash-id：PASS
- Flash：4MB
- PSRAM：2MB
- 原生 USB 下载链路：PASS
- Arduino IDE：固件上传成功
- Arduino 串口监视器：暂无输出，待排查

## 优先排查

1. 上传完成后确认 `BOOT` 已完全释放；
2. 只对 `EN` 做一次低脉冲复位，让芯片从 Flash 正常启动；
3. 重新检查运行时 COM 口，原生 USB 可能重新枚举；
4. Arduino IDE 设定：
   - Board: ESP32S3 Dev Module
   - Flash Size: 4MB
   - PSRAM: QSPI PSRAM
   - USB CDC On Boot: Enabled
5. 使用每秒持续打印的最小程序测试；
6. 若仍无输出，用板上 UART_TX/UART_RX/GND 接 USB-UART 区分“程序未启动”与“USB CDC 配置问题”。

## 判据

```text
上传成功 != 用户程序已正常从Flash运行
下载模式COM口 != 必然等于运行模式COM口
USB下载PASS != USB CDC运行输出必然PASS
```
