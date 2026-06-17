package com.lakpura.app;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthHelper {

    public interface AuthCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Stored after login so other screens can use it
    public static String accessToken = null;
    public static String currentUserEmail = null;

    public static void register(String email, String password, String fullName, AuthCallback callback) {
        executor.execute(() -> {
            try {
                // 1. Create the auth user
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("password", password);

                Request request = new Request.Builder()
                        .url(SupabaseClient.PROJECT_URL + "/auth/v1/signup")
                        .addHeader("apikey", SupabaseClient.ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (!response.isSuccessful()) {
                    String errorMsg = extractError(responseBody);
                    mainHandler.post(() -> callback.onError(errorMsg));
                    return;
                }

                // Parse access token
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                // Supabase may return access_token directly or inside a session object
                String token = null;
                if (json.has("access_token")) {
                    token = json.get("access_token").getAsString();
                } else if (json.has("session") && !json.get("session").isJsonNull()) {
                    token = json.getAsJsonObject("session").get("access_token").getAsString();
                }

                if (token == null) {
                    // Email confirmation may be required — still success
                    mainHandler.post(() -> callback.onSuccess("Account created! Check your email to confirm, then log in."));
                    return;
                }

                accessToken = token;
                currentUserEmail = email;

                // 2. Insert profile row
                String userId = json.has("id")
                        ? json.get("id").getAsString()
                        : json.getAsJsonObject("user").get("id").getAsString();

                insertProfile(userId, fullName, email);

                mainHandler.post(() -> callback.onSuccess("Account created successfully!"));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public static void login(String email, String password, AuthCallback callback) {
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("password", password);

                Request request = new Request.Builder()
                        .url(SupabaseClient.PROJECT_URL + "/auth/v1/token?grant_type=password")
                        .addHeader("apikey", SupabaseClient.ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (!response.isSuccessful()) {
                    String errorMsg = extractError(responseBody);
                    mainHandler.post(() -> callback.onError(errorMsg));
                    return;
                }

                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                accessToken = json.get("access_token").getAsString();
                currentUserEmail = email;

                mainHandler.post(() -> callback.onSuccess("Login successful!"));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public static void logout() {
        accessToken = null;
        currentUserEmail = null;
    }

    private static void insertProfile(String userId, String fullName, String email) {
        try {
            JsonObject profile = new JsonObject();
            profile.addProperty("id", userId);
            profile.addProperty("full_name", fullName);
            profile.addProperty("email", email);

            Request request = new Request.Builder()
                    .url(SupabaseClient.PROJECT_URL + "/rest/v1/profiles")
                    .addHeader("apikey", SupabaseClient.ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(RequestBody.create(profile.toString(), JSON))
                    .build();

            client.newCall(request).execute();
        } catch (IOException ignored) {}
    }

    private static String extractError(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error_description")) return json.get("error_description").getAsString();
            if (json.has("msg")) return json.get("msg").getAsString();
            if (json.has("message")) return json.get("message").getAsString();
        } catch (Exception ignored) {}
        return "Something went wrong. Please try again.";
    }
}
