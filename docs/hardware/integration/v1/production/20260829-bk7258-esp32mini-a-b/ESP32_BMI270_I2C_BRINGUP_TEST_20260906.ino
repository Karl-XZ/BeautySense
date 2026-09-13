#include <Wire.h>
#include <7Semi_BMI270.h>
#include <math.h>

// Production-board SENSOR_I2C mapping
// SENSOR_I2C_SDA -> ESP32-S3 GPIO21
// SENSOR_I2C_SCL -> ESP32-S3 GPIO18
static const int SDA_PIN = 21;
static const int SCL_PIN = 18;

static const uint8_t BMI270_ADDR_0 = 0x68;
static const uint8_t BMI270_ADDR_1 = 0x69;
static const uint8_t BMI270_CHIP_ID_REG = 0x00;
static const uint8_t BMI270_EXPECTED_ID = 0x24;

BMI270_7Semi imu;
uint8_t bmiAddress = 0;
unsigned long sampleCount = 0;

bool i2cExists(uint8_t addr)
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
  Serial.println("========== I2C SCAN ==========");

  int count = 0;
  for (uint8_t addr = 1; addr < 127; ++addr)
  {
    Wire.beginTransmission(addr);
    uint8_t err = Wire.endTransmission();
    if (err == 0)
    {
      Serial.print("Found device: 0x");
      if (addr < 0x10) Serial.print("0");
      Serial.println(addr, HEX);
      ++count;
    }
  }

  if (count == 0)
    Serial.println("No I2C devices found!");

  Serial.println("==============================");
}

uint8_t findBMI270Address()
{
  if (i2cExists(BMI270_ADDR_0)) return BMI270_ADDR_0;
  if (i2cExists(BMI270_ADDR_1)) return BMI270_ADDR_1;
  return 0;
}

void fatalStop(const char *message)
{
  Serial.println();
  Serial.println("========== BMI270 FAIL ==========");
  Serial.println(message);
  while (true) delay(1000);
}

void setup()
{
  Serial.begin(115200);
  delay(3000);

  Serial.println();
  Serial.println("=======================================");
  Serial.println("ESP32-S3 + BMI270 FULL TEST");
  Serial.println("Library: 7Semi BMI270 1.0.0");
  Serial.println("SDA = GPIO21");
  Serial.println("SCL = GPIO18");
  Serial.println("=======================================");

  // Stage 1: raw I2C test at 100 kHz.
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  delay(100);

  scanI2C();

  bmiAddress = findBMI270Address();
  if (bmiAddress == 0)
    fatalStop("No response at 0x68 or 0x69.");

  Serial.print("BMI270 candidate address: 0x");
  Serial.println(bmiAddress, HEX);

  uint8_t chipId = 0;
  if (!readRegister(bmiAddress, BMI270_CHIP_ID_REG, chipId))
    fatalStop("Raw CHIP_ID read failed.");

  Serial.print("Raw CHIP_ID = 0x");
  if (chipId < 0x10) Serial.print("0");
  Serial.println(chipId, HEX);

  if (chipId != BMI270_EXPECTED_ID)
    fatalStop("Raw CHIP_ID is not 0x24.");

  Serial.println("Raw CHIP_ID PASS.");

  // Important for this 7Semi wrapper:
  // imu.begin(cfg) calls Wire.begin() internally. End the temporary scanner bus
  // first so the library owns the bus cleanly during BMI270 config upload.
  Wire.end();
  delay(100);

  BMI270_7Semi::Config cfg;
  cfg.bus = BMI270_7Semi::Bus::I2C;
  cfg.i2c = &Wire;
  cfg.addr = bmiAddress;
  cfg.sda = SDA_PIN;
  cfg.scl = SCL_PIN;
  cfg.i2cHz = 400000; // Match the 7Semi official I2C example for full init.

  Serial.println();
  Serial.println("Starting 7Semi BMI270 full initialization...");

  if (!imu.begin(cfg))
  {
    Serial.println("7Semi imu.begin() FAILED.");
    Serial.println("Raw I2C and CHIP_ID already passed, so do not immediately rework the PCB.");
    Serial.println("Next suspect: 7Semi/Bosch full config-load initialization path or bus integrity during long transfer.");
    while (true) delay(1000);
  }

  Serial.println("7Semi imu.begin() PASS.");

  uint8_t id = imu.chipId();
  Serial.print("Library CHIP_ID = 0x");
  if (id < 0x10) Serial.print("0");
  Serial.println(id, HEX);

  int8_t err = imu.setAccelConfig(
    BMI2_ACC_ODR_100HZ,
    BMI2_ACC_RANGE_2G,
    BMI2_ACC_NORMAL_AVG4,
    BMI2_PERF_OPT_MODE
  );
  if (err != BMI2_OK)
    fatalStop("Accelerometer config failed.");

  err = imu.setGyroConfig(
    BMI2_GYR_ODR_100HZ,
    BMI2_GYR_RANGE_2000,
    BMI2_GYR_NORMAL_MODE,
    BMI2_PERF_OPT_MODE
  );
  if (err != BMI2_OK)
    fatalStop("Gyroscope config failed.");

  imu.enableTemp(true);

  Serial.println();
  Serial.println("===== BMI270 BASIC TEST PASS =====");
  Serial.println("Keep board still first: |A| ~ 1 g, gyro near 0 dps.");
  Serial.println();
}

void loop()
{
  float ax, ay, az;
  float gx, gy, gz;
  float tempC;

  if (!imu.readAccel(ax, ay, az))
  {
    Serial.println("readAccel failed");
    delay(500);
    return;
  }

  if (!imu.readGyro(gx, gy, gz))
  {
    Serial.println("readGyro failed");
    delay(500);
    return;
  }

  int8_t tempErr = imu.readTemperatureC(tempC);
  float aMag = sqrtf(ax * ax + ay * ay + az * az);

  Serial.print("#");
  Serial.print(sampleCount++);
  Serial.print(" ACC[g] X=");
  Serial.print(ax, 3);
  Serial.print(" Y=");
  Serial.print(ay, 3);
  Serial.print(" Z=");
  Serial.print(az, 3);
  Serial.print(" |A|=");
  Serial.print(aMag, 3);

  Serial.print(" GYRO[dps] X=");
  Serial.print(gx, 2);
  Serial.print(" Y=");
  Serial.print(gy, 2);
  Serial.print(" Z=");
  Serial.print(gz, 2);

  if (tempErr == BMI2_OK)
  {
    Serial.print(" TEMP=");
    Serial.print(tempC, 1);
    Serial.print("C");
  }
  else
  {
    Serial.print(" TEMP_ERR=");
    Serial.print(tempErr);
  }

  Serial.println();
  delay(200);
}
