#include <WiFi.h>

// =============================
// 填你自己的 2.4GHz Wi-Fi
// =============================
const char* WIFI_SSID = "YOUR_WIFI_NAME";
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";

void scanWiFi() {
  Serial.println();
  Serial.println("========== WIFI SCAN ==========");

  int n = WiFi.scanNetworks(false, true);

  if (n <= 0) {
    Serial.println("No Wi-Fi networks found.");
    return;
  }

  Serial.print("Found networks: ");
  Serial.println(n);

  for (int i = 0; i < n; i++) {
    Serial.print(i + 1);
    Serial.print(". SSID=");
    Serial.print(WiFi.SSID(i));
    Serial.print(" RSSI=");
    Serial.print(WiFi.RSSI(i));
    Serial.print(" dBm CH=");
    Serial.print(WiFi.channel(i));
    Serial.print(" ENC=");
    Serial.println(WiFi.encryptionType(i));
  }

  WiFi.scanDelete();
  Serial.println("===============================");
}

void setup() {
  Serial.begin(115200);
  delay(2500);

  Serial.println();
  Serial.println("====================================");
  Serial.println("ESP32-S3 WIFI CONNECT TEST");
  Serial.println("====================================");

  Serial.print("MAC: ");
  Serial.println(WiFi.macAddress());

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.disconnect(true, true);
  delay(500);

  // 先扫描，验证 Wi-Fi 射频部分能否看到 AP
  scanWiFi();

  if (String(WIFI_SSID) == "YOUR_WIFI_NAME") {
    Serial.println();
    Serial.println("SSID/password not configured.");
    Serial.println("Edit WIFI_SSID and WIFI_PASS, then upload again.");
    return;
  }

  Serial.println();
  Serial.print("Connecting to: ");
  Serial.println(WIFI_SSID);

  WiFi.begin(WIFI_SSID, WIFI_PASS);

  unsigned long start = millis();
  const unsigned long timeoutMs = 20000;

  while (WiFi.status() != WL_CONNECTED && millis() - start < timeoutMs) {
    Serial.print(".");
    delay(500);
  }

  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("========== WIFI PASS ==========");
    Serial.println("Connected successfully.");
    Serial.print("SSID: ");
    Serial.println(WiFi.SSID());
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());
    Serial.print("Gateway: ");
    Serial.println(WiFi.gatewayIP());
    Serial.print("RSSI: ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
    Serial.print("Channel: ");
    Serial.println(WiFi.channel());
    Serial.println("===============================");
  } else {
    Serial.println("========== WIFI FAIL ==========");
    Serial.print("WiFi.status() = ");
    Serial.println((int)WiFi.status());
    Serial.println("Check SSID/password, 2.4GHz band, and external antenna connection.");
    Serial.println("===============================");
  }
}

void loop() {
  static unsigned long last = 0;

  if (millis() - last >= 3000) {
    last = millis();

    if (WiFi.status() == WL_CONNECTED) {
      Serial.print("WiFi alive | RSSI=");
      Serial.print(WiFi.RSSI());
      Serial.print(" dBm | IP=");
      Serial.println(WiFi.localIP());
    } else {
      Serial.println("WiFi disconnected");
    }
  }
}
