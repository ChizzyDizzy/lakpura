package com.lakpura;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class NotificationsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        ProgressBar progressBar = findViewById(R.id.progressBar);
        TextView tvEmpty        = findViewById(R.id.tvEmpty);
        LinearLayout llNotifications = findViewById(R.id.llNotifications);

        progressBar.setVisibility(View.GONE);
        llNotifications.removeAllViews();
        tvEmpty.setText("No notifications yet");
        tvEmpty.setVisibility(View.VISIBLE);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
