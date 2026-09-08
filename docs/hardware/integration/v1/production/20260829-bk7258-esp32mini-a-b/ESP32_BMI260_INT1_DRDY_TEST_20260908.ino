#include <Wire.h>
#include "bmi260_config.h"

// Silver-age smart-care B1 / current mounted IMU behaves as BMI260 (CHIP_ID 0x27)
// Purpose: verify the physical INT1 line from IMU to ESP32-S3 GPIO33.
// This is a DATA READY interrupt test, not a BMI270 identity test.

static const int SDA_PIN = 21;
static const int SCL_PIN = 18;
static const int IMU_INT1_PIN = 33;

static const uint8_t IMU_ADDR = 0x68;
static const uint8_t PCA_ADDR = 0x70;

static const uint8_t REG_CHIP_ID         = 0x00;
static const uint8_t REG_INT_STATUS_1    = 0x1D;
static const uint8_t REG_INTERNAL_STATUS = 0x21;
static const uint8_t REG_ACC_X_LSB       = 0x0C;
static const uint8_t REG_ACC_CONF        = 0x40;
static const uint8_t REG_ACC_RANGE       = 0x41;
static const uint8_t REG_GYR_CONF        = 0x42;
static const uint8_t REG_GYR_RANGE       = 0x43;
static const uint8_t REG_INT1_IO_CTRL    = 0x53;
static const uint8_t REG_INT_LATCH       = 0x55;
static const uint8_t REG_INT_MAP_DATA    = 0x58;
static const uint8_t REG_INIT_CTRL       = 0x59;
static const uint8_t REG_INIT_ADDR_0     = 0x5B;
static const uint8_t REG_INIT_DATA       = 0x5E;
static const uint8_t REG_PWR_CONF        = 0x7C;
static const uint8_t REG_PWR_CTRL        = 0x7D;
static const uint8_t REG_CMD             = 0x7E;

static const uint8_t BMI260_CHIP_ID = 0x27;
static const uint8_t CMD_SOFT_RESET = 0xB6;
static const uint8_t INIT_OK = 0x01;
static const size_t CONFIG_CHUNK = 16;

volatile uint32_t irqCount = 0;

void IRAM_ATTR onImuInt1()
{
  irqCount++;
}

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

  pwrConf &= ~0x01; // disable advanced power save during config upload
  if (!writeReg(REG_PWR_CONF, pwrConf)) return false;
  delayMicroseconds(500);

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
    uint8_t addrBytes[2] = {
      (uint8_t)(wordAddr & 0x0F),
      (uint8_t)(wordAddr >> 4)
    };

    if (!writeBytes(REG_INIT_ADDR_0, addrBytes, 2)) return false;
    delayMicroseconds(20);

    if (!writeBytes(REG_INIT_DATA, bmi260_config_file + index, chunk)) return false;
    delayMicroseconds(20);
  }

  if (!writeReg(REG_INIT_CTRL, 0x01)) return false;
  delay(25);

  for (int i = 0; i < 100; i++)
  {
    uint8_t status = 0;
    if (!readReg(REG_INTERNAL_STATUS, status)) return false;
    if ((status & 0x0F) == INIT_OK) return true;
    delay(5);
  }

  return false;
}

bool configureSensors()
{
  // Accel: 100 Hz, +/-2 g
  if (!writeReg(REG_ACC_CONF, 0xA8)) return false;
  delayMicroseconds(500);
  if (!writeReg(REG_ACC_RANGE, 0x00)) return false;
  delayMicroseconds(500);

  // Gyro: 100 Hz, +/-2000 dps
  if (!writeReg(REG_GYR_CONF, 0xE8)) return false;
  delayMicroseconds(500);
  if (!writeReg(REG_GYR_RANGE, 0x00)) return false;
  delayMicroseconds(500);

  // Enable gyro + accel
  if (!writeReg(REG_PWR_CTRL, 0x06)) return false;
  delay(50);

  return true;
}

bool configureInt1DataReady()
{
  // INT1_IO_CTRL 0x53:
  // bit1 lvl=1       -> active high
  // bit2 od=0        -> push-pull
  // bit3 output_en=1 -> output enabled
  // => 0b00001010 = 0x0A
  if (!writeReg(REG_INT1_IO_CTRL, 0x0A)) return false;

  // Non-latched interrupt
  if (!writeReg(REG_INT_LATCH, 0x00)) return false;

  // INT_MAP_DATA 0x58 bit2 = DRDY mapped to INT1
  if (!writeReg(REG_INT_MAP_DATA, 0x04)) return false;

  delay(20);
  return true;
}

void printReg(const char *name, uint8_t reg)
{
  uint8_t value = 0;
  Serial.print(name);
  Serial.print(" = ");
  if (!readReg(reg, value))
  {
    Serial.println("READ FAIL");
    return;
  }
  Serial.print("0x");
  if (value < 0x10) Serial.print('0');
  Serial.println(value, HEX);
}

void setup()
{
  Serial.begin(115200);
  delay(2500);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 + BMI260 INT1 DRDY TEST");
  Serial.println("SDA=GPIO21 SCL=GPIO18 INT1=GPIO33");
  Serial.println("========================================");

  pinMode(IMU_INT1_PIN, INPUT);

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000);
  delay(100);

  pcaOff();
  Serial.println("PCA9540B CH0/CH1 = OFF");

  uint8_t id = 0;
  if (!readReg(REG_CHIP_ID, id)) stopWith("CHIP_ID read failed");

  Serial.print("CHIP_ID = 0x");
  if (id < 0x10) Serial.print('0');
  Serial.println(id, HEX);

  if (id != BMI260_CHIP_ID)
    stopWith("Current device is not CHIP_ID 0x27; do not run BMI260 config.");

  if (!writeReg(REG_CMD, CMD_SOFT_RESET)) stopWith("Soft reset failed");
  delay(5);

  if (!uploadBMI260Config()) stopWith("BMI260 config load FAILED");
  Serial.println("BMI260 config load PASS");

  if (!configureSensors()) stopWith("Accel/Gyro setup FAILED");
  Serial.println("Accel/Gyro setup PASS");

  if (!configureInt1DataReady()) stopWith("INT1 DRDY setup FAILED");
  Serial.println("INT1 DRDY setup PASS");

  printReg("INT1_IO_CTRL", REG_INT1_IO_CTRL);
  printReg("INT_LATCH", REG_INT_LATCH);
  printReg("INT_MAP_DATA", REG_INT_MAP_DATA);

  attachInterrupt(
    digitalPinToInterrupt(IMU_INT1_PIN),
    onImuInt1,
    RISING
  );

  Serial.println();
  Serial.println("Interrupt counter starts now.");
  Serial.println("Expected: GPIO33 receives repeated interrupts while IMU runs.");
  Serial.println("A roughly steady non-zero count each second = physical INT1 path works.");
  Serial.println();
}

void loop()
{
  static uint32_t lastMs = 0;
  static uint32_t lastCount = 0;

  // Keep reading sensor data so the normal data path is active.
  uint8_t data[12];
  readBytes(REG_ACC_X_LSB, data, sizeof(data));

  if (millis() - lastMs >= 1000)
  {
    lastMs += 1000;

    noInterrupts();
    uint32_t nowCount = irqCount;
    interrupts();

    uint32_t perSec = nowCount - lastCount;
    lastCount = nowCount;

    uint8_t intStatus1 = 0;
    readReg(REG_INT_STATUS_1, intStatus1);

    int pinLevel = digitalRead(IMU_INT1_PIN);

    Serial.print("INT1 IRQ/s = ");
    Serial.print(perSec);
    Serial.print(" | total = ");
    Serial.print(nowCount);
    Serial.print(" | GPIO33 = ");
    Serial.print(pinLevel);
    Serial.print(" | INT_STATUS_1 = 0x");
    if (intStatus1 < 0x10) Serial.print('0');
    Serial.println(intStatus1, HEX);

    if (nowCount == 0 && millis() > 5000)
    {
      Serial.println("WARNING: no INT1 edge seen yet.");
    }
  }
}
