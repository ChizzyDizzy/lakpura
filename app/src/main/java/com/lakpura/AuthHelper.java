package com.lakpura;

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

    public interface LoginCallback {
        void onSuccess(boolean isAdmin);
        void onError(String error);
    }

    public interface UserCountCallback {
        void onResult(int count);
        void onError(String error);
    }

    public interface NotificationsCallback {
        void onSuccess(List<NotificationItem> items);
        void onError(String error);
    }

    public interface CustomersCallback {
        void onSuccess(List<CustomerItem> items);
        void onError(String error);
    }

    public interface JobsCallback {
        void onSuccess(List<JobItem> items);
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

    public static class CustomerItem {
        public String id, name, phone, email, address;
        public CustomerItem(String id, String name, String phone, String email, String address) {
            this.id      = id;
            this.name    = name;
            this.phone   = phone;
            this.email   = email;
            this.address = address;
        }
    }

    public static class JobItem {
        public String id, title, status, customerName, scheduledDate, notes;
        public JobItem(String id, String title, String status, String customerName, String scheduledDate, String notes) {
            this.id            = id;
            this.title         = title;
            this.status        = status;
            this.customerName  = customerName;
            this.scheduledDate = scheduledDate;
            this.notes         = notes;
        }
    }

    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static String accessToken = null;
    public static String currentUserEmail = null;
    public static String currentUserName = null;
    public static boolean currentIsAdmin = false;

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------
    public static void login(String email, String password, LoginCallback callback) {
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("password", password);

                Request request = new Request.Builder()
                        .url(ApiClient.BASE_URL + "/auth/login")
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.code() == 429) {
                    mainHandler.post(() -> callback.onError("Too many login attempts. Please try again in a few minutes."));
                    return;
                }
                if (response.code() == 403) {
                    mainHandler.post(() -> callback.onError("Account inactive or requires web sign-in."));
                    return;
                }
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError(extractError(responseBody)));
                    return;
                }

                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                accessToken = json.get("access_token").getAsString();

                JsonObject user = json.getAsJsonObject("user");
                currentUserEmail = user.get("email").getAsString();
                currentUserName  = user.has("full_name") ? user.get("full_name").getAsString() : email;
                currentIsAdmin   = user.has("is_admin") && user.get("is_admin").getAsBoolean();

                com.google.firebase.messaging.FirebaseMessaging.getInstance()
                        .subscribeToTopic("lakpura_all");

                mainHandler.post(() -> callback.onSuccess(currentIsAdmin));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------
    public static void logout() {
        if (accessToken != null) {
            String token = accessToken;
            executor.execute(() -> {
                try {
                    Request request = new Request.Builder()
                            .url(ApiClient.BASE_URL + "/auth/logout")
                            .addHeader("Authorization", "Bearer " + token)
                            .addHeader("Content-Type", "application/json")
                            .post(RequestBody.create("{}", JSON))
                            .build();
                    client.newCall(request).execute();
                } catch (IOException ignored) {}
            });
        }
        accessToken = null;
        currentUserEmail = null;
        currentUserName = null;
        currentIsAdmin = false;
    }

    // -------------------------------------------------------------------------
    // Get notifications
    // -------------------------------------------------------------------------
    public static void getNotifications(NotificationsCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(ApiClient.BASE_URL + "/notifications")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.code() == 401) {
                    handleSessionExpired();
                    mainHandler.post(() -> callback.onError("Session expired. Please log in again."));
                    return;
                }
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Could not load notifications."));
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

    // -------------------------------------------------------------------------
    // Get customers
    // -------------------------------------------------------------------------
    public static void getCustomers(CustomersCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(ApiClient.BASE_URL + "/customers")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.code() == 401) {
                    handleSessionExpired();
                    mainHandler.post(() -> callback.onError("Session expired. Please log in again."));
                    return;
                }
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Could not load customers."));
                    return;
                }

                JsonArray arr = JsonParser.parseString(responseBody).getAsJsonArray();
                List<CustomerItem> items = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    String id      = str(obj, "id");
                    String name    = str(obj, "name");
                    String phone   = str(obj, "phone");
                    String email   = str(obj, "email");
                    String address = str(obj, "address");
                    items.add(new CustomerItem(id, name, phone, email, address));
                }

                mainHandler.post(() -> callback.onSuccess(items));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Get jobs (customerId may be null for all jobs)
    // -------------------------------------------------------------------------
    public static void getJobs(String customerId, JobsCallback callback) {
        executor.execute(() -> {
            try {
                String url = ApiClient.BASE_URL + "/jobs";
                if (customerId != null && !customerId.isEmpty()) {
                    url += "?customer_id=" + customerId;
                }

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.code() == 401) {
                    handleSessionExpired();
                    mainHandler.post(() -> callback.onError("Session expired. Please log in again."));
                    return;
                }
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Could not load jobs."));
                    return;
                }

                JsonArray arr = JsonParser.parseString(responseBody).getAsJsonArray();
                List<JobItem> items = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    String id            = str(obj, "id");
                    String title         = str(obj, "title");
                    String status        = str(obj, "status").isEmpty() ? "open" : str(obj, "status");
                    String customerName  = str(obj, "customer_name");
                    String scheduledDate = str(obj, "scheduled_date");
                    String notes         = str(obj, "notes");
                    items.add(new JobItem(id, title, status, customerName, scheduledDate, notes));
                }

                mainHandler.post(() -> callback.onSuccess(items));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Update job status
    // -------------------------------------------------------------------------
    public static void updateJobStatus(String jobId, String status, AuthCallback callback) {
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("status", status);

                Request request = new Request.Builder()
                        .url(ApiClient.BASE_URL + "/jobs/" + jobId)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .patch(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = client.newCall(request).execute();

                if (response.code() == 401) {
                    handleSessionExpired();
                    mainHandler.post(() -> callback.onError("Session expired. Please log in again."));
                    return;
                }
                if (!response.isSuccessful()) {
                    String rb = response.body().string();
                    mainHandler.post(() -> callback.onError(extractError(rb)));
                    return;
                }

                mainHandler.post(() -> callback.onSuccess("Status updated successfully"));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Get user count (admin only)
    // -------------------------------------------------------------------------
    public static void getUserCount(UserCountCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(ApiClient.BASE_URL + "/users/count")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.code() == 401) {
                    handleSessionExpired();
                    mainHandler.post(() -> callback.onError("Session expired."));
                    return;
                }
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Could not get user count."));
                    return;
                }

                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                int count = json.has("count") ? json.get("count").getAsInt() : 0;
                mainHandler.post(() -> callback.onResult(count));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Send notification (admin only)
    // -------------------------------------------------------------------------
    public static void sendInAppNotification(String subject, String message, AuthCallback callback) {
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("subject", subject);
                body.addProperty("message", message);

                Request request = new Request.Builder()
                        .url(ApiClient.BASE_URL + "/notifications/send")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = client.newCall(request).execute();

                if (response.code() == 501) {
                    mainHandler.post(() -> callback.onError("Sending notifications is coming in Phase 3."));
                    return;
                }
                if (response.code() == 401) {
                    handleSessionExpired();
                    mainHandler.post(() -> callback.onError("Session expired. Please log in again."));
                    return;
                }
                if (!response.isSuccessful()) {
                    String rb = response.body().string();
                    mainHandler.post(() -> callback.onError(extractError(rb)));
                    return;
                }

                sendFcmTopicMessage(subject, message);
                mainHandler.post(() -> callback.onSuccess("Notification sent to all users!"));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Create user (admin only)
    // -------------------------------------------------------------------------
    public static void createUser(String email, String password, String fullName, boolean makeAdmin, AuthCallback callback) {
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("password", password);
                body.addProperty("full_name", fullName);
                body.addProperty("is_admin", makeAdmin);

                Request request = new Request.Builder()
                        .url(ApiClient.BASE_URL + "/users/create")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = client.newCall(request).execute();

                if (response.code() == 501) {
                    mainHandler.post(() -> callback.onError("Creating users is coming in Phase 3."));
                    return;
                }
                if (response.code() == 401) {
                    handleSessionExpired();
                    mainHandler.post(() -> callback.onError("Session expired. Please log in again."));
                    return;
                }
                if (!response.isSuccessful()) {
                    String rb = response.body().string();
                    mainHandler.post(() -> callback.onError(extractError(rb)));
                    return;
                }

                String role = makeAdmin ? "Admin" : "Standard User";
                mainHandler.post(() -> callback.onSuccess("User created successfully as " + role + "!"));

            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // FCM push via V1 API
    // -------------------------------------------------------------------------
    private static void sendFcmTopicMessage(String title, String body) {
        try {
            String oauthToken = getFcmOauthToken();
            if (oauthToken == null) return;

            JsonObject notification = new JsonObject();
            notification.addProperty("title", title);
            notification.addProperty("body", body);

            JsonObject androidNotification = new JsonObject();
            androidNotification.addProperty("channel_id", "lakpura_notifications");
            JsonObject androidConfig = new JsonObject();
            androidConfig.add("notification", androidNotification);
            androidConfig.addProperty("priority", "high");

            JsonObject msg = new JsonObject();
            msg.addProperty("topic", "lakpura_all");
            msg.add("notification", notification);
            msg.add("android", androidConfig);

            JsonObject payload = new JsonObject();
            payload.add("message", msg);

            String fcmUrl = "https://fcm.googleapis.com/v1/projects/"
                    + ApiClient.FCM_PROJECT_ID + "/messages:send";

            Request request = new Request.Builder()
                    .url(fcmUrl)
                    .addHeader("Authorization", "Bearer " + oauthToken)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .build();

            client.newCall(request).execute();
        } catch (Exception ignored) {}
    }

    private static String getFcmOauthToken() {
        try {
            long now = System.currentTimeMillis() / 1000L;

            String header = Base64.encodeToString(
                    "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

            JsonObject claims = new JsonObject();
            claims.addProperty("iss", ApiClient.FCM_CLIENT_EMAIL);
            claims.addProperty("scope", "https://www.googleapis.com/auth/firebase.messaging");
            claims.addProperty("aud", "https://oauth2.googleapis.com/token");
            claims.addProperty("iat", now);
            claims.addProperty("exp", now + 3600);
            String claimSet = Base64.encodeToString(
                    claims.toString().getBytes(StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

            String signingInput = header + "." + claimSet;
            String rawKey = ApiClient.FCM_PRIVATE_KEY
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
    // Helpers
    // -------------------------------------------------------------------------
    private static void handleSessionExpired() {
        accessToken = null;
        currentUserEmail = null;
        currentUserName = null;
        currentIsAdmin = false;
    }

    private static String str(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private static String extractError(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) return json.get("error").getAsString();
            if (json.has("message")) return json.get("message").getAsString();
        } catch (Exception ignored) {}
        return "Something went wrong. Please try again.";
    }
}
