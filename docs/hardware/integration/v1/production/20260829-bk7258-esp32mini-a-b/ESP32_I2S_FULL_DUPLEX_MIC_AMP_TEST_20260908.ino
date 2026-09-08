#include <Arduino.h>
#include <math.h>
#include "driver/i2s_std.h"

// 银龄智护 B1
// ESP32-S3 shared-clock I2S full-duplex test
// MIC RX + MAX98357A TX simultaneously

static const int PIN_BCLK = 36;
static const int PIN_WS   = 37;
static const int PIN_DIN  = 38;  // MIC -> ESP32-S3
static const int PIN_DOUT = 39;  // ESP32-S3 -> MAX98357A
static const int PIN_SD   = 40;  // MAX98357A SD_MODE network control

static const uint32_t SAMPLE_RATE = 16000;
static const float TONE_FREQ_HZ = 1000.0f;
static const float TONE_LEVEL = 0.02f;  // low level for first concurrency test

static i2s_chan_handle_t txHandle = NULL;
static i2s_chan_handle_t rxHandle = NULL;

static volatile bool toneEnabled = false;
static volatile bool txTaskAlive = false;
static int activeMicChannel = 0; // 0=left, 1=right

static int32_t rxBuffer[256 * 2];

bool initFullDuplexI2S()
{
  pinMode(PIN_SD, OUTPUT);
  digitalWrite(PIN_SD, HIGH);
  delay(10);

  i2s_chan_config_t chanCfg =
    I2S_CHANNEL_DEFAULT_CONFIG(
      I2S_NUM_AUTO,
      I2S_ROLE_MASTER
    );

  esp_err_t err =
    i2s_new_channel(
      &chanCfg,
      &txHandle,
      &rxHandle
    );

  if (err != ESP_OK)
  {
    Serial.printf("i2s_new_channel FAIL: 0x%x\n", err);
    return false;
  }

  i2s_std_config_t cfg =
  {
    .clk_cfg =
      I2S_STD_CLK_DEFAULT_CONFIG(
        SAMPLE_RATE
      ),

    .slot_cfg =
      I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(
        I2S_DATA_BIT_WIDTH_32BIT,
        I2S_SLOT_MODE_STEREO
      ),

    .gpio_cfg =
    {
      .mclk = I2S_GPIO_UNUSED,
      .bclk = (gpio_num_t)PIN_BCLK,
      .ws   = (gpio_num_t)PIN_WS,
      .dout = (gpio_num_t)PIN_DOUT,
      .din  = (gpio_num_t)PIN_DIN,
      .invert_flags =
      {
        .mclk_inv = false,
        .bclk_inv = false,
        .ws_inv   = false
      }
    }
  };

  err = i2s_channel_init_std_mode(txHandle, &cfg);
  if (err != ESP_OK)
  {
    Serial.printf("TX std init FAIL: 0x%x\n", err);
    return false;
  }

  err = i2s_channel_init_std_mode(rxHandle, &cfg);
  if (err != ESP_OK)
  {
    Serial.printf("RX std init FAIL: 0x%x\n", err);
    return false;
  }

  err = i2s_channel_enable(txHandle);
  if (err != ESP_OK)
  {
    Serial.printf("TX enable FAIL: 0x%x\n", err);
    return false;
  }

  err = i2s_channel_enable(rxHandle);
  if (err != ESP_OK)
  {
    Serial.printf("RX enable FAIL: 0x%x\n", err);
    return false;
  }

  return true;
}

void txTask(void *parameter)
{
  const size_t FRAMES = 256;
  static int32_t txBuffer[FRAMES * 2];

  float phase = 0.0f;
  const float phaseStep =
    2.0f * PI * TONE_FREQ_HZ / SAMPLE_RATE;

  const int32_t amplitude =
    (int32_t)(2147483647.0f * TONE_LEVEL);

  txTaskAlive = true;

  while (true)
  {
    bool tone = toneEnabled;

    for (size_t i = 0; i < FRAMES; i++)
    {
      int32_t sample = 0;

      if (tone)
      {
        sample = (int32_t)(sinf(phase) * amplitude);
        phase += phaseStep;
        if (phase >= 2.0f * PI) phase -= 2.0f * PI;
      }

      // Same data on left/right so MAX98357A channel selection does not matter.
      txBuffer[i * 2]     = sample;
      txBuffer[i * 2 + 1] = sample;
    }

    size_t written = 0;
    esp_err_t err =
      i2s_channel_write(
        txHandle,
        txBuffer,
        sizeof(txBuffer),
        &written,
        portMAX_DELAY
      );

    if (err != ESP_OK)
    {
      Serial.printf("TX WRITE FAIL: 0x%x\n", err);
      delay(100);
    }
  }
}

bool readRxBlock(size_t &frames)
{
  size_t received = 0;

  esp_err_t err =
    i2s_channel_read(
      rxHandle,
      rxBuffer,
      sizeof(rxBuffer),
      &received,
      portMAX_DELAY
    );

  if (err != ESP_OK)
  {
    Serial.printf("RX READ FAIL: 0x%x\n", err);
    frames = 0;
    return false;
  }

  frames = received / (sizeof(int32_t) * 2);
  return true;
}

void detectMicChannel()
{
  toneEnabled = false;
  delay(200);

  uint64_t leftEnergy = 0;
  uint64_t rightEnergy = 0;
  uint32_t totalFrames = 0;

  while (totalFrames < SAMPLE_RATE / 2)
  {
    size_t frames = 0;
    if (!readRxBlock(frames)) continue;

    for (size_t i = 0; i < frames; i++)
    {
      int32_t l = rxBuffer[i * 2] >> 8;
      int32_t r = rxBuffer[i * 2 + 1] >> 8;

      leftEnergy += llabs((long long)l);
      rightEnergy += llabs((long long)r);
    }

    totalFrames += frames;
  }

  activeMicChannel =
    (rightEnergy > leftEnergy) ? 1 : 0;

  Serial.println();
  Serial.println("MIC CHANNEL DETECTION");
  Serial.printf("LEFT energy  = %llu\n", (unsigned long long)leftEnergy);
  Serial.printf("RIGHT energy = %llu\n", (unsigned long long)rightEnergy);
  Serial.print("Selected MIC channel = ");
  Serial.println(activeMicChannel ? "RIGHT" : "LEFT");
}

void printMicLevelForMs(uint32_t durationMs)
{
  uint64_t squareSum = 0;
  uint32_t count = 0;
  int32_t peak = 0;

  unsigned long start = millis();

  while (millis() - start < durationMs)
  {
    size_t frames = 0;
    if (!readRxBlock(frames)) continue;

    for (size_t i = 0; i < frames; i++)
    {
      int32_t raw =
        rxBuffer[i * 2 + activeMicChannel];

      // Same conversion used in previous working MIC test.
      int32_t sample = raw >> 16;

      int32_t a = abs(sample);
      if (a > peak) peak = a;

      squareSum +=
        (uint64_t)((int64_t)sample * (int64_t)sample);

      count++;
    }
  }

  float rms = 0.0f;
  if (count > 0)
  {
    rms = sqrt((double)squareSum / count);
  }

  float dbfs = -120.0f;
  if (rms > 0.0f)
  {
    dbfs = 20.0f * log10f(rms / 32768.0f);
  }

  Serial.print("TX=");
  Serial.print(toneEnabled ? "TONE    " : "SILENCE ");
  Serial.print(" | MIC RMS=");
  Serial.print(rms, 1);
  Serial.print(" Peak=");
  Serial.print(peak);
  Serial.print(" dBFS=");
  Serial.println(dbfs, 1);
}

void setup()
{
  Serial.begin(115200);
  delay(2000);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 I2S FULL-DUPLEX AUDIO TEST");
  Serial.println("MIC RX + MAX98357A TX SIMULTANEOUSLY");
  Serial.println("========================================");
  Serial.println("BCLK = GPIO36");
  Serial.println("WS   = GPIO37");
  Serial.println("DIN  = GPIO38 (MIC)");
  Serial.println("DOUT = GPIO39 (MAX98357A)");
  Serial.println("SD   = GPIO40");
  Serial.println("Sample rate = 16000 Hz, stereo 32-bit slots");
  Serial.println("Tone = 1000 Hz @ 2% digital level");

  if (!initFullDuplexI2S())
  {
    Serial.println("FULL-DUPLEX INIT: FAIL");
    while (true) delay(1000);
  }

  Serial.println("FULL-DUPLEX INIT: PASS");

  BaseType_t ok =
    xTaskCreatePinnedToCore(
      txTask,
      "audio_tx",
      4096,
      NULL,
      2,
      NULL,
      1
    );

  if (ok != pdPASS)
  {
    Serial.println("TX TASK CREATE: FAIL");
    while (true) delay(1000);
  }

  while (!txTaskAlive) delay(10);

  detectMicChannel();

  Serial.println();
  Serial.println("TEST START");
  Serial.println("Pattern: 2 s silence -> 2 s 1 kHz tone -> repeat.");
  Serial.println("During silence, speak near MIC and confirm RMS rises.");
  Serial.println("During tone, bone transducer should keep playing while MIC RX continues.");
  Serial.println();
}

void loop()
{
  toneEnabled = false;
  Serial.println("--- SILENCE PHASE ---");
  printMicLevelForMs(500);
  printMicLevelForMs(500);
  printMicLevelForMs(500);
  printMicLevelForMs(500);

  toneEnabled = true;
  Serial.println("--- TONE PHASE ---");
  printMicLevelForMs(500);
  printMicLevelForMs(500);
  printMicLevelForMs(500);
  printMicLevelForMs(500);
}
