package com.example.tiltcolor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.tiltcolor.service.GuardService;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 1000;

    public static final String PREFS = "tilt_guard_prefs";
    public static final String KEY_ENABLED = "enabled";
    public static final String ACTION_UPDATE_ENABLED = "com.example.tiltcolor.ACTION_UPDATE_ENABLED";

    private Switch swEnable;
    private TextView tvTips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // ↓レイアウトはこの後に記載

        swEnable = findViewById(R.id.swEnable);
        tvTips   = findViewById(R.id.tvTips);

        // 現在の有効/無効を反映（既定: 有効）
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
        swEnable.setChecked(enabled);
        renderTips(enabled);

        // 通知許可（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
        }
        // オーバーレイ権限
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }

        // 監視サービス起動（常駐）
        ContextCompat.startForegroundService(this, new Intent(this, GuardService.class));

        // トグル変更 → 永続化 + サービスへ通知
        swEnable.setOnCheckedChangeListener((btn, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, isChecked).apply();
            renderTips(isChecked);

            // サービスへ“設定更新”ブロードキャスト
            Intent upd = new Intent(ACTION_UPDATE_ENABLED).setPackage(getPackageName());
            sendBroadcast(upd);
        });
    }

    private void renderTips(boolean enabled) {
        tvTips.setText(enabled ? "歩行中＋下向きで画面をブロックします"
                : "ブロックは無効です（監視は停止/解除）");
    }
}
