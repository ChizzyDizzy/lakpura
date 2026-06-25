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

public class CustomersActivity extends AppCompatActivity {

    private LinearLayout llCustomers;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private EditText etSearch;

    private List<AuthHelper.CustomerItem> allCustomers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers);

        llCustomers  = findViewById(R.id.llCustomers);
        progressBar  = findViewById(R.id.progressBar);
        tvEmpty      = findViewById(R.id.tvEmpty);
        etSearch     = findViewById(R.id.etSearch);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCustomers(s.toString().trim());
            }
            public void afterTextChanged(Editable s) {}
        });

        loadCustomers();
    }

    private void loadCustomers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        llCustomers.removeAllViews();

        AuthHelper.getCustomers(new AuthHelper.CustomersCallback() {
            @Override
            public void onSuccess(List<AuthHelper.CustomerItem> items) {
                progressBar.setVisibility(View.GONE);
                allCustomers = items;
                filterCustomers(etSearch.getText().toString().trim());
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(CustomersActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterCustomers(String query) {
        llCustomers.removeAllViews();
        List<AuthHelper.CustomerItem> filtered = new ArrayList<>();
        for (AuthHelper.CustomerItem c : allCustomers) {
            if (query.isEmpty()
                    || c.name.toLowerCase().contains(query.toLowerCase())
                    || c.phone.contains(query)) {
                filtered.add(c);
            }
        }
        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            for (AuthHelper.CustomerItem c : filtered) {
                addCustomerCard(c);
            }
        }
    }

    private void addCustomerCard(AuthHelper.CustomerItem item) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setRadius(16f);
        card.setCardElevation(4f);
        card.setCardBackgroundColor(getResources().getColor(R.color.surface, null));
        card.setForeground(getResources().getDrawable(android.R.attr.selectableItemBackground > 0
                ? android.R.attr.selectableItemBackground : 0, null));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(40, 28, 40, 28);

        TextView tvName = new TextView(this);
        tvName.setText(item.name);
        tvName.setTextSize(16f);
        tvName.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvPhone = new TextView(this);
        tvPhone.setText(item.phone.isEmpty() ? "No phone" : item.phone);
        tvPhone.setTextSize(13f);
        tvPhone.setTextColor(getResources().getColor(R.color.text_secondary, null));
        LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        phoneParams.setMargins(0, 4, 0, 0);
        tvPhone.setLayoutParams(phoneParams);

        inner.addView(tvName);
        inner.addView(tvPhone);
        card.addView(inner);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerDetailActivity.class);
            intent.putExtra("customer_id", item.id);
            intent.putExtra("customer_name", item.name);
            intent.putExtra("customer_phone", item.phone);
            intent.putExtra("customer_email", item.email);
            intent.putExtra("customer_address", item.address);
            startActivity(intent);
        });

        llCustomers.addView(card);
    }
}
