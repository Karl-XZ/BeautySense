# BLE GATT 手机测试记录（2026-09-09）

状态：PASS / STRONG

程序：`ESP32_BLE_GATT_PHONE_TEST_20260909.ino`

## 最终实测

```text
connected=1
connectCount=3
disconnectCount=2
writeCount=4
notifyCount=2386
pingSeen=1
echoSeen=1
notifyAckSeen=1
passLatched=1
heap≈180360 B（断开时约 180404 B）
```

最终关键日志：

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

BLE RX #4: PASS
BLE TX: TEST PASS

================ BLE TEST RESULT ================
BLE GATT PHONE TEST: PASS
CONN=3 DISC=2 WR=4 NTF=2386 PING=1 ECHO=1 NACK=1 PASS=1
=================================================
AUDIO CUE: PASS
```

## 已确认

- BLE advertising / iPhone discovery：PASS
- BLE connection：PASS
- disconnect + advertising restart + reconnect：PASS
- 累计连接 3 次：PASS
- GATT TX Read：PASS
- GATT TX Notify：PASS，连续发送至 `notifyCount=2386`
- GATT RX Write：PASS，共 4 次有效写入
- `PING -> PONG`：PASS
- `ECHO 12345 -> ECHO:12345`：PASS
- 手机确认持续收到 Notify，并回写 `NOTIFY_OK`：PASS
- 最终 `PASS` 命令：PASS，程序 `passLatched=1`
- PASS 声音提示：已触发 `AUDIO CUE: PASS`
- Heap 长时间观察稳定在约 180360 B；断开时约 180404 B，无持续下降

## 判定

BLE GATT 手机控制/数据链路正式关闭为：**PASS / STRONG**。

该结论覆盖：广播、iPhone 发现、连接、Read、Write、Notify、确定性命令、带 payload 回显、断开、重新广播、重连和最终状态锁存。

边界：这里验证的是 ESP32-S3 BLE GATT 控制/数据链路，不代表 Bluetooth Classic A2DP，也不代表 LE Audio 音频链路。