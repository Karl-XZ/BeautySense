#include <Arduino.h>
#include <math.h>
#include "driver/i2s_std.h"

// 银龄智护 B1 / ESP32-S3 -> MAX98357A 小型播放测试
// AUDIO_BCLK=GPIO36, AUDIO_WS=GPIO37, AMP_DOUT=GPIO39, AMP_SD_MODE=GPIO40

static const int PIN_BCLK = 36;
static const int PIN_WS   = 37;
static const int PIN_DOUT = 39;
static const int PIN_SD   = 40;

static const uint32_t SAMPLE_RATE = 44100;
static const float AMPLITUDE = 0.08f;  // 8%数字幅度，首测保守

static i2s_chan_handle_t txHandle = NULL;

bool initAudio() {
  // 当前板 GPIO40 通过板上 SD_MODE 网络控制 MAX98357A；首测拉高使其退出 shutdown。
  pinMode(PIN_SD, OUTPUT);
  digitalWrite(PIN_SD, HIGH);
  delay(10);

  i2s_chan_config_t chanCfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO, I2S_ROLE_MASTER);
  if (i2s_new_channel(&chanCfg, &txHandle, NULL) != ESP_OK) return false;

  i2s_std_config_t cfg = {
    .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(SAMPLE_RATE),
    .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_STEREO),
    .gpio_cfg = {
      .mclk = I2S_GPIO_UNUSED,
      .bclk = (gpio_num_t)PIN_BCLK,
      .ws   = (gpio_num_t)PIN_WS,
      .dout = (gpio_num_t)PIN_DOUT,
      .din  = I2S_GPIO_UNUSED,
      .invert_flags = {
        .mclk_inv = false,
        .bclk_inv = false,
        .ws_inv   = false,
      },
    },
  };

  if (i2s_channel_init_std_mode(txHandle, &cfg) != ESP_OK) return false;
  if (i2s_channel_enable(txHandle) != ESP_OK) return false;
  return true;
}

void playTone(float freqHz, uint32_t ms) {
  const size_t FRAMES = 256;
  int16_t buf[FRAMES * 2];
  const uint32_t totalFrames = (uint64_t)SAMPLE_RATE * ms / 1000;
  uint32_t sentFrames = 0;
  float phase = 0.0f;
  const float step = 2.0f * PI * freqHz / SAMPLE_RATE;
  const int16_t peak = (int16_t)(32767.0f * AMPLITUDE);

  while (sentFrames < totalFrames) {
    size_t n = min((uint32_t)FRAMES, totalFrames - sentFrames);
    for (size_t i = 0; i < n; i++) {
      int16_t s = (int16_t)(sinf(phase) * peak);
      phase += step;
      if (phase >= 2.0f * PI) phase -= 2.0f * PI;
      buf[i * 2] = s;
      buf[i * 2 + 1] = s;
    }

    size_t written = 0;
    esp_err_t err = i2s_channel_write(txHandle, buf, n * 2 * sizeof(int16_t), &written, portMAX_DELAY);
    if (err != ESP_OK) {
      Serial.printf("I2S write FAIL: 0x%x\n", err);
      return;
    }
    sentFrames += n;
  }
}

void silence(uint32_t ms) {
  const size_t FRAMES = 256;
  int16_t zero[FRAMES * 2] = {0};
  uint32_t totalFrames = (uint64_t)SAMPLE_RATE * ms / 1000;
  uint32_t done = 0;
  while (done < totalFrames) {
    size_t n = min((uint32_t)FRAMES, totalFrames - done);
    size_t written = 0;
    i2s_channel_write(txHandle, zero, n * 2 * sizeof(int16_t), &written, portMAX_DELAY);
    done += n;
  }
}

void setup() {
  Serial.begin(115200);
  delay(1500);
  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 MAX98357A TONE TEST");
  Serial.println("========================================");
  Serial.println("BCLK=GPIO36 WS=GPIO37 DATA=GPIO39 SD_MODE=GPIO40");
  Serial.println("Output level: 8% digital amplitude");

  if (!initAudio()) {
    Serial.println("AUDIO INIT: FAIL");
    while (true) delay(1000);
  }

  Serial.println("AUDIO INIT: PASS");
  Serial.println("Playing 3-note test every 5 seconds...");
}

void loop() {
  Serial.println("PLAY: 440 Hz");
  playTone(440.0f, 500);
  silence(120);

  Serial.println("PLAY: 660 Hz");
  playTone(660.0f, 500);
  silence(120);

  Serial.println("PLAY: 880 Hz");
  playTone(880.0f, 700);
  silence(3000);
}
