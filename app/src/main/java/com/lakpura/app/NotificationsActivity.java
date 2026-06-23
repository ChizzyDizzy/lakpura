package com.lakpura.app;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private LinearLayout llNotifications;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        llNotifications = findViewById(R.id.llNotifications);
        progressBar     = findViewById(R.id.progressBar);
        tvEmpty         = findViewById(R.id.tvEmpty);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadNotifications();
    }

    private void loadNotifications() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        AuthHelper.getNotifications(new AuthHelper.NotificationsCallback() {
            @Override
            public void onSuccess(List<AuthHelper.NotificationItem> items) {
                progressBar.setVisibility(View.GONE);
                llNotifications.removeAllViews();

                if (items.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }

                for (AuthHelper.NotificationItem item : items) {
                    addNotificationCard(item);
                }
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(NotificationsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addNotificationCard(AuthHelper.NotificationItem item) {
        // Format the date — trim to just date + time
        String date = item.sentAt.length() > 16
                ? item.sentAt.substring(0, 16).replace("T", "  ")
                : item.sentAt;

        // Card container
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setRadius(16f);
        card.setCardElevation(4f);
        card.setCardBackgroundColor(getResources().getColor(R.color.surface, null));

        // Inner layout
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(40, 32, 40, 32);

        // Date
        TextView tvDate = new TextView(this);
        tvDate.setText(date);
        tvDate.setTextSize(11f);
        tvDate.setTextColor(getResources().getColor(R.color.text_secondary, null));

        // Subject
        TextView tvSubject = new TextView(this);
        tvSubject.setText(item.subject);
        tvSubject.setTextSize(16f);
        tvSubject.setTextColor(getResources().getColor(R.color.primary, null));
        tvSubject.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams subjectParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subjectParams.setMargins(0, 6, 0, 8);
        tvSubject.setLayoutParams(subjectParams);

        // Message
        TextView tvMessage = new TextView(this);
        tvMessage.setText(item.message);
        tvMessage.setTextSize(14f);
        tvMessage.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvMessage.setLineSpacing(4f, 1f);

        inner.addView(tvDate);
        inner.addView(tvSubject);
        inner.addView(tvMessage);
        card.addView(inner);
        llNotifications.addView(card);
    }
}
