package com.example.tiltcolor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;   // ★ 追加

public class BlackoutActivity extends AppCompatActivity {

    public static final String ACTION_HIDE_BLACKOUT = "com.example.tiltcolor.ACTION_HIDE_BLACKOUT";

    private final BroadcastReceiver closer = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_HIDE_BLACKOUT.equals(intent.getAction())) {
                finish();
            }
        }
    };

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View v = new View(this);
        v.setBackgroundColor(0xFF000000);
        setContentView(v);

        IntentFilter filter = new IntentFilter(ACTION_HIDE_BLACKOUT);

        // ★ API分岐は不要。常に互換APIでフラグ付き登録にする
        ContextCompat.registerReceiver(
                this,
                closer,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED   // 外部から受けない
        );
    }


    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(closer); } catch (Exception ignored) {}
    }
}
