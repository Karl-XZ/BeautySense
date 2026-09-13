/**
 * BeautySense - Vision Overlay & Intelligence Layer Renderer
 * Renders YOLOv8 cosmetic boxes, MediaPipe face mesh, FaceOrganCropper organ ROIs,
 * and visual quality inspection defect overlays over real camera preview images.
 */

class BeautySenseVisionEngine {
    constructor() {
        this.canvas = null;
        this.ctx = null;
        this.img = null;
        this.cam = null;
        this.currentMode = null;
        this.currentData = null;
        this.init();
    }

    init() {
        this.canvas = document.getElementById('visionCanvas');
        this.img = document.getElementById('camImg');
        this.cam = document.getElementById('cam');

        if (this.canvas) {
            this.ctx = this.canvas.getContext('2d');
            this.resize();
            window.addEventListener('resize', () => this.resize());
        }
    }

    resize() {
        if (!this.canvas) return;
        const dpr = window.devicePixelRatio || 1;
        this.canvas.width = window.innerWidth * dpr;
        this.canvas.height = window.innerHeight * dpr;
        if (this.ctx) {
            this.ctx.scale(dpr, dpr);
        }
        if (this.currentMode) {
            this.render(this.currentMode, this.currentData);
        }
    }

    setCameraImage(imageDataUrl) {
        if (!this.img) this.img = document.getElementById('camImg');
        if (!this.cam) this.cam = document.getElementById('cam');

        if (this.img && imageDataUrl) {
            this.img.src = imageDataUrl;
            this.img.style.display = 'block';
            if (this.cam) this.cam.style.display = 'none';
        }
    }

    clear() {
        this.currentMode = null;
        this.currentData = null;
        if (!this.ctx || !this.canvas) return;
        const dpr = window.devicePixelRatio || 1;
        this.ctx.clearRect(0, 0, this.canvas.width / dpr, this.canvas.height / dpr);
    }

    render(mode, data = {}) {
        this.currentMode = mode;
        this.currentData = data;
        if (!this.ctx || !this.canvas) return;
        const dpr = window.devicePixelRatio || 1;
        const w = this.canvas.width / dpr;
        const h = this.canvas.height / dpr;
        this.ctx.clearRect(0, 0, w, h);

        switch (mode) {
            case 'cosmetics':
                this.renderCosmeticsDetection(w, h, data);
                break;
            case 'face_landmarks':
                this.renderFaceLandmarks(w, h, data);
                break;
            case 'quality_inspection':
                this.renderQualityInspection(w, h, data);
                break;
            case 'spatial_audio_alignment':
                this.renderSpatialAudioAlignment(w, h, data);
                break;
            default:
                break;
        }
    }

    // --- Helper: Draw Tech Corner Bounding Box ---
    drawTechBox(x, y, w, h, color, label, meta = '', isTarget = false) {
        const ctx = this.ctx;
        const cornerLen = Math.min(18, w * 0.2, h * 0.2);

        // Semi-transparent background
        ctx.fillStyle = isTarget ? 'rgba(244, 63, 94, 0.14)' : 'rgba(15, 23, 42, 0.28)';
        ctx.fillRect(x, y, w, h);

        // Glowing border
        ctx.save();
        ctx.strokeStyle = color;
        ctx.lineWidth = isTarget ? 2.5 : 1.8;
        ctx.shadowColor = color;
        ctx.shadowBlur = isTarget ? 10 : 6;

        // Draw 4 corners
        ctx.beginPath();
        // Top-left
        ctx.moveTo(x, y + cornerLen);
        ctx.lineTo(x, y);
        ctx.lineTo(x + cornerLen, y);
        // Top-right
        ctx.moveTo(x + w - cornerLen, y);
        ctx.lineTo(x + w, y);
        ctx.lineTo(x + w, y + cornerLen);
        // Bottom-right
        ctx.moveTo(x + w, y + h - cornerLen);
        ctx.lineTo(x + w, y + h);
        ctx.lineTo(x + w - cornerLen, y + h);
        // Bottom-left
        ctx.moveTo(x + cornerLen, y + h);
        ctx.lineTo(x, y + h);
        ctx.lineTo(x, y + h - cornerLen);
        ctx.stroke();
        ctx.restore();

        // Label Badge
        if (label) {
            ctx.save();
            ctx.font = 'bold 12px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
            const paddingX = 8;
            const badgeH = 22;
            const textWidth = ctx.measureText(label).width;
            const badgeW = textWidth + paddingX * 2;
            const badgeY = Math.max(10, y - badgeH - 4);

            // Badge bg
            ctx.fillStyle = isTarget ? 'rgba(244, 63, 94, 0.92)' : 'rgba(15, 23, 42, 0.82)';
            ctx.beginPath();
            ctx.roundRect(x, badgeY, badgeW, badgeH, 4);
            ctx.fill();

            // Badge text
            ctx.fillStyle = '#ffffff';
            ctx.fillText(label, x + paddingX, badgeY + 15);

            // Meta text below label if present
            if (meta) {
                ctx.font = '10px -apple-system, BlinkMacSystemFont, sans-serif';
                ctx.fillStyle = isTarget ? '#ffcdd2' : 'rgba(255, 255, 255, 0.85)';
                ctx.fillText(meta, x + badgeW + 6, badgeY + 15);
            }
            ctx.restore();
        }
    }

    // --- Mode 1: Cosmetics Detection (YOLOv8) ---
    renderCosmeticsDetection(w, h, data) {
        const ctx = this.ctx;

        // Top Vision Badge
        this.drawTopBadge(w, '👁️ YOLOv8 视觉检测 · 已精准识别 4 类梳妆台美妆资产');

        // Bounding Box 1: YSL 小金条口红 (Target)
        const bx1 = w * 0.56;
        const by1 = h * 0.44;
        const bw1 = w * 0.30;
        const bh1 = h * 0.32;
        this.drawTechBox(bx1, by1, bw1, bh1, '#f43f5e', '💄 YSL 小金条 #1966', '98% · 距右手15cm', true);

        // Targeting reticle & pointer line on YSL
        ctx.save();
        ctx.strokeStyle = '#f43f5e';
        ctx.lineWidth = 1.5;
        ctx.setLineDash([4, 4]);
        ctx.beginPath();
        ctx.arc(bx1 + bw1 / 2, by1 + bh1 / 2, 26, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(bx1 + bw1 / 2, by1 + bh1 / 2, 4, 0, Math.PI * 2);
        ctx.fillStyle = '#f43f5e';
        ctx.fill();
        ctx.restore();

        // Bounding Box 2: 兰蔻持妆粉底液
        const bx2 = w * 0.22;
        const by2 = h * 0.34;
        const bw2 = w * 0.28;
        const bh2 = h * 0.38;
        this.drawTechBox(bx2, by2, bw2, bh2, '#fbbf24', '🧴 兰蔻持妆粉底液', '96% · 距右手28cm');

        // Bounding Box 3: 植村秀砍刀眉笔
        const bx3 = w * 0.04;
        const by3 = h * 0.46;
        const bw3 = w * 0.18;
        const bh3 = h * 0.30;
        this.drawTechBox(bx3, by3, bw3, bh3, '#2dd4bf', '✏️ 砍刀眉笔 #灰棕', '93% · 距右手12cm');

        // Bounding Box 4: 纪梵希散粉
        const bx4 = w * 0.65;
        const by4 = h * 0.18;
        const bw4 = w * 0.26;
        const bh4 = h * 0.22;
        this.drawTechBox(bx4, by4, bw4, bh4, '#c084fc', '🪞 幻彩轻盈散粉', '94% · 距右手34cm');
    }

    // --- Mode 2: MediaPipe 468 Face Mesh & Organ Cropper ---
    renderFaceLandmarks(w, h, data) {
        const ctx = this.ctx;
        this.drawTopBadge(w, '🎯 MediaPipe 468点面部网格 & FaceOrganCropper 区域定位');

        const cx = w * 0.50;
        const cy = h * 0.42;
        const rx = w * 0.28;
        const ry = h * 0.24;

        // Face Oval Outline
        ctx.save();
        ctx.strokeStyle = 'rgba(244, 114, 182, 0.45)';
        ctx.lineWidth = 1.5;
        ctx.setLineDash([6, 4]);
        ctx.beginPath();
        ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();

        // Eyebrows Organ Crop Box
        const browX = cx - rx * 0.85;
        const browY = cy - ry * 0.55;
        const browW = rx * 1.7;
        const browH = ry * 0.38;
        this.drawTechBox(browX, browY, browW, browH, '#38bdf8', '眉弓对称分析区', 'Symmetry: 0.96');

        // Lips Organ Crop Box
        const lipX = cx - rx * 0.55;
        const lipY = cy + ry * 0.32;
        const lipW = rx * 1.1;
        const lipH = ry * 0.40;
        this.drawTechBox(lipX, lipY, lipW, lipH, '#fb7185', '唇部上妆切片 ROI', '待上妆');

        // Golden Ratio Center Midline
        ctx.save();
        ctx.strokeStyle = 'rgba(251, 191, 36, 0.4)';
        ctx.lineWidth = 1;
        ctx.setLineDash([2, 4]);
        ctx.beginPath();
        ctx.moveTo(cx, cy - ry * 1.15);
        ctx.lineTo(cx, cy + ry * 1.15);
        ctx.stroke();
        ctx.restore();

        // 468 Landmark Mesh Dots (Selected facial feature landmarks)
        ctx.save();
        ctx.fillStyle = 'rgba(255, 255, 255, 0.65)';
        const landmarkPoints = [
            // Left eyebrow
            [cx - rx * 0.65, cy - ry * 0.42], [cx - rx * 0.50, cy - ry * 0.48], [cx - rx * 0.35, cy - ry * 0.50], [cx - rx * 0.20, cy - ry * 0.44],
            // Right eyebrow
            [cx + rx * 0.20, cy - ry * 0.44], [cx + rx * 0.35, cy - ry * 0.50], [cx + rx * 0.50, cy - ry * 0.48], [cx + rx * 0.65, cy - ry * 0.42],
            // Nose bridge & tip
            [cx, cy - ry * 0.30], [cx, cy - ry * 0.10], [cx, cy + ry * 0.08], [cx - rx * 0.15, cy + ry * 0.12], [cx + rx * 0.15, cy + ry * 0.12],
            // Lips contours
            [cx - rx * 0.35, cy + ry * 0.50], [cx - rx * 0.18, cy + ry * 0.42], [cx, cy + ry * 0.44], [cx + rx * 0.18, cy + ry * 0.42], [cx + rx * 0.35, cy + ry * 0.50],
            [cx + rx * 0.20, cy + ry * 0.58], [cx, cy + ry * 0.60], [cx - rx * 0.20, cy + ry * 0.58],
            // Jawline
            [cx - rx * 0.80, cy + ry * 0.10], [cx - rx * 0.60, cy + ry * 0.65], [cx - rx * 0.30, cy + ry * 0.92], [cx, cy + ry * 1.02],
            [cx + rx * 0.30, cy + ry * 0.92], [cx + rx * 0.60, cy + ry * 0.65], [cx + rx * 0.80, cy + ry * 0.10],
        ];

        landmarkPoints.forEach(([px, py]) => {
            ctx.beginPath();
            ctx.arc(px, py, 2, 0, Math.PI * 2);
            ctx.fill();
        });
        ctx.restore();
    }

    // --- Mode 3: Quality Inspection & Defect Detection ---
    renderQualityInspection(w, h, data) {
        const ctx = this.ctx;
        this.drawTopBadge(w, '🔬 FaceOrganCropper 局部质检切片 · 唇线溢出与饱和度分析');

        const cx = w * 0.50;
        const cy = h * 0.44;
        const boxSize = Math.min(w * 0.72, h * 0.34);
        const bx = cx - boxSize / 2;
        const by = cy - boxSize / 2;

        // Crop ROI Box (256x256 crop view)
        this.drawTechBox(bx, by, boxSize, boxSize, '#22d3ee', '🔬 局部质检切片 (256×256)', 'Lips ROI Analysis', false);

        // Lip Contour Curve
        ctx.save();
        ctx.strokeStyle = '#34d399';
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 2]);
        ctx.beginPath();
        // Trace upper lip
        ctx.moveTo(cx - boxSize * 0.38, cy + boxSize * 0.05);
        ctx.quadraticCurveTo(cx - boxSize * 0.18, cy - boxSize * 0.18, cx, cy - boxSize * 0.10);
        ctx.quadraticCurveTo(cx + boxSize * 0.18, cy - boxSize * 0.18, cx + boxSize * 0.38, cy + boxSize * 0.05);
        ctx.stroke();
        ctx.restore();

        // Overflow Defect Marker (Upper Right Lip Peak)
        const defX = cx + boxSize * 0.08;
        const defY = cy - boxSize * 0.22;
        const defW = boxSize * 0.22;
        const defH = boxSize * 0.16;

        ctx.save();
        ctx.fillStyle = 'rgba(239, 68, 68, 0.28)';
        ctx.strokeStyle = '#ef4444';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.roundRect(defX, defY, defW, defH, 6);
        ctx.fill();
        ctx.stroke();

        // Defect Callout Arrow
        ctx.beginPath();
        ctx.moveTo(defX + defW, defY);
        ctx.lineTo(defX + defW + 24, defY - 24);
        ctx.lineTo(defX + defW + 60, defY - 24);
        ctx.stroke();

        ctx.font = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif';
        ctx.fillStyle = '#ef4444';
        ctx.fillText('⚠️ 溢出 +1.8mm', defX + defW + 28, defY - 30);
        ctx.restore();

        // Floating Inspection Scoreboard (Top Right)
        this.drawScorecard(w * 0.45, by - 64, [
            { label: '色彩饱和度', val: '84% (合格)', ok: true },
            { label: '边缘溢出度', val: '18% (未达标)', ok: false },
            { label: '质检结论', val: '需要微调补妆', ok: false }
        ]);
    }

    // --- Mode 4: Spatial Audio Alignment & Vector ---
    renderSpatialAudioAlignment(w, h, data) {
        const ctx = this.ctx;
        this.drawTopBadge(w, '🎧 双耳空间音频导向 · 唇峰对齐偏差实时解算');

        const cx = w * 0.50;
        const cy = h * 0.44;

        // Target Anchor: Upper Lip Peak (Bullseye)
        const tx = cx;
        const ty = cy - 20;

        ctx.save();
        // Golden Reticle Target
        ctx.strokeStyle = '#fbbf24';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(tx, ty, 18, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(tx, ty, 6, 0, Math.PI * 2);
        ctx.fillStyle = '#fbbf24';
        ctx.fill();

        // Reticle ticks
        ctx.beginPath();
        ctx.moveTo(tx - 24, ty); ctx.lineTo(tx - 10, ty);
        ctx.moveTo(tx + 10, ty); ctx.lineTo(tx + 24, ty);
        ctx.moveTo(tx, ty - 24); ctx.lineTo(tx, ty - 10);
        ctx.moveTo(tx, ty + 10); ctx.lineTo(tx, ty + 24);
        ctx.stroke();

        ctx.font = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif';
        ctx.fillStyle = '#fbbf24';
        ctx.fillText('🎯 唇峰目标锚点', tx - 38, ty - 30);
        ctx.restore();

        // Current Tool Position: offset by (-28, +15)
        const toolX = tx - 45;
        const toolY = ty + 30;

        ctx.save();
        ctx.fillStyle = '#f43f5e';
        ctx.beginPath();
        ctx.arc(toolX, toolY, 8, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = 'rgba(244, 63, 94, 0.4)';
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.arc(toolX, toolY, 16, 0, Math.PI * 2);
        ctx.stroke();

        ctx.font = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif';
        ctx.fillStyle = '#f43f5e';
        ctx.fillText('💄 唇膏尖端', toolX - 25, toolY + 28);
        ctx.restore();

        // Displacement Vector Arrow
        ctx.save();
        ctx.strokeStyle = '#38bdf8';
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 3]);
        ctx.beginPath();
        ctx.moveTo(toolX, toolY);
        ctx.lineTo(tx, ty);
        ctx.stroke();

        // Vector measurement label
        const midX = (toolX + tx) / 2;
        const midY = (toolY + ty) / 2;
        ctx.fillStyle = '#0284c7';
        ctx.beginPath();
        ctx.roundRect(midX - 55, midY - 14, 110, 20, 4);
        ctx.fill();
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 10px sans-serif';
        ctx.fillText('Δx: -28, Δy: +15', midX - 44, midY);
        ctx.restore();

        // Spatial Audio Wavefront (panning right to guide user to move right)
        ctx.save();
        ctx.strokeStyle = 'rgba(56, 189, 248, 0.6)';
        ctx.lineWidth = 2;
        for (let r = 1; r <= 3; r++) {
            ctx.beginPath();
            ctx.arc(toolX, toolY, r * 22, -Math.PI * 0.35, Math.PI * 0.35);
            ctx.stroke();
        }

        ctx.font = '11px -apple-system, BlinkMacSystemFont, sans-serif';
        ctx.fillStyle = '#38bdf8';
        ctx.fillText('右声道声像引导 🔊 540Hz', toolX + 75, toolY + 4);
        ctx.restore();
    }

    // --- Helper: Top Vision Badge (Disabled to keep top controls completely clean) ---
    drawTopBadge(w, text) {
        return;
    }

    // --- Helper: Scorecard Card ---
    drawScorecard(x, y, items) {
        const ctx = this.ctx;
        const w = 180;
        const h = 88;
        ctx.save();
        ctx.fillStyle = 'rgba(15, 23, 42, 0.88)';
        ctx.strokeStyle = 'rgba(56, 189, 248, 0.5)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.roundRect(x, y, w, h, 8);
        ctx.fill();
        ctx.stroke();

        ctx.font = 'bold 11px sans-serif';
        ctx.fillStyle = '#38bdf8';
        ctx.fillText('【FaceOrgan 质检打分】', x + 10, y + 20);

        items.forEach((item, idx) => {
            const itemY = y + 38 + idx * 16;
            ctx.font = '10px sans-serif';
            ctx.fillStyle = 'rgba(255, 255, 255, 0.8)';
            ctx.fillText(item.label + ':', x + 10, itemY);
            ctx.fillStyle = item.ok ? '#4ade80' : '#f87171';
            ctx.fillText(item.val, x + 72, itemY);
        });
        ctx.restore();
    }
}

// Global exposure
window.BeautySenseVision = new BeautySenseVisionEngine();
