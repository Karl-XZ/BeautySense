#include <Arduino.h>
#include <math.h>
#include "BLEDevice.h"
#include "BLEServer.h"
#include "BLEUtils.h"
#include "BLE2902.h"
#include "driver/i2s_std.h"

// 银龄智护 B1 - ESP32-S3 BLE GATT phone test
//
// What this validates:
//   1) BLE advertising / phone discovery
//   2) BLE connect / disconnect / reconnect
//   3) GATT READ
//   4) GATT WRITE / WRITE_NR
//   5) GATT NOTIFY
//
// IMPORTANT:
// - ESP32-S3 does BLE, not Bluetooth Classic A2DP audio sink.
// - This test validates the BLE control/data channel, not Bluetooth audio streaming.
// - MAX98357A is used only for START / PASS / FAIL cue tones.
//
// Recommended phone test sequence:
//   A. Scan and connect to: YINLING-BLE-TEST
//   B. Enable notifications on TX characteristic
//   C. Confirm SEQ=... notifications keep increasing, then WRITE ASCII: NOTIFY_OK
//   D. WRITE ASCII: PING          -> expect PONG
//   E. WRITE ASCII: ECHO 12345    -> expect ECHO:12345
//   F. Disconnect and reconnect until connect count >= 3
//   G. WRITE ASCII: PASS
//
// PASS requires:
//   connectCount >= 3
//   PING seen
//   ECHO seen
//   NOTIFY_OK seen
//
// Commands:
//   PING
//   ECHO <text>
//   NOTIFY_OK
//   STATUS
//   PASS
//   FAIL

static const char *DEVICE_NAME = "YINLING-BLE-TEST";

static const char *SERVICE_UUID = "8f13a000-6fd4-4f8c-b8b7-7cc16b48a001";
static const char *RX_UUID      = "8f13a001-6fd4-4f8c-b8b7-7cc16b48a001"; // phone -> ESP32
static const char *TX_UUID      = "8f13a002-6fd4-4f8c-b8b7-7cc16b48a001"; // ESP32 -> phone

// ---------------- MAX98357A / I2S cue audio ----------------
static const int PIN_BCLK = 36;
static const int PIN_WS   = 37;
static const int PIN_DOUT = 39;
static const int PIN_SD   = 40;
static const uint32_t AUDIO_RATE = 16000;
static const float CUE_LEVEL = 0.02f;

static i2s_chan_handle_t cueTx = NULL;
static bool cueAudioReady = false;

// ---------------- BLE state ----------------
static BLEServer *gServer = nullptr;
static BLECharacteristic *gRx = nullptr;
static BLECharacteristic *gTx = nullptr;

static volatile bool deviceConnected = false;
static volatile bool restartAdvertising = false;
static volatile bool pendingPassCue = false;
static volatile bool pendingFailCue = false;

static volatile uint32_t connectCount = 0;
static volatile uint32_t disconnectCount = 0;
static volatile uint32_t writeCount = 0;
static volatile uint32_t notifyCount = 0;

static volatile bool pingSeen = false;
static volatile bool echoSeen = false;
static volatile bool notifyAckSeen = false;
static volatile bool passLatched = false;
static volatile bool failLatched = false;

static uint32_t notifySeq = 0;
static uint32_t lastNotifyMs = 0;
static uint32_t lastHealthMs = 0;

// ============================================================
// Audio cue helpers
// ============================================================

bool initCueAudio()
{
  pinMode(PIN_SD, OUTPUT);
  digitalWrite(PIN_SD, HIGH);
  delay(10);

  i2s_chan_config_t ch = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO, I2S_ROLE_MASTER);
  if (i2s_new_channel(&ch, &cueTx, NULL) != ESP_OK)
    return false;

  i2s_std_config_t cfg = {
    .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(AUDIO_RATE),
    .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(
      I2S_DATA_BIT_WIDTH_32BIT,
      I2S_SLOT_MODE_STEREO
    ),
    .gpio_cfg = {
      .mclk = I2S_GPIO_UNUSED,
      .bclk = (gpio_num_t)PIN_BCLK,
      .ws   = (gpio_num_t)PIN_WS,
      .dout = (gpio_num_t)PIN_DOUT,
      .din  = I2S_GPIO_UNUSED,
      .invert_flags = {
        .mclk_inv = false,
        .bclk_inv = false,
        .ws_inv   = false
      }
    }
  };

  if (i2s_channel_init_std_mode(cueTx, &cfg) != ESP_OK)
    return false;

  if (i2s_channel_enable(cueTx) != ESP_OK)
    return false;

  return true;
}

void cueSilence(uint32_t ms)
{
  if (!cueAudioReady || !cueTx) return;

  static int32_t z[128 * 2] = {0};
  uint32_t framesLeft = (AUDIO_RATE * ms) / 1000;

  while (framesLeft)
  {
    uint32_t frames = framesLeft > 128 ? 128 : framesLeft;
    size_t written = 0;
    i2s_channel_write(cueTx, z, frames * 2 * sizeof(int32_t), &written, portMAX_DELAY);
    framesLeft -= frames;
  }
}

void cueTone(float hz, uint32_t ms)
{
  if (!cueAudioReady || !cueTx) return;

  static int32_t buf[128 * 2];
  float phase = 0.0f;
  const float step = 2.0f * PI * hz / AUDIO_RATE;
  const int32_t amp = (int32_t)(2147483647.0f * CUE_LEVEL);
  uint32_t framesLeft = (AUDIO_RATE * ms) / 1000;

  while (framesLeft)
  {
    uint32_t frames = framesLeft > 128 ? 128 : framesLeft;

    for (uint32_t i = 0; i < frames; i++)
    {
      int32_t s = (int32_t)(sinf(phase) * amp);
      phase += step;
      if (phase >= 2.0f * PI) phase -= 2.0f * PI;
      buf[i * 2] = s;
      buf[i * 2 + 1] = s;
    }

    size_t written = 0;
    i2s_channel_write(cueTx, buf, frames * 2 * sizeof(int32_t), &written, portMAX_DELAY);
    framesLeft -= frames;
  }

  cueSilence(35);
}

void playStartCue()
{
  Serial.println("AUDIO CUE: START");
  cueTone(660, 110);
  cueTone(880, 110);
  cueTone(1175, 150);
}

void playPassCue()
{
  Serial.println("AUDIO CUE: PASS");
  cueTone(1047, 170);
  cueTone(1568, 260);
}

void playFailCue()
{
  Serial.println("AUDIO CUE: FAIL");
  cueTone(660, 150);
  cueTone(440, 170);
  cueTone(220, 260);
}

// ============================================================
// BLE helpers
// ============================================================

String buildStatus()
{
  String s;
  s.reserve(180);
  s += "CONN=" + String((uint32_t)connectCount);
  s += " DISC=" + String((uint32_t)disconnectCount);
  s += " WR=" + String((uint32_t)writeCount);
  s += " NTF=" + String((uint32_t)notifyCount);
  s += " PING=" + String(pingSeen ? 1 : 0);
  s += " ECHO=" + String(echoSeen ? 1 : 0);
  s += " NACK=" + String(notifyAckSeen ? 1 : 0);
  s += " PASS=" + String(passLatched ? 1 : 0);
  return s;
}

void txSetAndNotify(const String &msg)
{
  if (!gTx) return;

  gTx->setValue(msg.c_str());

  if (deviceConnected)
  {
    gTx->notify();
    notifyCount++;
  }

  Serial.printf("BLE TX: %s\n", msg.c_str());
}

bool passRequirementsMet()
{
  return connectCount >= 3 && pingSeen && echoSeen && notifyAckSeen;
}

class ServerCallbacks : public BLEServerCallbacks
{
  void onConnect(BLEServer *server) override
  {
    (void)server;
    deviceConnected = true;
    connectCount++;

    Serial.println();
    Serial.printf("BLE CONNECTED | connectCount=%lu\n", (unsigned long)connectCount);
  }

  void onDisconnect(BLEServer *server) override
  {
    (void)server;
    deviceConnected = false;
    disconnectCount++;
    restartAdvertising = true;

    Serial.printf("BLE DISCONNECTED | disconnectCount=%lu\n", (unsigned long)disconnectCount);
  }
};

class RxCallbacks : public BLECharacteristicCallbacks
{
  void onWrite(BLECharacteristic *characteristic) override
  {
    String v = characteristic->getValue();
    v.trim();

    if (v.length() == 0)
      return;

    writeCount++;
    Serial.printf("BLE RX #%lu: %s\n", (unsigned long)writeCount, v.c_str());

    if (v.equalsIgnoreCase("PING"))
    {
      pingSeen = true;
      txSetAndNotify("PONG");
      return;
    }

    if (v.startsWith("ECHO ") || v.startsWith("echo "))
    {
      echoSeen = true;
      String payload = v.substring(5);
      txSetAndNotify("ECHO:" + payload);
      return;
    }

    if (v.equalsIgnoreCase("NOTIFY_OK"))
    {
      notifyAckSeen = true;
      txSetAndNotify("NOTIFY_ACK_RECORDED");
      return;
    }

    if (v.equalsIgnoreCase("STATUS"))
    {
      txSetAndNotify(buildStatus());
      return;
    }

    if (v.equalsIgnoreCase("PASS"))
    {
      if (passRequirementsMet())
      {
        if (!passLatched)
        {
          passLatched = true;
          pendingPassCue = true;
        }
        txSetAndNotify("TEST PASS");
      }
      else
      {
        String need = "NOT READY: need CONN>=3,PING,ECHO,NOTIFY_OK | ";
        need += buildStatus();
        txSetAndNotify(need);
      }
      return;
    }

    if (v.equalsIgnoreCase("FAIL"))
    {
      if (!failLatched)
      {
        failLatched = true;
        pendingFailCue = true;
      }
      txSetAndNotify("TEST MANUAL FAIL");
      return;
    }

    txSetAndNotify("UNKNOWN CMD | use PING / ECHO <text> / NOTIFY_OK / STATUS / PASS");
  }
};

bool initBLE()
{
  BLEDevice::init(DEVICE_NAME);

  gServer = BLEDevice::createServer();
  if (!gServer) return false;
  gServer->setCallbacks(new ServerCallbacks());

  BLEService *service = gServer->createService(SERVICE_UUID);
  if (!service) return false;

  gTx = service->createCharacteristic(
    TX_UUID,
    BLECharacteristic::PROPERTY_READ |
    BLECharacteristic::PROPERTY_NOTIFY
  );
  if (!gTx) return false;
  gTx->addDescriptor(new BLE2902());
  gTx->setValue("READY");

  gRx = service->createCharacteristic(
    RX_UUID,
    BLECharacteristic::PROPERTY_WRITE |
    BLECharacteristic::PROPERTY_WRITE_NR
  );
  if (!gRx) return false;
  gRx->setCallbacks(new RxCallbacks());

  service->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  if (!adv) return false;
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  adv->setMinPreferred(0x06);

  BLEDevice::startAdvertising();
  return true;
}

// ============================================================
// Setup / loop
// ============================================================

void setup()
{
  Serial.begin(115200);
  delay(1500);

  Serial.println();
  Serial.println("====================================================");
  Serial.println("YINLING-ZHIHU ESP32-S3 BLE GATT PHONE TEST");
  Serial.println("BLE advertising + connect + read/write/notify + reconnect");
  Serial.println("Bluetooth Classic / A2DP audio is NOT part of this test");
  Serial.println("====================================================");

  cueAudioReady = initCueAudio();
  Serial.printf("CUE AUDIO: %s\n", cueAudioReady ? "PASS" : "UNAVAILABLE (BLE test can continue)");

  if (!initBLE())
  {
    Serial.println("BLE INIT FAIL");
    if (cueAudioReady) playFailCue();
    while (true) delay(1000);
  }

  Serial.println("BLE INIT PASS");
  Serial.printf("DEVICE NAME: %s\n", DEVICE_NAME);
  Serial.printf("SERVICE UUID: %s\n", SERVICE_UUID);
  Serial.printf("RX UUID (WRITE): %s\n", RX_UUID);
  Serial.printf("TX UUID (READ/NOTIFY): %s\n", TX_UUID);
  Serial.println();
  Serial.println("PHONE FLOW:");
  Serial.println("1) connect + enable TX notify");
  Serial.println("2) confirm SEQ notifications, write NOTIFY_OK");
  Serial.println("3) write PING");
  Serial.println("4) write ECHO 12345");
  Serial.println("5) disconnect/reconnect until CONN>=3");
  Serial.println("6) write PASS");
  Serial.println();
  Serial.println("BLE TEST READY / ADVERTISING");

  if (cueAudioReady) playStartCue();

  lastNotifyMs = millis();
  lastHealthMs = millis();
}

void loop()
{
  if (restartAdvertising)
  {
    restartAdvertising = false;
    delay(250);
    BLEDevice::startAdvertising();
    Serial.println("BLE ADVERTISING RESTARTED");
  }

  if (pendingPassCue)
  {
    pendingPassCue = false;
    Serial.println();
    Serial.println("================ BLE TEST RESULT ================");
    Serial.println("BLE GATT PHONE TEST: PASS");
    Serial.println(buildStatus());
    Serial.println("=================================================");
    if (cueAudioReady) playPassCue();
  }

  if (pendingFailCue)
  {
    pendingFailCue = false;
    Serial.println();
    Serial.println("================ BLE TEST RESULT ================");
    Serial.println("BLE GATT PHONE TEST: MANUAL FAIL");
    Serial.println(buildStatus());
    Serial.println("=================================================");
    if (cueAudioReady) playFailCue();
  }

  uint32_t now = millis();

  if (deviceConnected && !passLatched && !failLatched && now - lastNotifyMs >= 1000)
  {
    lastNotifyMs = now;
    notifySeq++;

    String msg = "SEQ=" + String(notifySeq);
    msg += " UP=" + String(now / 1000);
    msg += " CONN=" + String((uint32_t)connectCount);

    txSetAndNotify(msg);
  }

  if (now - lastHealthMs >= 5000)
  {
    lastHealthMs = now;
    Serial.printf(
      "HEALTH BLE: connected=%d conn=%lu disc=%lu write=%lu notify=%lu ping=%d echo=%d notifyAck=%d pass=%d heap=%u\n",
      deviceConnected ? 1 : 0,
      (unsigned long)connectCount,
      (unsigned long)disconnectCount,
      (unsigned long)writeCount,
      (unsigned long)notifyCount,
      pingSeen ? 1 : 0,
      echoSeen ? 1 : 0,
      notifyAckSeen ? 1 : 0,
      passLatched ? 1 : 0,
      (unsigned)ESP.getFreeHeap()
    );
  }

  delay(10);
}
