package com.lakpura;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.List;

public class JobsActivity extends AppCompatActivity {

    private LinearLayout llJobs;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private TextView tvHeader;

    private String customerId;
    private String customerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jobs);

        llJobs      = findViewById(R.id.llJobs);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty     = findViewById(R.id.tvEmpty);
        tvHeader    = findViewById(R.id.tvHeader);

        customerId   = getIntent().getStringExtra("customer_id");
        customerName = getIntent().getStringExtra("customer_name");

        if (customerName != null) {
            tvHeader.setText("Jobs — " + customerName);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadJobs();
    }

    private void loadJobs() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        llJobs.removeAllViews();

        AuthHelper.getJobs(customerId, new AuthHelper.JobsCallback() {
            @Override
            public void onSuccess(List<AuthHelper.JobItem> items) {
                progressBar.setVisibility(View.GONE);
                if (items.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                for (AuthHelper.JobItem job : items) {
                    addJobCard(job);
                }
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(JobsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addJobCard(AuthHelper.JobItem item) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setRadius(16f);
        card.setCardElevation(4f);
        card.setCardBackgroundColor(getResources().getColor(R.color.surface, null));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(40, 28, 40, 28);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(item.title);
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvCustomer = new TextView(this);
        tvCustomer.setText(item.customerName);
        tvCustomer.setTextSize(13f);
        tvCustomer.setTextColor(getResources().getColor(R.color.text_secondary, null));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p1.setMargins(0, 4, 0, 0);
        tvCustomer.setLayoutParams(p1);

        TextView tvStatus = new TextView(this);
        tvStatus.setText(formatStatus(item.status));
        tvStatus.setTextSize(12f);
        tvStatus.setTextColor(statusColor(item.status));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p2.setMargins(0, 6, 0, 0);
        tvStatus.setLayoutParams(p2);

        TextView tvDate = new TextView(this);
        tvDate.setText(item.scheduledDate.isEmpty() ? "" : "Scheduled: " + item.scheduledDate);
        tvDate.setTextSize(12f);
        tvDate.setTextColor(getResources().getColor(R.color.text_secondary, null));
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p3.setMargins(0, 4, 0, 0);
        tvDate.setLayoutParams(p3);

        inner.addView(tvTitle);
        if (!item.customerName.isEmpty() && customerId == null) inner.addView(tvCustomer);
        inner.addView(tvStatus);
        if (!item.scheduledDate.isEmpty()) inner.addView(tvDate);
        card.addView(inner);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, JobDetailActivity.class);
            intent.putExtra("job_id", item.id);
            intent.putExtra("job_title", item.title);
            intent.putExtra("job_status", item.status);
            intent.putExtra("job_customer", item.customerName);
            intent.putExtra("job_date", item.scheduledDate);
            intent.putExtra("job_notes", item.notes);
            startActivity(intent);
        });

        llJobs.addView(card);
    }

    private String formatStatus(String status) {
        switch (status) {
            case "in_progress": return "● In Progress";
            case "completed":   return "✓ Completed";
            case "cancelled":   return "✕ Cancelled";
            default:            return "○ Open";
        }
    }

    private int statusColor(String status) {
        switch (status) {
            case "in_progress": return 0xFFF59E0B;
            case "completed":   return 0xFF10B981;
            case "cancelled":   return 0xFFEF4444;
            default:            return 0xFF6366F1;
        }
    }
}
