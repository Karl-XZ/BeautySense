#include <WiFi.h>
#include <WebServer.h>
#include <math.h>
#include "esp_arduino_version.h"
#include "esp_heap_caps.h"
#include "driver/i2s_std.h"

// 银龄智护 B1 / ESP32-S3-MINI-1U-N4R2 / I2S MIC
// 仓库不保存真实 Wi-Fi 密码。烧录前仅在本地填写。
const char* WIFI_SSID = "DIILAB";
const char* WIFI_PASS = "CHANGE_ME_LOCAL_ONLY";

// Production pin map
static const int PIN_I2S_BCLK = 36;
static const int PIN_I2S_WS   = 37;
static const int PIN_I2S_DIN  = 38;

static const uint32_t SAMPLE_RATE = 16000;
static const uint16_t WAV_BITS = 16;
static const uint16_t WAV_CHANNELS = 1;
static const uint32_t RECORD_SECONDS = 10;
static const int DIGITAL_GAIN = 4;  // 1/2/4；太响失真就改小

static i2s_chan_handle_t rx_chan = NULL;
static WebServer server(80);

static uint8_t* wavBuf = NULL;
static size_t wavCapacity = 0;
static size_t wavSize = 0;
static int activeSlot = 0; // 0=Left, 1=Right

static int32_t rawStereo[512 * 2];

static void put16le(uint8_t* p, uint16_t v) {
  p[0] = (uint8_t)(v & 0xFF);
  p[1] = (uint8_t)((v >> 8) & 0xFF);
}

static void put32le(uint8_t* p, uint32_t v) {
  p[0] = (uint8_t)(v & 0xFF);
  p[1] = (uint8_t)((v >> 8) & 0xFF);
  p[2] = (uint8_t)((v >> 16) & 0xFF);
  p[3] = (uint8_t)((v >> 24) & 0xFF);
}

static void writeWavHeader(uint8_t* h, uint32_t dataBytes) {
  memcpy(h + 0, "RIFF", 4);
  put32le(h + 4, 36 + dataBytes);
  memcpy(h + 8, "WAVE", 4);
  memcpy(h + 12, "fmt ", 4);
  put32le(h + 16, 16);
  put16le(h + 20, 1); // PCM
  put16le(h + 22, WAV_CHANNELS);
  put32le(h + 24, SAMPLE_RATE);
  put32le(h + 28, SAMPLE_RATE * WAV_CHANNELS * WAV_BITS / 8);
  put16le(h + 32, WAV_CHANNELS * WAV_BITS / 8);
  put16le(h + 34, WAV_BITS);
  memcpy(h + 36, "data", 4);
  put32le(h + 40, dataBytes);
}

static bool initI2S() {
  i2s_chan_config_t chan_cfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO, I2S_ROLE_MASTER);
  esp_err_t err = i2s_new_channel(&chan_cfg, NULL, &rx_chan);
  if (err != ESP_OK) {
    Serial.printf("i2s_new_channel FAIL: 0x%x\n", err);
    return false;
  }

  i2s_std_config_t std_cfg = {
    .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(SAMPLE_RATE),
    .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_32BIT, I2S_SLOT_MODE_STEREO),
    .gpio_cfg = {
      .mclk = I2S_GPIO_UNUSED,
      .bclk = (gpio_num_t)PIN_I2S_BCLK,
      .ws = (gpio_num_t)PIN_I2S_WS,
      .dout = I2S_GPIO_UNUSED,
      .din = (gpio_num_t)PIN_I2S_DIN,
      .invert_flags = {
        .mclk_inv = false,
        .bclk_inv = false,
        .ws_inv = false,
      },
    },
  };

  err = i2s_channel_init_std_mode(rx_chan, &std_cfg);
  if (err != ESP_OK) {
    Serial.printf("i2s_channel_init_std_mode FAIL: 0x%x\n", err);
    return false;
  }

  err = i2s_channel_enable(rx_chan);
  if (err != ESP_OK) {
    Serial.printf("i2s_channel_enable FAIL: 0x%x\n", err);
    return false;
  }

  Serial.println("I2S RX INIT: PASS");
  Serial.printf("BCLK=GPIO%d WS=GPIO%d DIN=GPIO%d SAMPLE=%u Hz\n",
                PIN_I2S_BCLK, PIN_I2S_WS, PIN_I2S_DIN, SAMPLE_RATE);
  return true;
}

static bool i2sRead(void* dst, size_t bytes, size_t* got) {
  esp_err_t err = i2s_channel_read(rx_chan, dst, bytes, got, portMAX_DELAY);
  return err == ESP_OK;
}

static void flushI2S() {
  for (int i = 0; i < 6; ++i) {
    size_t got = 0;
    i2sRead(rawStereo, sizeof(rawStereo), &got);
  }
}

static void detectActiveSlot() {
  flushI2S();

  uint64_t sumL = 0;
  uint64_t sumR = 0;
  uint32_t framesTotal = 0;
  const uint32_t targetFrames = SAMPLE_RATE / 2;

  while (framesTotal < targetFrames) {
    size_t got = 0;
    if (!i2sRead(rawStereo, sizeof(rawStereo), &got)) continue;

    size_t frames = got / (sizeof(int32_t) * 2);
    for (size_t i = 0; i < frames; ++i) {
      int32_t l = rawStereo[2 * i + 0] >> 8;
      int32_t r = rawStereo[2 * i + 1] >> 8;
      sumL += (uint64_t)llabs((long long)l);
      sumR += (uint64_t)llabs((long long)r);
    }
    framesTotal += frames;
  }

  activeSlot = (sumR > sumL) ? 1 : 0;
  Serial.printf("Slot energy: LEFT=%llu RIGHT=%llu\n",
                (unsigned long long)sumL,
                (unsigned long long)sumR);
  Serial.printf("Selected MIC slot: %s\n", activeSlot ? "RIGHT" : "LEFT");
}

static inline int16_t convertMicSample(int32_t raw32) {
  // 典型 I2S 24-bit 数字麦克风在 32-bit slot 中高位对齐。
  // 先取高 16 bit，再做可调数字增益。
  int32_t s = raw32 >> 16;
  s *= DIGITAL_GAIN;
  if (s > 32767) s = 32767;
  if (s < -32768) s = -32768;
  return (int16_t)s;
}

static bool recordWav() {
  if (!wavBuf) return false;

  Serial.println();
  Serial.println("========================================");
  Serial.printf("RECORD START: %u seconds\n", RECORD_SECONDS);
  Serial.println("Speak now / clap / make a normal voice sample.");
  Serial.println("========================================");

  flushI2S();

  const uint32_t targetSamples = SAMPLE_RATE * RECORD_SECONDS;
  uint32_t writtenSamples = 0;
  uint8_t* pcm = wavBuf + 44;

  uint64_t oneSecSq = 0;
  uint32_t oneSecCount = 0;
  int32_t oneSecPeak = 0;
  uint32_t nextReport = SAMPLE_RATE;

  while (writtenSamples < targetSamples) {
    size_t got = 0;
    if (!i2sRead(rawStereo, sizeof(rawStereo), &got)) {
      Serial.println("I2S read FAIL");
      return false;
    }

    size_t frames = got / (sizeof(int32_t) * 2);
    for (size_t i = 0; i < frames && writtenSamples < targetSamples; ++i) {
      int32_t raw = rawStereo[2 * i + activeSlot];
      int16_t s16 = convertMicSample(raw);

      pcm[writtenSamples * 2 + 0] = (uint8_t)(s16 & 0xFF);
      pcm[writtenSamples * 2 + 1] = (uint8_t)((s16 >> 8) & 0xFF);

      int32_t a = abs((int32_t)s16);
      if (a > oneSecPeak) oneSecPeak = a;
      oneSecSq += (uint64_t)((int64_t)s16 * (int64_t)s16);
      oneSecCount++;
      writtenSamples++;

      if (writtenSamples >= nextReport) {
        float rms = oneSecCount ? sqrt((double)oneSecSq / oneSecCount) : 0.0f;
        float dbfs = (rms > 0.0f) ? 20.0f * log10f(rms / 32768.0f) : -120.0f;
        Serial.printf("sec=%u RMS=%.1f peak=%ld dBFS=%.1f\n",
                      writtenSamples / SAMPLE_RATE,
                      rms,
                      (long)oneSecPeak,
                      dbfs);
        oneSecSq = 0;
        oneSecCount = 0;
        oneSecPeak = 0;
        nextReport += SAMPLE_RATE;
      }
    }
  }

  uint32_t dataBytes = writtenSamples * 2;
  writeWavHeader(wavBuf, dataBytes);
  wavSize = 44 + dataBytes;

  Serial.println("========================================");
  Serial.printf("RECORD DONE: samples=%u WAV bytes=%u\n",
                writtenSamples, (unsigned)wavSize);
  Serial.println("========================================");
  return true;
}

static void sendWav(bool attachment) {
  if (!wavBuf || wavSize <= 44) {
    server.send(503, "text/plain", "No recording available");
    return;
  }

  server.sendHeader("Cache-Control", "no-store");
  if (attachment) {
    server.sendHeader("Content-Disposition", "attachment; filename=mic_recording.wav");
  }
  server.setContentLength(wavSize);
  server.send(200, "audio/wav", "");

  WiFiClient client = server.client();
  size_t sent = 0;
  while (sent < wavSize && client.connected()) {
    size_t chunk = min((size_t)1460, wavSize - sent);
    size_t n = client.write(wavBuf + sent, chunk);
    if (n == 0) break;
    sent += n;
    delay(0);
  }
}

static void handleRoot() {
  String html = R"HTML(
<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ESP32-S3 MIC Recorder</title>
<style>
body{font-family:Arial;background:#111;color:#eee;text-align:center;margin:30px}
button,a{font-size:18px;padding:10px 18px;margin:10px}
a{color:#8ecbff}.box{max-width:720px;margin:auto;background:#1c1c1c;padding:24px;border-radius:12px}
audio{width:100%;margin-top:16px}
</style></head><body><div class="box">
<h2>ESP32-S3 I2S MIC Recorder</h2>
<p>16 kHz / mono / 16-bit WAV / 10 s</p>
<audio controls src="/recording.wav"></audio><br>
<form action="/record" method="get"><button type="submit">Record new 10-second sample</button></form>
<a href="/download">Download WAV</a>
</div></body></html>
)HTML";
  server.send(200, "text/html", html);
}

static void handleRecord() {
  server.send(200, "text/plain", "Recording 10 seconds now. Wait until serial says RECORD DONE, then go back to the main page.");
  delay(100);
  recordWav();
}

static void startWebServer() {
  server.on("/", HTTP_GET, handleRoot);
  server.on("/record", HTTP_GET, handleRecord);
  server.on("/recording.wav", HTTP_GET, []() { sendWav(false); });
  server.on("/download", HTTP_GET, []() { sendWav(true); });
  server.begin();
  Serial.println("HTTP AUDIO SERVER: PASS");
}

static bool connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  Serial.print("Connecting Wi-Fi");
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) {
    Serial.print('.');
    delay(500);
  }
  Serial.println();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WIFI: FAIL");
    return false;
  }

  Serial.println("WIFI: PASS");
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());
  return true;
}

void setup() {
  Serial.begin(115200);
  delay(2000);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 I2S MIC TRUE WAV RECORD TEST");
  Serial.println("========================================");
  Serial.printf("Arduino-ESP32 major: %d\n", ESP_ARDUINO_VERSION_MAJOR);
  Serial.printf("PSRAM: %s\n", psramFound() ? "PASS" : "NOT FOUND");

  if (!psramFound()) {
    Serial.println("This test expects the N4R2 PSRAM. STOP.");
    while (true) delay(1000);
  }

  wavCapacity = 44 + SAMPLE_RATE * RECORD_SECONDS * 2;
  wavBuf = (uint8_t*)heap_caps_malloc(wavCapacity, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
  if (!wavBuf) {
    Serial.println("PSRAM WAV buffer allocation FAIL");
    while (true) delay(1000);
  }
  Serial.printf("WAV buffer allocated: %u bytes\n", (unsigned)wavCapacity);

  if (!initI2S()) {
    while (true) delay(1000);
  }

  detectActiveSlot();

  if (!connectWiFi()) {
    Serial.println("Wi-Fi failed, but serial recording can still be debugged after fixing credentials/network.");
  } else {
    startWebServer();
    Serial.println("After RECORD DONE, open:");
    Serial.print("http://");
    Serial.print(WiFi.localIP());
    Serial.println("/");
  }

  // 上电自动录一段，证明是真录音而不只是音量检测
  if (recordWav()) {
    Serial.println("MIC WAV RECORDING: CREATED");
  } else {
    Serial.println("MIC WAV RECORDING: FAIL");
  }
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    server.handleClient();
  }
  delay(2);
}
