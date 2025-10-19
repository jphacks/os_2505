// 例: 一時フェイク（必要なら sensor/FakePoseProvider.java として保存）
package com.example.tiltcolor.sensor;

import android.os.Handler;
import android.os.Looper;
import com.example.tiltcolor.domain.PoseData;

public class FakePoseProvider implements PoseProvider {
    private final Handler h = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean running = false;
    private float t = 0f;
    private PoseData last = new PoseData(0,0,0,System.currentTimeMillis());

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            float pitch = (float)(-20f * Math.sin(t)); // -20〜+20°往復
            last = new PoseData(pitch, 0f, 0f, System.currentTimeMillis());
            if (listener != null) listener.onPose(last);
            t += 0.05f;
            h.postDelayed(this, 16);
        }
    };

    @Override public void start() { running = true; h.post(loop); }
    @Override public void stop() { running = false; h.removeCallbacks(loop); }
    @Override public PoseData getLastPose() { return last; }
    @Override public void setListener(Listener l) { this.listener = l; }
}
