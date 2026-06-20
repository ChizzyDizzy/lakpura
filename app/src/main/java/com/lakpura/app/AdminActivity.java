package com.lakpura.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class AdminActivity extends AppCompatActivity {

    // Notification fields
    private EditText etSubject, etMessage;
    private Button btnSend;
    private ProgressBar progressBarNotif;
    private TextView tvRecipientCount, tvSentLog;

    // Create user fields
    private EditText etNewFullName, etNewEmail, etNewPassword;
    private CheckBox cbMakeAdmin;
    private Button btnCreateUser;
    private ProgressBar progressBarUser;

    // Logout
    private Button btnLogout;

    private List<String> allEmails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Notification views
        etSubject          = findViewById(R.id.etSubject);
        etMessage          = findViewById(R.id.etMessage);
        btnSend            = findViewById(R.id.btnSend);
        progressBarNotif   = findViewById(R.id.progressBarNotif);
        tvRecipientCount   = findViewById(R.id.tvRecipientCount);
        tvSentLog          = findViewById(R.id.tvSentLog);

        // Create user views
        etNewFullName      = findViewById(R.id.etNewFullName);
        etNewEmail         = findViewById(R.id.etNewEmail);
        etNewPassword      = findViewById(R.id.etNewPassword);
        cbMakeAdmin        = findViewById(R.id.cbMakeAdmin);
        btnCreateUser      = findViewById(R.id.btnCreateUser);
        progressBarUser    = findViewById(R.id.progressBarUser);

        btnLogout          = findViewById(R.id.btnLogout);

        loadRecipients();

        btnSend.setOnClickListener(v -> sendNotification());
        btnCreateUser.setOnClickListener(v -> createUser());
        btnLogout.setOnClickListener(v -> logout());
    }

    // -----------------------------------------------------------------------
    // Notifications
    // -----------------------------------------------------------------------
    private void loadRecipients() {
        tvRecipientCount.setText("Loading recipients...");
        btnSend.setEnabled(false);

        AuthHelper.getAllUserEmails(new AuthHelper.EmailListCallback() {
            @Override
            public void onSuccess(List<String> emails) {
                allEmails = emails;
                tvRecipientCount.setText(emails.size() + " registered users will receive this");
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

        progressBarNotif.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        AuthHelper.sendEmailNotification(allEmails, subject, message, new AuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String msg) {
                progressBarNotif.setVisibility(View.GONE);
                btnSend.setEnabled(true);
                tvSentLog.setText("Last sent: \"" + subject + "\" → " + allEmails.size() + " users");
                etSubject.setText("");
                etMessage.setText("");
                Toast.makeText(AdminActivity.this, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                progressBarNotif.setVisibility(View.GONE);
                btnSend.setEnabled(true);
                Toast.makeText(AdminActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Create User
    // -----------------------------------------------------------------------
    private void createUser() {
        String fullName  = etNewFullName.getText().toString().trim();
        String email     = etNewEmail.getText().toString().trim();
        String password  = etNewPassword.getText().toString().trim();
        boolean isAdmin  = cbMakeAdmin.isChecked();

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all user fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBarUser.setVisibility(View.VISIBLE);
        btnCreateUser.setEnabled(false);

        AuthHelper.createUser(email, password, fullName, isAdmin, new AuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String msg) {
                progressBarUser.setVisibility(View.GONE);
                btnCreateUser.setEnabled(true);
                etNewFullName.setText("");
                etNewEmail.setText("");
                etNewPassword.setText("");
                cbMakeAdmin.setChecked(false);
                Toast.makeText(AdminActivity.this, msg, Toast.LENGTH_LONG).show();
                loadRecipients(); // refresh recipient count
            }

            @Override
            public void onError(String error) {
                progressBarUser.setVisibility(View.GONE);
                btnCreateUser.setEnabled(true);
                Toast.makeText(AdminActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Logout
    // -----------------------------------------------------------------------
    private void logout() {
        AuthHelper.logout();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        // Prevent back navigation from admin panel
    }
}
