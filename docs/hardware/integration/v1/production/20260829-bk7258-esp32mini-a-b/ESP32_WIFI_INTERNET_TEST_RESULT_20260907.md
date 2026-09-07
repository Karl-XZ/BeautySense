# ESP32-S3 Wi-Fi Internet Test Result — 2026-09-07

Target: ESP32-S3-MINI-1U-N4R2

Test program:

`ESP32_WIFI_INTERNET_HTTP_TEST_20260907.ino`

## Network

```text
SSID: DIILAB
IPv4: 192.168.1.68
Gateway: 192.168.1.1
DNS server: 192.168.1.1
Initial RSSI: -38 dBm
```

Wi-Fi password is intentionally not stored in this repository.

## 10-round Internet test

Each round executed:

```text
Wi-Fi connected
  -> DNS example.com
  -> TCP example.com:80
  -> HTTP GET /
  -> HTTP/1.1 200 OK
```

Observed RSSI over the ten rounds:

```text
-38, -37, -34, -33, -30, -35, -33, -33, -33, -32 dBm
```

All ten rounds returned DNS success, TCP connection success, and `HTTP/1.1 200 OK`.

Final result:

```text
WiFi connected rounds = 10 / 10
DNS PASS              = 10 / 10
HTTP/TCP PASS         = 10 / 10
INTERNET TEST: PASS
```

## Qualification status

```text
2.4 GHz AP scan            PASS
STA association            PASS
DHCP                       PASS
DNS resolution             PASS
Public TCP connection      PASS
HTTP request/response      PASS
10-round short stability   PASS
Basic external RF path     PASS
```

This confirms basic Internet connectivity through the ESP32-S3 Wi-Fi path. It does not yet qualify long-duration stability, throughput, weak-signal sensitivity, conducted/radiated RF power, antenna S11/matching, enclosure detuning, or EMI/EMC.
