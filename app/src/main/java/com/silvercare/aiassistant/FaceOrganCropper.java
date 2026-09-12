package com.silvercare.aiassistant;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

/**
 * BeautySense 本地小图像与五官切片分析工具
 * 负责快速从全分辨率相机画面中裁剪局部五官 (lips, brows, cheeks) 高清切片，
 * 并提供端侧快速唇线溢出与上色均匀度判定。
 */
final class FaceOrganCropper {

    static final class CropResult {
        final String organType;
        final String croppedDataUrl;
        final int cropWidth;
        final int cropHeight;
        final JSONObject localMetrics; // 局部质检初筛指标

        CropResult(String organType, String croppedDataUrl, int cropWidth, int cropHeight, JSONObject localMetrics) {
            this.organType = organType;
            this.croppedDataUrl = croppedDataUrl;
            this.cropWidth = cropWidth;
            this.cropHeight = cropHeight;
            this.localMetrics = localMetrics;
        }
    }

    /**
     * 根据五官类型裁剪局部图片，并进行端侧色彩与对称性初筛
     */
    static CropResult cropOrgan(String imageDataUrl, String organType) {
        if (imageDataUrl == null || !imageDataUrl.contains(",")) {
            return null;
        }

        try {
            String base64Data = imageDataUrl.substring(imageDataUrl.indexOf(",") + 1);
            byte[] decoded = Base64.decode(base64Data, Base64.DEFAULT);
            Bitmap fullBitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            if (fullBitmap == null) return null;

            int width = fullBitmap.getWidth();
            int height = fullBitmap.getHeight();

            // 根据五官解剖比例计算裁剪区域 (归一化预估基准，居中对准人脸五官)
            int cropX, cropY, cropW, cropH;

            switch (organType.toLowerCase()) {
                case "lips":
                case "mouth":
                    // 嘴唇区域：位于人脸中下部 (X: 32%~68%, Y: 56%~78%)
                    cropX = (int) (width * 0.32);
                    cropY = (int) (height * 0.56);
                    cropW = (int) (width * 0.36);
                    cropH = (int) (height * 0.22);
                    break;

                case "eyebrows":
                case "eyebrow_right":
                    // 右眉区域 (画面右侧视角): (X: 48%~78%, Y: 26%~42%)
                    cropX = (int) (width * 0.48);
                    cropY = (int) (height * 0.26);
                    cropW = (int) (width * 0.30);
                    cropH = (int) (height * 0.16);
                    break;

                case "eyebrow_left":
                    // 左眉区域 (画面左侧视角): (X: 22%~52%, Y: 26%~42%)
                    cropX = (int) (width * 0.22);
                    cropY = (int) (height * 0.26);
                    cropW = (int) (width * 0.30);
                    cropH = (int) (height * 0.16);
                    break;

                case "cheeks":
                case "cheek_right":
                    // 右面颊苹果肌区域: (X: 52%~82%, Y: 46%~66%)
                    cropX = (int) (width * 0.52);
                    cropY = (int) (height * 0.46);
                    cropW = (int) (width * 0.30);
                    cropH = (int) (height * 0.20);
                    break;

                default:
                    // 默认整脸居中区域 (X: 20%~80%, Y: 18%~82%)
                    cropX = (int) (width * 0.20);
                    cropY = (int) (height * 0.18);
                    cropW = (int) (width * 0.60);
                    cropH = (int) (height * 0.64);
                    break;
            }

            // 安全边界约束
            cropX = Math.max(0, Math.min(width - 1, cropX));
            cropY = Math.max(0, Math.min(height - 1, cropY));
            cropW = Math.max(10, Math.min(width - cropX, cropW));
            cropH = Math.max(10, Math.min(height - cropY, cropH));

            Bitmap croppedBitmap = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH);

            // 进行端侧轻量级色彩与边缘饱和度分析 (Face Parsing 边缘初筛)
            JSONObject localMetrics = analyzeOrganPatch(croppedBitmap, organType);

            // 压缩为局部高质量 JPEG Base64
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
            byte[] croppedBytes = outputStream.toByteArray();
            String croppedDataUrl = "data:image/jpeg;base64," + Base64.encodeToString(croppedBytes, Base64.NO_WRAP);

            fullBitmap.recycle();
            croppedBitmap.recycle();

            return new CropResult(organType, croppedDataUrl, cropW, cropH, localMetrics);
        } catch (Exception e) {
            DiagnosticLogger.event("crop_organ_error", new JSONObject());
            return null;
        }
    }

    /**
     * 端侧快速评估切片：计算红色通道饱和度分布、对称性粗估与边缘溢出风险
     */
    private static JSONObject analyzeOrganPatch(Bitmap bitmap, String organType) {
        JSONObject metrics = new JSONObject();
        try {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int sampleStep = Math.max(1, w / 40); // 稀疏采样保证毫秒级执行

            long totalRed = 0, totalGreen = 0, totalBlue = 0;
            int redProminentPixels = 0;
            int leftRedPixels = 0, rightRedPixels = 0;
            int borderRedPixels = 0; // 边缘区域的红色像素 (溢出风险)

            int totalSamples = 0;
            for (int y = 0; y < h; y += sampleStep) {
                for (int x = 0; x < w; x += sampleStep) {
                    int pixel = bitmap.getPixel(x, y);
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);

                    totalRed += r;
                    totalGreen += g;
                    totalBlue += b;
                    totalSamples++;

                    // 红色显色度判断 (口红/腮红特征)
                    boolean isReddish = r > (g + 20) && r > (b + 20) && r > 90;
                    if (isReddish) {
                        redProminentPixels++;
                        if (x < w / 2) leftRedPixels++;
                        else rightRedPixels++;

                        // 如果在图片四周边缘出现高饱和红色，代表可能涂抹溢出
                        if (x < w * 0.12 || x > w * 0.88 || y < h * 0.12 || y > h * 0.88) {
                            borderRedPixels++;
                        }
                    }
                }
            }

            double redCoverage = totalSamples > 0 ? (double) redProminentPixels / totalSamples : 0;
            double symmetryRatio = (leftRedPixels + rightRedPixels) > 0
                ? (double) Math.min(leftRedPixels, rightRedPixels) / Math.max(leftRedPixels, rightRedPixels)
                : 1.0;
            double overflowRatio = redProminentPixels > 0 ? (double) borderRedPixels / redProminentPixels : 0;

            metrics.put("coverage_ratio", Math.round(redCoverage * 100.0) / 100.0);
            metrics.put("symmetry_ratio", Math.round(symmetryRatio * 100.0) / 100.0);
            metrics.put("overflow_risk", overflowRatio > 0.15 ? "high" : (overflowRatio > 0.06 ? "medium" : "low"));
            metrics.put("avg_redness", totalSamples > 0 ? (int) (totalRed / totalSamples) : 0);
        } catch (Exception ignored) {
        }
        return metrics;
    }
}
