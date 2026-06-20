package com.lakpura.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class AdminActivity extends AppCompatActivity {

    private EditText etSubject, etMessage;
    private Button btnSend, btnLogout;
    private ProgressBar progressBar;
    private TextView tvRecipientCount, tvSentLog;

    private List<String> allEmails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        etSubject        = findViewById(R.id.etSubject);
        etMessage        = findViewById(R.id.etMessage);
        btnSend          = findViewById(R.id.btnSend);
        btnLogout        = findViewById(R.id.btnLogout);
        progressBar      = findViewById(R.id.progressBar);
        tvRecipientCount = findViewById(R.id.tvRecipientCount);
        tvSentLog        = findViewById(R.id.tvSentLog);

        loadRecipients();

        btnSend.setOnClickListener(v -> sendNotification());

        btnLogout.setOnClickListener(v -> {
            AuthHelper.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void loadRecipients() {
        tvRecipientCount.setText("Loading recipients...");
        btnSend.setEnabled(false);

        AuthHelper.getAllUserEmails(new AuthHelper.EmailListCallback() {
            @Override
            public void onSuccess(List<String> emails) {
                allEmails = emails;
                tvRecipientCount.setText("Recipients: " + emails.size() + " registered users");
                btnSend.setEnabled(true);
            }

            @Override
            public void onError(String error) {
                tvRecipientCount.setText("Could not load recipients");
                Toast.makeText(AdminActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendNotification() {
        String subject = etSubject.getText().toString().trim();
        String message = etMessage.getText().toString().trim();

        if (subject.isEmpty() || message.isEmpty()) {
            Toast.makeText(this, "Please fill in subject and message", Toast.LENGTH_SHORT).show();
            return;
        }

        if (allEmails == null || allEmails.isEmpty()) {
            Toast.makeText(this, "No recipients found", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        AuthHelper.sendEmailNotification(allEmails, subject, message, new AuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String message2) {
                setLoading(false);
                tvSentLog.setText("Last sent: \"" + subject + "\" → " + allEmails.size() + " users");
                etSubject.setText("");
                etMessage.setText("");
                Toast.makeText(AdminActivity.this, message2, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(AdminActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!loading);
    }

    @Override
    public void onBackPressed() {
        // Prevent back navigation from admin panel
    }
}
