package com.lakpura;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class CustomerDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);

        String id      = getIntent().getStringExtra("customer_id");
        String name    = getIntent().getStringExtra("customer_name");
        String phone   = getIntent().getStringExtra("customer_phone");
        String email   = getIntent().getStringExtra("customer_email");
        String address = getIntent().getStringExtra("customer_address");

        ((TextView) findViewById(R.id.tvCustomerName)).setText(name != null ? name : "—");
        ((TextView) findViewById(R.id.tvCustomerPhone)).setText(phone != null && !phone.isEmpty() ? phone : "—");
        ((TextView) findViewById(R.id.tvCustomerEmail)).setText(email != null && !email.isEmpty() ? email : "—");
        ((TextView) findViewById(R.id.tvCustomerAddress)).setText(address != null && !address.isEmpty() ? address : "—");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        Button btnViewJobs = findViewById(R.id.btnViewJobs);
        btnViewJobs.setOnClickListener(v -> {
            Intent intent = new Intent(this, JobsActivity.class);
            intent.putExtra("customer_id", id);
            intent.putExtra("customer_name", name);
            startActivity(intent);
        });
    }
}
