package com.example.tiltcolor.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.example.tiltcolor.domain.PoseData;

public class SensorRepository implements PoseProvider, SensorEventListener {

    private final SensorManager sm;
    private final Sensor rotVec;
    private final Sensor acc;
    private Listener listener;
    private PoseData lastPose = new PoseData(0,0,0,0,System.currentTimeMillis());

    private final float[] accLP = new float[3];
    private boolean accInit = false;
    private double lastTilt = 0.0;

    public SensorRepository(Context context) {
        sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotVec = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public void start() {
        if (rotVec != null) sm.registerListener(this, rotVec, SensorManager.SENSOR_DELAY_UI);
        if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void stop() {
        sm.unregisterListener(this);
    }

    @Override
    public PoseData getLastPose() { return lastPose; }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                updateOrientation(event.values);
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                updateTilt(event.values);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateOrientation(float[] values) {
        if (values == null || values.length < 3) return;
        float[] R = new float[9];
        SensorManager.getRotationMatrixFromVector(R, values);
        float[] ori = new float[3];
        SensorManager.getOrientation(R, ori);
        float yaw = (float) Math.toDegrees(ori[0]);
        float pitch = (float) -Math.toDegrees(ori[1]); // 正面0°, 下向き負
        float roll = (float) Math.toDegrees(ori[2]);
        lastPose = new PoseData(pitch, roll, yaw, (float) lastTilt, System.currentTimeMillis());
        if (listener != null) listener.onPose(lastPose);
    }

    private void updateTilt(float[] values) {
        if (values == null || values.length < 3) return;
        final float alpha = 0.1f;
        if (!accInit) {
            System.arraycopy(values, 0, accLP, 0, 3);
            accInit = true;
        } else {
            for (int i = 0; i < 3; i++) accLP[i] += alpha * (values[i] - accLP[i]);
        }
        float ax = accLP[0], ay = accLP[1], az = accLP[2];
        double g = Math.max(1e-6, Math.sqrt(ax*ax + ay*ay + az*az));
        double ratio = Math.min(1.0, Math.max(0.0, Math.abs(az) / g));
        lastTilt = Math.toDegrees(Math.acos(ratio));
        // tilt更新時にPose通知（姿勢が変わらなくても tilt を更新したい場合）
        lastPose = new PoseData(lastPose.pitchDeg, lastPose.rollDeg, lastPose.yawDeg, (float) lastTilt, System.currentTimeMillis());
        if (listener != null) listener.onPose(lastPose);
    }
}
