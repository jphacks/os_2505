package com.example.tiltcolor.util;

/**
 * 2値ヒステリシス判定器。
 *  - x < low  → true（DOWN）
 *  - x > high → false（FRONT）
 *  - その間は直前の状態を保持。
 */
public final class Hysteresis {

    private final float low;
    private final float high;
    private boolean state = false; // false=FRONT, true=DOWN

    public Hysteresis(float low, float high) {
        this.low = low;
        this.high = high;
    }

    /** 現在の値から次の状態を返す */
    public boolean next(float x) {
        if (!state && x < low) {
            state = true;
        } else if (state && x > high) {
            state = false;
        }
        return state;
    }
}
