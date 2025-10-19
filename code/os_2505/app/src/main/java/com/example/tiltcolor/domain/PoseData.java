package com.example.tiltcolor.domain;

/**
 * 端末姿勢のスナップショット（度数・UNIXミリ秒）
 * 規約:
 *  - pitch: 正面=0°, 下向きほど負方向（deg）
 *  - roll, yaw: 将来拡張用（deg）
 *  - tilt: 水平面からの角度（0°=水平, 90°=縦持ち）
 */
public final class PoseData {
    public final float pitchDeg;
    public final float rollDeg;
    public final float yawDeg;
    public final float tiltDeg;
    public final long tsMillis;

    public PoseData(float pitchDeg, float rollDeg, float yawDeg, float tiltDeg, long tsMillis) {
        this.pitchDeg = pitchDeg;
        this.rollDeg = rollDeg;
        this.yawDeg = yawDeg;
        this.tiltDeg = tiltDeg;
        this.tsMillis = tsMillis;
    }

    // 旧形式互換（Fakeや既存コードから呼べるように）
    public PoseData(float pitchDeg, float rollDeg, float yawDeg, long tsMillis) {
        this(pitchDeg, rollDeg, yawDeg, 0f, tsMillis);
    }

    @Override public String toString() {
        return "PoseData{pitch=" + pitchDeg +
                ", roll=" + rollDeg +
                ", yaw=" + yawDeg +
                ", tilt=" + tiltDeg +
                ", ts=" + tsMillis + "}";
    }
}
