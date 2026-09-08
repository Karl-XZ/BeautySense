#include <WiFi.h>
#include "esp_camera.h"
#include "esp_http_server.h"

// 银龄智护 B1 / ESP32-S3-MINI-1U-N4R2 / OV5640 DVP
// 本仓库不保存真实 Wi-Fi 密码。烧录前在本地填写。

const char* WIFI_SSID = "DIILAB";
const char* WIFI_PASS = "CHANGE_ME_LOCAL_ONLY";

// Camera pin matrix (production board)
#define CAM_SDA    1
#define CAM_SCL    2
#define CAM_D0     4
#define CAM_D1     5
#define CAM_D2     6
#define CAM_D3     7
#define CAM_D4     8
#define CAM_D5     9
#define CAM_D6     10
#define CAM_D7     11
#define CAM_PCLK   12
#define CAM_HREF   13
#define CAM_VSYNC  14
#define CAM_XCLK   15
#define CAM_RESET  16
#define CAM_PWDN   17

static httpd_handle_t camera_httpd = NULL;

static const char* STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=frame";
static const char* STREAM_BOUNDARY = "\r\n--frame\r\n";
static const char* STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

static esp_err_t index_handler(httpd_req_t* req) {
  static const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!doctype html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Yinling Zhihu OV5640</title>
  <style>
    body{margin:0;background:#111;color:#eee;font-family:Arial;text-align:center}
    h2{margin:12px 0}
    img{width:100%;max-width:900px;height:auto;background:#000}
    p{font-size:14px;color:#bbb}
  </style>
</head>
<body>
  <h2>ESP32-S3 + OV5640 Live Video</h2>
  <img src="/stream">
  <p>VGA JPEG MJPEG stream</p>
</body>
</html>
)rawliteral";

  httpd_resp_set_type(req, "text/html");
  return httpd_resp_send(req, INDEX_HTML, HTTPD_RESP_USE_STRLEN);
}

static esp_err_t stream_handler(httpd_req_t* req) {
  esp_err_t res = httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
  if (res != ESP_OK) return res;

  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

  char part_buf[64];

  while (true) {
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("Camera capture failed");
      res = ESP_FAIL;
      break;
    }

    if (fb->format != PIXFORMAT_JPEG) {
      Serial.println("Unexpected non-JPEG frame");
      esp_camera_fb_return(fb);
      res = ESP_FAIL;
      break;
    }

    size_t hlen = snprintf(part_buf, sizeof(part_buf), STREAM_PART, fb->len);

    res = httpd_resp_send_chunk(req, STREAM_BOUNDARY, strlen(STREAM_BOUNDARY));
    if (res == ESP_OK) {
      res = httpd_resp_send_chunk(req, part_buf, hlen);
    }
    if (res == ESP_OK) {
      res = httpd_resp_send_chunk(req, (const char*)fb->buf, fb->len);
    }

    esp_camera_fb_return(fb);

    if (res != ESP_OK) {
      break;
    }
  }

  return res;
}

void startCameraServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = 80;
  config.max_uri_handlers = 4;

  if (httpd_start(&camera_httpd, &config) != ESP_OK) {
    Serial.println("HTTP server start FAIL");
    return;
  }

  httpd_uri_t index_uri = {
    .uri = "/",
    .method = HTTP_GET,
    .handler = index_handler,
    .user_ctx = NULL
  };

  httpd_uri_t stream_uri = {
    .uri = "/stream",
    .method = HTTP_GET,
    .handler = stream_handler,
    .user_ctx = NULL
  };

  httpd_register_uri_handler(camera_httpd, &index_uri);
  httpd_register_uri_handler(camera_httpd, &stream_uri);
}

bool initCamera() {
  camera_config_t config;
  memset(&config, 0, sizeof(config));

  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;

  config.pin_d0 = CAM_D0;
  config.pin_d1 = CAM_D1;
  config.pin_d2 = CAM_D2;
  config.pin_d3 = CAM_D3;
  config.pin_d4 = CAM_D4;
  config.pin_d5 = CAM_D5;
  config.pin_d6 = CAM_D6;
  config.pin_d7 = CAM_D7;

  config.pin_xclk = CAM_XCLK;
  config.pin_pclk = CAM_PCLK;
  config.pin_vsync = CAM_VSYNC;
  config.pin_href = CAM_HREF;
  config.pin_sccb_sda = CAM_SDA;
  config.pin_sccb_scl = CAM_SCL;
  config.pin_pwdn = CAM_PWDN;
  config.pin_reset = CAM_RESET;

  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  if (psramFound()) {
    config.frame_size = FRAMESIZE_VGA;      // 640x480
    config.jpeg_quality = 12;
    config.fb_count = 2;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.grab_mode = CAMERA_GRAB_LATEST;
  } else {
    config.frame_size = FRAMESIZE_QVGA;     // 320x240 fallback
    config.jpeg_quality = 14;
    config.fb_count = 1;
    config.fb_location = CAMERA_FB_IN_DRAM;
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("esp_camera_init FAIL: 0x%x\n", err);
    return false;
  }

  sensor_t* s = esp_camera_sensor_get();
  if (s) {
    Serial.printf("Camera PID: 0x%04X\n", s->id.PID);
    // First bring-up: keep conservative defaults.
    s->set_framesize(s, psramFound() ? FRAMESIZE_VGA : FRAMESIZE_QVGA);
  }

  return true;
}

void setup() {
  Serial.begin(115200);
  delay(2000);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ESP32-S3 OV5640 LIVE VIDEO TEST");
  Serial.println("========================================");
  Serial.printf("PSRAM: %s\n", psramFound() ? "PASS" : "NOT FOUND");

  if (!initCamera()) {
    Serial.println("CAMERA INIT: FAIL");
    while (true) delay(1000);
  }

  Serial.println("CAMERA INIT: PASS");

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  Serial.print("Connecting Wi-Fi");
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) {
    Serial.print('.');
    delay(500);
  }
  Serial.println();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WIFI: FAIL");
    while (true) delay(1000);
  }

  Serial.println("WIFI: PASS");
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());
  Serial.print("RSSI: ");
  Serial.print(WiFi.RSSI());
  Serial.println(" dBm");

  startCameraServer();

  Serial.println();
  Serial.println("Open this address on a device connected to the same LAN:");
  Serial.print("http://");
  Serial.print(WiFi.localIP());
  Serial.println("/");

  Serial.print("Direct MJPEG stream: http://");
  Serial.print(WiFi.localIP());
  Serial.println("/stream");

  Serial.println("========================================");
  Serial.println("VIDEO SERVER READY");
  Serial.println("========================================");
}

void loop() {
  static unsigned long last = 0;
  if (millis() - last >= 5000) {
    last = millis();
    Serial.print("alive | WiFi RSSI=");
    Serial.print(WiFi.RSSI());
    Serial.print(" dBm | free heap=");
    Serial.print(ESP.getFreeHeap());
    Serial.print(" | free PSRAM=");
    Serial.println(ESP.getFreePsram());
  }
}
