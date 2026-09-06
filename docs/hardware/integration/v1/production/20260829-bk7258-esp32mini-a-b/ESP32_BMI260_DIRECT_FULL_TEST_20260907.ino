#include <Wire.h>
#include <math.h>
#include "bmi260_config.h"

static const int SDA_PIN = 21;
static const int SCL_PIN = 18;
static const uint8_t IMU_ADDR = 0x68;
static const uint8_t PCA_ADDR = 0x70;

static const uint8_t REG_CHIP_ID         = 0x00;
static const uint8_t REG_INTERNAL_STATUS = 0x21;
static const uint8_t REG_ACC_X_LSB       = 0x0C;
static const uint8_t REG_ACC_CONF        = 0x40;
static const uint8_t REG_ACC_RANGE       = 0x41;
static const uint8_t REG_GYR_CONF        = 0x42;
static const uint8_t REG_GYR_RANGE       = 0x43;
static const uint8_t REG_INIT_CTRL       = 0x59;
static const uint8_t REG_INIT_ADDR_0     = 0x5B;
static const uint8_t REG_INIT_ADDR_1     = 0x5C;
static const uint8_t REG_INIT_DATA       = 0x5E;
static const uint8_t REG_PWR_CONF        = 0x7C;
static const uint8_t REG_PWR_CTRL        = 0x7D;
static const uint8_t REG_CMD             = 0x7E;

static const uint8_t BMI260_CHIP_ID      = 0x27;
static const uint8_t CMD_SOFT_RESET      = 0xB6;
static const uint8_t INIT_OK             = 0x01;
static const size_t CONFIG_CHUNK         = 16;

bool writeReg(uint8_t reg, uint8_t value)
{
  Wire.beginTransmission(IMU_ADDR);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

bool writeBytes(uint8_t reg, const uint8_t *data, size_t len)
{
  Wire.beginTransmission(IMU_ADDR);
  Wire.write(reg);
  for (size_t i = 0; i < len; i++) Wire.write(data[i]);
  return Wire.endTransmission() == 0;
}

bool readReg(uint8_t reg, uint8_t &value)
{
  Wire.beginTransmission(IMU_ADDR);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(IMU_ADDR, (uint8_t)1) != 1) return false;
  value = Wire.read();
  return true;
}

bool readBytes(uint8_t reg, uint8_t *data, size_t len)
{
  Wire.beginTransmission(IMU_ADDR);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(IMU_ADDR, (uint8_t)len) != len) return false;
  for (size_t i = 0; i < len; i++) data[i] = Wire.read();
  return true;
}

bool i2cAck(uint8_t addr)
{
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

void pcaOff()
{
  Wire.beginTransmission(PCA_ADDR);
  Wire.write(0x00);
  Wire.endTransmission();
  delay(20);
}

void stopWith(const char *msg)
{
  Serial.println();
  Serial.println("========== TEST STOP ==========");
  Serial.println(msg);
  while (true) delay(1000);
}

bool uploadBMI260Config()
{
  uint8_t pwrConf = 0;
  if (!readReg(REG_PWR_CONF, pwrConf)) return false;

  // Disable advanced power save before config upload.
  pwrConf &= ~0x01;
  if (!writeReg(REG_PWR_CONF, pwrConf)) return false;
  delayMicroseconds(500);

  // Prepare config loading.
  if (!writeReg(REG_INIT_CTRL, 0x00)) return false;
  delayMicroseconds(500);

  const size_t configSize = sizeof(bmi260_config_file);
  Serial.print("BMI260 config bytes = ");
  Serial.println(configSize);

  for (size_t index = 0; index < configSize; index += CONFIG_CHUNK)
  {
    size_t chunk = CONFIG_CHUNK;
    if (index + chunk > configSize) chunk = configSize - index;

    uint16_t wordAddr = (uint16_t)(index / 2);
    uint8_t addrBytes[2];
    addrBytes[0] = (uint8_t)(wordAddr & 0x0F);
    addrBytes[1] = (uint8_t)(wordAddr >> 4);

    if (!writeBytes(REG_INIT_ADDR_0, addrBytes, 2)) return false;
    delayMicroseconds(20);

    if (!writeBytes(REG_INIT_DATA, bmi260_config_file + index, chunk)) return false;
    delayMicroseconds(20);

    if ((index % 1024) == 0)
    {
      Serial.print("Config progress: ");
      Serial.print(index);
      Serial.print(" / ");
      Serial.println(configSize);
    }
  }

  // Complete config loading.
  if (!writeReg(REG_INIT_CTRL, 0x01)) return false;
  delay(25);

  for (int i = 0; i < 100; i++)
  {
    uint8_t status = 0;
    if (!readReg(REG_INTERNAL_STATUS, status)) return false;

    Serial.print("INTERNAL_STATUS = 0x");
    if (status < 0x10) Serial.print('0');
    Serial.println(status, HEX);

    if ((status & 0x0F) == INIT_OK) return true;
    delay(5);
  }

  return false;
}

bool configureSensors()
{
  // Accel: 100 Hz, normal bandwidth, high performance filter, +/-2 g.
  if (!writeReg(REG_ACC_CONF, 0xA8)) return false;
  delayMicroseconds(500);
  if (!writeReg(REG_ACC_RANGE, 0x00)) return false;
  delayMicroseconds(500);

  // Gyro: 100 Hz, normal bandwidth, high-performance noise/filter, +/-2000 dps.
  if (!writeReg(REG_GYR_CONF, 0xE8)) return false;
  delayMicroseconds(500);
  if (!writeReg(REG_GYR_RANGE, 0x00)) return false;
  delayMicroseconds(500);

  // Enable gyro + accel. Bits: GYR_EN=bit1, ACC_EN=bit2.
  if (!writeReg(REG_PWR_CTRL, 0x06)) return false;
  delay(50);

  return true;
}

void setup()
{
  Serial.begin(115200);
  delay(3000);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 + BMI260 DIRECT FUNCTION TEST");
  Serial.println("SDA=GPIO21  SCL=GPIO18  IMU=0x68");
  Serial.println("========================================");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000);
  delay(100);

  pcaOff();
  Serial.println("PCA9540B CH0/CH1 = OFF");

  if (!i2cAck(IMU_ADDR)) stopWith("0x68 NO ACK");

  uint8_t id = 0;
  if (!readReg(REG_CHIP_ID, id)) stopWith("CHIP_ID read failed");

  Serial.print("CHIP_ID before reset = 0x");
  if (id < 0x10) Serial.print('0');
  Serial.println(id, HEX);

  if (id != BMI260_CHIP_ID)
    stopWith("CHIP_ID is not 0x27, so do not run BMI260 config.");

  Serial.println("CHIP_ID 0x27 PASS");

  Serial.println("Soft reset...");
  if (!writeReg(REG_CMD, CMD_SOFT_RESET)) stopWith("Soft reset write failed");
  delay(5);

  if (!readReg(REG_CHIP_ID, id)) stopWith("CHIP_ID read after reset failed");
  Serial.print("CHIP_ID after reset = 0x");
  if (id < 0x10) Serial.print('0');
  Serial.println(id, HEX);

  if (id != BMI260_CHIP_ID)
    stopWith("CHIP_ID changed after reset");

  Serial.println();
  Serial.println("Uploading BMI260 8192-byte config...");

  if (!uploadBMI260Config())
    stopWith("BMI260 config load FAILED");

  Serial.println("BMI260 config load PASS");

  if (!configureSensors())
    stopWith("Accel/Gyro configuration FAILED");

  Serial.println("Accel/Gyro enable PASS");
  Serial.println();
  Serial.println("LIVE DATA START");
  Serial.println("Keep board still: |A| should be around 1 g, gyro near 0 dps.");
  Serial.println("Then tilt/rotate board and confirm values change.");
  Serial.println();
}

void loop()
{
  uint8_t buf[12];
  if (!readBytes(REG_ACC_X_LSB, buf, sizeof(buf)))
  {
    Serial.println("DATA READ FAIL");
    delay(500);
    return;
  }

  int16_t axRaw = (int16_t)((uint16_t)buf[1]  << 8 | buf[0]);
  int16_t ayRaw = (int16_t)((uint16_t)buf[3]  << 8 | buf[2]);
  int16_t azRaw = (int16_t)((uint16_t)buf[5]  << 8 | buf[4]);
  int16_t gxRaw = (int16_t)((uint16_t)buf[7]  << 8 | buf[6]);
  int16_t gyRaw = (int16_t)((uint16_t)buf[9]  << 8 | buf[8]);
  int16_t gzRaw = (int16_t)((uint16_t)buf[11] << 8 | buf[10]);

  float ax = axRaw / 16384.0f;
  float ay = ayRaw / 16384.0f;
  float az = azRaw / 16384.0f;

  float gx = gxRaw / 16.384f;
  float gy = gyRaw / 16.384f;
  float gz = gzRaw / 16.384f;

  float aMag = sqrtf(ax * ax + ay * ay + az * az);

  Serial.print("ACC[g] X="); Serial.print(ax, 3);
  Serial.print(" Y="); Serial.print(ay, 3);
  Serial.print(" Z="); Serial.print(az, 3);
  Serial.print(" |A|="); Serial.print(aMag, 3);

  Serial.print("   GYRO[dps] X="); Serial.print(gx, 2);
  Serial.print(" Y="); Serial.print(gy, 2);
  Serial.print(" Z="); Serial.println(gz, 2);

  delay(200);
}
