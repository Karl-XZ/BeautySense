#include <Wire.h>

// 银龄智护 B1：双路 DRV2605L 强振动 RTP 测试
// ESP32-S3 SENSOR_I2C: SDA=GPIO21, SCL=GPIO18
// PCA9540B: 0x70
// DRV2605L: fixed 0x5A on CH0 / CH1
// 目的：不用 ROM 短效果，直接使用 RTP 连续驱动做“明显可感知”的短脉冲。

static const int SDA_PIN = 21;
static const int SCL_PIN = 18;

static const uint8_t PCA_ADDR = 0x70;
static const uint8_t DRV_ADDR = 0x5A;

static const uint8_t PCA_OFF = 0x00;
static const uint8_t PCA_CH0 = 0x04;
static const uint8_t PCA_CH1 = 0x05;

// DRV2605L registers
static const uint8_t REG_STATUS   = 0x00;
static const uint8_t REG_MODE     = 0x01;
static const uint8_t REG_RTP      = 0x02;
static const uint8_t REG_FEEDBACK = 0x1A;
static const uint8_t REG_CONTROL3 = 0x1D;

// MODE[2:0] = 5 => RTP mode
static const uint8_t MODE_RTP = 0x05;

// RTP signed mode default: +0x7F is maximum positive command.
// 保持短脉冲，不做长时间连续满幅驱动。
static const uint8_t RTP_LEVEL = 0x7F;
static const uint32_t ON_MS = 700;
static const uint32_t OFF_MS = 600;
static const int REPEAT_COUNT = 3;

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

void printHex8(uint8_t v)
{
  if (v < 0x10) Serial.print('0');
  Serial.print(v, HEX);
}

void printSelectedStatus(const char *label)
{
  uint8_t st = 0;
  if (!readReg(REG_STATUS, st))
  {
    Serial.print(label);
    Serial.println(" STATUS READ FAIL");
    return;
  }

  Serial.print(label);
  Serial.print(" STATUS=0x");
  printHex8(st);
  Serial.print("  OVER_TEMP=");
  Serial.print((st & 0x02) ? "YES" : "NO");
  Serial.print("  OC=");
  Serial.println((st & 0x01) ? "YES" : "NO");
}

bool configureSelectedDRVForStrongRTP()
{
  if (!ack(DRV_ADDR)) return false;

  uint8_t feedback = 0;
  uint8_t control3 = 0;

  if (!readReg(REG_FEEDBACK, feedback)) return false;
  if (!readReg(REG_CONTROL3, control3)) return false;

  // LRA mode: N_ERM_LRA = 1
  feedback |= 0x80;
  if (!writeReg(REG_FEEDBACK, feedback)) return false;

  // CONTROL3:
  // bit3 DATA_FORMAT_RTP = 0 => signed RTP
  // bit0 LRA_OPEN_LOOP   = 0 => closed-loop / auto-resonance
  control3 &= (uint8_t)~0x08;
  control3 &= (uint8_t)~0x01;
  if (!writeReg(REG_CONTROL3, control3)) return false;

  // 先清零 RTP，再进入 RTP mode
  if (!writeReg(REG_RTP, 0x00)) return false;
  if (!writeReg(REG_MODE, MODE_RTP)) return false;

  delay(20);
  return true;
}

bool strongBurstSelected(const char *label)
{
  if (!configureSelectedDRVForStrongRTP())
  {
    Serial.print(label);
    Serial.println(" CONFIG FAIL");
    return false;
  }

  Serial.println();
  Serial.print("===== ");
  Serial.print(label);
  Serial.println(" STRONG RTP =====");

  for (int i = 1; i <= REPEAT_COUNT; i++)
  {
    Serial.print(label);
    Serial.print(" pulse ");
    Serial.print(i);
    Serial.print("/");
    Serial.println(REPEAT_COUNT);

    if (!writeReg(REG_RTP, RTP_LEVEL))
    {
      Serial.println("RTP WRITE FAIL");
      return false;
    }

    delay(ON_MS);

    // 立即停振
    writeReg(REG_RTP, 0x00);
    delay(OFF_MS);

    printSelectedStatus(label);
  }

  // 测试结束后回到 Internal Trigger / idle，并确保 RTP=0
  writeReg(REG_RTP, 0x00);
  writeReg(REG_MODE, 0x00);

  return true;
}

void setup()
{
  Serial.begin(115200);
  delay(2500);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 DUAL DRV2605L STRONG RTP TEST");
  Serial.println("SDA=21 SCL=18 PCA=0x70 DRV=0x5A");
  Serial.println("RTP=0x7F, 700 ms ON, 600 ms OFF, x3");
  Serial.println("========================================");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000);
  delay(100);

  if (!ack(PCA_ADDR))
  {
    Serial.println("PCA9540B 0x70 NO ACK - STOP");
    while (true) delay(1000);
  }

  // CH0
  pcaSelect(PCA_CH0);
  bool ch0 = ack(DRV_ADDR);
  Serial.print("CH0 DRV2605L 0x5A = ");
  Serial.println(ch0 ? "PASS" : "FAIL");

  // CH1
  pcaSelect(PCA_CH1);
  bool ch1 = ack(DRV_ADDR);
  Serial.print("CH1 DRV2605L 0x5A = ");
  Serial.println(ch1 ? "PASS" : "FAIL");

  if (!ch0 && !ch1)
  {
    Serial.println("No DRV2605L detected on either branch - STOP");
    pcaSelect(PCA_OFF);
    while (true) delay(1000);
  }

  delay(1000);

  if (ch0)
  {
    pcaSelect(PCA_CH0);
    strongBurstSelected("CH0 / LRA A");
  }

  delay(1200);

  if (ch1)
  {
    pcaSelect(PCA_CH1);
    strongBurstSelected("CH1 / LRA B");
  }

  pcaSelect(PCA_OFF);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ONE ROUND DONE");
  Serial.println("If both motors were clearly strong, haptic drive path is strong-functional.");
  Serial.println("The test will repeat after 5 seconds.");
  Serial.println("========================================");
}

void loop()
{
  delay(5000);

  pcaSelect(PCA_CH0);
  if (ack(DRV_ADDR)) strongBurstSelected("CH0 / LRA A");

  delay(1200);

  pcaSelect(PCA_CH1);
  if (ack(DRV_ADDR)) strongBurstSelected("CH1 / LRA B");

  pcaSelect(PCA_OFF);
}
