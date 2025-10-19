package com.example.tiltcolor.sensor;

import android.os.Handler;
import android.os.Looper;

import com.example.tiltcolor.domain.PoseData;

/**
 * AのSensorRepositoryが未実装でも動作確認できるダミーセンサー。
 * pitch角を -25°〜+10° の範囲で往復させ、一定周期でPoseDataを通知する。
 */
public class FakeSensorRepository implements PoseProvider {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private float t = 0f;
    private PoseData lastPose = new PoseData(0f, 0f, 0f, System.currentTimeMillis());
    private Listener listener;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            // 角度を滑らかに往復させる（周期およそ 2π / 0.05 = 約125フレーム ≒ 2秒）
            float pitch = (float) (-17.5f * Math.sin(t) - 7.5f);
            lastPose = new PoseData(pitch, 0f, 0f, System.currentTimeMillis());

            if (listener != null) listener.onPose(lastPose);

            t += 0.05f;
            handler.postDelayed(this, 16);  // 約60Hzで更新
        }
    };

    @Override
    public void start() {
        if (running) return;
        running = true;
        handler.post(loop);
    }

    @Override
    public void stop() {
        running = false;
        handler.removeCallbacks(loop);
    }

    @Override
    public PoseData getLastPose() {
        return lastPose;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
