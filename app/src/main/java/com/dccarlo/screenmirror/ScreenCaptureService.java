package com.dccarlo.screenmirror;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenMirror";
    private static final String CHANNEL_ID =
            "screen_mirror_channel";

    private MediaProjection mediaProjection;

    private PeerConnectionFactory peerConnectionFactory;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private ScreenCapturerAndroid screenCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private EglBase eglBase;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        Log.d(TAG, "ScreenCaptureService created");
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

        if (projectionData == null) {
            Log.e(TAG, "MediaProjection data is null");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (resultCode != android.app.Activity.RESULT_OK) {
            Log.e(TAG, "Screen capture permission was not granted");
            stopSelf();
            return START_NOT_STICKY;
        }

        startWebRtcScreenCapture(
                resultCode,
                projectionData
        );

        return START_NOT_STICKY;
    }

    private void startWebRtcScreenCapture(
            int resultCode,
            Intent projectionData) {

        Log.d(TAG, "Starting WebRTC screen capture...");

        initializeWebRtc();

        DisplayMetrics metrics =
                new DisplayMetrics();

        WindowManager windowManager =
                (WindowManager)
                        getSystemService(WINDOW_SERVICE);

        windowManager
                .getDefaultDisplay()
                .getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        /*
         * Limit the initial capture size.
         * This keeps the first test lighter on the Vivo Y15.
         */
        int maxWidth = 1280;

        if (width > maxWidth) {

            float scale =
                    (float) maxWidth / width;

            width = maxWidth;
            height =
                    Math.round(height * scale);
        }

        Log.d(
                TAG,
                "Capture resolution: "
                        + width
                        + "x"
                        + height
        );

        screenCapturer =
                new ScreenCapturerAndroid(
                        projectionData,
                        new MediaProjection.Callback() {

                            @Override
                            public void onStop() {

                                Log.d(
                                        TAG,
                                        "MediaProjection stopped"
                                );

                                stopCapture();
                            }
                        }
                );

        videoSource =
                peerConnectionFactory
                        .createVideoSource(
                                true
                        );

        eglBase =
                EglBase.create();

        surfaceTextureHelper =
                SurfaceTextureHelper.create(
                        "ScreenCaptureThread",
                        eglBase.getEglBaseContext()
                );

        screenCapturer.initialize(
                surfaceTextureHelper,
                getApplicationContext(),
                videoSource.getCapturerObserver()
        );

        screenCapturer.startCapture(
                width,
                height,
                30
        );

        videoTrack =
                peerConnectionFactory
                        .createVideoTrack(
                                "SCREEN_TRACK",
                                videoSource
                        );

        videoTrack.setEnabled(true);

        Log.d(
                TAG,
                "================================"
        );

        Log.d(
                TAG,
                "WEBRTC SCREEN CAPTURE STARTED"
        );

        Log.d(
                TAG,
                "VideoTrack created: "
                        + videoTrack.id()
        );

        Log.d(
                TAG,
                "================================"
        );
    }

    private void initializeWebRtc() {

        Log.d(TAG, "Initializing PeerConnectionFactory");

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                        .builder(getApplicationContext())
                        .createInitializationOptions()
        );

        PeerConnectionFactory.Options options =
                new PeerConnectionFactory.Options();

        peerConnectionFactory =
                PeerConnectionFactory
                        .builder()
                        .setOptions(options)
                        .createPeerConnectionFactory();

        Log.d(
                TAG,
                "PeerConnectionFactory initialized"
        );
    }

    private void stopCapture() {

        Log.d(TAG, "Stopping screen capture...");

        if (screenCapturer != null) {

            try {
                screenCapturer.stopCapture();
            } catch (Exception e) {
                Log.e(
                        TAG,
                        "Error stopping capturer",
                        e
                );
            }

            screenCapturer.dispose();
            screenCapturer = null;
        }

        if (videoTrack != null) {
            videoTrack.dispose();
            videoTrack = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }

        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
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

        stopCapture();

        Log.d(
                TAG,
                "ScreenCaptureService destroyed"
        );

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
