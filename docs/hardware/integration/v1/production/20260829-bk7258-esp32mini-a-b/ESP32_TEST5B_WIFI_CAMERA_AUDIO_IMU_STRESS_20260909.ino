#include <Arduino.h>
#include <WiFi.h>
#include <Wire.h>
#include <Preferences.h>
#include <math.h>
#include "esp_camera.h"
#include "driver/i2s_std.h"

#if __has_include("bmi260_config.h")
#include "bmi260_config.h"
#else
#error "Place the same bmi260_config.h used by ESP32_BMI260_DIRECT_FULL_TEST_20260907.ino in this sketch folder."
#endif

// 银龄智护 B1 - Test 5B
// ESP32-S3: Wi-Fi + OV5640 + MIC RX + MAX98357A TX + BMI260-like IMU/PCA concurrency stress
// Arduino-ESP32 3.x
//
// Wi-Fi behavior:
// 1) First try credentials already saved by the ESP32 Wi-Fi stack from earlier tests.
// 2) Then try this project's own NVS Preferences namespace.
// 3) Only if both fail, ask once over Serial and save the successful credentials to NVS.
// Normal sketch uploads do not require re-entering the password unless flash/NVS is erased.
// The password is never printed to Serial and is not stored in this Git repository.
//
// Current mounted IMU responds as BMI260-like: I2C 0x68, CHIP_ID=0x27.
// This sketch deliberately stops if CHIP_ID is not 0x27.
// PCA9540B stays with CH0/CH1 disconnected (0x00). Haptic is NOT included in Test 5B yet.

// ---------------- Wi-Fi / NVS ----------------
static const char *DEFAULT_WIFI_SSID = "DIILAB";
static const char *WIFI_NVS_NAMESPACE = "yinling_wifi";
static const char *WIFI_NVS_KEY_SSID = "ssid";
static const char *WIFI_NVS_KEY_PASS = "pass";

// ---------------- Camera ----------------
static const int CAM_SIOD  = 1;
static const int CAM_SIOC  = 2;
static const int CAM_D0    = 4;
static const int CAM_D1    = 5;
static const int CAM_D2    = 6;
static const int CAM_D3    = 7;
static const int CAM_D4    = 8;
static const int CAM_D5    = 9;
static const int CAM_D6    = 10;
static const int CAM_D7    = 11;
static const int CAM_PCLK  = 12;
static const int CAM_HREF  = 13;
static const int CAM_VSYNC = 14;
static const int CAM_XCLK  = 15;
static const int CAM_RESET = 16;
static const int CAM_PWDN  = 17;

// ---------------- Sensor I2C ----------------
static const int SENSOR_SDA = 21;
static const int SENSOR_SCL = 18;
static const uint8_t IMU_ADDR = 0x68;
static const uint8_t PCA_ADDR = 0x70;
static const uint8_t BMI260_CHIP_ID = 0x27;

static const uint8_t REG_CHIP_ID         = 0x00;
static const uint8_t REG_ACC_X_LSB       = 0x0C;
static const uint8_t REG_INTERNAL_STATUS = 0x21;
static const uint8_t REG_ACC_CONF        = 0x40;
static const uint8_t REG_ACC_RANGE       = 0x41;
static const uint8_t REG_GYR_CONF        = 0x42;
static const uint8_t REG_GYR_RANGE       = 0x43;
static const uint8_t REG_INIT_CTRL       = 0x59;
static const uint8_t REG_INIT_ADDR_0     = 0x5B;
static const uint8_t REG_INIT_DATA       = 0x5E;
static const uint8_t REG_PWR_CONF        = 0x7C;
static const uint8_t REG_PWR_CTRL        = 0x7D;
static const uint8_t REG_CMD             = 0x7E;

static const uint8_t CMD_SOFT_RESET = 0xB6;
static const uint8_t INIT_OK = 0x01;
static const size_t BMI_CONFIG_CHUNK = 16;

// ---------------- Audio ----------------
static const int PIN_BCLK = 36;
static const int PIN_WS   = 37;
static const int PIN_DIN  = 38;  // MIC -> ESP32-S3
static const int PIN_DOUT = 39;  // ESP32-S3 -> MAX98357A
static const int PIN_SD   = 40;

static const uint32_t SAMPLE_RATE = 16000;
static const float TONE_FREQ_HZ = 1000.0f;
static const float TONE_LEVEL = 0.02f;

static i2s_chan_handle_t txHandle = NULL;
static i2s_chan_handle_t rxHandle = NULL;
static volatile bool toneEnabled = false;
static volatile bool txTaskAlive = false;
static volatile uint32_t txErrors = 0;

static uint32_t rxErrors = 0;
static uint32_t camErrors = 0;
static uint32_t wifiDropEvents = 0;
static uint32_t cameraFrames = 0;
static uint32_t imuReads = 0;
static uint32_t imuErrors = 0;
static uint32_t pcaErrors = 0;

static int activeMicChannel = 0;
static int32_t rxBuffer[256 * 2];

static float lastAx = 0.0f;
static float lastAy = 0.0f;
static float lastAz = 0.0f;
static float lastAMag = 0.0f;
static float lastGx = 0.0f;
static float lastGy = 0.0f;
static float lastGz = 0.0f;

static uint32_t lastCameraMs = 0;
static uint32_t lastHealthMs = 0;
static uint32_t lastToneToggleMs = 0;
static uint32_t lastImuMs = 0;
static uint32_t lastWifiState = WL_IDLE_STATUS;
static uint32_t testStartMs = 0;
static bool milestonePrinted = false;

// ============================================================
// Wi-Fi credentials: saved ESP32 config -> project NVS -> one-time Serial
// ============================================================

static bool waitForWiFi(uint32_t timeoutMs)
{
  const uint32_t t0 = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - t0 < timeoutMs)
  {
    Serial.print('.');
    delay(300);
  }
  Serial.println();
  return WiFi.status() == WL_CONNECTED;
}

static void printWiFiPass()
{
  Serial.printf("WiFi PASS | SSID=%s IP=%s RSSI=%d dBm\n",
                WiFi.SSID().c_str(),
                WiFi.localIP().toString().c_str(),
                WiFi.RSSI());
}

static bool connectWithCredentials(const String &ssid, const String &pass, uint32_t timeoutMs = 15000)
{
  if (ssid.length() == 0) return false;

  WiFi.disconnect(false, false);
  delay(200);
  WiFi.begin(ssid.c_str(), pass.c_str());
  Serial.printf("WiFi connecting to %s", ssid.c_str());
  return waitForWiFi(timeoutMs);
}

static String readSerialLineBlocking(const char *prompt)
{
  Serial.print(prompt);
  while (!Serial.available()) delay(20);
  String s = Serial.readStringUntil('\n');
  s.trim();
  return s;
}

static bool loadProjectWiFi(String &ssid, String &pass)
{
  Preferences prefs;
  if (!prefs.begin(WIFI_NVS_NAMESPACE, true)) return false;
  ssid = prefs.getString(WIFI_NVS_KEY_SSID, "");
  pass = prefs.getString(WIFI_NVS_KEY_PASS, "");
  prefs.end();
  return ssid.length() > 0;
}

static bool saveProjectWiFi(const String &ssid, const String &pass)
{
  Preferences prefs;
  if (!prefs.begin(WIFI_NVS_NAMESPACE, false)) return false;
  const size_t n1 = prefs.putString(WIFI_NVS_KEY_SSID, ssid);
  const size_t n2 = prefs.putString(WIFI_NVS_KEY_PASS, pass);
  prefs.end();
  return n1 > 0 && n2 > 0;
}

static bool initWiFiAuto()
{
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);

  // This is expected to work immediately after the previous Test5A/Test4 Wi-Fi runs,
  // because ESP32 normally keeps the last successful AP configuration in NVS.
  Serial.print("WiFi: trying ESP32 saved credentials");
  WiFi.begin();
  if (waitForWiFi(12000))
  {
    printWiFiPass();
    return true;
  }

  String ssid;
  String pass;
  if (loadProjectWiFi(ssid, pass))
  {
    Serial.println("WiFi: trying project NVS credentials");
    if (connectWithCredentials(ssid, pass))
    {
      printWiFiPass();
      return true;
    }
  }

  Serial.println();
  Serial.println("No usable saved Wi-Fi credentials found.");
  Serial.println("Enter them once in Serial Monitor. They will be saved in ESP32 NVS.");
  Serial.println("The password will not be echoed back by this sketch after entry.");

  ssid = readSerialLineBlocking("SSID [DIILAB]: ");
  if (ssid.length() == 0) ssid = DEFAULT_WIFI_SSID;
  pass = readSerialLineBlocking("Password: ");

  if (!connectWithCredentials(ssid, pass))
  {
    Serial.println("WiFi FAIL: credentials were not saved because connection failed.");
    return false;
  }

  if (saveProjectWiFi(ssid, pass))
    Serial.println("WiFi credentials saved to ESP32 NVS for future test sketches.");
  else
    Serial.println("WARNING: WiFi connected, but project NVS save failed.");

  printWiFiPass();
  return true;
}

// ============================================================
// Camera
// ============================================================

bool initCamera()
{
  camera_config_t c = {};
  c.ledc_channel = LEDC_CHANNEL_0;
  c.ledc_timer = LEDC_TIMER_0;
  c.pin_d0 = CAM_D0;
  c.pin_d1 = CAM_D1;
  c.pin_d2 = CAM_D2;
  c.pin_d3 = CAM_D3;
  c.pin_d4 = CAM_D4;
  c.pin_d5 = CAM_D5;
  c.pin_d6 = CAM_D6;
  c.pin_d7 = CAM_D7;
  c.pin_xclk = CAM_XCLK;
  c.pin_pclk = CAM_PCLK;
  c.pin_vsync = CAM_VSYNC;
  c.pin_href = CAM_HREF;
  c.pin_sccb_sda = CAM_SIOD;
  c.pin_sccb_scl = CAM_SIOC;
  c.pin_pwdn = CAM_PWDN;
  c.pin_reset = CAM_RESET;
  c.xclk_freq_hz = 20000000;
  c.pixel_format = PIXFORMAT_JPEG;
  c.frame_size = FRAMESIZE_QVGA;
  c.jpeg_quality = 14;
  c.fb_count = 1;
  c.fb_location = CAMERA_FB_IN_PSRAM;
  c.grab_mode = CAMERA_GRAB_WHEN_EMPTY;

  esp_err_t err = esp_camera_init(&c);
  if (err != ESP_OK)
  {
    Serial.printf("CAMERA INIT FAIL: 0x%x\n", err);
    return false;
  }
  return true;
}

void captureOneFrame()
{
  camera_fb_t *fb = esp_camera_fb_get();
  if (!fb)
  {
    camErrors++;
    Serial.println("CAM FRAME FAIL");
    return;
  }

  cameraFrames++;
  Serial.printf("CAM frame=%lu bytes=%u %ux%u\n",
                (unsigned long)cameraFrames,
                (unsigned)fb->len,
                (unsigned)fb->width,
                (unsigned)fb->height);
  esp_camera_fb_return(fb);
}

// ============================================================
// Audio Full-Duplex
// ============================================================

bool initAudio()
{
  pinMode(PIN_SD, OUTPUT);
  digitalWrite(PIN_SD, HIGH);
  delay(10);

  i2s_chan_config_t chanCfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO, I2S_ROLE_MASTER);
  esp_err_t err = i2s_new_channel(&chanCfg, &txHandle, &rxHandle);
  if (err != ESP_OK)
  {
    Serial.printf("i2s_new_channel FAIL: 0x%x\n", err);
    return false;
  }

  i2s_std_config_t cfg =
  {
    .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(SAMPLE_RATE),
    .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_STEREO),
    .gpio_cfg =
    {
      .mclk = I2S_GPIO_UNUSED,
      .bclk = (gpio_num_t)PIN_BCLK,
      .ws = (gpio_num_t)PIN_WS,
      .dout = (gpio_num_t)PIN_DOUT,
      .din = (gpio_num_t)PIN_DIN,
      .invert_flags = { .mclk_inv = false, .bclk_inv = false, .ws_inv = false }
    }
  };

  if (i2s_channel_init_std_mode(txHandle, &cfg) != ESP_OK) return false;
  if (i2s_channel_init_std_mode(rxHandle, &cfg) != ESP_OK) return false;
  if (i2s_channel_enable(txHandle) != ESP_OK) return false;
  if (i2s_channel_enable(rxHandle) != ESP_OK) return false;
  return true;
}

void txTask(void *parameter)
{
  const size_t FRAMES = 256;
  static int32_t txBuffer[FRAMES * 2];
  float phase = 0.0f;
  const float phaseStep = 2.0f * PI * TONE_FREQ_HZ / SAMPLE_RATE;
  const int32_t amplitude = (int32_t)(2147483647.0f * TONE_LEVEL);
  txTaskAlive = true;

  while (true)
  {
    const bool tone = toneEnabled;

    for (size_t i = 0; i < FRAMES; i++)
    {
      int32_t s = 0;
      if (tone)
      {
        s = (int32_t)(sinf(phase) * amplitude);
        phase += phaseStep;
        if (phase >= 2.0f * PI) phase -= 2.0f * PI;
      }

      txBuffer[i * 2] = s;
      txBuffer[i * 2 + 1] = s;
    }

    size_t written = 0;
    esp_err_t err = i2s_channel_write(txHandle, txBuffer, sizeof(txBuffer), &written, portMAX_DELAY);
    if (err != ESP_OK)
    {
      txErrors++;
      delay(10);
    }
  }
}

bool readRxBlock(size_t &frames)
{
  size_t received = 0;
  esp_err_t err = i2s_channel_read(rxHandle, rxBuffer, sizeof(rxBuffer), &received, pdMS_TO_TICKS(1000));

  if (err != ESP_OK)
  {
    rxErrors++;
    frames = 0;
    return false;
  }

  frames = received / (sizeof(int32_t) * 2);
  return true;
}

void detectMicChannel()
{
  toneEnabled = false;
  uint64_t leftEnergy = 0;
  uint64_t rightEnergy = 0;
  uint32_t total = 0;

  while (total < SAMPLE_RATE / 2)
  {
    size_t frames = 0;
    if (!readRxBlock(frames)) continue;

    for (size_t i = 0; i < frames; i++)
    {
      leftEnergy += llabs((long long)(rxBuffer[i * 2] >> 8));
      rightEnergy += llabs((long long)(rxBuffer[i * 2 + 1] >> 8));
    }
    total += frames;
  }

  activeMicChannel = (rightEnergy > leftEnergy) ? 1 : 0;
  Serial.printf("MIC channel = %s | L=%llu R=%llu\n",
                activeMicChannel ? "RIGHT" : "LEFT",
                (unsigned long long)leftEnergy,
                (unsigned long long)rightEnergy);
}

void readMic500ms()
{
  const uint32_t start = millis();
  uint64_t sq = 0;
  uint32_t count = 0;
  int32_t peak = 0;

  while (millis() - start < 500)
  {
    size_t frames = 0;
    if (!readRxBlock(frames)) continue;

    for (size_t i = 0; i < frames; i++)
    {
      int32_t sample = rxBuffer[i * 2 + activeMicChannel] >> 16;
      int32_t a = abs(sample);
      if (a > peak) peak = a;
      sq += (uint64_t)((int64_t)sample * sample);
      count++;
    }
  }

  float rms = count ? sqrt((double)sq / count) : 0.0f;
  float dbfs = rms > 0.0f ? 20.0f * log10f(rms / 32768.0f) : -120.0f;

  Serial.printf("AUDIO %-4s | MIC RMS=%7.1f Peak=%5ld dBFS=%6.1f\n",
                toneEnabled ? "TONE" : "MUTE",
                rms,
                (long)peak,
                dbfs);
}

// ============================================================
// BMI260-like + PCA9540B
// ============================================================

bool i2cAck(uint8_t addr)
{
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

bool pcaOff()
{
  Wire.beginTransmission(PCA_ADDR);
  Wire.write(0x00);
  return Wire.endTransmission() == 0;
}

bool imuWriteReg(uint8_t reg, uint8_t value)
{
  Wire.beginTransmission(IMU_ADDR);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

bool imuWriteBytes(uint8_t reg, const uint8_t *data, size_t len)
{
  Wire.beginTransmission(IMU_ADDR);
  Wire.write(reg);
  for (size_t i = 0; i < len; i++) Wire.write(data[i]);
  return Wire.endTransmission() == 0;
}

bool imuReadReg(uint8_t reg, uint8_t &value)
{
  Wire.beginTransmission(IMU_ADDR);
  Wire.write(reg);

  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(IMU_ADDR, (uint8_t)1) != 1) return false;

  value = Wire.read();
  return true;
}

bool imuReadBytes(uint8_t reg, uint8_t *data, size_t len)
{
  Wire.beginTransmission(IMU_ADDR);
  Wire.write(reg);

  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(IMU_ADDR, (uint8_t)len) != len) return false;

  for (size_t i = 0; i < len; i++) data[i] = Wire.read();
  return true;
}

bool uploadBMI260Config()
{
  uint8_t pwrConf = 0;
  if (!imuReadReg(REG_PWR_CONF, pwrConf)) return false;

  pwrConf &= (uint8_t)~0x01;
  if (!imuWriteReg(REG_PWR_CONF, pwrConf)) return false;
  delayMicroseconds(500);

  if (!imuWriteReg(REG_INIT_CTRL, 0x00)) return false;
  delayMicroseconds(500);

  const size_t configSize = sizeof(bmi260_config_file);
  Serial.printf("BMI260 config bytes = %u\n", (unsigned)configSize);

  for (size_t index = 0; index < configSize; index += BMI_CONFIG_CHUNK)
  {
    size_t chunk = BMI_CONFIG_CHUNK;
    if (index + chunk > configSize) chunk = configSize - index;

    uint16_t wordAddr = (uint16_t)(index / 2);
    uint8_t addrBytes[2];
    addrBytes[0] = (uint8_t)(wordAddr & 0x0F);
    addrBytes[1] = (uint8_t)(wordAddr >> 4);

    if (!imuWriteBytes(REG_INIT_ADDR_0, addrBytes, 2)) return false;
    delayMicroseconds(20);

    if (!imuWriteBytes(REG_INIT_DATA, bmi260_config_file + index, chunk)) return false;
    delayMicroseconds(20);
  }

  if (!imuWriteReg(REG_INIT_CTRL, 0x01)) return false;
  delay(25);

  for (int i = 0; i < 100; i++)
  {
    uint8_t status = 0;
    if (!imuReadReg(REG_INTERNAL_STATUS, status)) return false;
    if ((status & 0x0F) == INIT_OK) return true;
    delay(5);
  }

  return false;
}

bool configureBMI260Sensors()
{
  if (!imuWriteReg(REG_ACC_CONF, 0xA8)) return false; // 100 Hz, +/-2 g
  delayMicroseconds(500);
  if (!imuWriteReg(REG_ACC_RANGE, 0x00)) return false;
  delayMicroseconds(500);

  if (!imuWriteReg(REG_GYR_CONF, 0xE8)) return false; // 100 Hz, +/-2000 dps
  delayMicroseconds(500);
  if (!imuWriteReg(REG_GYR_RANGE, 0x00)) return false;
  delayMicroseconds(500);

  if (!imuWriteReg(REG_PWR_CTRL, 0x06)) return false;
  delay(50);
  return true;
}

bool initSensorBusAndBMI260()
{
  Wire.begin(SENSOR_SDA, SENSOR_SCL);
  Wire.setClock(400000);
  delay(100);

  if (!i2cAck(PCA_ADDR))
  {
    Serial.println("PCA9540B 0x70: FAIL");
    return false;
  }

  if (!pcaOff())
  {
    Serial.println("PCA OFF write: FAIL");
    return false;
  }

  Serial.println("PCA9540B 0x70: PASS | CH0/CH1=OFF");

  if (!i2cAck(IMU_ADDR))
  {
    Serial.println("IMU 0x68: NO ACK");
    return false;
  }

  uint8_t id = 0;
  if (!imuReadReg(REG_CHIP_ID, id)) return false;
  Serial.printf("IMU CHIP_ID before reset = 0x%02X\n", id);

  if (id != BMI260_CHIP_ID)
  {
    Serial.println("Expected current BMI260-like CHIP_ID 0x27. Stop.");
    return false;
  }

  if (!imuWriteReg(REG_CMD, CMD_SOFT_RESET)) return false;
  delay(5);

  if (!imuReadReg(REG_CHIP_ID, id)) return false;
  Serial.printf("IMU CHIP_ID after reset = 0x%02X\n", id);
  if (id != BMI260_CHIP_ID) return false;

  Serial.println("Uploading BMI260 config...");
  if (!uploadBMI260Config())
  {
    Serial.println("BMI260 CONFIG: FAIL");
    return false;
  }

  Serial.println("BMI260 CONFIG: PASS");

  if (!configureBMI260Sensors())
  {
    Serial.println("BMI260 SENSOR CONFIG: FAIL");
    return false;
  }

  Serial.println("BMI260 SENSOR CONFIG: PASS");
  return true;
}

void readImuOnce()
{
  if (!i2cAck(PCA_ADDR))
  {
    pcaErrors++;
    Serial.println("PCA ACK FAIL");
  }

  uint8_t buf[12];
  if (!imuReadBytes(REG_ACC_X_LSB, buf, sizeof(buf)))
  {
    imuErrors++;
    Serial.println("IMU DATA READ FAIL");
    return;
  }

  int16_t axRaw = (int16_t)((uint16_t)buf[1]  << 8 | buf[0]);
  int16_t ayRaw = (int16_t)((uint16_t)buf[3]  << 8 | buf[2]);
  int16_t azRaw = (int16_t)((uint16_t)buf[5]  << 8 | buf[4]);
  int16_t gxRaw = (int16_t)((uint16_t)buf[7]  << 8 | buf[6]);
  int16_t gyRaw = (int16_t)((uint16_t)buf[9]  << 8 | buf[8]);
  int16_t gzRaw = (int16_t)((uint16_t)buf[11] << 8 | buf[10]);

  lastAx = axRaw / 16384.0f;
  lastAy = ayRaw / 16384.0f;
  lastAz = azRaw / 16384.0f;
  lastGx = gxRaw / 16.384f;
  lastGy = gyRaw / 16.384f;
  lastGz = gzRaw / 16.384f;
  lastAMag = sqrtf(lastAx * lastAx + lastAy * lastAy + lastAz * lastAz);

  imuReads++;

  Serial.printf("IMU #%lu ACC[g]=%.3f,%.3f,%.3f |A|=%.3f GYR[dps]=%.1f,%.1f,%.1f\n",
                (unsigned long)imuReads,
                lastAx, lastAy, lastAz, lastAMag,
                lastGx, lastGy, lastGz);
}

// ============================================================
// Health
// ============================================================

void printHealth()
{
  wl_status_t ws = WiFi.status();

  if ((uint32_t)ws != lastWifiState)
  {
    if (lastWifiState == WL_CONNECTED && ws != WL_CONNECTED)
      wifiDropEvents++;
    lastWifiState = (uint32_t)ws;
  }

  Serial.println("----- HEALTH TEST5B -----");
  Serial.printf("uptime=%lus WiFi=%s RSSI=%d dBm IP=%s\n",
                (unsigned long)(millis() / 1000),
                ws == WL_CONNECTED ? "CONNECTED" : "DOWN",
                ws == WL_CONNECTED ? WiFi.RSSI() : 0,
                ws == WL_CONNECTED ? WiFi.localIP().toString().c_str() : "-");

  Serial.printf("heap=%u psram_free=%u camFrames=%lu camErr=%lu txErr=%lu rxErr=%lu wifiDrops=%lu\n",
                (unsigned)ESP.getFreeHeap(),
                (unsigned)ESP.getFreePsram(),
                (unsigned long)cameraFrames,
                (unsigned long)camErrors,
                (unsigned long)txErrors,
                (unsigned long)rxErrors,
                (unsigned long)wifiDropEvents);

  Serial.printf("imuReads=%lu imuErr=%lu pcaErr=%lu |A|=%.3f gyro=%.1f,%.1f,%.1f\n",
                (unsigned long)imuReads,
                (unsigned long)imuErrors,
                (unsigned long)pcaErrors,
                lastAMag,
                lastGx, lastGy, lastGz);
}

void stopForever(const char *msg)
{
  Serial.println();
  Serial.println("========== TEST STOP ==========");
  Serial.println(msg);
  while (true) delay(1000);
}

// ============================================================
// Setup / Loop
// ============================================================

void setup()
{
  Serial.begin(115200);
  Serial.setTimeout(120000);
  delay(2000);

  Serial.println();
  Serial.println("========================================================");
  Serial.println("YINLING-ZHIHU B1 TEST5B CONCURRENCY STRESS");
  Serial.println("WiFi + OV5640 + MIC RX + MAX98357A TX + BMI260/PCA");
  Serial.println("WiFi credentials: AUTO FROM NVS; serial entry only if missing");
  Serial.println("HAPTIC IS EXCLUDED");
  Serial.println("========================================================");

  if (!psramFound()) stopForever("PSRAM: FAIL");
  Serial.println("PSRAM: PASS");

  if (!initCamera()) stopForever("CAMERA: FAIL");
  Serial.println("CAMERA: PASS");

  if (!initAudio()) stopForever("AUDIO FULL-DUPLEX INIT: FAIL");
  Serial.println("AUDIO FULL-DUPLEX INIT: PASS");

  BaseType_t ok = xTaskCreatePinnedToCore(txTask, "audio_tx", 4096, NULL, 2, NULL, 1);
  if (ok != pdPASS) stopForever("TX TASK CREATE: FAIL");
  while (!txTaskAlive) delay(10);

  detectMicChannel();

  if (!initSensorBusAndBMI260())
    stopForever("SENSOR BUS / BMI260 INIT: FAIL");
  Serial.println("SENSOR BUS / BMI260 INIT: PASS");

  if (!initWiFiAuto())
    stopForever("WIFI INIT: FAIL");

  lastWifiState = (uint32_t)WiFi.status();
  testStartMs = millis();
  lastToneToggleMs = millis();
  lastCameraMs = millis();
  lastImuMs = millis();
  lastHealthMs = millis();

  Serial.println();
  Serial.println("TEST5B RUNNING");
  Serial.println("Audio: 2 s MUTE / 2 s 1 kHz TONE");
  Serial.println("Camera: one QVGA JPEG about every 2 s");
  Serial.println("IMU: one accel+gyro read about every 1 s");
  Serial.println("PCA: ACK checked with IMU reads; CH0/CH1 stay OFF");
  Serial.println("First target: 30 minutes. Haptic remains excluded.");
}

void loop()
{
  uint32_t now = millis();

  if (now - lastToneToggleMs >= 2000)
  {
    toneEnabled = !toneEnabled;
    lastToneToggleMs = now;
  }

  readMic500ms();
  now = millis();

  if (now - lastImuMs >= 1000)
  {
    readImuOnce();
    lastImuMs = millis();
  }

  if (millis() - lastCameraMs >= 2000)
  {
    captureOneFrame();
    lastCameraMs = millis();
  }

  if (millis() - lastHealthMs >= 5000)
  {
    printHealth();
    lastHealthMs = millis();
  }

  if (!milestonePrinted && millis() - testStartMs >= 30UL * 60UL * 1000UL)
  {
    milestonePrinted = true;

    Serial.println();
    Serial.println("========== TEST5B 30-MIN MILESTONE ==========");
    Serial.printf("camErr=%lu txErr=%lu rxErr=%lu wifiDrops=%lu imuErr=%lu pcaErr=%lu imuReads=%lu\n",
                  (unsigned long)camErrors,
                  (unsigned long)txErrors,
                  (unsigned long)rxErrors,
                  (unsigned long)wifiDropEvents,
                  (unsigned long)imuErrors,
                  (unsigned long)pcaErrors,
                  (unsigned long)imuReads);

    if (camErrors == 0 &&
        txErrors == 0 &&
        rxErrors == 0 &&
        imuErrors == 0 &&
        pcaErrors == 0 &&
        imuReads > 0 &&
        WiFi.status() == WL_CONNECTED)
    {
      Serial.println("TEST5B 30-MIN RESULT: PASS CANDIDATE");
    }
    else
    {
      Serial.println("TEST5B 30-MIN RESULT: CHECK ERRORS");
    }

    Serial.println("==============================================");
  }
}
