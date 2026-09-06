#include <Wire.h>
#include "SparkFun_BMI270_Arduino_Library.h"
#include <math.h>

// Silver-age care ESP32-S3 production-board SENSOR_I2C mapping
// SENSOR_I2C_SDA -> GPIO21
// SENSOR_I2C_SCL -> GPIO18
static const int SDA_PIN = 21;
static const int SCL_PIN = 18;

static const uint8_t BMI270_ADDR_0 = 0x68;
static const uint8_t BMI270_ADDR_1 = 0x69;
static const uint8_t BMI270_CHIP_ID_REG = 0x00;
static const uint8_t BMI270_EXPECTED_ID = 0x24;

BMI270 imu;
uint8_t bmiAddress = 0;
unsigned long sampleCount = 0;

bool i2cDeviceExists(uint8_t addr)
{
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

bool readRegister(uint8_t addr, uint8_t reg, uint8_t &value)
{
  Wire.beginTransmission(addr);
  Wire.write(reg);

  if (Wire.endTransmission(false) != 0)
    return false;

  if (Wire.requestFrom((int)addr, 1) != 1)
    return false;

  value = Wire.read();
  return true;
}

void scanI2C()
{
  Serial.println();
  Serial.println("===== I2C SCAN =====");

  int count = 0;
  for (uint8_t addr = 1; addr < 127; addr++)
  {
    Wire.beginTransmission(addr);
    uint8_t error = Wire.endTransmission();

    if (error == 0)
    {
      Serial.print("Found I2C device: 0x");
      if (addr < 0x10)
        Serial.print("0");
      Serial.println(addr, HEX);
      count++;
    }
  }

  if (count == 0)
    Serial.println("No I2C devices found.");

  Serial.println("====================");
}

uint8_t findBMI270Address()
{
  if (i2cDeviceExists(BMI270_ADDR_0))
    return BMI270_ADDR_0;

  if (i2cDeviceExists(BMI270_ADDR_1))
    return BMI270_ADDR_1;

  return 0;
}

void fatalStop(const char *message)
{
  Serial.println();
  Serial.println("===== BMI270 TEST FAIL =====");
  Serial.println(message);
  Serial.println("Check BMI270 power, SDA/SCL soldering, pull-ups and address strap.");

  while (true)
    delay(1000);
}

void setup()
{
  Serial.begin(115200);
  delay(3000);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 + BMI270 FULL BRING-UP TEST");
  Serial.println("SDA = GPIO21, SCL = GPIO18");
  Serial.println("========================================");

  // First board bring-up stays at 100 kHz for margin.
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);

  scanI2C();

  bmiAddress = findBMI270Address();
  if (bmiAddress == 0)
    fatalStop("No device responded at BMI270 address 0x68 or 0x69.");

  Serial.print("BMI270 candidate address: 0x");
  Serial.println(bmiAddress, HEX);

  // Verify the identity register before running the full library init.
  uint8_t chipId = 0;
  if (!readRegister(bmiAddress, BMI270_CHIP_ID_REG, chipId))
    fatalStop("Failed to read CHIP_ID register 0x00.");

  Serial.print("CHIP_ID = 0x");
  if (chipId < 0x10)
    Serial.print("0");
  Serial.println(chipId, HEX);

  if (chipId != BMI270_EXPECTED_ID)
    fatalStop("CHIP_ID is not 0x24; responding device is not confirmed as BMI270.");

  Serial.println("CHIP_ID PASS: BMI270 identity confirmed.");

  // SparkFun BMI270 Arduino Library initialization.
  int8_t err = imu.beginI2C(bmiAddress, Wire);
  if (err != BMI2_OK)
  {
    Serial.print("imu.beginI2C() failed, error code = ");
    Serial.println(err);
    fatalStop("BMI270 library initialization failed.");
  }

  Serial.println("Library init PASS.");
  Serial.println();
  Serial.println("Live data starts now.");
  Serial.println("Keep the board still first: |A| should be roughly 1 g and gyro should be near 0 dps.");
  Serial.println("Then rotate/tilt the board and confirm the six axes change continuously.");
  Serial.println();
}

void loop()
{
  int8_t err = imu.getSensorData();
  if (err != BMI2_OK)
  {
    Serial.print("getSensorData() error = ");
    Serial.println(err);
    delay(500);
    return;
  }

  float tempC = NAN;
  int8_t tempErr = imu.getTemperature(&tempC);

  float ax = imu.data.accelX;
  float ay = imu.data.accelY;
  float az = imu.data.accelZ;
  float gx = imu.data.gyroX;
  float gy = imu.data.gyroY;
  float gz = imu.data.gyroZ;
  float aMag = sqrtf(ax * ax + ay * ay + az * az);

  Serial.print("#");
  Serial.print(sampleCount++);

  Serial.print("  ACC[g] X=");
  Serial.print(ax, 3);
  Serial.print(" Y=");
  Serial.print(ay, 3);
  Serial.print(" Z=");
  Serial.print(az, 3);
  Serial.print(" |A|=");
  Serial.print(aMag, 3);

  Serial.print("  GYRO[dps] X=");
  Serial.print(gx, 2);
  Serial.print(" Y=");
  Serial.print(gy, 2);
  Serial.print(" Z=");
  Serial.print(gz, 2);

  if (tempErr == BMI2_OK)
  {
    Serial.print("  T=");
    Serial.print(tempC, 1);
    Serial.print("C");
  }
  else
  {
    Serial.print("  T=ERR(");
    Serial.print(tempErr);
    Serial.print(")");
  }

  Serial.println();
  delay(200);
}
