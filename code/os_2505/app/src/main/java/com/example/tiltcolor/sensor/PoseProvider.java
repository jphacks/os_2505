package com.example.tiltcolor.sensor;

import com.example.tiltcolor.domain.PoseData;

/**
 * 姿勢データ供給I/F。
 * A担当の実装（例: SensorRepository）／Cのフェイク実装の双方がこれを満たす。
 */
public interface PoseProvider {

    /** センサー購読やループ開始。onResume 等で呼ぶ。 */
    void start();

    /** センサー購読停止。onPause 等で呼ぶ。 */
    void stop();

    /** 直近の姿勢（nullにしないこと）。 */
    PoseData getLastPose();

    /** 新しい姿勢が来るたびに通知。UIスレッド or 明示のスレッドで可。 */
    void setListener(Listener listener);

    interface Listener {
        void onPose(PoseData pose);
    }
}
