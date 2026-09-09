#include <Arduino.h>
#include <Wire.h>
#include <Preferences.h>
#include <math.h>
#include "esp_camera.h"
#include "esp_system.h"
#include "driver/i2s_std.h"

#if __has_include("bmi260_config.h")
#include "bmi260_config.h"
#else
#error "Place bmi260_config.h in the same sketch folder."
#endif

// 银龄智护 B1 - Test 5C OFFLINE V2
// 20x real cold-boot validation without Wi-Fi dependency.
// Fixes V1 ambiguity:
// 1) upload/reset boot can run a PRECHECK but is NEVER reported as a cold-boot PASS;
// 2) only ESP_RST_POWERON increments cold/pass/fail counters;
// 3) after the 15 s result the foreground test stops printing and waits for power cycle.
//
// Validates per real cold boot:
// PSRAM + OV5640 + MIC RX + MAX98357A TX + PCA9540B + BMI260-like IMU.
// Wi-Fi / Haptic / Battery / FPC intentionally excluded.

static const uint32_t SANITY_MS = 15000;
static const uint32_t TARGET_COLD_BOOTS = 20;
static const char *TEST_NVS_NAMESPACE = "yinling_5c_o2";

// Camera
static const int CAM_SIOD=1, CAM_SIOC=2;
static const int CAM_D0=4, CAM_D1=5, CAM_D2=6, CAM_D3=7;
static const int CAM_D4=8, CAM_D5=9, CAM_D6=10, CAM_D7=11;
static const int CAM_PCLK=12, CAM_HREF=13, CAM_VSYNC=14, CAM_XCLK=15;
static const int CAM_RESET=16, CAM_PWDN=17;

// Sensor I2C
static const int SENSOR_SDA=21, SENSOR_SCL=18;
static const uint8_t IMU_ADDR=0x68, PCA_ADDR=0x70;
static const uint8_t BMI260_CHIP_ID=0x27;
static const uint8_t REG_CHIP_ID=0x00;
static const uint8_t REG_ACC_X_LSB=0x0C;
static const uint8_t REG_INTERNAL_STATUS=0x21;
static const uint8_t REG_ACC_CONF=0x40, REG_ACC_RANGE=0x41;
static const uint8_t REG_GYR_CONF=0x42, REG_GYR_RANGE=0x43;
static const uint8_t REG_INIT_CTRL=0x59, REG_INIT_ADDR_0=0x5B, REG_INIT_DATA=0x5E;
static const uint8_t REG_PWR_CONF=0x7C, REG_PWR_CTRL=0x7D, REG_CMD=0x7E;
static const uint8_t CMD_SOFT_RESET=0xB6, INIT_OK=0x01;
static const size_t BMI_CONFIG_CHUNK=16;

// Audio
static const int PIN_BCLK=36, PIN_WS=37, PIN_DIN=38, PIN_DOUT=39, PIN_SD=40;
static const uint32_t SAMPLE_RATE=16000;
static const float TONE_FREQ_HZ=1000.0f, TONE_LEVEL=0.02f;
static i2s_chan_handle_t txHandle=NULL, rxHandle=NULL;
static volatile bool txTaskAlive=false, toneEnabled=false;
static volatile uint32_t txErrors=0;

static uint32_t rxErrors=0, camErrors=0, imuErrors=0, pcaErrors=0;
static uint32_t cameraFrames=0, imuReads=0, micBlocks=0;
static int activeMicChannel=0;
static int32_t rxBuffer[256*2];

static uint32_t lastCameraMs=0, lastImuMs=0, lastToneMs=0, lastHealthMs=0;
static uint32_t sanityStartMs=0;
static bool thisIsPowerOn=false;
static bool resultPrinted=false;
static bool testFinished=false;
static uint32_t currentColdBootNo=0;

const char *resetReasonName(esp_reset_reason_t r){
  switch(r){
    case ESP_RST_POWERON: return "POWERON";
    case ESP_RST_EXT: return "EXT_RESET";
    case ESP_RST_SW: return "SOFTWARE";
    case ESP_RST_PANIC: return "PANIC";
    case ESP_RST_INT_WDT: return "INT_WDT";
    case ESP_RST_TASK_WDT: return "TASK_WDT";
    case ESP_RST_WDT: return "WDT";
    case ESP_RST_DEEPSLEEP: return "DEEPSLEEP";
    case ESP_RST_BROWNOUT: return "BROWNOUT";
    default: return "OTHER";
  }
}

void readCounters(uint32_t &cold,uint32_t &pass,uint32_t &fail){
  Preferences p;
  p.begin(TEST_NVS_NAMESPACE,true);
  cold=p.getUInt("cold",0); pass=p.getUInt("pass",0); fail=p.getUInt("fail",0);
  p.end();
}

void resetCounters(){
  Preferences p;
  if(p.begin(TEST_NVS_NAMESPACE,false)){
    p.clear(); p.end();
    Serial.println("V2 counters cleared. Power OFF >=5 s, then POWER ON.");
  }
}

void beginBootAccounting(){
  esp_reset_reason_t rr=esp_reset_reason();
  thisIsPowerOn=(rr==ESP_RST_POWERON);

  uint32_t cold=0,pass=0,fail=0;
  readCounters(cold,pass,fail);

  if(thisIsPowerOn){
    cold++;
    Preferences p;
    p.begin(TEST_NVS_NAMESPACE,false);
    p.putUInt("cold",cold);
    p.end();
  }
  currentColdBootNo=cold;

  Serial.printf("RESET REASON: %s\n",resetReasonName(rr));
  Serial.printf("TEST5C V2 COUNTERS: cold=%lu pass=%lu fail=%lu target=%lu\n",
                (unsigned long)cold,(unsigned long)pass,(unsigned long)fail,
                (unsigned long)TARGET_COLD_BOOTS);

  if(thisIsPowerOn){
    Serial.printf("MODE: REAL COLD BOOT #%lu -> WILL BE COUNTED\n",(unsigned long)cold);
  }else{
    Serial.println("MODE: PRECHECK ONLY -> NOT A COLD BOOT, NOT COUNTED");
    Serial.println("This commonly happens immediately after upload/reset.");
  }
}

void recordRealColdBootResult(bool passNow){
  if(!thisIsPowerOn) return;
  Preferences p;
  p.begin(TEST_NVS_NAMESPACE,false);
  uint32_t pass=p.getUInt("pass",0), fail=p.getUInt("fail",0), cold=p.getUInt("cold",0);
  if(passNow){ pass++; p.putUInt("pass",pass); }
  else { fail++; p.putUInt("fail",fail); }
  p.end();

  Serial.printf("PERSISTENT V2 RESULT: cold=%lu pass=%lu fail=%lu\n",
                (unsigned long)cold,(unsigned long)pass,(unsigned long)fail);
  if(cold>=TARGET_COLD_BOOTS && pass>=TARGET_COLD_BOOTS && fail==0){
    Serial.println("================================================");
    Serial.println("TEST5C OFFLINE V2 20x RESULT: PASS CANDIDATE");
    Serial.println("================================================");
  }
}

bool initCamera(){
  camera_config_t c={};
  c.ledc_channel=LEDC_CHANNEL_0; c.ledc_timer=LEDC_TIMER_0;
  c.pin_d0=CAM_D0; c.pin_d1=CAM_D1; c.pin_d2=CAM_D2; c.pin_d3=CAM_D3;
  c.pin_d4=CAM_D4; c.pin_d5=CAM_D5; c.pin_d6=CAM_D6; c.pin_d7=CAM_D7;
  c.pin_xclk=CAM_XCLK; c.pin_pclk=CAM_PCLK; c.pin_vsync=CAM_VSYNC; c.pin_href=CAM_HREF;
  c.pin_sccb_sda=CAM_SIOD; c.pin_sccb_scl=CAM_SIOC;
  c.pin_pwdn=CAM_PWDN; c.pin_reset=CAM_RESET;
  c.xclk_freq_hz=20000000; c.pixel_format=PIXFORMAT_JPEG;
  c.frame_size=FRAMESIZE_QVGA; c.jpeg_quality=14; c.fb_count=1;
  c.fb_location=CAMERA_FB_IN_PSRAM; c.grab_mode=CAMERA_GRAB_WHEN_EMPTY;
  esp_err_t e=esp_camera_init(&c);
  if(e!=ESP_OK){ Serial.printf("CAMERA INIT FAIL: 0x%x\n",e); return false; }
  return true;
}

void captureFrame(){
  camera_fb_t *fb=esp_camera_fb_get();
  if(!fb){ camErrors++; Serial.println("CAM FRAME FAIL"); return; }
  cameraFrames++;
  Serial.printf("CAM #%lu bytes=%u\n",(unsigned long)cameraFrames,(unsigned)fb->len);
  esp_camera_fb_return(fb);
}

bool initAudio(){
  pinMode(PIN_SD,OUTPUT); digitalWrite(PIN_SD,HIGH); delay(10);
  i2s_chan_config_t ch=I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO,I2S_ROLE_MASTER);
  if(i2s_new_channel(&ch,&txHandle,&rxHandle)!=ESP_OK) return false;
  i2s_std_config_t cfg={
    .clk_cfg=I2S_STD_CLK_DEFAULT_CONFIG(SAMPLE_RATE),
    .slot_cfg=I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_32BIT,I2S_SLOT_MODE_STEREO),
    .gpio_cfg={
      .mclk=I2S_GPIO_UNUSED,.bclk=(gpio_num_t)PIN_BCLK,.ws=(gpio_num_t)PIN_WS,
      .dout=(gpio_num_t)PIN_DOUT,.din=(gpio_num_t)PIN_DIN,
      .invert_flags={.mclk_inv=false,.bclk_inv=false,.ws_inv=false}
    }
  };
  if(i2s_channel_init_std_mode(txHandle,&cfg)!=ESP_OK) return false;
  if(i2s_channel_init_std_mode(rxHandle,&cfg)!=ESP_OK) return false;
  if(i2s_channel_enable(txHandle)!=ESP_OK) return false;
  if(i2s_channel_enable(rxHandle)!=ESP_OK) return false;
  return true;
}

void txTask(void *){
  const size_t FRAMES=256;
  static int32_t tx[FRAMES*2];
  float phase=0.0f;
  const float step=2.0f*PI*TONE_FREQ_HZ/SAMPLE_RATE;
  const int32_t amp=(int32_t)(2147483647.0f*TONE_LEVEL);
  txTaskAlive=true;
  while(true){
    bool tone=toneEnabled;
    for(size_t i=0;i<FRAMES;i++){
      int32_t s=0;
      if(tone){ s=(int32_t)(sinf(phase)*amp); phase+=step; if(phase>=2.0f*PI) phase-=2.0f*PI; }
      tx[i*2]=s; tx[i*2+1]=s;
    }
    size_t written=0;
    if(i2s_channel_write(txHandle,tx,sizeof(tx),&written,portMAX_DELAY)!=ESP_OK) txErrors++;
  }
}

bool readRx(size_t &frames){
  size_t got=0;
  if(i2s_channel_read(rxHandle,rxBuffer,sizeof(rxBuffer),&got,pdMS_TO_TICKS(1000))!=ESP_OK){
    rxErrors++; frames=0; return false;
  }
  frames=got/(sizeof(int32_t)*2); return true;
}

void detectMicChannel(){
  uint64_t le=0,re=0; uint32_t total=0; toneEnabled=false;
  while(total<SAMPLE_RATE/2){
    size_t frames=0; if(!readRx(frames)) continue;
    for(size_t i=0;i<frames;i++){
      le+=llabs((long long)(rxBuffer[i*2]>>8));
      re+=llabs((long long)(rxBuffer[i*2+1]>>8));
    }
    total+=frames;
  }
  activeMicChannel=(re>le)?1:0;
  Serial.printf("MIC channel=%s\n",activeMicChannel?"RIGHT":"LEFT");
}

void readMic250ms(){
  uint32_t t0=millis(); uint64_t sq=0; uint32_t n=0; int32_t peak=0;
  while(millis()-t0<250){
    size_t frames=0; if(!readRx(frames)) continue;
    for(size_t i=0;i<frames;i++){
      int32_t s=rxBuffer[i*2+activeMicChannel]>>16;
      int32_t a=abs(s); if(a>peak) peak=a;
      sq+=(uint64_t)((int64_t)s*s); n++;
    }
  }
  micBlocks++;
  float rms=n?sqrt((double)sq/n):0.0f;
  Serial.printf("MIC #%lu RMS=%.1f Peak=%ld\n",(unsigned long)micBlocks,rms,(long)peak);
}

bool i2cAck(uint8_t a){ Wire.beginTransmission(a); return Wire.endTransmission()==0; }
bool pcaOff(){ Wire.beginTransmission(PCA_ADDR); Wire.write(0x00); return Wire.endTransmission()==0; }
bool imuWriteReg(uint8_t r,uint8_t v){ Wire.beginTransmission(IMU_ADDR); Wire.write(r); Wire.write(v); return Wire.endTransmission()==0; }
bool imuWriteBytes(uint8_t r,const uint8_t *d,size_t n){
  Wire.beginTransmission(IMU_ADDR); Wire.write(r); for(size_t i=0;i<n;i++) Wire.write(d[i]); return Wire.endTransmission()==0;
}
bool imuReadReg(uint8_t r,uint8_t &v){
  Wire.beginTransmission(IMU_ADDR); Wire.write(r); if(Wire.endTransmission(false)!=0) return false;
  if(Wire.requestFrom(IMU_ADDR,(uint8_t)1)!=1) return false; v=Wire.read(); return true;
}
bool imuReadBytes(uint8_t r,uint8_t *d,size_t n){
  Wire.beginTransmission(IMU_ADDR); Wire.write(r); if(Wire.endTransmission(false)!=0) return false;
  if(Wire.requestFrom(IMU_ADDR,(uint8_t)n)!=n) return false; for(size_t i=0;i<n;i++) d[i]=Wire.read(); return true;
}

bool uploadBMI260Config(){
  uint8_t pc=0;
  if(!imuReadReg(REG_PWR_CONF,pc)) return false;
  pc&=(uint8_t)~0x01;
  if(!imuWriteReg(REG_PWR_CONF,pc)) return false;
  delayMicroseconds(500);
  if(!imuWriteReg(REG_INIT_CTRL,0x00)) return false;
  delayMicroseconds(500);
  const size_t total=sizeof(bmi260_config_file);
  Serial.printf("BMI260 config bytes=%u\n",(unsigned)total);
  for(size_t index=0;index<total;index+=BMI_CONFIG_CHUNK){
    size_t chunk=BMI_CONFIG_CHUNK; if(index+chunk>total) chunk=total-index;
    uint16_t wa=(uint16_t)(index/2);
    uint8_t ab[2]={(uint8_t)(wa&0x0F),(uint8_t)(wa>>4)};
    if(!imuWriteBytes(REG_INIT_ADDR_0,ab,2)) return false;
    delayMicroseconds(20);
    if(!imuWriteBytes(REG_INIT_DATA,bmi260_config_file+index,chunk)) return false;
    delayMicroseconds(20);
  }
  if(!imuWriteReg(REG_INIT_CTRL,0x01)) return false;
  delay(25);
  for(int i=0;i<100;i++){
    uint8_t s=0; if(!imuReadReg(REG_INTERNAL_STATUS,s)) return false;
    if((s&0x0F)==INIT_OK) return true;
    delay(5);
  }
  return false;
}

bool initIMU(){
  Wire.begin(SENSOR_SDA,SENSOR_SCL); Wire.setClock(400000); delay(100);
  if(!i2cAck(PCA_ADDR)) return false;
  if(!pcaOff()) return false;
  if(!i2cAck(IMU_ADDR)) return false;
  uint8_t id=0;
  if(!imuReadReg(REG_CHIP_ID,id)) return false;
  Serial.printf("IMU CHIP_ID=0x%02X\n",id);
  if(id!=BMI260_CHIP_ID) return false;
  if(!imuWriteReg(REG_CMD,CMD_SOFT_RESET)) return false;
  delay(5);
  if(!imuReadReg(REG_CHIP_ID,id) || id!=BMI260_CHIP_ID) return false;
  if(!uploadBMI260Config()) return false;
  if(!imuWriteReg(REG_ACC_CONF,0xA8)) return false;
  if(!imuWriteReg(REG_ACC_RANGE,0x00)) return false;
  if(!imuWriteReg(REG_GYR_CONF,0xE8)) return false;
  if(!imuWriteReg(REG_GYR_RANGE,0x00)) return false;
  if(!imuWriteReg(REG_PWR_CTRL,0x06)) return false;
  delay(50); return true;
}

void readIMU(){
  if(!i2cAck(PCA_ADDR)) pcaErrors++;
  uint8_t b[12];
  if(!imuReadBytes(REG_ACC_X_LSB,b,sizeof(b))){ imuErrors++; return; }
  int16_t axr=(int16_t)(((uint16_t)b[1]<<8)|b[0]);
  int16_t ayr=(int16_t)(((uint16_t)b[3]<<8)|b[2]);
  int16_t azr=(int16_t)(((uint16_t)b[5]<<8)|b[4]);
  int16_t gxr=(int16_t)(((uint16_t)b[7]<<8)|b[6]);
  int16_t gyr=(int16_t)(((uint16_t)b[9]<<8)|b[8]);
  int16_t gzr=(int16_t)(((uint16_t)b[11]<<8)|b[10]);
  float ax=axr/16384.0f, ay=ayr/16384.0f, az=azr/16384.0f;
  float amag=sqrtf(ax*ax+ay*ay+az*az);
  float gx=gxr/16.384f, gy=gyr/16.384f, gz=gzr/16.384f;
  imuReads++;
  Serial.printf("IMU #%lu |A|=%.3f gyro=%.1f,%.1f,%.1f\n",(unsigned long)imuReads,amag,gx,gy,gz);
}

void failInit(const char *stage){
  Serial.printf("INIT FAILURE: %s\n",stage);
  if(thisIsPowerOn){
    Serial.println("THIS REAL COLD BOOT: FAIL");
    recordRealColdBootResult(false);
  }else{
    Serial.println("PRECHECK INIT: FAIL (NOT COUNTED)");
  }
  while(true) delay(1000);
}

void setup(){
  Serial.begin(115200); delay(1800);
  Serial.println();
  Serial.println("====================================================");
  Serial.println("YINLING-ZHIHU B1 TEST5C OFFLINE V2 - 20x COLD BOOT");
  Serial.println("Camera + MIC + Audio + PCA + BMI260-like");
  Serial.println("Wi-Fi/Haptic/Battery/FPC excluded");
  Serial.println("====================================================");

  beginBootAccounting();

  if(!psramFound()) failInit("PSRAM");
  Serial.println("PSRAM PASS");

  if(!initCamera()) failInit("CAMERA");
  Serial.println("CAMERA PASS");

  if(!initAudio()) failInit("AUDIO");
  Serial.println("AUDIO INIT PASS");

  BaseType_t ok=xTaskCreatePinnedToCore(txTask,"audio_tx",4096,NULL,2,NULL,1);
  if(ok!=pdPASS) failInit("TX TASK");
  while(!txTaskAlive) delay(10);
  detectMicChannel();

  if(!initIMU()) failInit("PCA/IMU");
  Serial.println("PCA/IMU INIT PASS");

  sanityStartMs=millis();
  lastCameraMs=lastImuMs=lastToneMs=lastHealthMs=sanityStartMs;

  if(thisIsPowerOn)
    Serial.printf("REAL COLD BOOT #%lu SANITY WINDOW START: %lu s\n",(unsigned long)currentColdBootNo,(unsigned long)(SANITY_MS/1000));
  else
    Serial.printf("PRECHECK SANITY WINDOW START: %lu s (NOT COUNTED)\n",(unsigned long)(SANITY_MS/1000));
}

void loop(){
  if(Serial.available()){
    String cmd=Serial.readStringUntil('\n'); cmd.trim();
    if(cmd=="RESET5C2") resetCounters();
  }

  if(testFinished){ delay(250); return; }

  uint32_t now=millis();
  if(now-lastToneMs>=1000){ toneEnabled=!toneEnabled; lastToneMs=now; }

  readMic250ms();
  now=millis();
  if(now-lastImuMs>=1000){ readIMU(); lastImuMs=millis(); }
  if(millis()-lastCameraMs>=2000){ captureFrame(); lastCameraMs=millis(); }

  if(millis()-lastHealthMs>=5000){
    Serial.printf("HEALTH: cam=%lu/%lu txErr=%lu rxErr=%lu imu=%lu/%lu pcaErr=%lu mic=%lu heap=%u psram=%u\n",
      (unsigned long)cameraFrames,(unsigned long)camErrors,
      (unsigned long)txErrors,(unsigned long)rxErrors,
      (unsigned long)imuReads,(unsigned long)imuErrors,
      (unsigned long)pcaErrors,(unsigned long)micBlocks,
      (unsigned)ESP.getFreeHeap(),(unsigned)ESP.getFreePsram());
    lastHealthMs=millis();
  }

  if(!resultPrinted && millis()-sanityStartMs>=SANITY_MS){
    resultPrinted=true;
    bool passNow=(camErrors==0 && txErrors==0 && rxErrors==0 && imuErrors==0 && pcaErrors==0 &&
                  cameraFrames>=5 && imuReads>=8 && micBlocks>=10);

    Serial.println();
    if(thisIsPowerOn) Serial.println("============== REAL COLD BOOT RESULT ==============");
    else              Serial.println("================ PRECHECK RESULT ==================");

    Serial.printf("cameraFrames=%lu imuReads=%lu micBlocks=%lu\n",
                  (unsigned long)cameraFrames,(unsigned long)imuReads,(unsigned long)micBlocks);
    Serial.printf("camErr=%lu txErr=%lu rxErr=%lu imuErr=%lu pcaErr=%lu\n",
                  (unsigned long)camErrors,(unsigned long)txErrors,(unsigned long)rxErrors,
                  (unsigned long)imuErrors,(unsigned long)pcaErrors);

    if(thisIsPowerOn){
      Serial.printf("THIS REAL COLD BOOT #%lu: %s\n",(unsigned long)currentColdBootNo,passNow?"PASS":"FAIL");
      recordRealColdBootResult(passNow);
      Serial.println("NEXT: power OFF >=5 s, then power ON. Do not press Reset instead.");
    }else{
      Serial.printf("PRECHECK ONLY: %s (NOT COUNTED)\n",passNow?"PASS":"FAIL");
      Serial.println("NEXT: now do a real power OFF >=5 s -> power ON to start cold-boot #1.");
    }
    Serial.println("====================================================");

    toneEnabled=false;
    testFinished=true;
  }
}
