package com.lakpura.app;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    public interface AdminCheckCallback {
        void onResult(boolean isAdmin);
    }

    public interface EmailListCallback {
        void onSuccess(List<String> emails);
        void onError(String error);
    }

    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static String accessToken = null;
    public static String currentUserEmail = null;

    // -------------------------------------------------------------------------
    // Register
    // -------------------------------------------------------------------------
    public static void register(String email, String password, String fullName, AuthCallback callback) {
        executor.execute(() -> {
            try {
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
                    mainHandler.post(() -> callback.onError(extractError(responseBody)));
                    return;
                }

                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                String token = null;
                if (json.has("access_token")) {
                    token = json.get("access_token").getAsString();
                } else if (json.has("session") && !json.get("session").isJsonNull()) {
                    token = json.getAsJsonObject("session").get("access_token").getAsString();
                }

                if (token == null) {
                    mainHandler.post(() -> callback.onSuccess("Account created! Check your email to confirm, then log in."));
                    return;
                }

                accessToken = token;
                currentUserEmail = email;

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

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------
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
                    mainHandler.post(() -> callback.onError(extractError(responseBody)));
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

    // -------------------------------------------------------------------------
    // Check if current user is admin
    // -------------------------------------------------------------------------
    public static void checkIsAdmin(AdminCheckCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(SupabaseClient.PROJECT_URL + "/rest/v1/profiles?select=is_admin&id=eq." + getCurrentUserId())
                        .addHeader("apikey", SupabaseClient.ANON_KEY)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onResult(false));
                    return;
                }

                JsonArray arr = JsonParser.parseString(responseBody).getAsJsonArray();
                boolean isAdmin = false;
                if (arr.size() > 0) {
                    JsonObject profile = arr.get(0).getAsJsonObject();
                    if (profile.has("is_admin") && !profile.get("is_admin").isJsonNull()) {
                        isAdmin = profile.get("is_admin").getAsBoolean();
                    }
                }

                boolean finalIsAdmin = isAdmin;
                mainHandler.post(() -> callback.onResult(finalIsAdmin));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onResult(false));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Get all user emails (admin only — RLS enforces this)
    // -------------------------------------------------------------------------
    public static void getAllUserEmails(EmailListCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(SupabaseClient.PROJECT_URL + "/rest/v1/profiles?select=email")
                        .addHeader("apikey", SupabaseClient.ANON_KEY)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError(extractError(responseBody)));
                    return;
                }

                JsonArray arr = JsonParser.parseString(responseBody).getAsJsonArray();
                List<String> emails = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("email") && !obj.get("email").isJsonNull()) {
                        emails.add(obj.get("email").getAsString());
                    }
                }

                mainHandler.post(() -> callback.onSuccess(emails));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Send email notification via Brevo to a list of recipients
    // -------------------------------------------------------------------------
    public static void sendEmailNotification(List<String> emails, String subject, String message, AuthCallback callback) {
        executor.execute(() -> {
            try {
                // Build recipient array
                JsonArray toArray = new JsonArray();
                for (String email : emails) {
                    JsonObject recipient = new JsonObject();
                    recipient.addProperty("email", email);
                    toArray.add(recipient);
                }

                // Sender
                JsonObject sender = new JsonObject();
                sender.addProperty("name", SupabaseClient.BREVO_SENDER_NAME);
                sender.addProperty("email", SupabaseClient.BREVO_SENDER_EMAIL);

                // Body
                JsonObject body = new JsonObject();
                body.add("sender", sender);
                body.add("to", toArray);
                body.addProperty("subject", subject);
                body.addProperty("htmlContent",
                        "<div style='font-family:sans-serif;padding:20px'>"
                        + "<h2>" + subject + "</h2>"
                        + "<p>" + message + "</p>"
                        + "<hr><small>Sent via Lakpura App</small>"
                        + "</div>");

                Request request = new Request.Builder()
                        .url("https://api.brevo.com/v3/smtp/email")
                        .addHeader("api-key", SupabaseClient.BREVO_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Email failed: " + responseBody));
                    return;
                }

                // Log notification to Supabase
                logNotification(subject, message);

                mainHandler.post(() -> callback.onSuccess("Notification sent to " + emails.size() + " users!"));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------
    public static void logout() {
        accessToken = null;
        currentUserEmail = null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------
    private static void insertProfile(String userId, String fullName, String email) {
        try {
            JsonObject profile = new JsonObject();
            profile.addProperty("id", userId);
            profile.addProperty("full_name", fullName);
            profile.addProperty("email", email);
            profile.addProperty("is_admin", false);

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

    private static void logNotification(String subject, String message) {
        try {
            JsonObject log = new JsonObject();
            log.addProperty("subject", subject);
            log.addProperty("message", message);

            Request request = new Request.Builder()
                    .url(SupabaseClient.PROJECT_URL + "/rest/v1/notifications")
                    .addHeader("apikey", SupabaseClient.ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(RequestBody.create(log.toString(), JSON))
                    .build();

            client.newCall(request).execute();
        } catch (IOException ignored) {}
    }

    private static String getCurrentUserId() {
        // Decode the user ID from the JWT access token (middle segment)
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return "";
            String payload = new String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE));
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            return json.get("sub").getAsString();
        } catch (Exception e) {
            return "";
        }
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
