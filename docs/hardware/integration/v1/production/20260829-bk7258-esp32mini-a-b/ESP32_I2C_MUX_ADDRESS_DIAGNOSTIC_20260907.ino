#include <Wire.h>

// Silver-age care production board
// SENSOR_I2C_SDA -> ESP32-S3 GPIO21
// SENSOR_I2C_SCL -> ESP32-S3 GPIO18
static const int SDA_PIN = 21;
static const int SCL_PIN = 18;

// Upstream devices / expected addresses
static const uint8_t PCA9540B_ADDR = 0x70;
static const uint8_t BMI_ADDR_LOW  = 0x68;
static const uint8_t BMI_ADDR_HIGH = 0x69;
static const uint8_t DRV2605L_ADDR = 0x5A;
static const uint8_t CHIP_ID_REG   = 0x00;

// PCA9540B control values
static const uint8_t PCA_NONE = 0x00;
static const uint8_t PCA_CH0  = 0x04;
static const uint8_t PCA_CH1  = 0x05;

bool i2cAck(uint8_t addr)
{
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

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

bool pcaSelect(uint8_t control)
{
  Wire.beginTransmission(PCA9540B_ADDR);
  Wire.write(control);
  return Wire.endTransmission() == 0;
}

bool pcaReadControl(uint8_t &control)
{
  if (Wire.requestFrom((uint8_t)PCA9540B_ADDR, (uint8_t)1) != 1) return false;
  if (!Wire.available()) return false;
  control = Wire.read();
  return true;
}

void printHex2(uint8_t v)
{
  if (v < 0x10) Serial.print('0');
  Serial.print(v, HEX);
}

void scanBus()
{
  int count = 0;
  Serial.println("I2C scan:");

  for (uint8_t addr = 1; addr < 127; ++addr)
  {
    if (i2cAck(addr))
    {
      Serial.print("  ACK 0x");
      printHex2(addr);

      if (addr == PCA9540B_ADDR) Serial.print("  <- PCA9540B");
      if (addr == DRV2605L_ADDR) Serial.print("  <- DRV2605L expected address");
      if (addr == BMI_ADDR_LOW || addr == BMI_ADDR_HIGH) Serial.print("  <- BMI-family candidate");

      Serial.println();
      ++count;
    }
  }

  if (count == 0) Serial.println("  no devices");
}

void readChipIdRepeated(uint8_t addr, int times)
{
  Serial.print("CHIP_ID test at 0x");
  printHex2(addr);
  Serial.println(":");

  if (!i2cAck(addr))
  {
    Serial.println("  no ACK");
    return;
  }

  int ok = 0;
  int id24 = 0;
  int id27 = 0;
  int other = 0;

  for (int i = 0; i < times; ++i)
  {
    uint8_t id = 0;
    bool r = readReg8(addr, CHIP_ID_REG, id);

    Serial.print("  #");
    Serial.print(i);
    Serial.print(" -> ");

    if (!r)
    {
      Serial.println("READ FAIL");
    }
    else
    {
      ++ok;
      Serial.print("0x");
      printHex2(id);

      if (id == 0x24)
      {
        ++id24;
        Serial.print("  BMI270 ID");
      }
      else if (id == 0x27)
      {
        ++id27;
        Serial.print("  BMI260-family ID value");
      }
      else
      {
        ++other;
      }

      Serial.println();
    }

    delay(20);
  }

  Serial.print("  summary: ok=");
  Serial.print(ok);
  Serial.print("  0x24=");
  Serial.print(id24);
  Serial.print("  0x27=");
  Serial.print(id27);
  Serial.print("  other=");
  Serial.println(other);
}

void runState(const char *name, uint8_t control)
{
  Serial.println();
  Serial.println("================================================");
  Serial.print("STATE: ");
  Serial.println(name);
  Serial.println("================================================");

  if (!pcaSelect(control))
  {
    Serial.println("PCA9540B write FAILED");
  }
  else
  {
    delay(20);
    uint8_t rb = 0;
    if (pcaReadControl(rb))
    {
      Serial.print("PCA9540B control readback = 0x");
      printHex2(rb);
      Serial.println();
    }
  }

  scanBus();

  // Check both legal BMI270 I2C addresses every time.
  readChipIdRepeated(BMI_ADDR_LOW, 10);
  readChipIdRepeated(BMI_ADDR_HIGH, 10);

  if (i2cAck(DRV2605L_ADDR))
    Serial.println("0x5A is present on this reachable bus state.");
  else
    Serial.println("0x5A not present (expected now because both DRV2605L chips are removed).");
}

void setup()
{
  Serial.begin(115200);
  delay(3000);

  Serial.println();
  Serial.println("ESP32-S3 SENSOR_I2C / PCA9540B / BMI address diagnostic");
  Serial.println("SDA GPIO21, SCL GPIO18, 100 kHz");
  Serial.println("Expected BMI270 addresses: 0x68 (SDO low) or 0x69 (SDO high)");
  Serial.println("Expected BMI270 CHIP_ID register 0x00 = 0x24");
  Serial.println("PCA9540B: 0x70, NONE=0x00, CH0=0x04, CH1=0x05");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  delay(100);

  if (!i2cAck(PCA9540B_ADDR))
  {
    Serial.println("ERROR: PCA9540B 0x70 does not ACK.");
  }

  // Most important state first: isolate both downstream branches.
  runState("MUX OFF / both downstream channels disconnected", PCA_NONE);

  // Then explicitly expose each branch one at a time.
  runState("MUX CH0 selected", PCA_CH0);
  runState("MUX CH1 selected", PCA_CH1);

  // Return to isolated state.
  pcaSelect(PCA_NONE);

  Serial.println();
  Serial.println("DONE. Interpret by comparing whether 0x68/0x69 and CHIP_ID change between states.");
}

void loop()
{
  delay(1000);
}
