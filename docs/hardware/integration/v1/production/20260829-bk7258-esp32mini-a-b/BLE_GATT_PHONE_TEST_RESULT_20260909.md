# BLE GATT 手机测试记录（2026-09-09）

状态：READY FOR FINAL PASS / ALL REQUIREMENTS MET EXCEPT FINAL PASS COMMAND

程序：`ESP32_BLE_GATT_PHONE_TEST_20260909.ino`

## 当前实测

```text
connected=1
connectCount=3
disconnectCount=2
writeCount=3
notifyCount>=2090
pingSeen=1
echoSeen=1
notifyAckSeen=1
passLatched=0
heap≈180360 B（断开时约 180404 B）
```

关键日志：

```text
BLE RX #1: PING
BLE TX: PONG

BLE RX #2: ECHO 12345
BLE TX: ECHO:12345

BLE RX #3: NOTIFY_OK
BLE TX: NOTIFY_ACK_RECORDED

BLE DISCONNECTED | disconnectCount=2
BLE ADVERTISING RESTARTED
BLE CONNECTED | connectCount=3
```

## 已确认

- BLE advertising / iPhone discovery：PASS
- BLE connection：PASS
- disconnect + advertising restart + reconnect：PASS
- 第 3 次连接达成：PASS（`connectCount=3`）
- GATT TX Read：PASS
- GATT TX Notify：PASS，SEQ 持续增长至 2000+
- GATT RX Write：PASS
- `PING -> PONG` 双向命令链路：PASS
- `ECHO 12345 -> ECHO:12345` 双向数据链路：PASS
- 手机已确认持续收到 Notify，并回写 `NOTIFY_OK`：PASS
- Heap 长时间观察稳定在约 180360 B；断开时短暂约 180404 B，无持续下降

## 最后一步

程序正式门槛：

```text
connectCount >= 3
pingSeen = 1
echoSeen = 1
notifyAckSeen = 1
```

当前四项已经全部满足。现在只需在 RX / A001 写入：

```text
PASS
```

预期：

```text
BLE RX #4: PASS
BLE TX: TEST PASS
================ BLE TEST RESULT ================
BLE GATT PHONE TEST: PASS
...
AUDIO CUE: PASS
```

在看到正式 `BLE GATT PHONE TEST: PASS` 前，记录仍不关闭为最终 PASS。