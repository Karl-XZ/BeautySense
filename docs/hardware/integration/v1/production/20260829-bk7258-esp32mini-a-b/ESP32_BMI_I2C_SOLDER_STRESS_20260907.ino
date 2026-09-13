#include <Wire.h>

static const int SDA_PIN = 21;
static const int SCL_PIN = 18;
static const uint8_t BMI_ADDR = 0x68;
static const uint8_t PCA_ADDR = 0x70;

bool readReg8(uint8_t addr, uint8_t reg, uint8_t &value)
{
  Wire.beginTransmission(addr);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom((uint8_t)addr, (uint8_t)1) != 1) return false;
  if (!Wire.available()) return false;
  value = Wire.read();
  return true;
}

bool writeReg8(uint8_t addr, uint8_t reg, uint8_t value)
{
  Wire.beginTransmission(addr);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

void pcaOff()
{
  Wire.beginTransmission(PCA_ADDR);
  Wire.write(0x00);
  Wire.endTransmission();
  delay(20);
}

void printHex2(uint8_t v)
{
  if (v < 0x10) Serial.print('0');
  Serial.print(v, HEX);
}

void readBasicRegisters()
{
  const uint8_t regs[] = {0x00, 0x02, 0x03, 0x7C, 0x7D};
  const char *names[] = {"CHIP_ID", "ERR_REG", "STATUS", "PWR_CONF", "PWR_CTRL"};

  Serial.println("Basic register snapshot:");
  for (size_t i = 0; i < sizeof(regs); i++)
  {
    uint8_t v = 0;
    bool ok = readReg8(BMI_ADDR, regs[i], v);
    Serial.print("  ");
    Serial.print(names[i]);
    Serial.print(" (0x");
    printHex2(regs[i]);
    Serial.print(") = ");
    if (!ok)
    {
      Serial.println("READ FAIL");
    }
    else
    {
      Serial.print("0x");
      printHex2(v);
      Serial.println();
    }
  }
}

void stressRead(uint32_t hz, int count)
{
  Wire.setClock(hz);
  delay(20);

  int ok = 0;
  int fail = 0;
  int id24 = 0;
  int id27 = 0;
  int other = 0;

  Serial.println();
  Serial.print("=== CHIP_ID stress @ ");
  Serial.print(hz);
  Serial.print(" Hz, count=");
  Serial.print(count);
  Serial.println(" ===");

  for (int i = 0; i < count; i++)
  {
    uint8_t id = 0;
    if (!readReg8(BMI_ADDR, 0x00, id))
    {
      fail++;
    }
    else
    {
      ok++;
      if (id == 0x24) id24++;
      else if (id == 0x27) id27++;
      else other++;
    }

    if ((i + 1) % 100 == 0)
    {
      Serial.print("progress ");
      Serial.print(i + 1);
      Serial.print(" / ");
      Serial.println(count);
    }

    delay(2);
  }

  Serial.println("Result:");
  Serial.print("  OK      = "); Serial.println(ok);
  Serial.print("  FAIL    = "); Serial.println(fail);
  Serial.print("  0x24    = "); Serial.println(id24);
  Serial.print("  0x27    = "); Serial.println(id27);
  Serial.print("  OTHER   = "); Serial.println(other);
}

void setup()
{
  Serial.begin(115200);
  delay(3000);

  Serial.println();
  Serial.println("ESP32-S3 IMU I2C solder / identity stress test");
  Serial.println("SDA=GPIO21, SCL=GPIO18, target=0x68");
  Serial.println("Do not rework the IMU during this test.");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  delay(100);

  pcaOff();
  readBasicRegisters();

  stressRead(100000, 1000);
  stressRead(400000, 1000);

  Wire.setClock(100000);
  readBasicRegisters();

  Serial.println();
  Serial.println("TEST FINISHED");
  Serial.println("If FAIL=0 and CHIP_ID remains one stable value at both speeds,");
  Serial.println("the active I2C/power solder connections are likely stable.");
  Serial.println("Power-cycle the whole board and run again for a second pass.");
}

void loop()
{
  delay(1000);
}
