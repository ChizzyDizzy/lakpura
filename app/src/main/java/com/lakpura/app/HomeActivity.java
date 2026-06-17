package com.lakpura.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        Button btnLogout   = findViewById(R.id.btnLogout);

        String email = AuthHelper.currentUserEmail != null
                ? AuthHelper.currentUserEmail
                : "User";

        tvWelcome.setText("Welcome,\n" + email + "!");

        btnLogout.setOnClickListener(v -> {
            AuthHelper.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    // Prevent going back to login when already logged in
    @Override
    public void onBackPressed() {
        // Do nothing — user must use logout button
    }
}
