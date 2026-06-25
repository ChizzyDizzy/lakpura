package com.lakpura;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class JobDetailActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnUpdateStatus;
    private ProgressBar progressBar;

    private String jobId;
    private String currentStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job_detail);

        jobId         = getIntent().getStringExtra("job_id");
        String title  = getIntent().getStringExtra("job_title");
        currentStatus = getIntent().getStringExtra("job_status");
        String customer = getIntent().getStringExtra("job_customer");
        String date   = getIntent().getStringExtra("job_date");
        String notes  = getIntent().getStringExtra("job_notes");

        tvStatus       = findViewById(R.id.tvStatus);
        btnUpdateStatus = findViewById(R.id.btnUpdateStatus);
        progressBar    = findViewById(R.id.progressBar);

        ((TextView) findViewById(R.id.tvJobTitle)).setText(title != null ? title : "—");
        ((TextView) findViewById(R.id.tvCustomer)).setText(customer != null && !customer.isEmpty() ? customer : "—");
        ((TextView) findViewById(R.id.tvDate)).setText(date != null && !date.isEmpty() ? date : "—");
        ((TextView) findViewById(R.id.tvNotes)).setText(notes != null && !notes.isEmpty() ? notes : "—");
        updateStatusDisplay(currentStatus);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnUpdateStatus.setOnClickListener(v -> advanceStatus());
    }

    private void advanceStatus() {
        String nextStatus;
        switch (currentStatus) {
            case "open":        nextStatus = "in_progress"; break;
            case "in_progress": nextStatus = "completed"; break;
            default:
                Toast.makeText(this, "Job is already " + currentStatus, Toast.LENGTH_SHORT).show();
                return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnUpdateStatus.setEnabled(false);

        AuthHelper.updateJobStatus(jobId, nextStatus, new AuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                progressBar.setVisibility(View.GONE);
                currentStatus = nextStatus;
                updateStatusDisplay(currentStatus);
                btnUpdateStatus.setEnabled(true);
                Toast.makeText(JobDetailActivity.this, "Status updated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                btnUpdateStatus.setEnabled(true);
                Toast.makeText(JobDetailActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateStatusDisplay(String status) {
        tvStatus.setText(formatStatus(status));
        tvStatus.setTextColor(statusColor(status));
        switch (status) {
            case "open":        btnUpdateStatus.setText("Mark as In Progress"); btnUpdateStatus.setVisibility(View.VISIBLE); break;
            case "in_progress": btnUpdateStatus.setText("Mark as Completed"); btnUpdateStatus.setVisibility(View.VISIBLE); break;
            default:            btnUpdateStatus.setVisibility(View.GONE); break;
        }
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
