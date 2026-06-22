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

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnAdminLogin;
    private ProgressBar progressBar;
    private TextView tvGoToRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail        = findViewById(R.id.etEmail);
        etPassword     = findViewById(R.id.etPassword);
        btnLogin       = findViewById(R.id.btnLogin);
        btnAdminLogin  = findViewById(R.id.btnAdminLogin);
        progressBar    = findViewById(R.id.progressBar);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);

        btnLogin.setOnClickListener(v -> attemptLogin(false));
        btnAdminLogin.setOnClickListener(v -> attemptLogin(true));
        tvGoToRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin(boolean goToAdmin) {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        AuthHelper.login(email, password, new AuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                setLoading(false);
                if (goToAdmin) {
                    startActivity(new Intent(LoginActivity.this, AdminActivity.class));
                } else {
                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                }
                finish();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnAdminLogin.setEnabled(!loading);
    }
}
