/**
 * 银龄智护 Spatial Audio System
 * Provides audio feedback for navigation cues
 */

class SpatialAudio {
    constructor() {
        this.ctx = null;
        this.gain = null;
        // Micro Guidance Oscillators
        this.microOsc = null;
        this.microGain = null;
        this.microPan = null;
        this.isPlayingMicro = false;
    }

    init() {
        if (this.ctx) return;
        this.ctx = new (window.AudioContext || window.webkitAudioContext)();
        this.gain = this.ctx.createGain();
        this.gain.gain.value = 0.5;
        this.gain.connect(this.ctx.destination);
    }

    playTone(freq, type, dur) {
        if (!this.ctx) return;
        this.ctx.resume();
        const o = this.ctx.createOscillator();
        const g = this.ctx.createGain();
        o.type = type; o.frequency.value = freq;
        o.connect(g); g.connect(this.gain);
        g.gain.setValueAtTime(0.3, this.ctx.currentTime);
        g.gain.exponentialRampToValueAtTime(0.01, this.ctx.currentTime + dur);
        o.start(); o.stop(this.ctx.currentTime + dur);
    }

    // Continuous Micro-Guidance Tone
    startMicroTone() {
        if (!this.ctx || this.isPlayingMicro) return;
        this.ctx.resume();

        this.microOsc = this.ctx.createOscillator();
        this.microOsc.type = 'triangle'; // Softer than square, clearer than sine
        this.microOsc.frequency.value = 440; // Base C4

        this.microPan = this.ctx.createStereoPanner();
        this.microPan.pan.value = 0;

        this.microGain = this.ctx.createGain();
        this.microGain.gain.value = 0; // Start silent, fade in

        this.microOsc.connect(this.microPan);
        this.microPan.connect(this.microGain);
        this.microGain.connect(this.gain);

        this.microOsc.start();
        this.microGain.gain.linearRampToValueAtTime(0.2, this.ctx.currentTime + 0.5);
        this.isPlayingMicro = true;
    }

    updateMicroTone(x, y) {
        // x: -1 (left) to 1 (right)
        // y: -1 (down) to 1 (up) - assuming relative vector input
        if (!this.isPlayingMicro || !this.microOsc) return;

        // Map X to Pan
        // Clamp between -1 and 1
        const panVal = Math.max(-1, Math.min(1, x / 20));
        this.microPan.pan.setTargetAtTime(panVal, this.ctx.currentTime, 0.1);

        // Map Y to Pitch
        // Center (0) = 440Hz. Up (+Y) = Higher Pitch. Down (-Y) = Lower Pitch.
        const baseFreq = 440;
        const pitchShift = (y / 20) * 200;
        const targetFreq = Math.max(100, Math.min(1000, baseFreq + pitchShift));

        this.microOsc.frequency.setTargetAtTime(targetFreq, this.ctx.currentTime, 0.1);
    }

    stopMicroTone() {
        if (!this.isPlayingMicro || !this.microOsc) return;
        const now = this.ctx.currentTime;
        this.microGain.gain.cancelScheduledValues(now);
        this.microGain.gain.setValueAtTime(this.microGain.gain.value, now);
        this.microGain.gain.linearRampToValueAtTime(0, now + 0.2);

        this.microOsc.stop(now + 0.2);
        setTimeout(() => {
            this.microOsc = null;
            this.microPan = null;
            this.microGain = null;
            this.isPlayingMicro = false;
        }, 250);
    }

    playSonar(dir, dist, prio) {
        if (!this.ctx) return;
        const pan = Math.sin(this.parseDir(dir) * Math.PI / 180);
        const freq = prio === 'critical' ? 880 : 440;
        // Stereo Panner
        const panner = this.ctx.createStereoPanner();
        panner.pan.value = pan;

        const o = this.ctx.createOscillator();
        const g = this.ctx.createGain();
        o.frequency.value = freq;

        o.connect(g); g.connect(panner); panner.connect(this.gain);

        const now = this.ctx.currentTime;
        g.gain.setValueAtTime(0, now);
        g.gain.linearRampToValueAtTime(0.4, now + 0.05);
        g.gain.exponentialRampToValueAtTime(0.01, now + 0.2);

        o.start(now); o.stop(now + 0.2);
    }

    playTargetLocked(dir, dist) {
        // Fast high pitch ping
        this.playTone(1200, 'square', 0.1);
    }

    playSuccess() {
        // Happy chord for "Push Now"
        this.playTone(523.25, 'sine', 0.2); // C5
        setTimeout(() => this.playTone(659.25, 'sine', 0.2), 100); // E5
        setTimeout(() => this.playTone(783.99, 'sine', 0.4), 200); // G5
    }

    playGeigerClick(x, y) {
        // Simple, non-annoying click
        if (!this.ctx) return;
        const distance = Math.sqrt(x * x + y * y); // Proximity to center
        const max_dist = 140; // ~sqrt(100^2 + 100^2)
        const proximity = 1 - (Math.min(distance, max_dist) / max_dist); // 0 to 1, 1 is closer

        const o = this.ctx.createOscillator();
        const g = this.ctx.createGain();
        o.type = 'triangle';
        o.frequency.value = 1000 + (proximity * 800); // Pitch rises slightly as you get closer

        o.connect(g);
        g.connect(this.gain);

        const now = this.ctx.currentTime;
        g.gain.setValueAtTime(0.2, now);
        g.gain.exponentialRampToValueAtTime(0.01, now + 0.05);
        o.start(now);
        o.stop(now + 0.05);
    }

    parseDir(dir) {
        if (!dir) return 0;
        if (dir.includes('left')) return -45;
        if (dir.includes('right')) return 45;
        return 0;
    }

    /**
     * BeautySense 双耳立体声空间音频导向 (Stereo Spatial Audio Guidance)
     * 纯软件算法：硬件仅需普通摄像头与普通立体声扬声器/耳机。
     * 由摄像头视觉模型计算手部与五官相对偏差，再由双声道立体声平衡 (Stereo Pan) 与变频提示音指引空间方位与精细对齐。
     */
    playSpatialAudioPulse(x = 0, y = 0, isTargetZone = false) {
        if (!this.ctx) return;
        this.ctx.resume();
        const dist = Math.sqrt(x * x + y * y);
        const maxDist = 140;
        const proximity = 1 - (Math.min(dist, maxDist) / maxDist);

        const panVal = Math.max(-1, Math.min(1, x / 60));
        const panner = this.ctx.createStereoPanner ? this.ctx.createStereoPanner() : null;
        const o = this.ctx.createOscillator();
        const g = this.ctx.createGain();

        o.type = isTargetZone ? 'sine' : 'triangle';
        o.frequency.value = isTargetZone ? 1046.5 : (360 + (proximity * 520));

        if (panner) {
            panner.pan.setValueAtTime(panVal, this.ctx.currentTime);
            o.connect(g);
            g.connect(panner);
            panner.connect(this.gain);
        } else {
            o.connect(g);
            g.connect(this.gain);
        }

        const now = this.ctx.currentTime;
        const dur = isTargetZone ? 0.12 : 0.06;
        g.gain.setValueAtTime(0, now);
        g.gain.linearRampToValueAtTime(0.28, now + 0.01);
        g.gain.exponentialRampToValueAtTime(0.001, now + dur);

        o.start(now);
        o.stop(now + dur);
    }

    playAcousticRadarPulse(x = 0, y = 0, isTargetZone = false) {
        this.playSpatialAudioPulse(x, y, isTargetZone);
    }

    /**
     * 步骤完成或质检达标的正向和弦
     */
    playQualityPassed() {
        this.playSuccess();
    }

    /**
     * 质检未达标需要补涂或修正的提示音
     */
    playNeedsCorrection() {
        if (!this.ctx) return;
        this.ctx.resume();
        this.playTone(587.33, 'triangle', 0.12);
        setTimeout(() => this.playTone(392.00, 'sine', 0.22), 140);
    }
}

// Global instance
const spatialAudio = new SpatialAudio();
