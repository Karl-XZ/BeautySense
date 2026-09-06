#include <Wire.h>

// TODO: Replace these with the actual ESP32-S3 GPIOs connected to SENSOR_I2C.
#define SDA_PIN  8
#define SCL_PIN  9

#define BMI270_ADDR_0  0x68
#define BMI270_ADDR_1  0x69
#define BMI270_CHIP_ID_REG  0x00
#define BMI270_EXPECTED_ID  0x24

bool i2cDeviceExists(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

bool readRegister(uint8_t addr, uint8_t reg, uint8_t &value) {
  Wire.beginTransmission(addr);
  Wire.write(reg);

  if (Wire.endTransmission(false) != 0) {
    return false;
  }

  if (Wire.requestFrom((int)addr, 1) != 1) {
    return false;
  }

  value = Wire.read();
  return true;
}

void scanI2C() {
  Serial.println();
  Serial.println("===== I2C SCAN =====");

  int count = 0;
  for (uint8_t addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    uint8_t error = Wire.endTransmission();

    if (error == 0) {
      Serial.print("Found I2C device: 0x");
      if (addr < 0x10) Serial.print("0");
      Serial.println(addr, HEX);
      count++;
    }
  }

  if (count == 0) {
    Serial.println("No I2C devices found.");
  }
  Serial.println("====================");
}

bool testBMI270(uint8_t addr) {
  if (!i2cDeviceExists(addr)) return false;

  Serial.print("Device responds at 0x");
  Serial.println(addr, HEX);

  uint8_t chipID = 0;
  if (!readRegister(addr, BMI270_CHIP_ID_REG, chipID)) {
    Serial.println("Failed to read CHIP_ID.");
    return false;
  }

  Serial.print("CHIP_ID = 0x");
  if (chipID < 0x10) Serial.print("0");
  Serial.println(chipID, HEX);

  if (chipID == BMI270_EXPECTED_ID) {
    Serial.println("BMI270 PASS");
    return true;
  }

  Serial.println("Device found, but CHIP_ID is not BMI270.");
  return false;
}

void setup() {
  Serial.begin(115200);
  delay(3000);

  Serial.println();
  Serial.println("BMI270 Bring-up Test");
  Serial.println("====================");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);

  scanI2C();

  Serial.println();
  Serial.println("Testing BMI270...");

  bool pass = false;
  if (testBMI270(BMI270_ADDR_0)) {
    pass = true;
  } else if (testBMI270(BMI270_ADDR_1)) {
    pass = true;
  }

  Serial.println();
  if (pass) {
    Serial.println("===== BMI270 TEST PASS =====");
  } else {
    Serial.println("===== BMI270 TEST FAIL =====");
  }
}

void loop() {
  delay(1000);
}
