#include <WiFi.h>

// =============================
// 填你自己的 2.4GHz Wi-Fi
// =============================
const char* WIFI_SSID = "YOUR_WIFI_NAME";
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";

// 公网测试目标
const char* TEST_HOST = "example.com";
const uint16_t TEST_PORT = 80;

const int TEST_ROUNDS = 10;

bool ensureWiFi()
{
  if (WiFi.status() == WL_CONNECTED)
    return true;

  Serial.println("WiFi disconnected, reconnecting...");
  WiFi.disconnect();
  delay(300);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 15000)
  {
    Serial.print(".");
    delay(500);
  }
  Serial.println();

  return WiFi.status() == WL_CONNECTED;
}

bool testDNS(IPAddress &ip)
{
  Serial.print("DNS: ");
  Serial.print(TEST_HOST);
  Serial.print(" -> ");

  if (!WiFi.hostByName(TEST_HOST, ip))
  {
    Serial.println("FAIL");
    return false;
  }

  Serial.print(ip);
  Serial.println("  PASS");
  return true;
}

bool testHTTP()
{
  WiFiClient client;

  Serial.print("TCP: ");
  Serial.print(TEST_HOST);
  Serial.print(":");
  Serial.print(TEST_PORT);
  Serial.print(" -> ");

  if (!client.connect(TEST_HOST, TEST_PORT))
  {
    Serial.println("FAIL");
    return false;
  }

  Serial.println("PASS");

  client.print("GET / HTTP/1.1\r\n");
  client.print("Host: example.com\r\n");
  client.print("Connection: close\r\n");
  client.print("User-Agent: ESP32-S3-Bringup\r\n");
  client.print("\r\n");

  unsigned long start = millis();
  while (!client.available() && client.connected() && millis() - start < 5000)
  {
    delay(10);
  }

  if (!client.available())
  {
    Serial.println("HTTP: no response -> FAIL");
    client.stop();
    return false;
  }

  String statusLine = client.readStringUntil('\n');
  statusLine.trim();

  Serial.print("HTTP: ");
  Serial.println(statusLine);

  bool ok = statusLine.startsWith("HTTP/");

  // 清掉剩余数据，避免占用连接
  unsigned long drainStart = millis();
  while ((client.connected() || client.available()) && millis() - drainStart < 1500)
  {
    while (client.available())
      client.read();
    delay(1);
  }

  client.stop();

  if (ok)
    Serial.println("HTTP response PASS");
  else
    Serial.println("HTTP response FAIL");

  return ok;
}

void setup()
{
  Serial.begin(115200);
  delay(2500);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 WIFI INTERNET TEST");
  Serial.println("DNS + TCP + HTTP, 10 rounds");
  Serial.println("========================================");

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);

  Serial.print("Connecting to: ");
  Serial.println(WIFI_SSID);

  WiFi.begin(WIFI_SSID, WIFI_PASS);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000)
  {
    Serial.print(".");
    delay(500);
  }
  Serial.println();

  if (WiFi.status() != WL_CONNECTED)
  {
    Serial.println("WIFI CONNECT FAIL");
    Serial.println("STOP TEST");
    return;
  }

  Serial.println("WIFI CONNECT PASS");
  Serial.print("SSID: ");
  Serial.println(WiFi.SSID());
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());
  Serial.print("Gateway: ");
  Serial.println(WiFi.gatewayIP());
  Serial.print("DNS: ");
  Serial.println(WiFi.dnsIP());
  Serial.print("RSSI: ");
  Serial.print(WiFi.RSSI());
  Serial.println(" dBm");

  int wifiPass = 0;
  int dnsPass = 0;
  int httpPass = 0;

  for (int round = 1; round <= TEST_ROUNDS; round++)
  {
    Serial.println();
    Serial.print("========== ROUND ");
    Serial.print(round);
    Serial.print(" / ");
    Serial.print(TEST_ROUNDS);
    Serial.println(" ==========");

    if (!ensureWiFi())
    {
      Serial.println("WiFi reconnect FAIL");
      delay(1500);
      continue;
    }

    wifiPass++;

    Serial.print("WiFi RSSI = ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");

    IPAddress resolvedIP;
    if (!testDNS(resolvedIP))
    {
      delay(1500);
      continue;
    }

    dnsPass++;

    if (testHTTP())
      httpPass++;

    delay(1500);
  }

  Serial.println();
  Serial.println("========================================");
  Serial.println("FINAL RESULT");
  Serial.println("========================================");

  Serial.print("WiFi connected rounds = ");
  Serial.print(wifiPass);
  Serial.print(" / ");
  Serial.println(TEST_ROUNDS);

  Serial.print("DNS PASS              = ");
  Serial.print(dnsPass);
  Serial.print(" / ");
  Serial.println(TEST_ROUNDS);

  Serial.print("HTTP/TCP PASS         = ");
  Serial.print(httpPass);
  Serial.print(" / ");
  Serial.println(TEST_ROUNDS);

  if (wifiPass == TEST_ROUNDS &&
      dnsPass == TEST_ROUNDS &&
      httpPass == TEST_ROUNDS)
  {
    Serial.println();
    Serial.println("INTERNET TEST: PASS");
  }
  else
  {
    Serial.println();
    Serial.println("INTERNET TEST: FAIL / PARTIAL");
  }
}

void loop()
{
  delay(1000);
}
