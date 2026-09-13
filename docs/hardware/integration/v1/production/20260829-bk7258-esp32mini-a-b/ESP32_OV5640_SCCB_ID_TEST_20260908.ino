#include <Wire.h>
#include <esp_arduino_version.h>

// Silver Age Care B1 / ESP32-S3-MINI-1U-N4R2
// OV5640 SCCB + CHIP ID bring-up test
// Production pin map:
//   SCCB_SDA -> GPIO1
//   SCCB_SCL -> GPIO2
//   XCLK     -> GPIO15
//   RESET    -> GPIO16  (OV5640 RESETB, active LOW)
//   PWDN     -> GPIO17  (OV5640 PWDN, active HIGH)

static const int CAM_SDA   = 1;
static const int CAM_SCL   = 2;
static const int CAM_XCLK  = 15;
static const int CAM_RESET = 16;
static const int CAM_PWDN  = 17;

static const uint8_t OV5640_ADDR = 0x3C; // 7-bit SCCB address (8-bit write address is 0x78)

static const uint32_t SCCB_FREQ = 100000;
static const uint32_t XCLK_FREQ = 20000000;

#if ESP_ARDUINO_VERSION_MAJOR < 3
static const int XCLK_LEDC_CH = 0;
#endif

TwoWire CamWire(1);

void startXCLK()
{
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  if (!ledcAttach(CAM_XCLK, XCLK_FREQ, 1))
  {
    Serial.println("XCLK LEDC attach FAIL");
    return;
  }
  ledcWrite(CAM_XCLK, 1); // 1-bit resolution: 50% duty
#else
  ledcSetup(XCLK_LEDC_CH, XCLK_FREQ, 1);
  ledcAttachPin(CAM_XCLK, XCLK_LEDC_CH);
  ledcWrite(XCLK_LEDC_CH, 1); // 50% duty
#endif

  Serial.print("XCLK started on GPIO");
  Serial.print(CAM_XCLK);
  Serial.print(" @ ");
  Serial.print(XCLK_FREQ / 1000000);
  Serial.println(" MHz");
}

void cameraWakeAndReset()
{
  pinMode(CAM_PWDN, OUTPUT);
  pinMode(CAM_RESET, OUTPUT);

  // OV5640 bare-sensor polarity:
  // PWDN = HIGH -> power-down, LOW -> normal operation
  // RESETB = LOW -> reset, HIGH -> normal operation
  digitalWrite(CAM_PWDN, LOW);

  digitalWrite(CAM_RESET, LOW);
  delay(10);
  digitalWrite(CAM_RESET, HIGH);
  delay(30);
}

bool sccbAck(uint8_t addr)
{
  CamWire.beginTransmission(addr);
  return CamWire.endTransmission() == 0;
}

bool readReg16(uint16_t reg, uint8_t &value)
{
  CamWire.beginTransmission(OV5640_ADDR);
  CamWire.write((uint8_t)(reg >> 8));
  CamWire.write((uint8_t)(reg & 0xFF));

  // repeated-start read
  uint8_t err = CamWire.endTransmission(false);
  if (err != 0)
    return false;

  int n = CamWire.requestFrom((int)OV5640_ADDR, 1);
  if (n != 1 || !CamWire.available())
    return false;

  value = CamWire.read();
  return true;
}

void scanCameraBus()
{
  Serial.println();
  Serial.println("Scanning Camera SCCB bus...");

  int found = 0;

  for (uint8_t addr = 1; addr < 127; addr++)
  {
    CamWire.beginTransmission(addr);
    uint8_t err = CamWire.endTransmission();

    if (err == 0)
    {
      Serial.print("Found address: 0x");
      if (addr < 0x10) Serial.print('0');
      Serial.println(addr, HEX);
      found++;
    }
  }

  Serial.print("Total devices found: ");
  Serial.println(found);
}

void setup()
{
  Serial.begin(115200);
  delay(2500);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 OV5640 SCCB CHIP-ID TEST");
  Serial.println("========================================");

  Serial.println("Pin map:");
  Serial.println("SCCB_SDA = GPIO1");
  Serial.println("SCCB_SCL = GPIO2");
  Serial.println("XCLK     = GPIO15");
  Serial.println("RESET    = GPIO16");
  Serial.println("PWDN     = GPIO17");

  startXCLK();
  cameraWakeAndReset();

  CamWire.begin(CAM_SDA, CAM_SCL, SCCB_FREQ);
  delay(20);

  scanCameraBus();

  Serial.println();
  Serial.print("Checking OV5640 SCCB ACK at 0x");
  Serial.println(OV5640_ADDR, HEX);

  if (!sccbAck(OV5640_ADDR))
  {
    Serial.println("OV5640 SCCB ACK: FAIL");
    Serial.println();
    Serial.println("RESULT: CAMERA SCCB FAIL");
    Serial.println("Check camera power, FPC direction, GPIO1/GPIO2, XCLK, RESET/PWDN.");
    return;
  }

  Serial.println("OV5640 SCCB ACK: PASS");

  uint8_t idHigh = 0;
  uint8_t idLow  = 0;

  bool okHigh = readReg16(0x300A, idHigh);
  bool okLow  = readReg16(0x300B, idLow);

  Serial.println();

  if (okHigh)
  {
    Serial.print("REG 0x300A = 0x");
    if (idHigh < 0x10) Serial.print('0');
    Serial.println(idHigh, HEX);
  }
  else
  {
    Serial.println("REG 0x300A read FAIL");
  }

  if (okLow)
  {
    Serial.print("REG 0x300B = 0x");
    if (idLow < 0x10) Serial.print('0');
    Serial.println(idLow, HEX);
  }
  else
  {
    Serial.println("REG 0x300B read FAIL");
  }

  uint16_t chipId = ((uint16_t)idHigh << 8) | idLow;

  Serial.print("CHIP_ID = 0x");
  if (chipId < 0x1000) Serial.print('0');
  Serial.println(chipId, HEX);

  Serial.println();
  Serial.println("========================================");

  if (okHigh && okLow && chipId == 0x5640)
  {
    Serial.println("CAMERA POWER/SCCB/IDENTITY: PASS");
    Serial.println("OV5640 CHIP_ID = 0x5640");
  }
  else
  {
    Serial.println("CAMERA IDENTITY: FAIL / PARTIAL");
    Serial.println("Expected OV5640 CHIP_ID = 0x5640");
  }

  Serial.println("========================================");
}

void loop()
{
  static unsigned long last = 0;

  if (millis() - last >= 2000)
  {
    last = millis();

    uint8_t h = 0;
    uint8_t l = 0;

    bool okH = readReg16(0x300A, h);
    bool okL = readReg16(0x300B, l);

    if (okH && okL)
    {
      uint16_t id = ((uint16_t)h << 8) | l;
      Serial.print("OV5640 alive | CHIP_ID=0x");
      if (id < 0x1000) Serial.print('0');
      Serial.println(id, HEX);
    }
    else
    {
      Serial.println("OV5640 read FAIL");
    }
  }
}
