package com.dccarlo.screenmirror;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    private static final int REQUEST_SCREEN_CAPTURE = 1001;
    private static final int REQUEST_NOTIFICATION = 1002;

    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionManager =
                (MediaProjectionManager)
                        getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("📺 Screen Mirror");
        title.setTextSize(28);

        TextView status = new TextView(this);
        status.setText(
                "Phone → WebRTC → TV\n\n" +
                "Ready to start screen capture."
        );
        status.setTextSize(18);

        Button startButton = new Button(this);
        startButton.setText("Start Mirroring");

        startButton.setOnClickListener(v -> requestScreenCapture());

        layout.addView(title);
        layout.addView(status);
        layout.addView(startButton);

        setContentView(layout);

        requestNotificationPermission();
    }

    private void requestNotificationPermission() {

        if (android.os.Build.VERSION.SDK_INT >= 33) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        REQUEST_NOTIFICATION
                );
            }
        }
    }

    private void requestScreenCapture() {

        Intent intent =
                projectionManager.createScreenCaptureIntent();

        startActivityForResult(
                intent,
                REQUEST_SCREEN_CAPTURE
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQUEST_SCREEN_CAPTURE) {

            if (resultCode == RESULT_OK && data != null) {

                Intent serviceIntent =
                        new Intent(
                                this,
                                ScreenCaptureService.class
                        );

                serviceIntent.putExtra(
                        "resultCode",
                        resultCode
                );

                serviceIntent.putExtra(
                        "data",
                        data
                );

                ContextCompat.startForegroundService(
                        this,
                        serviceIntent
                );
            }
        }
    }
}