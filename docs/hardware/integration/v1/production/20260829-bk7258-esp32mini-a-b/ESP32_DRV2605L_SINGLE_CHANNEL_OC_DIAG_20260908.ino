#include <Arduino.h>
#include <Wire.h>

static const int SDA_PIN = 21;
static const int SCL_PIN = 18;

static const uint8_t PCA_ADDR = 0x70;
static const uint8_t DRV_ADDR = 0x5A;

// 先只测 CH1 / LRA B。若要测 CH0，把 0x05 改成 0x04。
static const uint8_t TEST_PCA_CHANNEL = 0x05;
static const char *TEST_NAME = "CH1 / LRA B";

static const uint8_t REG_STATUS   = 0x00;
static const uint8_t REG_MODE     = 0x01;
static const uint8_t REG_RTP      = 0x02;
static const uint8_t REG_FEEDBACK = 0x1A;
static const uint8_t REG_CONTROL3 = 0x1D;

static const uint8_t MODE_RTP = 0x05;

bool pcaSelect(uint8_t value)
{
  Wire.beginTransmission(PCA_ADDR);
  Wire.write(value);
  bool ok = Wire.endTransmission() == 0;
  delay(5);
  return ok;
}

bool ack(uint8_t addr)
{
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

bool writeReg(uint8_t reg, uint8_t value)
{
  Wire.beginTransmission(DRV_ADDR);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

bool readReg(uint8_t reg, uint8_t &value)
{
  Wire.beginTransmission(DRV_ADDR);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(DRV_ADDR, (uint8_t)1) != 1) return false;
  value = Wire.read();
  return true;
}

void printStatus(const char *prefix, uint8_t s)
{
  Serial.print(prefix);
  Serial.print(" STATUS=0x");
  if (s < 0x10) Serial.print('0');
  Serial.print(s, HEX);
  Serial.print("  DIAG=");
  Serial.print((s & 0x08) ? "FAIL" : "OK");
  Serial.print("  OT=");
  Serial.print((s & 0x02) ? "YES" : "NO");
  Serial.print("  OC=");
  Serial.println((s & 0x01) ? "YES" : "NO");
}

void stopDrive()
{
  writeReg(REG_RTP, 0x00);
  delay(5);
  writeReg(REG_MODE, 0x00);
}

void hardStop(const char *msg)
{
  stopDrive();
  Serial.println();
  Serial.println("========== TEST STOP ==========");
  Serial.println(msg);
  while (true) delay(1000);
}

bool configLRAForRTP()
{
  uint8_t feedback = 0;
  uint8_t control3 = 0;

  if (!readReg(REG_FEEDBACK, feedback)) return false;
  if (!readReg(REG_CONTROL3, control3)) return false;

  // LRA mode
  feedback |= 0x80;
  if (!writeReg(REG_FEEDBACK, feedback)) return false;

  // Signed RTP input; closed-loop LRA path
  control3 &= (uint8_t)~0x08;
  control3 &= (uint8_t)~0x01;
  if (!writeReg(REG_CONTROL3, control3)) return false;

  if (!writeReg(REG_RTP, 0x00)) return false;
  if (!writeReg(REG_MODE, MODE_RTP)) return false;

  delay(20);
  return true;
}

void runStep(uint8_t level)
{
  // 先读一次清掉之前锁存的状态位
  uint8_t oldStatus = 0;
  if (!readReg(REG_STATUS, oldStatus)) hardStop("STATUS read failed before step");

  Serial.println();
  Serial.print("RTP level 0x");
  if (level < 0x10) Serial.print('0');
  Serial.println(level, HEX);

  if (!writeReg(REG_RTP, level)) hardStop("RTP write failed");

  // 只短驱动 120 ms，避免已存在短路时长时间硬顶
  delay(120);

  if (!writeReg(REG_RTP, 0x00)) hardStop("RTP stop failed");
  delay(20);

  uint8_t status = 0;
  if (!readReg(REG_STATUS, status)) hardStop("STATUS read failed after step");
  printStatus("After step", status);

  if (status & 0x01)
  {
    hardStop("OC_DETECT asserted. Stop driving. Check OUT+/OUT-/LRA hardware before any stronger test.");
  }

  if (status & 0x02)
  {
    hardStop("OVER_TEMP asserted. Stop driving.");
  }

  delay(800);
}

void setup()
{
  Serial.begin(115200);
  delay(2500);

  Serial.println();
  Serial.println("========================================");
  Serial.println("DRV2605L SINGLE-CHANNEL OC DIAGNOSTIC");
  Serial.print("Target: ");
  Serial.println(TEST_NAME);
  Serial.println("Only one PCA branch is enabled.");
  Serial.println("========================================");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000);
  delay(100);

  if (!ack(PCA_ADDR)) hardStop("PCA9540B 0x70 NO ACK");
  if (!pcaSelect(TEST_PCA_CHANNEL)) hardStop("PCA channel select failed");
  if (!ack(DRV_ADDR)) hardStop("Selected DRV2605L 0x5A NO ACK");

  Serial.println("PCA selected: PASS");
  Serial.println("DRV2605L 0x5A: PASS");

  // 第一次读取会清除之前锁存的 OC/OT 标志
  uint8_t s1 = 0;
  uint8_t s2 = 0;
  if (!readReg(REG_STATUS, s1)) hardStop("Initial STATUS read failed");
  delay(10);
  if (!readReg(REG_STATUS, s2)) hardStop("Clean STATUS read failed");

  printStatus("Latched old", s1);
  printStatus("Clean base ", s2);

  if (!configLRAForRTP()) hardStop("LRA/RTP config failed");

  Serial.println();
  Serial.println("Starting conservative RTP ramp: 0x08 -> 0x10 -> 0x18 -> 0x20 -> 0x30");
  Serial.println("Each step is only 120 ms. Any OC/OT stops the test immediately.");

  const uint8_t levels[] = {0x08, 0x10, 0x18, 0x20, 0x30};
  for (size_t i = 0; i < sizeof(levels); i++)
  {
    runStep(levels[i]);
  }

  stopDrive();

  Serial.println();
  Serial.println("========== RESULT ==========");
  Serial.println("No OC/OT during conservative ramp.");
  Serial.println("Electrical short is less likely on this branch, but actuator tuning still needs separate validation.");
}

void loop()
{
  delay(1000);
}
