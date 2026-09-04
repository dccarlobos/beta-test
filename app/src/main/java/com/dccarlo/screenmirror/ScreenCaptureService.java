package com.dccarlo.screenmirror;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.Nullable;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID =
            "screen_mirror_channel";

    private MediaProjection mediaProjection;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        Notification notification =
                new Notification.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setContentTitle("Screen Mirror")
                        .setContentText(
                                "Screen mirroring is active"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_view
                        )
                        .build();

        startForeground(
                1,
                notification,
                android.content.pm.ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        );

        int resultCode =
                intent.getIntExtra(
                        "resultCode",
                        -1
                );

        Intent projectionData =
                intent.getParcelableExtra("data");

        MediaProjectionManager manager =
                (MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        if (projectionData != null) {

            mediaProjection =
                    manager.getMediaProjection(
                            resultCode,
                            projectionData
                    );

            startCapture();
        }

        return START_NOT_STICKY;
    }

    private void startCapture() {

        WindowManager windowManager =
                (WindowManager)
                        getSystemService(WINDOW_SERVICE);

        DisplayMetrics metrics =
                new DisplayMetrics();

        windowManager
                .getDefaultDisplay()
                .getMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        android.util.Log.d(
                "ScreenMirror",
                "Capture started: "
                        + width
                        + "x"
                        + height
                        + " density="
                        + density
        );

        /*
         * WebRTC ScreenCapturerAndroid
         * will be connected here in the next stage.
         */
    }

    private void createNotificationChannel() {

        if (android.os.Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Screen Mirror",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    @Override
    public void onDestroy() {

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}