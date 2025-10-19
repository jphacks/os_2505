package com.example.tiltcolor.motion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import java.util.ArrayDeque;

/**
 * 加速度のみで「動いている/止まっている」を判定するユーティリティ。
 * - TYPE_LINEAR_ACCELERATION を優先。無ければ ACC - gravity（ローパス）で線形加速度を推定。
 * - 窓内RMS + ヒステリシス + 持続時間で安定判定。
 */
public class MotionDetector implements SensorEventListener {

    // ===== 窓・しきい値・持続（必要に応じて調整） =====
    private static final long WINDOW_MS = 1500;     // RMS窓幅
    private static final long HOLD_MS   = 500;      // 状態切替に必要な持続
    private static final double THRESH_MOVE = 0.30; // 上回り続け→MOVING
    private static final double THRESH_STILL = 0.30;// 下回り続け→STILL

    public interface Listener {
        void onMotionState(boolean moving, double rms);
    }

    private final SensorManager sm;
    private final Sensor linearAcc, acc;
    private boolean useLinear;
    private Listener listener;

    // 重力ローパス
    private final float[] g = new float[3];
    private boolean gInit = false;

    private static class Sample { long t; double v; Sample(long t, double v){ this.t=t; this.v=v; } }
    private final ArrayDeque<Sample> window = new ArrayDeque<>();

    private enum State { MOVING, STILL }
    private State state = State.STILL;
    private Boolean candidateMoving = null;
    private long candidateStart = 0L;

    public MotionDetector(Context ctx) {
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        linearAcc = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        acc       = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        useLinear = (linearAcc != null);
    }

    public void setListener(Listener l) { this.listener = l; }

    public void start() {
        if (useLinear && linearAcc != null) {
            sm.registerListener(this, linearAcc, SensorManager.SENSOR_DELAY_GAME);
        } else if (acc != null) {
            sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
            useLinear = false;
        }
    }

    public void stop() { sm.unregisterListener(this); }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent e) {
        int type = e.sensor.getType();
        if (useLinear && type != Sensor.TYPE_LINEAR_ACCELERATION) return;
        if (!useLinear && type != Sensor.TYPE_ACCELEROMETER) return;

        long now = SystemClock.elapsedRealtime();

        // 線形加速度を取得/推定
        double lx, ly, lz;
        if (useLinear) {
            lx = e.values[0]; ly = e.values[1]; lz = e.values[2];
        } else {
            final float alpha = 0.9f;
            if (!gInit) {
                System.arraycopy(e.values, 0, g, 0, 3);
                gInit = true;
            } else {
                g[0] = alpha * g[0] + (1 - alpha) * e.values[0];
                g[1] = alpha * g[1] + (1 - alpha) * e.values[1];
                g[2] = alpha * g[2] + (1 - alpha) * e.values[2];
            }
            lx = e.values[0] - g[0];
            ly = e.values[1] - g[1];
            lz = e.values[2] - g[2];
        }

        // |a| を窓へ
        double mag = Math.sqrt(lx*lx + ly*ly + lz*lz);
        window.addLast(new Sample(now, mag));
        while (!window.isEmpty() && (now - window.peekFirst().t) > WINDOW_MS) {
            window.removeFirst();
        }

        // RMS
        double rms = computeRms();

        // ヒステリシス + 持続
        boolean movingNow = (state == State.MOVING) ? !(rms < THRESH_STILL) : (rms > THRESH_MOVE);

        if (candidateMoving == null || candidateMoving != movingNow) {
            candidateMoving = movingNow;
            candidateStart = now;
        } else if ((now - candidateStart) >= HOLD_MS) {
            State newState = movingNow ? State.MOVING : State.STILL;
            if (newState != state) state = newState;
        }

        if (listener != null) listener.onMotionState(state == State.MOVING, rms);
    }

    private double computeRms() {
        if (window.isEmpty()) return 0.0;
        double sumSq = 0.0;
        for (Sample s : window) sumSq += s.v * s.v;
        return Math.sqrt(sumSq / window.size());
    }
}
