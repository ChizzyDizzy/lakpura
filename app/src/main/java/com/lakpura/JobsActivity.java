package com.lakpura;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.ArrayList;
import java.util.List;

public class JobsActivity extends AppCompatActivity {

    private LinearLayout llJobs;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private TextView tvHeader;
    private EditText etSearch;

    private String customerId;
    private String customerName;
    private List<AuthHelper.JobItem> allJobs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jobs);

        llJobs      = findViewById(R.id.llJobs);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty     = findViewById(R.id.tvEmpty);
        tvHeader    = findViewById(R.id.tvHeader);
        etSearch    = findViewById(R.id.etSearch);

        customerId   = getIntent().getStringExtra("customer_id");
        customerName = getIntent().getStringExtra("customer_name");

        if (customerName != null) {
            tvHeader.setText("Jobs — " + customerName);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterJobs(s.toString().trim());
            }
            public void afterTextChanged(Editable s) {}
        });

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
                allJobs = items;
                filterJobs(etSearch.getText().toString().trim());
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(JobsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterJobs(String query) {
        llJobs.removeAllViews();
        List<AuthHelper.JobItem> filtered = new ArrayList<>();
        for (AuthHelper.JobItem j : allJobs) {
            if (query.isEmpty()
                    || j.title.toLowerCase().contains(query.toLowerCase())
                    || j.customerName.toLowerCase().contains(query.toLowerCase())
                    || j.status.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(j);
            }
        }
        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            for (AuthHelper.JobItem job : filtered) {
                addJobCard(job);
            }
        }
    }

    private void addJobCard(AuthHelper.JobItem item) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 14);
        card.setLayoutParams(cardParams);
        card.setRadius(12f);
        card.setCardElevation(3f);
        card.setCardBackgroundColor(getResources().getColor(R.color.surface, null));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(36, 24, 36, 24);

        // Status colour bar on left via horizontal wrapper
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        View statusBar = new View(this);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(8, LinearLayout.LayoutParams.MATCH_PARENT);
        barParams.setMarginEnd(20);
        statusBar.setLayoutParams(barParams);
        statusBar.setBackgroundColor(statusColor(item.status));
        statusBar.setMinimumHeight(80);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(!item.title.isEmpty() ? item.title : "Untitled Job");
        tvTitle.setTextSize(15f);
        tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        textBlock.addView(tvTitle);

        // Customer name + phone on same line
        if (!item.customerName.isEmpty() && customerId == null) {
            TextView tvCustomer = new TextView(this);
            tvCustomer.setText(item.customerName);
            tvCustomer.setTextSize(13f);
            tvCustomer.setTextColor(getResources().getColor(R.color.text_secondary, null));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 4, 0, 0);
            tvCustomer.setLayoutParams(cp);
            textBlock.addView(tvCustomer);
        }

        // Status + date on same line
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brp.setMargins(0, 6, 0, 0);
        bottomRow.setLayoutParams(brp);

        TextView tvStatus = new TextView(this);
        tvStatus.setText(formatStatus(item.status));
        tvStatus.setTextSize(12f);
        tvStatus.setTextColor(statusColor(item.status));
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        bottomRow.addView(tvStatus);

        if (!item.scheduledDate.isEmpty()) {
            TextView tvDate = new TextView(this);
            tvDate.setText("  ·  " + item.scheduledDate);
            tvDate.setTextSize(12f);
            tvDate.setTextColor(getResources().getColor(R.color.text_secondary, null));
            bottomRow.addView(tvDate);
        }

        textBlock.addView(bottomRow);
        row.addView(statusBar);
        row.addView(textBlock);
        inner.addView(row);
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
            case "in_progress": return "In Progress";
            case "completed":   return "Completed";
            case "cancelled":   return "Cancelled";
            default:            return "Open";
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
