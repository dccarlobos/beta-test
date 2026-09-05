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

import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenMirror";

    private static final String CHANNEL_ID =
            "screen_mirror_channel";

    /*
     * Local Node.js signaling server.
     *
     * Node.js is running on the same phone
     * through Termux.
     */
    private static final String SIGNAL_URL =
            "http://127.0.0.1:8080/signal";

    /*
     * Temporary fixed room for the first test.
     *
     * The TV receiver uses the same session.
     *
     * Later we can replace this with automatic
     * room pairing/discovery.
     */
    private static final String SESSION_ID =
            "screenmirror";

    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;

    private VideoSource videoSource;
    private VideoTrack videoTrack;

    private ScreenCapturerAndroid screenCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private EglBase eglBase;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        Log.d(
                TAG,
                "ScreenCaptureService created"
        );
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

            Log.e(
                    TAG,
                    "MediaProjection data is null"
            );

            stopSelf();

            return START_NOT_STICKY;
        }

        if (resultCode != android.app.Activity.RESULT_OK) {

            Log.e(
                    TAG,
                    "Screen capture permission was not granted"
            );

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

        Log.d(
                TAG,
                "Starting WebRTC screen capture..."
        );

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

        int maxWidth = 1280;

        if (width > maxWidth) {

            float scale =
                    (float) maxWidth / width;

            width = maxWidth;

            height =
                    Math.round(
                            height * scale
                    );
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
                        .createVideoSource(true);

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
                "WEBRTC SCREEN CAPTURE STARTED"
        );

        Log.d(
                TAG,
                "VideoTrack created: "
                        + videoTrack.id()
        );

        createPeerConnection();

        if (peerConnection != null) {

            peerConnection.addTrack(
                    videoTrack
            );

            Log.d(
                    TAG,
                    "VideoTrack added to PeerConnection"
            );

            /*
             * 2C-3
             *
             * Create SDP offer after the screen
             * VideoTrack has been added.
             */
            createSdpOffer();
        }
    }

    private void initializeWebRtc() {

        Log.d(
                TAG,
                "Initializing PeerConnectionFactory"
        );

        PeerConnectionFactory.initialize(
                PeerConnectionFactory
                        .InitializationOptions
                        .builder(
                                getApplicationContext()
                        )
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

    private void createPeerConnection() {

        Log.d(
                TAG,
                "Creating PeerConnection..."
        );

        List<PeerConnection.IceServer> iceServers =
                new ArrayList<>();

        /*
         * No public STUN server is required for this
         * first local-network milestone.
         *
         * Later we can add STUN/TURN for internet use.
         */

        PeerConnection.RTCConfiguration
                rtcConfig =
                new PeerConnection.RTCConfiguration(
                        iceServers
                );

        rtcConfig.sdpSemantics =
                PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection =
                peerConnectionFactory
                        .createPeerConnection(
                                rtcConfig,
                                new PeerConnection.Observer() {

                                    @Override
                                    public void onSignalingChange(
                                            PeerConnection.SignalingState state) {

                                        Log.d(
                                                TAG,
                                                "Signaling state: "
                                                        + state
                                        );
                                    }

                                    @Override
                                    public void onIceConnectionChange(
                                            PeerConnection.IceConnectionState state) {

                                        Log.d(
                                                TAG,
                                                "ICE connection state: "
                                                        + state
                                        );
                                    }

                                    @Override
                                    public void onIceConnectionReceivingChange(
                                            boolean receiving) {

                                        Log.d(
                                                TAG,
                                                "ICE receiving: "
                                                        + receiving
                                        );
                                    }

                                    @Override
                                    public void onIceGatheringChange(
                                            PeerConnection.IceGatheringState state) {

                                        Log.d(
                                                TAG,
                                                "ICE gathering state: "
                                                        + state
                                        );
                                    }

                                    @Override
                                    public void onIceCandidate(
                                            org.webrtc.IceCandidate candidate) {

                                        Log.d(
                                                TAG,
                                                "ICE candidate generated"
                                        );
                                    }

                                    @Override
                                    public void onIceCandidatesRemoved(
                                            org.webrtc.IceCandidate[] candidates) {

                                        Log.d(
                                                TAG,
                                                "ICE candidates removed"
                                        );
                                    }

                                    @Override
                                    public void onAddStream(
                                            org.webrtc.MediaStream stream) {

                                        Log.d(
                                                TAG,
                                                "Remote stream added"
                                        );
                                    }

                                    @Override
                                    public void onRemoveStream(
                                            org.webrtc.MediaStream stream) {

                                        Log.d(
                                                TAG,
                                                "Remote stream removed"
                                        );
                                    }

                                    @Override
                                    public void onDataChannel(
                                            org.webrtc.DataChannel dataChannel) {

                                        Log.d(
                                                TAG,
                                                "Data channel received"
                                        );
                                    }

                                    @Override
                                    public void onRenegotiationNeeded() {

                                        Log.d(
                                                TAG,
                                                "Renegotiation needed"
                                        );
                                    }

                                    @Override
                                    public void onAddTrack(
                                            org.webrtc.RtpReceiver receiver,
                                            org.webrtc.MediaStream[] mediaStreams) {

                                        Log.d(
                                                TAG,
                                                "Track added"
                                        );
                                    }

                                    @Override
                                    public void onConnectionChange(
                                            PeerConnection.PeerConnectionState state) {

                                        Log.d(
                                                TAG,
                                                "PeerConnection state: "
                                                        + state
                                        );
                                    }
                                }
                        );

        if (peerConnection != null) {

            Log.d(
                    TAG,
                    "================================"
            );

            Log.d(
                    TAG,
                    "PEER CONNECTION CREATED"
            );

            Log.d(
                    TAG,
                    "================================"
            );

        } else {

            Log.e(
                    TAG,
                    "FAILED TO CREATE PEER CONNECTION"
            );
        }
    }

    /*
     * ============================================================
     * 2C-3
     * CREATE SDP OFFER
     * ============================================================
     */

    private void createSdpOffer() {

        if (peerConnection == null) {

            Log.e(
                    TAG,
                    "Cannot create offer: PeerConnection is null"
            );

            return;
        }

        Log.d(
                TAG,
                "Creating SDP offer..."
        );

        MediaConstraints constraints =
                new MediaConstraints();

        peerConnection.createOffer(
                new SdpObserver() {

                    @Override
                    public void onCreateSuccess(
                            SessionDescription sessionDescription) {

                        Log.d(
                                TAG,
                                "SDP OFFER CREATED"
                        );

                        peerConnection.setLocalDescription(
                                new SdpObserver() {

                                    @Override
                                    public void onSetSuccess() {

                                        Log.d(
                                                TAG,
                                                "Local SDP description set"
                                        );

                                        /*
                                         * Send the offer to Node.js
                                         * only after local description
                                         * has been successfully set.
                                         */
                                        sendOfferToServer(
                                                sessionDescription
                                        );
                                    }

                                    @Override
                                    public void onSetFailure(
                                            String error) {

                                        Log.e(
                                                TAG,
                                                "Failed to set local SDP: "
                                                        + error
                                        );
                                    }

                                    @Override
                                    public void onCreateSuccess(
                                            SessionDescription ignored) {
                                    }

                                    @Override
                                    public void onCreateFailure(
                                            String error) {
                                    }

                                },
                                sessionDescription
                        );
                    }

                    @Override
                    public void onSetSuccess() {
                    }

                    @Override
                    public void onCreateFailure(
                            String error) {

                        Log.e(
                                TAG,
                                "Failed to create SDP offer: "
                                        + error
                        );
                    }

                    @Override
                    public void onSetFailure(
                            String error) {

                        Log.e(
                                TAG,
                                "SDP set failure: "
                                        + error
                        );
                    }

                },
                constraints
        );
    }

    /*
     * ============================================================
     * SEND SDP OFFER TO NODE.JS
     * ============================================================
     */

    private void sendOfferToServer(
            SessionDescription sessionDescription) {

        new Thread(
                () -> {

                    HttpURLConnection connection =
                            null;

                    try {

                        Log.d(
                                TAG,
                                "Sending SDP offer to Node.js..."
                        );

                        JSONObject offer =
                                new JSONObject();

                        offer.put(
                                "type",
                                "offer"
                        );

                        offer.put(
                                "session",
                                SESSION_ID
                        );

                        offer.put(
                                "offer",
                                sessionDescription.description
                        );

                        URL url =
                                new URL(
                                        SIGNAL_URL
                                );

                        connection =
                                (HttpURLConnection)
                                        url.openConnection();

                        connection.setRequestMethod(
                                "POST"
                        );

                        connection.setDoOutput(
                                true
                        );

                        connection.setConnectTimeout(
                                5000
                        );

                        connection.setReadTimeout(
                                5000
                        );

                        connection.setRequestProperty(
                                "Content-Type",
                                "application/json"
                        );

                        byte[] data =
                                offer.toString()
                                        .getBytes(
                                                StandardCharsets.UTF_8
                                        );

                        connection.setFixedLengthStreamingMode(
                                data.length
                        );

                        try (OutputStream output =
                                     connection.getOutputStream()) {

                            output.write(data);
                            output.flush();
                        }

                        int responseCode =
                                connection.getResponseCode();

                        Log.d(
                                TAG,
                                "Signaling server response: "
                                        + responseCode
                        );

                        InputStream inputStream;

                        if (responseCode >= 200 &&
                                responseCode < 300) {

                            inputStream =
                                    connection.getInputStream();

                        } else {

                            inputStream =
                                    connection.getErrorStream();
                        }

                        if (inputStream != null) {

                            String response =
                                    readResponse(
                                            inputStream
                                    );

                            Log.d(
                                    TAG,
                                    "Signaling response: "
                                            + response
                            );
                        }

                        if (responseCode >= 200 &&
                                responseCode < 300) {

                            Log.d(
                                    TAG,
                                    "SDP OFFER SENT SUCCESSFULLY"
                            );

                        } else {

                            Log.e(
                                    TAG,
                                    "Failed to send SDP offer"
                            );
                        }

                    } catch (Exception e) {

                        Log.e(
                                TAG,
                                "Error sending SDP offer",
                                e
                        );

                    } finally {

                        if (connection != null) {

                            connection.disconnect();
                        }
                    }

                }
        ).start();
    }

    private String readResponse(
            InputStream inputStream)
            throws Exception {

        StringBuilder result =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {

                result.append(line);
            }
        }

        return result.toString();
    }

    /*
     * ============================================================
     * STOP CAPTURE
     * ============================================================
     */

    private void stopCapture() {

        Log.d(
                TAG,
                "Stopping screen capture..."
        );

        if (peerConnection != null) {

            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }

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