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

        llCustomers = findViewById(R.id.llCustomers);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty     = findViewById(R.id.tvEmpty);
        etSearch    = findViewById(R.id.etSearch);

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
                    || c.phone.contains(query)
                    || c.email.toLowerCase().contains(query.toLowerCase())) {
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
        // Decide what to show as the primary label
        String primary = !item.name.isEmpty() ? item.name
                       : !item.phone.isEmpty() ? item.phone
                       : !item.email.isEmpty() ? item.email
                       : "Customer #" + item.id;

        // Secondary line: phone if name was used as primary, else email
        String secondary = !item.name.isEmpty() && !item.phone.isEmpty() ? item.phone
                         : !item.name.isEmpty() && !item.email.isEmpty() ? item.email
                         : !item.phone.isEmpty() && !item.name.isEmpty() ? item.phone
                         : "";

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
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(36, 24, 36, 24);
        inner.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Avatar circle with first letter
        TextView tvAvatar = new TextView(this);
        String avatarLetter = primary.length() > 0 ? String.valueOf(primary.charAt(0)).toUpperCase() : "?";
        tvAvatar.setText(avatarLetter);
        tvAvatar.setTextSize(18f);
        tvAvatar.setTextColor(0xFFFFFFFF);
        tvAvatar.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAvatar.setGravity(android.view.Gravity.CENTER);
        tvAvatar.setBackgroundColor(0xFF6366F1);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(80, 80);
        avatarParams.setMarginEnd(24);
        tvAvatar.setLayoutParams(avatarParams);

        // Text block
        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvPrimary = new TextView(this);
        tvPrimary.setText(primary);
        tvPrimary.setTextSize(15f);
        tvPrimary.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvPrimary.setTypeface(null, android.graphics.Typeface.BOLD);

        textBlock.addView(tvPrimary);

        if (!secondary.isEmpty()) {
            TextView tvSecondary = new TextView(this);
            tvSecondary.setText(secondary);
            tvSecondary.setTextSize(13f);
            tvSecondary.setTextColor(getResources().getColor(R.color.text_secondary, null));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sp.setMargins(0, 4, 0, 0);
            tvSecondary.setLayoutParams(sp);
            textBlock.addView(tvSecondary);
        }

        inner.addView(tvAvatar);
        inner.addView(textBlock);
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
