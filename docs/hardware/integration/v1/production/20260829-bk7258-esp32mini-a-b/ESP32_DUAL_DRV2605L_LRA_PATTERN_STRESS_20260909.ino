#include <Arduino.h>
#include <Wire.h>
#include <math.h>
#include "driver/i2s_std.h"
#include "esp_system.h"

// 银龄智护 B1
// ESP32-S3 + PCA9540B + dual DRV2605L + dual LRA
// 通道完整性 + 随机模板组合 + 长时间压力测试
//
// 目标：
// 1) PCA9540B CH0 / CH1 地址隔离与 I2C 完整性
// 2) 两颗 DRV2605L 0x5A 稳定访问
// 3) GPIO34 / GPIO35 外部触发链路
// 4) 保守 RTP 梯度：0x08 -> 0x10 -> 0x18 -> 0x20 -> 0x30，每级 120 ms
// 5) LRA Library 6，12 个基础模板，每轮随机选模板并随机打乱模板内部顺序
// 6) CH0-only / CH1-only / A-B 交替 / 双路近同时 四种策略循环覆盖
// 7) 默认 120 分钟长时间压力测试
// 8) 任意 OC / OT / GO timeout / I2C 失败立即停机并播放 FAIL 提示音
//
// IMPORTANT：
// - 之前本板曾出现 OC_DETECT，因此本程序故意不使用 0x7F 长时间 RTP。
// - 长压测也故意排除 effect 14/15/16（Strong Buzz / 750 ms / 1000 ms 100% Alert）。
// - 这是一份“稳定性/通道压力”程序，不是“最大振幅极限”程序。

// ============================================================
// USER SETTINGS
// ============================================================

static const uint32_t STRESS_DURATION_MIN = 120; // 0 = endless
static const bool VERBOSE_EFFECTS = false;

// ============================================================
// HAPTIC PINS / ADDRESSES
// ============================================================

static const int SDA_PIN = 21;
static const int SCL_PIN = 18;
static const int HAPTIC_L_TRIG = 34;
static const int HAPTIC_R_TRIG = 35;

static const uint8_t PCA_ADDR = 0x70;
static const uint8_t DRV_ADDR = 0x5A;

static const uint8_t PCA_OFF = 0x00;
static const uint8_t PCA_CH0 = 0x04;
static const uint8_t PCA_CH1 = 0x05;

// ============================================================
// DRV2605L REGISTERS
// ============================================================

static const uint8_t REG_STATUS    = 0x00;
static const uint8_t REG_MODE      = 0x01;
static const uint8_t REG_RTP       = 0x02;
static const uint8_t REG_LIBRARY   = 0x03;
static const uint8_t REG_WAVESEQ1  = 0x04;
static const uint8_t REG_GO        = 0x0C;
static const uint8_t REG_FEEDBACK  = 0x1A;
static const uint8_t REG_CONTROL3  = 0x1D;

static const uint8_t MODE_INTTRIG = 0x00;
static const uint8_t MODE_EXTEDGE = 0x01;
static const uint8_t MODE_RTP     = 0x05;

// STATUS bits used as hard safety gates in this test.
static const uint8_t STATUS_OT = 0x02;
static const uint8_t STATUS_OC = 0x01;

// ============================================================
// AUDIO CUE: MAX98357A
// ============================================================

static const int AUDIO_BCLK = 36;
static const int AUDIO_WS   = 37;
static const int AUDIO_DOUT = 39;
static const int AUDIO_SD   = 40;

static const uint32_t AUDIO_SR = 22050;
static const float AUDIO_LEVEL = 0.05f;
static i2s_chan_handle_t audioTx = NULL;
static bool audioCueReady = false;

// ============================================================
// COUNTERS
// ============================================================

static uint32_t stressStartMs = 0;
static uint32_t lastHealthMs = 0;
static uint32_t roundCount = 0;
static uint32_t patternCount = 0;
static uint32_t ch0Effects = 0;
static uint32_t ch1Effects = 0;
static uint32_t extTrigChecks = 0;
static uint32_t i2cErrors = 0;
static uint32_t goTimeouts = 0;
static uint32_t ocEvents = 0;
static uint32_t otEvents = 0;
static uint32_t statusReads = 0;
static uint32_t diagBitSeen = 0;

static bool milestone10 = false;
static bool milestone30 = false;
static bool milestone60 = false;
static bool milestone120 = false;

// ============================================================
// EFFECT TEMPLATES
// ============================================================

// DRV2605L effect IDs used here (TouchSense 2200 table):
// 1 Strong Click 100%
// 2 Strong Click 60%
// 3 Strong Click 30%
// 4 Sharp Click 100%
// 5 Sharp Click 60%
// 6 Sharp Click 30%
// 7 Soft Bump 100%
// 8 Soft Bump 60%
// 9 Soft Bump 30%
// 10 Double Click 100%
// 11 Double Click 60%
// 12 Triple Click 100%
// 13 Soft Fuzz 60%
//
// 12 个基础模板；运行时会随机选择模板，并随机 shuffle 内部顺序。
// 所以实际序列远多于 12 种。

struct Pattern {
  const char *name;
  uint8_t len;
  uint8_t e[8];
};

static const Pattern patterns[] = {
  {"T01_SINGLE_STRONG60",    1, {2,0,0,0,0,0,0,0}},
  {"T02_CLICK_3LEVEL",       3, {1,2,3,0,0,0,0,0}},
  {"T03_SHARP_3LEVEL",       3, {4,5,6,0,0,0,0,0}},
  {"T04_BUMP_3LEVEL",        3, {7,8,9,0,0,0,0,0}},
  {"T05_DOUBLE_PAIR",        2, {10,11,0,0,0,0,0,0}},
  {"T06_TRIPLE_SOFT",        2, {12,13,0,0,0,0,0,0}},
  {"T07_SOFT_MIX",           4, {3,6,9,13,0,0,0,0}},
  {"T08_MEDIUM_MIX",         4, {2,5,8,11,0,0,0,0}},
  {"T09_ALTERNATE_CLICK",    5, {2,8,2,8,11,0,0,0}},
  {"T10_TICK_BUMP",          5, {5,9,5,9,13,0,0,0}},
  {"T11_SHORT_COMPLEX",      6, {1,3,5,7,9,11,0,0}},
  {"T12_FULL_SAFE_SET",      7, {2,3,5,6,8,9,13,0}},
};

static const uint8_t PATTERN_COUNT = sizeof(patterns) / sizeof(patterns[0]);

// ============================================================
// AUDIO HELPERS
// ============================================================

bool initAudioCue()
{
  pinMode(AUDIO_SD, OUTPUT);
  digitalWrite(AUDIO_SD, HIGH);
  delay(10);

  i2s_chan_config_t chanCfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO, I2S_ROLE_MASTER);
  if (i2s_new_channel(&chanCfg, &audioTx, NULL) != ESP_OK) return false;

  i2s_std_config_t cfg = {
    .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(AUDIO_SR),
    .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_STEREO),
    .gpio_cfg = {
      .mclk = I2S_GPIO_UNUSED,
      .bclk = (gpio_num_t)AUDIO_BCLK,
      .ws   = (gpio_num_t)AUDIO_WS,
      .dout = (gpio_num_t)AUDIO_DOUT,
      .din  = I2S_GPIO_UNUSED,
      .invert_flags = {
        .mclk_inv = false,
        .bclk_inv = false,
        .ws_inv   = false,
      },
    },
  };

  if (i2s_channel_init_std_mode(audioTx, &cfg) != ESP_OK) return false;
  if (i2s_channel_enable(audioTx) != ESP_OK) return false;
  return true;
}

void tone(float hz, uint32_t ms)
{
  if (!audioCueReady) return;

  const size_t FRAMES = 128;
  int16_t buf[FRAMES * 2];
  uint32_t total = (uint64_t)AUDIO_SR * ms / 1000;
  uint32_t done = 0;
  float phase = 0.0f;
  float step = 2.0f * PI * hz / AUDIO_SR;
  int16_t amp = (int16_t)(32767.0f * AUDIO_LEVEL);

  while (done < total)
  {
    uint32_t n = min((uint32_t)FRAMES, total - done);
    for (uint32_t i = 0; i < n; ++i)
    {
      int16_t s = (int16_t)(sinf(phase) * amp);
      phase += step;
      if (phase >= 2.0f * PI) phase -= 2.0f * PI;
      buf[i * 2] = s;
      buf[i * 2 + 1] = s;
    }

    size_t written = 0;
    i2s_channel_write(audioTx, buf, n * 2 * sizeof(int16_t), &written, portMAX_DELAY);
    done += n;
  }
}

void audioGap(uint32_t ms)
{
  if (!audioCueReady)
  {
    delay(ms);
    return;
  }

  const size_t FRAMES = 128;
  int16_t zero[FRAMES * 2] = {0};
  uint32_t total = (uint64_t)AUDIO_SR * ms / 1000;
  uint32_t done = 0;

  while (done < total)
  {
    uint32_t n = min((uint32_t)FRAMES, total - done);
    size_t written = 0;
    i2s_channel_write(audioTx, zero, n * 2 * sizeof(int16_t), &written, portMAX_DELAY);
    done += n;
  }
}

void cueStart()
{
  Serial.println("AUDIO CUE: START");
  tone(660, 120); audioGap(50);
  tone(880, 120); audioGap(50);
  tone(1175, 180); audioGap(100);
}

void cuePass()
{
  Serial.println("AUDIO CUE: PASS");
  tone(1047, 180); audioGap(60);
  tone(1568, 300); audioGap(100);
}

void cueFail()
{
  Serial.println("AUDIO CUE: FAIL");
  tone(660, 160); audioGap(50);
  tone(440, 180); audioGap(50);
  tone(220, 350); audioGap(100);
}

// ============================================================
// I2C HELPERS
// ============================================================

bool ack(uint8_t addr)
{
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

bool pcaSelect(uint8_t ctrl)
{
  Wire.beginTransmission(PCA_ADDR);
  Wire.write(ctrl);
  bool ok = (Wire.endTransmission() == 0);
  if (!ok) i2cErrors++;
  delayMicroseconds(500);
  return ok;
}

bool writeReg(uint8_t reg, uint8_t value)
{
  Wire.beginTransmission(DRV_ADDR);
  Wire.write(reg);
  Wire.write(value);
  bool ok = (Wire.endTransmission() == 0);
  if (!ok) i2cErrors++;
  return ok;
}

bool readReg(uint8_t reg, uint8_t &value)
{
  Wire.beginTransmission(DRV_ADDR);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0)
  {
    i2cErrors++;
    return false;
  }

  if (Wire.requestFrom(DRV_ADDR, (uint8_t)1) != 1)
  {
    i2cErrors++;
    return false;
  }

  value = Wire.read();
  return true;
}

const char *channelName(uint8_t pca)
{
  return (pca == PCA_CH0) ? "CH0/LRA-A" : "CH1/LRA-B";
}

// ============================================================
// SAFETY / STATUS
// ============================================================

void emergencyStopAll()
{
  const uint8_t chans[2] = {PCA_CH0, PCA_CH1};
  for (uint8_t i = 0; i < 2; ++i)
  {
    if (pcaSelect(chans[i]))
    {
      writeReg(REG_GO, 0x00);
      writeReg(REG_RTP, 0x00);
      writeReg(REG_MODE, 0x00);
    }
  }
  pcaSelect(PCA_OFF);
}

void failStop(const char *reason)
{
  emergencyStopAll();
  Serial.println();
  Serial.println("================ HAPTIC STRESS FAIL ================");
  Serial.println(reason);
  Serial.printf("round=%lu patterns=%lu ch0Effects=%lu ch1Effects=%lu\n",
                (unsigned long)roundCount,
                (unsigned long)patternCount,
                (unsigned long)ch0Effects,
                (unsigned long)ch1Effects);
  Serial.printf("i2cErr=%lu goTimeout=%lu OC=%lu OT=%lu statusReads=%lu diagBitSeen=%lu\n",
                (unsigned long)i2cErrors,
                (unsigned long)goTimeouts,
                (unsigned long)ocEvents,
                (unsigned long)otEvents,
                (unsigned long)statusReads,
                (unsigned long)diagBitSeen);
  Serial.println("=====================================================");
  cueFail();
  while (true) delay(1000);
}

void checkStatusOrFail(uint8_t pca, const char *context)
{
  if (!pcaSelect(pca)) failStop("PCA select failed while reading STATUS");

  uint8_t s = 0;
  if (!readReg(REG_STATUS, s)) failStop("DRV STATUS read failed");
  statusReads++;

  if (s & 0x08) diagBitSeen++;

  if (VERBOSE_EFFECTS)
  {
    Serial.printf("STATUS %s %s = 0x%02X OC=%u OT=%u\n",
                  channelName(pca), context, s,
                  (unsigned)((s & STATUS_OC) != 0),
                  (unsigned)((s & STATUS_OT) != 0));
  }

  if (s & STATUS_OC)
  {
    ocEvents++;
    failStop("OC_DETECT asserted. Stop immediately and inspect OUT+/OUT-/LRA/connection.");
  }

  if (s & STATUS_OT)
  {
    otEvents++;
    failStop("OVER_TEMP asserted. Stop immediately.");
  }
}

// ============================================================
// DRV CONFIG
// ============================================================

bool configureSelectedLRACommon()
{
  if (!ack(DRV_ADDR))
  {
    i2cErrors++;
    return false;
  }

  uint8_t feedback = 0;
  uint8_t control3 = 0;

  if (!readReg(REG_FEEDBACK, feedback)) return false;
  if (!readReg(REG_CONTROL3, control3)) return false;

  feedback |= 0x80;                // N_ERM_LRA = 1 -> LRA
  control3 &= (uint8_t)~0x20;      // ERM_OPEN_LOOP = 0
  control3 &= (uint8_t)~0x08;      // RTP signed
  control3 &= (uint8_t)~0x01;      // LRA open loop = 0 -> closed loop

  if (!writeReg(REG_FEEDBACK, feedback)) return false;
  if (!writeReg(REG_CONTROL3, control3)) return false;
  if (!writeReg(REG_LIBRARY, 0x06)) return false; // LRA library
  return true;
}

bool configureSelectedEffect(uint8_t effect, uint8_t mode)
{
  if (!configureSelectedLRACommon()) return false;

  if (!writeReg(REG_MODE, MODE_INTTRIG)) return false;
  if (!writeReg(REG_RTP, 0x00)) return false;
  if (!writeReg(REG_GO, 0x00)) return false;

  if (!writeReg(REG_WAVESEQ1, effect)) return false;
  for (uint8_t i = 1; i < 8; ++i)
  {
    if (!writeReg(REG_WAVESEQ1 + i, 0x00)) return false;
  }

  if (!writeReg(REG_MODE, mode)) return false;
  delay(2);
  return true;
}

bool configureSelectedRTP()
{
  if (!configureSelectedLRACommon()) return false;
  if (!writeReg(REG_RTP, 0x00)) return false;
  if (!writeReg(REG_MODE, MODE_RTP)) return false;
  delay(5);
  return true;
}

// ============================================================
// PLAYBACK
// ============================================================

void startEffect(uint8_t pca, uint8_t effect)
{
  if (!pcaSelect(pca)) failStop("PCA select failed before effect");

  // Read once before playback to clear any old latched status.
  uint8_t old = 0;
  if (!readReg(REG_STATUS, old)) failStop("Pre-effect STATUS read failed");
  statusReads++;

  if (!configureSelectedEffect(effect, MODE_INTTRIG)) failStop("DRV effect configuration failed");
  if (!writeReg(REG_GO, 0x01)) failStop("GO=1 write failed");

  if (pca == PCA_CH0) ch0Effects++;
  else ch1Effects++;

  if (VERBOSE_EFFECTS)
    Serial.printf("START %s effect=%u\n", channelName(pca), (unsigned)effect);
}

void waitEffectDone(uint8_t pca, uint32_t timeoutMs = 2200)
{
  uint32_t t0 = millis();

  while (millis() - t0 < timeoutMs)
  {
    if (!pcaSelect(pca)) failStop("PCA select failed while polling GO");

    uint8_t go = 0;
    if (!readReg(REG_GO, go)) failStop("GO read failed");

    if ((go & 0x01) == 0)
    {
      checkStatusOrFail(pca, "after-effect");
      return;
    }

    delay(5);
  }

  goTimeouts++;
  failStop("GO timeout during ROM effect playback");
}

void playEffectBlocking(uint8_t pca, uint8_t effect)
{
  startEffect(pca, effect);
  waitEffectDone(pca);
}

void playDualNearSimultaneous(uint8_t effectA, uint8_t effectB)
{
  startEffect(PCA_CH0, effectA);
  startEffect(PCA_CH1, effectB);

  bool doneA = false;
  bool doneB = false;
  uint32_t t0 = millis();

  while (millis() - t0 < 2200)
  {
    if (!doneA)
    {
      if (!pcaSelect(PCA_CH0)) failStop("PCA CH0 select failed in dual poll");
      uint8_t go = 0;
      if (!readReg(REG_GO, go)) failStop("CH0 GO read failed in dual poll");
      if ((go & 0x01) == 0) doneA = true;
    }

    if (!doneB)
    {
      if (!pcaSelect(PCA_CH1)) failStop("PCA CH1 select failed in dual poll");
      uint8_t go = 0;
      if (!readReg(REG_GO, go)) failStop("CH1 GO read failed in dual poll");
      if ((go & 0x01) == 0) doneB = true;
    }

    if (doneA && doneB)
    {
      checkStatusOrFail(PCA_CH0, "after-dual");
      checkStatusOrFail(PCA_CH1, "after-dual");
      return;
    }

    delay(5);
  }

  goTimeouts++;
  failStop("GO timeout during dual near-simultaneous playback");
}

// ============================================================
// STARTUP INTEGRITY: RTP RAMP
// ============================================================

void conservativeRtpRamp(uint8_t pca)
{
  static const uint8_t levels[] = {0x08, 0x10, 0x18, 0x20, 0x30};

  Serial.printf("RTP SAFE RAMP: %s\n", channelName(pca));

  if (!pcaSelect(pca)) failStop("PCA select failed before RTP ramp");
  if (!configureSelectedRTP()) failStop("RTP config failed");

  for (uint8_t i = 0; i < sizeof(levels); ++i)
  {
    uint8_t old = 0;
    if (!readReg(REG_STATUS, old)) failStop("Pre-RTP STATUS read failed");
    statusReads++;

    if (!writeReg(REG_RTP, levels[i])) failStop("RTP write failed");
    delay(120);
    if (!writeReg(REG_RTP, 0x00)) failStop("RTP stop failed");
    delay(25);

    checkStatusOrFail(pca, "RTP-ramp");
    delay(180);
  }

  if (!pcaSelect(pca)) failStop("PCA select failed ending RTP ramp");
  writeReg(REG_RTP, 0x00);
  writeReg(REG_MODE, 0x00);
}

// ============================================================
// STARTUP / PERIODIC EXTERNAL TRIGGER TEST
// ============================================================

void pulseTrigger(int pin)
{
  digitalWrite(pin, LOW);
  delay(5);
  digitalWrite(pin, HIGH);
  delay(20);
  digitalWrite(pin, LOW);
}

void externalTriggerSanity()
{
  Serial.println("EXTERNAL TRIGGER SANITY: GPIO34 + GPIO35");

  if (!pcaSelect(PCA_CH0)) failStop("CH0 select failed for external trigger");
  if (!configureSelectedEffect(2, MODE_EXTEDGE)) failStop("CH0 external trigger config failed");

  if (!pcaSelect(PCA_CH1)) failStop("CH1 select failed for external trigger");
  if (!configureSelectedEffect(5, MODE_EXTEDGE)) failStop("CH1 external trigger config failed");

  pcaSelect(PCA_OFF);

  pulseTrigger(HAPTIC_L_TRIG);
  delay(350);
  checkStatusOrFail(PCA_CH0, "GPIO34-trigger");

  pcaSelect(PCA_OFF);
  pulseTrigger(HAPTIC_R_TRIG);
  delay(350);
  checkStatusOrFail(PCA_CH1, "GPIO35-trigger");

  pcaSelect(PCA_OFF);
  extTrigChecks++;
}

// ============================================================
// RANDOMIZATION / PATTERN EXECUTION
// ============================================================

void shuffleSequence(uint8_t *seq, uint8_t len)
{
  if (len < 2) return;

  for (int i = len - 1; i > 0; --i)
  {
    int j = random(0, i + 1);
    uint8_t t = seq[i];
    seq[i] = seq[j];
    seq[j] = t;
  }
}

void runPatternRound()
{
  uint8_t pidx = (uint8_t)random(0, PATTERN_COUNT);
  const Pattern &p = patterns[pidx];

  uint8_t seq[8] = {0};
  for (uint8_t i = 0; i < p.len; ++i) seq[i] = p.e[i];
  shuffleSequence(seq, p.len);

  // Balanced channel coverage:
  // 0 CH0 only
  // 1 CH1 only
  // 2 A/B alternating
  // 3 dual near-simultaneous with cross-shifted sequence
  uint8_t strategy = roundCount % 4;

  Serial.printf("ROUND %lu | pattern=%u/%u %s | variant=shuffle | strategy=%u | len=%u\n",
                (unsigned long)(roundCount + 1),
                (unsigned)(pidx + 1),
                (unsigned)PATTERN_COUNT,
                p.name,
                (unsigned)strategy,
                (unsigned)p.len);

  if (strategy == 0)
  {
    for (uint8_t i = 0; i < p.len; ++i)
    {
      playEffectBlocking(PCA_CH0, seq[i]);
      delay(random(70, 190));
    }
  }
  else if (strategy == 1)
  {
    for (uint8_t i = 0; i < p.len; ++i)
    {
      playEffectBlocking(PCA_CH1, seq[i]);
      delay(random(70, 190));
    }
  }
  else if (strategy == 2)
  {
    for (uint8_t i = 0; i < p.len; ++i)
    {
      uint8_t ch = (i & 1) ? PCA_CH1 : PCA_CH0;
      playEffectBlocking(ch, seq[i]);
      delay(random(70, 190));
    }
  }
  else
  {
    uint8_t shift = (p.len > 1) ? (uint8_t)random(1, p.len) : 0;
    for (uint8_t i = 0; i < p.len; ++i)
    {
      uint8_t eA = seq[i];
      uint8_t eB = seq[(i + shift) % p.len];
      playDualNearSimultaneous(eA, eB);
      delay(random(90, 220));
    }
  }

  roundCount++;
  patternCount++;

  // Periodically re-validate the GPIO trigger path during the long run.
  if ((roundCount % 100) == 0)
  {
    externalTriggerSanity();
  }

  // Random cool-down / idle interval to avoid turning this into a thermal abuse test.
  delay(random(500, 1300));
}

// ============================================================
// HEALTH / MILESTONES
// ============================================================

void printHealth()
{
  uint32_t elapsedSec = (millis() - stressStartMs) / 1000;

  Serial.println("----- HAPTIC STRESS HEALTH -----");
  Serial.printf("elapsed=%lus rounds=%lu patterns=%lu extTrigChecks=%lu\n",
                (unsigned long)elapsedSec,
                (unsigned long)roundCount,
                (unsigned long)patternCount,
                (unsigned long)extTrigChecks);
  Serial.printf("ch0Effects=%lu ch1Effects=%lu i2cErr=%lu goTimeout=%lu OC=%lu OT=%lu\n",
                (unsigned long)ch0Effects,
                (unsigned long)ch1Effects,
                (unsigned long)i2cErrors,
                (unsigned long)goTimeouts,
                (unsigned long)ocEvents,
                (unsigned long)otEvents);
  Serial.printf("statusReads=%lu diagBitSeen=%lu heap=%u\n",
                (unsigned long)statusReads,
                (unsigned long)diagBitSeen,
                (unsigned)ESP.getFreeHeap());
}

void printMilestone(uint32_t min)
{
  Serial.println();
  Serial.printf("========== HAPTIC STRESS %lu-MIN MILESTONE ==========\n", (unsigned long)min);
  printHealth();
  Serial.println("PASS SO FAR: no hard-stop condition observed.");
  Serial.println("======================================================");
}

void updateMilestones()
{
  uint32_t elapsedMin = (millis() - stressStartMs) / 60000UL;

  if (!milestone10 && elapsedMin >= 10)
  {
    milestone10 = true;
    printMilestone(10);
  }

  if (!milestone30 && elapsedMin >= 30)
  {
    milestone30 = true;
    printMilestone(30);
  }

  if (!milestone60 && elapsedMin >= 60)
  {
    milestone60 = true;
    printMilestone(60);
  }

  if (!milestone120 && elapsedMin >= 120)
  {
    milestone120 = true;
    printMilestone(120);
  }
}

void finishPass()
{
  emergencyStopAll();
  Serial.println();
  Serial.println("================ HAPTIC STRESS RESULT ================");
  Serial.println("DUAL DRV2605L / LRA CHANNEL STRESS: PASS CANDIDATE");
  printHealth();
  Serial.println("Requirements observed by firmware:");
  Serial.println("  i2cErr=0, goTimeout=0, OC=0, OT=0");
  Serial.println("  CH0 and CH1 effect counters both increased");
  Serial.println("  GPIO34/GPIO35 external-trigger sanity checks executed");
  Serial.println("Manual check still required: both LRAs physically felt normal and no abnormal heating/noise.");
  Serial.println("======================================================");
  cuePass();
  while (true) delay(1000);
}

// ============================================================
// SETUP
// ============================================================

void setup()
{
  Serial.begin(115200);
  delay(2000);

  Serial.println();
  Serial.println("============================================================");
  Serial.println("YINLING-ZHIHU B1 DUAL DRV2605L / LRA PATTERN STRESS TEST");
  Serial.println("PCA=0x70 DRV=0x5A | SDA=21 SCL=18 | TRIG=GPIO34/GPIO35");
  Serial.printf("Base templates=%u | duration=%lu min\n",
                (unsigned)PATTERN_COUNT,
                (unsigned long)STRESS_DURATION_MIN);
  Serial.println("Effect 14/15/16 and long 0x7F RTP are intentionally excluded.");
  Serial.println("============================================================");

  pinMode(HAPTIC_L_TRIG, OUTPUT);
  pinMode(HAPTIC_R_TRIG, OUTPUT);
  digitalWrite(HAPTIC_L_TRIG, LOW);
  digitalWrite(HAPTIC_R_TRIG, LOW);

  audioCueReady = initAudioCue();
  Serial.printf("AUDIO CUE INIT: %s\n", audioCueReady ? "PASS" : "DISABLED/FAIL (haptic test continues)");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000);
  delay(100);

  if (!ack(PCA_ADDR)) failStop("PCA9540B 0x70 NO ACK");
  Serial.println("PCA9540B 0x70: PASS");

  pcaSelect(PCA_CH0);
  if (!ack(DRV_ADDR)) failStop("CH0 DRV2605L 0x5A NO ACK");
  Serial.println("CH0 DRV2605L 0x5A: PASS");

  pcaSelect(PCA_CH1);
  if (!ack(DRV_ADDR)) failStop("CH1 DRV2605L 0x5A NO ACK");
  Serial.println("CH1 DRV2605L 0x5A: PASS");

  // First clear and inspect old latched status on both branches.
  checkStatusOrFail(PCA_CH0, "initial-clean");
  checkStatusOrFail(PCA_CH1, "initial-clean");

  Serial.println();
  Serial.println("========== PHASE A: CONSERVATIVE RTP INTEGRITY ==========");
  conservativeRtpRamp(PCA_CH0);
  conservativeRtpRamp(PCA_CH1);

  Serial.println();
  Serial.println("========== PHASE B: EXTERNAL TRIGGER INTEGRITY ==========");
  externalTriggerSanity();

  randomSeed(esp_random());

  Serial.println();
  Serial.println("========== PHASE C: RANDOM ROM PATTERN LONG STRESS ==========");
  Serial.println("Strategies: 0=CH0, 1=CH1, 2=alternate, 3=dual-near-simultaneous");
  Serial.println("Every 100 rounds: repeat GPIO34/GPIO35 trigger sanity check.");
  Serial.println("Any I2C / GO timeout / OC / OT -> immediate hard stop.");

  cueStart();

  stressStartMs = millis();
  lastHealthMs = stressStartMs;
}

// ============================================================
// LOOP
// ============================================================

void loop()
{
  runPatternRound();

  if (millis() - lastHealthMs >= 30000UL)
  {
    printHealth();
    lastHealthMs = millis();
  }

  updateMilestones();

  if (STRESS_DURATION_MIN > 0)
  {
    uint32_t targetMs = STRESS_DURATION_MIN * 60000UL;
    if (millis() - stressStartMs >= targetMs)
    {
      if (i2cErrors == 0 && goTimeouts == 0 && ocEvents == 0 && otEvents == 0 &&
          ch0Effects > 0 && ch1Effects > 0 && extTrigChecks > 0)
      {
        finishPass();
      }
      else
      {
        failStop("Duration reached but one or more PASS requirements are not satisfied");
      }
    }
  }
}
