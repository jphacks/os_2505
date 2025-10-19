package com.example.tiltcolor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.tiltcolor.MainActivity;
import com.example.tiltcolor.R;
import com.example.tiltcolor.domain.PoseData;
import com.example.tiltcolor.sensor.PoseProvider;
import com.example.tiltcolor.sensor.SensorRepository;
import com.example.tiltcolor.util.Hysteresis;
import com.example.tiltcolor.util.TiltMath;
import com.example.tiltcolor.motion.MotionDetector; // ★ 追加

public class GuardService extends Service {

    private static final String CHANNEL_ID = "tilt_guard_channel";
    private static final int NOTIF_ID = 1001;

    // 閾値（現在のあなたの値のまま維持。必要なら後で調整）
    private static final float HIDE_THRESHOLD = 70f;
    private static final float SHOW_THRESHOLD = 70f;
    private static final long  DEBOUNCE_MS    = 300;
    private static final float BASELINE_PITCH = 0f;

    private PoseProvider pose;
    private Hysteresis hysteresis;
    private long downSince = -1L, frontSince = -1L;
    private boolean isDown = false;

    // ★ 歩行判定
    private MotionDetector motionDetector;
    private boolean isMoving = false;

    // オーバーレイ
    private WindowManager wm;
    private View overlayView;
    private boolean overlayShown = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotification("監視中（前を向いて歩きましょう）"));

        // 姿勢
        hysteresis = new Hysteresis(HIDE_THRESHOLD, SHOW_THRESHOLD);
        pose = new SensorRepository(this);
        pose.setListener(this::onPose);
        pose.start();

        // ★ 歩行検出
        motionDetector = new MotionDetector(this);
        motionDetector.setListener((moving, rms) -> {
            isMoving = moving;
            evaluateOverlay(); // 歩行状態が変わったら再評価
        });
        motionDetector.start();

        // オーバーレイ
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        super.onDestroy();
        if (pose != null) pose.stop();
        if (motionDetector != null) motionDetector.stop(); // ★ 追加
        hideOverlay(); // 念のため消す
    }

    private void onPose(PoseData p) {
        float adjPitch = TiltMath.applyBaseline(p.pitchDeg, BASELINE_PITCH);

        long now = System.currentTimeMillis();
        boolean wantDown = hysteresis.next(adjPitch);
        if (wantDown) {
            if (downSince < 0) downSince = now;
            frontSince = -1L;
            if (now - downSince >= DEBOUNCE_MS) isDown = true;
        } else {
            if (frontSince < 0) frontSince = now;
            downSince = -1L;
            if (now - frontSince >= DEBOUNCE_MS) isDown = false;
        }

        Log.d("GuardService", String.format("pitch=%.1f isDown=%s isMoving=%s", adjPitch, isDown, isMoving));

        // ★ 姿勢が変わったら再評価
        evaluateOverlay();
    }

    // ★ AND 条件で制御（歩行中 かつ 下向き のときだけブロック）
    private void evaluateOverlay() {
        boolean shouldBlock = isMoving && isDown;
        if (shouldBlock && !overlayShown) {
            showOverlay();
        } else if (!shouldBlock && overlayShown) {
            hideOverlay();
        }
    }

    private void showOverlay() {
        if (overlayShown) return;
        if (!Settings.canDrawOverlays(this)) return; // 権限未付与なら出さない

        View v = new View(this);
        v.setBackgroundColor(0xFF000000);
        v.setOnTouchListener((view, event) -> true); // タッチを吸ってブロック

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;

        try {
            wm.addView(v, lp);
            overlayView = v;
            overlayShown = true;
            updateNotification("下向き＋歩行中：画面をロック中");
        } catch (Throwable t) {
            // 失敗時は黙って無視
        }
    }

    private void hideOverlay() {
        if (!overlayShown) return;
        if (overlayView != null) {
            try {
                wm.removeView(overlayView);
            } catch (Throwable ignored) {}
            overlayView = null;
        }
        overlayShown = false;
        updateNotification("監視中（前を向いて歩きましょう）");
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Tilt Guard", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Tilt Guard")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        Notification n = buildNotification(text);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, n);
    }
}
