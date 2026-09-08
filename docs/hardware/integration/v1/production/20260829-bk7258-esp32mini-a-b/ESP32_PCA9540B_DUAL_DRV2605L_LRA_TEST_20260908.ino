#include <Wire.h>

// 银龄智护 B1
// ESP32-S3 + PCA9540B + dual DRV2605L + dual LRA test
// No external library required.

static const int SDA_PIN = 21;
static const int SCL_PIN = 18;
static const int HAPTIC_L_TRIG = 34;
static const int HAPTIC_R_TRIG = 35;

static const uint8_t PCA_ADDR = 0x70;
static const uint8_t DRV_ADDR = 0x5A;

static const uint8_t PCA_OFF = 0x00;
static const uint8_t PCA_CH0 = 0x04;
static const uint8_t PCA_CH1 = 0x05;

// DRV2605L registers
static const uint8_t REG_STATUS    = 0x00;
static const uint8_t REG_MODE      = 0x01;
static const uint8_t REG_RTP       = 0x02;
static const uint8_t REG_LIBRARY   = 0x03;
static const uint8_t REG_WAVESEQ1  = 0x04;
static const uint8_t REG_GO        = 0x0C;
static const uint8_t REG_FEEDBACK  = 0x1A;
static const uint8_t REG_CONTROL3  = 0x1D;

static const uint8_t MODE_INTTRIG  = 0x00;
static const uint8_t MODE_EXTEDGE  = 0x01;

// ROM effect 15 = 750 ms Alert 100% in the DRV2605L effect list.
static const uint8_t TEST_EFFECT = 15;

bool writeByte(uint8_t addr, uint8_t reg, uint8_t value)
{
  Wire.beginTransmission(addr);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

bool readByte(uint8_t addr, uint8_t reg, uint8_t &value)
{
  Wire.beginTransmission(addr);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(addr, (uint8_t)1) != 1) return false;
  value = Wire.read();
  return true;
}

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
  delay(5);
  return ok;
}

void scanBus(const char *title)
{
  Serial.println();
  Serial.println(title);
  int count = 0;
  for (uint8_t a = 1; a < 127; ++a)
  {
    Wire.beginTransmission(a);
    if (Wire.endTransmission() == 0)
    {
      Serial.print("  0x");
      if (a < 0x10) Serial.print('0');
      Serial.println(a, HEX);
      count++;
    }
  }
  Serial.print("devices = ");
  Serial.println(count);
}

bool configureSelectedDRV(uint8_t mode, uint8_t effect)
{
  if (!ack(DRV_ADDR)) return false;

  uint8_t feedback = 0;
  uint8_t control3 = 0;

  if (!readByte(DRV_ADDR, REG_FEEDBACK, feedback)) return false;
  if (!readByte(DRV_ADDR, REG_CONTROL3, control3)) return false;

  // Wake / internal engine basic state.
  if (!writeByte(DRV_ADDR, REG_MODE, 0x00)) return false;
  if (!writeByte(DRV_ADDR, REG_RTP, 0x00)) return false;

  // LRA mode: N_ERM_LRA = 1.
  feedback |= 0x80;
  if (!writeByte(DRV_ADDR, REG_FEEDBACK, feedback)) return false;

  // Clear ERM_OPEN_LOOP; for LRA bring-up use closed-loop behavior.
  control3 &= (uint8_t)~0x20;
  if (!writeByte(DRV_ADDR, REG_CONTROL3, control3)) return false;

  // Library 6 is the LRA library.
  if (!writeByte(DRV_ADDR, REG_LIBRARY, 0x06)) return false;

  // Waveform sequencer: one visible effect then stop.
  if (!writeByte(DRV_ADDR, REG_WAVESEQ1 + 0, effect)) return false;
  for (uint8_t i = 1; i < 8; ++i)
  {
    if (!writeByte(DRV_ADDR, REG_WAVESEQ1 + i, 0x00)) return false;
  }

  if (!writeByte(DRV_ADDR, REG_GO, 0x00)) return false;
  if (!writeByte(DRV_ADDR, REG_MODE, mode)) return false;
  delay(10);

  uint8_t status = 0, lib = 0, modeRead = 0, fb = 0;
  readByte(DRV_ADDR, REG_STATUS, status);
  readByte(DRV_ADDR, REG_LIBRARY, lib);
  readByte(DRV_ADDR, REG_MODE, modeRead);
  readByte(DRV_ADDR, REG_FEEDBACK, fb);

  Serial.print("  STATUS=0x"); Serial.print(status, HEX);
  Serial.print(" LIB=0x"); Serial.print(lib, HEX);
  Serial.print(" MODE=0x"); Serial.print(modeRead, HEX);
  Serial.print(" FEEDBACK=0x"); Serial.println(fb, HEX);

  return true;
}

bool playSelectedDRV(uint8_t effect)
{
  if (!configureSelectedDRV(MODE_INTTRIG, effect)) return false;
  if (!writeByte(DRV_ADDR, REG_GO, 0x01)) return false;

  unsigned long start = millis();
  while (millis() - start < 2500)
  {
    uint8_t go = 0;
    if (!readByte(DRV_ADDR, REG_GO, go)) return false;
    if ((go & 0x01) == 0) return true;
    delay(10);
  }

  Serial.println("  GO timeout");
  writeByte(DRV_ADDR, REG_GO, 0x00);
  return false;
}

void pulseTrigger(int pin)
{
  digitalWrite(pin, LOW);
  delay(20);
  digitalWrite(pin, HIGH);
  delay(10);
  digitalWrite(pin, LOW);
}

void stopForever(const char *msg)
{
  Serial.println();
  Serial.println("========== TEST STOP ==========");
  Serial.println(msg);
  while (true) delay(1000);
}

void setup()
{
  Serial.begin(115200);
  delay(2500);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 + PCA9540B + DUAL DRV2605L LRA TEST");
  Serial.println("SDA=21 SCL=18  PCA=0x70  DRV=0x5A");
  Serial.println("TRIG_L=34 TRIG_R=35");
  Serial.println("========================================");

  pinMode(HAPTIC_L_TRIG, OUTPUT);
  pinMode(HAPTIC_R_TRIG, OUTPUT);
  digitalWrite(HAPTIC_L_TRIG, LOW);
  digitalWrite(HAPTIC_R_TRIG, LOW);

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000);
  delay(100);

  if (!ack(PCA_ADDR)) stopForever("PCA9540B 0x70 NO ACK");

  // ---------- Address isolation ----------
  pcaSelect(PCA_OFF);
  scanBus("PCA OFF: upstream bus");

  pcaSelect(PCA_CH0);
  scanBus("PCA CH0: expect one DRV2605L at 0x5A");
  bool ch0Ack = ack(DRV_ADDR);

  pcaSelect(PCA_CH1);
  scanBus("PCA CH1: expect one DRV2605L at 0x5A");
  bool ch1Ack = ack(DRV_ADDR);

  Serial.println();
  Serial.print("CH0 0x5A = "); Serial.println(ch0Ack ? "PASS" : "FAIL");
  Serial.print("CH1 0x5A = "); Serial.println(ch1Ack ? "PASS" : "FAIL");

  if (!ch0Ack || !ch1Ack)
    stopForever("Both PCA branches must see 0x5A before motor test.");

  // ---------- I2C GO test ----------
  Serial.println();
  Serial.println("========== PHASE A: I2C GO ==========");

  pcaSelect(PCA_CH0);
  Serial.println("CH0 / LRA A: effect 15");
  bool ch0Play = playSelectedDRV(TEST_EFFECT);
  Serial.println(ch0Play ? "CH0 PLAY: PASS" : "CH0 PLAY: FAIL");
  delay(800);

  pcaSelect(PCA_CH1);
  Serial.println("CH1 / LRA B: effect 15");
  bool ch1Play = playSelectedDRV(TEST_EFFECT);
  Serial.println(ch1Play ? "CH1 PLAY: PASS" : "CH1 PLAY: FAIL");
  delay(800);

  // ---------- External trigger setup ----------
  Serial.println();
  Serial.println("========== PHASE B: GPIO TRIGGER ==========");

  pcaSelect(PCA_CH0);
  if (!configureSelectedDRV(MODE_EXTEDGE, TEST_EFFECT))
    stopForever("CH0 external-trigger config failed");

  pcaSelect(PCA_CH1);
  if (!configureSelectedDRV(MODE_EXTEDGE, TEST_EFFECT))
    stopForever("CH1 external-trigger config failed");

  // Once both drivers are configured, the mux can be disconnected.
  pcaSelect(PCA_OFF);

  Serial.println("Both DRVs configured. PCA now OFF.");
  Serial.println("GPIO34 should trigger LRA A; GPIO35 should trigger LRA B.");
  Serial.println();
}

void loop()
{
  Serial.println("TRIGGER L / GPIO34");
  pulseTrigger(HAPTIC_L_TRIG);
  delay(1500);

  Serial.println("TRIGGER R / GPIO35");
  pulseTrigger(HAPTIC_R_TRIG);
  delay(1500);

  Serial.println("---- next round in 3 s ----");
  delay(3000);
}
