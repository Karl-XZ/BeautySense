# BLE GATT 手机测试记录（2026-09-09）

状态：IN PROGRESS / PARTIAL PASS

程序：`ESP32_BLE_GATT_PHONE_TEST_20260909.ino`

当前实测：

```text
connected=1
connectCount=2
disconnectCount=1
writeCount=1
notifyCount>=920
pingSeen=1
echoSeen=0
notifyAckSeen=0
passLatched=0
heap=180360 B
```

关键日志：

```text
BLE RX #1: PING
BLE TX: PONG
```

因此当前已确认：

- BLE advertising / iPhone discovery：PASS
- BLE connection：PASS
- disconnect + reconnect：PASS（目前累计连接 2 次）
- GATT TX Notify：PASS，SEQ 持续增长到 900+
- GATT RX Write：PASS
- `PING -> PONG` 双向命令链路：PASS
- Heap 在观察段稳定为约 180360 B

尚未完成：

1. 向 RX 写 `ECHO 12345`，确认 TX 返回 `ECHO:12345`
2. 向 RX 写 `NOTIFY_OK`，记录手机确实收到持续通知
3. 再断开并重连一次，使 `connectCount >= 3`
4. 最后写 `PASS`，触发正式 `BLE GATT PHONE TEST: PASS` 和 PASS 提示音

当前不能关闭为最终 PASS，因为程序正式门槛为：`connectCount>=3 && pingSeen && echoSeen && notifyAckSeen`。
