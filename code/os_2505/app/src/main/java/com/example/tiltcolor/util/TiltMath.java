package com.example.tiltcolor.util;

/**
 * 角度計算の簡易ユーティリティ。
 *  - applyBaseline(): 現在の角度から基準角を引いて補正する。
 *  - normalizeAngle(): 将来用（-180〜180°範囲に正規化）
 */
public final class TiltMath {

    private TiltMath() {} // インスタンス化禁止

    /**
     * ピッチ角を基準角（baseline）で補正。
     * 例: 基準が +10° のとき、raw=+15° → 結果=+5°
     */
    public static float applyBaseline(float rawPitchDeg, float baselineDeg) {
        return rawPitchDeg - baselineDeg;
    }

    /**
     * 角度を -180〜+180° の範囲に正規化（未使用だが将来拡張用）。
     */
    public static float normalizeAngle(float deg) {
        float a = deg % 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }
}
