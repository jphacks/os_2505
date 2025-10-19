package com.example.tiltcolor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.tiltcolor.domain.PoseData;
import com.example.tiltcolor.sensor.PoseProvider;
import com.example.tiltcolor.sensor.SensorRepository;
import com.example.tiltcolor.service.GuardService;
import com.example.tiltcolor.util.Hysteresis;
import com.example.tiltcolor.util.TiltMath;
import android.net.Uri;
import android.provider.Settings;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 1000;

    private View root;
    private View cover;          // 前面カバー（MainActivity前面時だけ有効）
    private TextView tvInfo;     // デバッグ表示（任意）

    private PoseProvider pose;
    private Hysteresis hysteresis;
    private long downSince = -1L, frontSince = -1L;
    private boolean isDown = false;
    private Boolean lastIsDown = null;

    // しきい値（正面=0°, 下向きほど負 の前提）
    // ※ 以前は HIDE/SHOW が同値(70/70)でヒステリシスが効いていませんでした
    private static final float HIDE_THRESHOLD = 70f;  // これ未満で「下向き」
    private static final float SHOW_THRESHOLD =  70f;  // これ超で「正面」
    private static final long  DEBOUNCE_MS    = 300;
    private static final float BASELINE_PITCH = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root   = findViewById(R.id.root);
        cover  = findViewById(R.id.cover);
        tvInfo = findViewById(R.id.tvInfo); // 無ければレイアウト側で追加 or この行は削除

        // 初期は「見える」（カバー透過）
        setCoverVisible(false);

        // Android 13+ は通知権限が必要（ForegroundServiceの通知用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIF
                );
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i); // 画面遷移してユーザーにONにしてもらう
            // 戻ってきたら再起動 or 画面に「許可後にアプリへ戻ってください」と表示でもOK
        }

        // Foreground Service を1回だけ起動（バックグラウンド黒画面はサービス側が担当）
        ContextCompat.startForegroundService(
                this,
                new Intent(this, GuardService.class)
        );

        // 画面可視化用にこのActivityでもセンサー購読
        hysteresis = new Hysteresis(HIDE_THRESHOLD, SHOW_THRESHOLD);
        pose = new SensorRepository(this);
        pose.setListener(p -> runOnUiThread(() -> onPose(p)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pose != null) pose.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pose != null) pose.stop();
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

        // 状態が変わったときだけUI更新（省電力）
        if (lastIsDown == null || lastIsDown != isDown) {
            lastIsDown = isDown;
            setCoverVisible(isDown); // 下向き=隠す(黒)/正面=見せる(透過)
        }

        if (tvInfo != null) {
            tvInfo.setText(String.format(Locale.JAPAN, "Pitch: %.1f°\nTilt: %.1f°",
                    adjPitch, p.tiltDeg));
        }
    }

    private void setCoverVisible(boolean hidden) {
        // 下向き（hidden=true）→ alpha=1（黒で不可視）
        // 正面（hidden=false）→ alpha=0（透過）
        if (cover == null) return;
        float target = hidden ? 1f : 0f;
        cover.animate().alpha(target).setDuration(120).start();
        cover.setClickable(hidden);
        cover.setFocusable(hidden);
    }
}
