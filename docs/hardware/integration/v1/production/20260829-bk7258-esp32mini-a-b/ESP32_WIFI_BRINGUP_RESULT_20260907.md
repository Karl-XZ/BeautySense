# ESP32-S3 Wi-Fi Bring-up Result — 2026-09-07

## Device under test

- ESP32-S3-MINI-1U-N4R2
- Arduino board profile: ESP32S3 Dev Module
- Test sketch: `ESP32_WIFI_CONNECT_TEST_20260907.ino`
- Wi-Fi mode: STA

## Observed scan result

The ESP32-S3 found 5 nearby 2.4 GHz APs. Representative entries from the serial log:

```text
DIILAB                 RSSI=-48 dBm  CH=9
xipunaizhan             RSSI=-59 dBm  CH=11
KCL7888                 RSSI=-65 dBm  CH=6
DIRECT-02-HP Laser...   RSSI=-75 dBm  CH=11
泰来通讯                 RSSI=-92 dBm  CH=6
```

This confirms the Wi-Fi receiver and external-antenna RF path are functioning at a basic level.

## Association / DHCP result

```text
Connecting to DIILAB
WIFI PASS
SSID: DIILAB
IP: 192.168.1.68
RSSI: -48 dBm
Channel: 9
```

The periodic keep-alive log remained stable, with RSSI around `-48` to `-49 dBm` and IP remaining `192.168.1.68`.

## Qualification status

```text
Wi-Fi scan / RX basic path       PASS
2.4 GHz AP discovery             PASS
STA association                  PASS
Wi-Fi TX/RX basic bidirectional  PASS
DHCP                             PASS
Connection persistence          PASS (short-duration bring-up)
RSSI at test position            ~ -48 to -49 dBm
Assigned IPv4                    192.168.1.68
Channel                          9
```

This test does not yet qualify sustained throughput, long-duration stability, RF sensitivity, conducted/radiated power, or antenna matching. Those require dedicated throughput/RF tests.
