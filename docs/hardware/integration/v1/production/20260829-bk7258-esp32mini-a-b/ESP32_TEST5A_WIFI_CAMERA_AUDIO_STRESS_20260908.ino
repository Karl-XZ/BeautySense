#include <Arduino.h>
#include <WiFi.h>
#include <math.h>
#include "esp_camera.h"
#include "driver/i2s_std.h"

// 银龄智护 B1 - Test 5A
// ESP32-S3: Wi-Fi + OV5640 capture + MIC RX + MAX98357A TX concurrency stress
// Arduino-ESP32 3.x

// ---------------- Wi-Fi ----------------
static const char *WIFI_SSID = "DIILAB";
static const char *WIFI_PASS = "CHANGE_ME_LOCAL_ONLY";

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
static int activeMicChannel = 0;
static int32_t rxBuffer[256 * 2];

static uint32_t lastCameraMs = 0;
static uint32_t lastHealthMs = 0;
static uint32_t lastToneToggleMs = 0;
static uint32_t lastWifiState = WL_IDLE_STATUS;
static uint32_t testStartMs = 0;
static bool milestonePrinted = false;

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
  uint64_t leftEnergy = 0, rightEnergy = 0;
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
                rms, (long)peak, dbfs);
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

void printHealth()
{
  wl_status_t ws = WiFi.status();
  if ((uint32_t)ws != lastWifiState)
  {
    if (lastWifiState == WL_CONNECTED && ws != WL_CONNECTED) wifiDropEvents++;
    lastWifiState = (uint32_t)ws;
  }

  Serial.println("----- HEALTH -----");
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
}

void setup()
{
  Serial.begin(115200);
  delay(2000);
  Serial.println();
  Serial.println("========================================");
  Serial.println("YINLING-ZHIHU B1 TEST5A CONCURRENCY STRESS");
  Serial.println("WiFi + OV5640 + MIC RX + MAX98357A TX");
  Serial.println("========================================");

  if (!psramFound())
  {
    Serial.println("PSRAM: FAIL");
    while (true) delay(1000);
  }
  Serial.println("PSRAM: PASS");

  if (!initCamera())
  {
    Serial.println("CAMERA: FAIL");
    while (true) delay(1000);
  }
  Serial.println("CAMERA: PASS");

  if (!initAudio())
  {
    Serial.println("AUDIO FULL-DUPLEX INIT: FAIL");
    while (true) delay(1000);
  }
  Serial.println("AUDIO FULL-DUPLEX INIT: PASS");

  BaseType_t ok = xTaskCreatePinnedToCore(txTask, "audio_tx", 4096, NULL, 2, NULL, 1);
  if (ok != pdPASS)
  {
    Serial.println("TX TASK CREATE: FAIL");
    while (true) delay(1000);
  }
  while (!txTaskAlive) delay(10);

  detectMicChannel();

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("WiFi connecting");
  const uint32_t w0 = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - w0 < 15000)
  {
    Serial.print('.');
    delay(300);
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED)
  {
    Serial.printf("WiFi PASS | IP=%s RSSI=%d dBm\n", WiFi.localIP().toString().c_str(), WiFi.RSSI());
  }
  else
  {
    Serial.println("WiFi FAIL - check local WIFI_PASS");
  }

  lastWifiState = (uint32_t)WiFi.status();
  testStartMs = millis();
  lastToneToggleMs = millis();
  lastCameraMs = millis();
  lastHealthMs = millis();

  Serial.println();
  Serial.println("TEST RUNNING: tone toggles every 2s, camera capture every 2s, MIC continuous.");
  Serial.println("First target: 10 minutes. Then extend to 30-60 minutes if stable.");
}

void loop()
{
  const uint32_t now = millis();

  if (now - lastToneToggleMs >= 2000)
  {
    toneEnabled = !toneEnabled;
    lastToneToggleMs = now;
  }

  readMic500ms();

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

  if (!milestonePrinted && millis() - testStartMs >= 10UL * 60UL * 1000UL)
  {
    milestonePrinted = true;
    Serial.println();
    Serial.println("========== 10 MIN MILESTONE ==========");
    Serial.printf("camErr=%lu txErr=%lu rxErr=%lu wifiDrops=%lu\n",
                  (unsigned long)camErrors,
                  (unsigned long)txErrors,
                  (unsigned long)rxErrors,
                  (unsigned long)wifiDropEvents);
    if (camErrors == 0 && txErrors == 0 && rxErrors == 0 && WiFi.status() == WL_CONNECTED)
      Serial.println("TEST5A 10-MIN RESULT: PASS CANDIDATE");
    else
      Serial.println("TEST5A 10-MIN RESULT: CHECK ERRORS");
    Serial.println("======================================");
  }
}
