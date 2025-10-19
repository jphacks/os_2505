package com.example.tiltcolor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import androidx.core.content.ContextCompat;

import com.example.tiltcolor.MainActivity;
import com.example.tiltcolor.R;
import com.example.tiltcolor.domain.PoseData;
import com.example.tiltcolor.sensor.PoseProvider;
import com.example.tiltcolor.sensor.SensorRepository;
import com.example.tiltcolor.util.Hysteresis;
import com.example.tiltcolor.util.TiltMath;
import com.example.tiltcolor.motion.MotionDetector;

public class GuardService extends Service {

    // === MainActivity との“ON/OFF共有” ===
    public static final String PREFS = "tilt_guard_prefs";
    public static final String KEY_ENABLED = "enabled";
    public static final String ACTION_UPDATE_ENABLED = "com.example.tiltcolor.ACTION_UPDATE_ENABLED";

    private static final String CHANNEL_ID = "tilt_guard_channel";
    private static final int NOTIF_ID = 1001;

    // しきい値（必要に応じて調整）
    private static final float HIDE_THRESHOLD = 65f;
    private static final float SHOW_THRESHOLD = 65f;
    private static final long  DEBOUNCE_MS    = 300;
    private static final float BASELINE_PITCH = 0f;

    private PoseProvider pose;
    private Hysteresis hysteresis;
    private long downSince = -1L, frontSince = -1L;
    private boolean isDown = false;

    // 歩行判定
    private MotionDetector motionDetector;
    private boolean isMoving = false;

    // ON/OFF
    private boolean enabled = true;

    // オーバーレイ
    private WindowManager wm;
    private View overlayView;
    private boolean overlayShown = false;

    // 設定更新ブロードキャスト受信
    private final BroadcastReceiver settingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
            updateOverlayState(); // すぐ反映
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotification("監視中（前を向いて歩きましょう）"));

        // 現在のON/OFFをロード
        enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, true);

        // 姿勢センサー
        hysteresis = new Hysteresis(HIDE_THRESHOLD, SHOW_THRESHOLD);
        pose = new SensorRepository(this);
        pose.setListener(this::onPose);
        pose.start();

        // 歩行検出
        motionDetector = new MotionDetector(this);
        motionDetector.setListener((moving, rms) -> {
            isMoving = moving;
            updateOverlayState();
        });
        motionDetector.start();

        // オーバーレイ
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 設定更新の購読（API33+ はフラグ必須）
        // 設定更新の購読（常に flags 付きの registerReceiver を使う）
        IntentFilter f = new IntentFilter(ACTION_UPDATE_ENABLED);

        // API33+ は NOT_EXPORTED を必須、33未満は 0 でOK（互換）
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Context.RECEIVER_NOT_EXPORTED  // 自アプリ内専用
                : 0;

        registerReceiver(settingReceiver, f, flags);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        super.onDestroy();
        if (pose != null) pose.stop();
        if (motionDetector != null) motionDetector.stop();
        try { unregisterReceiver(settingReceiver); } catch (Exception ignored) {}
        hideOverlay();
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

        Log.d("GuardService", String.format(
                "pitch=%.1f isDown=%s isMoving=%s enabled=%s",
                adjPitch, isDown, isMoving, enabled));

        updateOverlayState();
    }

    /** ON/OFF と 条件（歩行AND下向き）でオーバーレイを出し入れ */
    private void updateOverlayState() {
        boolean shouldBlock = enabled && isMoving && isDown;
        if (shouldBlock && !overlayShown) {
            showOverlay();
        } else if (!shouldBlock && overlayShown) {
            hideOverlay();
        }
        // 通知文言もON/OFFに合わせて更新
        if (!enabled) {
            updateNotification("無効：ブロックしません");
        } else if (overlayShown) {
            updateNotification("下向き＋歩行中：画面をロック中");
        } else {
            updateNotification("監視中（前を向いて歩きましょう）");
        }
    }

    private void showOverlay() {
        if (overlayShown) return;
        if (!Settings.canDrawOverlays(this)) return;

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setOnTouchListener((v, e) -> true); // タッチ吸収

        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(R.drawable.look_up);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);

        int sideMarginPx = (int) (getResources().getDisplayMetrics().density * 24);
        android.widget.FrameLayout.LayoutParams ivLp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        ivLp.gravity = Gravity.CENTER;
        ivLp.setMargins(sideMarginPx, sideMarginPx, sideMarginPx, sideMarginPx);
        root.addView(iv, ivLp);

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
            wm.addView(root, lp);
            overlayView = root;
            overlayShown = true;
        } catch (Throwable ignore) {}
    }

    private void hideOverlay() {
        if (!overlayShown) return;
        if (overlayView != null) {
            try { wm.removeView(overlayView); } catch (Throwable ignored) {}
            overlayView = null;
        }
        overlayShown = false;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Tilt Guard", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, n);
    }
}
