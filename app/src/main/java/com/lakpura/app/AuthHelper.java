package com.lakpura.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
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

                // Subscribe this device to FCM topic for push notifications
                com.google.firebase.messaging.FirebaseMessaging.getInstance()
                        .subscribeToTopic("lakpura_all");

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
    // -------------------------------------------------------------------------
    // Get user count (for admin panel display)
    // -------------------------------------------------------------------------
    public interface UserCountCallback {
        void onResult(int count);
        void onError(String error);
    }

    public static void getUserCount(UserCountCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(SupabaseClient.PROJECT_URL + "/rest/v1/profiles?select=id")
                        .addHeader("apikey", SupabaseClient.ANON_KEY)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Prefer", "count=exact")
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                String countHeader = response.header("content-range");
                int count = 0;
                if (countHeader != null && countHeader.contains("/")) {
                    try { count = Integer.parseInt(countHeader.split("/")[1]); } catch (Exception ignored) {}
                }
                int finalCount = count;
                mainHandler.post(() -> callback.onResult(finalCount));
            } catch (IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Send in-app only notification (saves to Supabase, no email)
    // -------------------------------------------------------------------------
    public static void sendInAppNotification(String subject, String message, AuthCallback callback) {
        executor.execute(() -> {
            boolean saved = logNotification(subject, message);
            if (!saved) {
                mainHandler.post(() -> callback.onError("Failed to send notification. Check your connection."));
                return;
            }
            // Send FCM push to all subscribed devices
            sendFcmTopicMessage(subject, message);
            mainHandler.post(() -> callback.onSuccess("Notification sent to all users!"));
        });
    }

    // -------------------------------------------------------------------------
    // Send FCM push via V1 API to topic "lakpura_all"
    // -------------------------------------------------------------------------
    private static void sendFcmTopicMessage(String title, String body) {
        try {
            String oauthToken = getFcmOauthToken();
            if (oauthToken == null) return;

            JsonObject notification = new JsonObject();
            notification.addProperty("title", title);
            notification.addProperty("body", body);

            JsonObject androidConfig = new JsonObject();
            JsonObject androidNotification = new JsonObject();
            androidNotification.addProperty("channel_id", "lakpura_notifications");
            androidConfig.add("notification", androidNotification);
            androidConfig.addProperty("priority", "high");

            JsonObject msg = new JsonObject();
            msg.addProperty("topic", "lakpura_all");
            msg.add("notification", notification);
            msg.add("android", androidConfig);

            JsonObject payload = new JsonObject();
            payload.add("message", msg);

            String fcmUrl = "https://fcm.googleapis.com/v1/projects/"
                    + SupabaseClient.FCM_PROJECT_ID + "/messages:send";

            Request request = new Request.Builder()
                    .url(fcmUrl)
                    .addHeader("Authorization", "Bearer " + oauthToken)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .build();

            client.newCall(request).execute();
        } catch (Exception ignored) {}
    }

    // Build a short-lived OAuth2 token from the service account private key
    private static String getFcmOauthToken() {
        try {
            long now = System.currentTimeMillis() / 1000L;

            // JWT header
            String header = Base64.encodeToString(
                    "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

            // JWT claim set
            JsonObject claims = new JsonObject();
            claims.addProperty("iss", SupabaseClient.FCM_CLIENT_EMAIL);
            claims.addProperty("scope", "https://www.googleapis.com/auth/firebase.messaging");
            claims.addProperty("aud", "https://oauth2.googleapis.com/token");
            claims.addProperty("iat", now);
            claims.addProperty("exp", now + 3600);
            String claimSet = Base64.encodeToString(
                    claims.toString().getBytes(StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

            // Sign
            String signingInput = header + "." + claimSet;
            String rawKey = SupabaseClient.FCM_PRIVATE_KEY
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.decode(rawKey, Base64.DEFAULT);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.encodeToString(
                    sig.sign(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

            String jwt = signingInput + "." + signature;

            // Exchange JWT for access token
            RequestBody tokenBody = new okhttp3.FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    .add("assertion", jwt)
                    .build();

            Request tokenRequest = new Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(tokenBody)
                    .build();

            Response tokenResponse = client.newCall(tokenRequest).execute();
            String tokenJson = tokenResponse.body().string();
            JsonObject tokenObj = JsonParser.parseString(tokenJson).getAsJsonObject();
            return tokenObj.get("access_token").getAsString();

        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Send in-app notification (saves to Supabase) + optional email via Brevo
    // -------------------------------------------------------------------------
    public static void sendNotification(List<String> emails, String subject, String message, AuthCallback callback) {
        executor.execute(() -> {
            // Step 1: always save to Supabase first so in-app works regardless of email
            boolean saved = logNotification(subject, message);
            if (!saved) {
                mainHandler.post(() -> callback.onError("Failed to save notification. Check your connection."));
                return;
            }

            // Step 2: try sending email via Brevo — if it fails, still report success for in-app
            try {
                JsonArray toArray = new JsonArray();
                for (String email : emails) {
                    JsonObject recipient = new JsonObject();
                    recipient.addProperty("email", email);
                    toArray.add(recipient);
                }

                JsonObject sender = new JsonObject();
                sender.addProperty("name", SupabaseClient.BREVO_SENDER_NAME);
                sender.addProperty("email", SupabaseClient.BREVO_SENDER_EMAIL);

                JsonObject body = new JsonObject();
                body.add("sender", sender);
                body.add("to", toArray);
                body.addProperty("subject", subject);
                body.addProperty("htmlContent",
                        "<div style='font-family:sans-serif;padding:20px'>"
                        + "<h2>" + subject + "</h2>"
                        + "<p>" + message.replace("\n", "<br>") + "</p>"
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
                    // In-app saved fine, email failed — report partial success
                    mainHandler.post(() -> callback.onSuccess(
                        "Notification saved! (" + emails.size() + " users can see it in-app)\nEmail delivery failed: " + extractError(responseBody)));
                } else {
                    mainHandler.post(() -> callback.onSuccess(
                        "Notification sent to " + emails.size() + " users via email and in-app!"));
                }

            } catch (IOException e) {
                // In-app saved fine, email had network error
                mainHandler.post(() -> callback.onSuccess(
                    "Notification saved in-app! Email could not be sent: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Get all notifications (for users to view)
    // -------------------------------------------------------------------------
    public static void getNotifications(NotificationsCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(SupabaseClient.PROJECT_URL + "/rest/v1/notifications?select=subject,message,sent_at&order=sent_at.desc")
                        .addHeader("apikey", SupabaseClient.ANON_KEY)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Could not load notifications"));
                    return;
                }

                JsonArray arr = JsonParser.parseString(responseBody).getAsJsonArray();
                List<NotificationItem> items = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    String s = obj.has("subject") ? obj.get("subject").getAsString() : "";
                    String m = obj.has("message") ? obj.get("message").getAsString() : "";
                    String t = obj.has("sent_at") ? obj.get("sent_at").getAsString() : "";
                    items.add(new NotificationItem(s, m, t));
                }

                mainHandler.post(() -> callback.onSuccess(items));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public interface NotificationsCallback {
        void onSuccess(List<NotificationItem> items);
        void onError(String error);
    }

    public static class NotificationItem {
        public String subject, message, sentAt;
        public NotificationItem(String subject, String message, String sentAt) {
            this.subject = subject;
            this.message = message;
            this.sentAt  = sentAt;
        }
    }

    // -------------------------------------------------------------------------
    // Create a new user (called by admin) — uses signup endpoint with admin's key
    // -------------------------------------------------------------------------
    public static void createUser(String email, String password, String fullName, boolean makeAdmin, AuthCallback callback) {
        executor.execute(() -> {
            try {
                // Step 1: sign up the new user
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("password", password);

                Request signupRequest = new Request.Builder()
                        .url(SupabaseClient.PROJECT_URL + "/auth/v1/signup")
                        .addHeader("apikey", SupabaseClient.ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response signupResponse = client.newCall(signupRequest).execute();
                String signupBody = signupResponse.body().string();

                if (!signupResponse.isSuccessful()) {
                    mainHandler.post(() -> callback.onError(extractError(signupBody)));
                    return;
                }

                JsonObject json = JsonParser.parseString(signupBody).getAsJsonObject();

                // Get new user's id and token
                String newUserId = null;
                String newUserToken = null;

                if (json.has("id")) {
                    newUserId = json.get("id").getAsString();
                } else if (json.has("user") && !json.get("user").isJsonNull()) {
                    newUserId = json.getAsJsonObject("user").get("id").getAsString();
                }

                if (json.has("access_token")) {
                    newUserToken = json.get("access_token").getAsString();
                } else if (json.has("session") && !json.get("session").isJsonNull()) {
                    newUserToken = json.getAsJsonObject("session").get("access_token").getAsString();
                }

                if (newUserId == null) {
                    mainHandler.post(() -> callback.onError("Could not retrieve new user ID."));
                    return;
                }

                // Step 2: insert profile using new user's token (if available) or admin's token
                String tokenToUse = newUserToken != null ? newUserToken : accessToken;

                JsonObject profile = new JsonObject();
                profile.addProperty("id", newUserId);
                profile.addProperty("full_name", fullName);
                profile.addProperty("email", email);
                profile.addProperty("is_admin", makeAdmin);

                Request profileRequest = new Request.Builder()
                        .url(SupabaseClient.PROJECT_URL + "/rest/v1/profiles")
                        .addHeader("apikey", SupabaseClient.ANON_KEY)
                        .addHeader("Authorization", "Bearer " + tokenToUse)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .post(RequestBody.create(profile.toString(), JSON))
                        .build();

                client.newCall(profileRequest).execute();

                // Step 3: if token was from new user but makeAdmin=true, update via admin token
                if (makeAdmin && newUserToken != null) {
                    updateAdminFlag(newUserId, true);
                }

                String role = makeAdmin ? "Admin" : "Standard User";
                mainHandler.post(() -> callback.onSuccess("User created successfully as " + role + "!"));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Update is_admin flag for a user (admin only)
    // -------------------------------------------------------------------------
    private static void updateAdminFlag(String userId, boolean isAdmin) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("is_admin", isAdmin);

            Request request = new Request.Builder()
                    .url(SupabaseClient.PROJECT_URL + "/rest/v1/profiles?id=eq." + userId)
                    .addHeader("apikey", SupabaseClient.ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).execute();
        } catch (IOException ignored) {}
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

    private static boolean logNotification(String subject, String message) {
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

            Response response = client.newCall(request).execute();
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
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
