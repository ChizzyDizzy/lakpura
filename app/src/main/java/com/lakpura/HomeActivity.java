package com.lakpura;

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

        TextView tvWelcome        = findViewById(R.id.tvWelcome);
        Button btnLogout          = findViewById(R.id.btnLogout);
        Button btnNotifications   = findViewById(R.id.btnNotifications);

        String name = AuthHelper.currentUserName != null
                ? AuthHelper.currentUserName : "User";
        tvWelcome.setText("Welcome,\n" + name + "!");

        btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));

        btnLogout.setOnClickListener(v -> {
            AuthHelper.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    @Override
    public void onBackPressed() {}
}
