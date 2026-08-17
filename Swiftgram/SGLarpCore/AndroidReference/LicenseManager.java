package tw.nekomimi.nekogram.helpers;

import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import org.json.JSONObject;

import androidx.annotation.Nullable;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * [MG] License activation against TelegramKeys server (same as AyuGram Desktop).
 */
public class LicenseManager {

    public static final String DEFAULT_SERVER_HOST = "2.26.48.141";
    public static final String SERVER_HOST = DEFAULT_SERVER_HOST;
    public static final int SERVER_PORT = 1048;

    public static String getServerHost() {
        final String custom = SharedConfig.larpServerHost;
        if (!TextUtils.isEmpty(custom)) {
            return normalizeServerHost(custom);
        }
        return DEFAULT_SERVER_HOST;
    }

    public static int getServerPort() {
        final String custom = SharedConfig.larpServerHost;
        if (!TextUtils.isEmpty(custom)) {
            final int fromCustom = parsePortFromHostField(custom);
            if (fromCustom > 0) {
                return fromCustom;
            }
        }
        return SERVER_PORT;
    }

    /** Accepts "2.26.48.141", "http://2.26.48.141:1048", strips path/query. */
    public static String normalizeServerHost(String raw) {
        if (raw == null) {
            return DEFAULT_SERVER_HOST;
        }
        String host = raw.trim();
        if (host.isEmpty()) {
            return DEFAULT_SERVER_HOST;
        }
        if (host.startsWith("http://")) {
            host = host.substring(7);
        } else if (host.startsWith("https://")) {
            host = host.substring(8);
        }
        final int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        final int colon = host.lastIndexOf(':');
        if (colon > 0 && colon < host.length() - 1) {
            final String maybePort = host.substring(colon + 1);
            boolean allDigits = true;
            for (int i = 0; i < maybePort.length(); i++) {
                if (!Character.isDigit(maybePort.charAt(i))) {
                    allDigits = false;
                    break;
                }
            }
            if (allDigits) {
                host = host.substring(0, colon);
            }
        }
        return host.isEmpty() ? DEFAULT_SERVER_HOST : host;
    }

    private static int parsePortFromHostField(String raw) {
        if (raw == null) {
            return -1;
        }
        String host = raw.trim();
        if (host.startsWith("http://")) {
            host = host.substring(7);
        } else if (host.startsWith("https://")) {
            host = host.substring(8);
        }
        final int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        final int colon = host.lastIndexOf(':');
        if (colon > 0 && colon < host.length() - 1) {
            try {
                return Integer.parseInt(host.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static String serverBaseUrl(String path) {
        return "http://" + getServerHost() + ":" + getServerPort() + path;
    }

    private static final long REVALIDATE_MS = 60 * 60 * 1000L;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Runnable revalidateRunnable;

    public interface ResultCallback {
        void onResult(boolean ok, String message);
    }

    public interface JsonCallback {
        void onResult(@Nullable JSONObject response, @Nullable String error);
    }

    public static boolean larpGiftsEnabled() {
        return isActive()
                && SharedConfig.larpVisualMenu
                && SharedConfig.larpFakeGifts;
    }

    public static boolean fakeBalanceEnabled() {
        return isActive()
                && SharedConfig.larpVisualMenu
                && (SharedConfig.larpFakeGifts
                || SharedConfig.fakeStarsAmount > 0
                || SharedConfig.fakeTonAmount > 0);
    }

    public static void start() {
        if (!TextUtils.isEmpty(SharedConfig.larpLicenseKey)) {
            // Soft unlock from disk; validate by IP — different IP clears key.
            if (!SharedConfig.larpLicenseActive) {
                SharedConfig.setLarpLicenseActive(true);
            }
            ensureActivated(null);
        }
        scheduleRevalidate();
        GiftRelayManager.start();
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) {
                LarpTransactionHelper.mergeStored(a, false);
                LarpTransactionHelper.mergeStored(a, true);
                LarpGiftStore.fetchInventoryFromServer(a);
                LarpGiftsInjector.syncAllForAccount(a);
                LarpPersonalChannel.apply(a);
                LarpProfileRelay.applySelfUsernames(a);
                LarpBadgeIcons.preload(a);
            }
        }
    }

    public static void postJson(String path, JSONObject body, JsonCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            final JSONObject response = postJsonSync(path, body);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(
                        response,
                        response != null ? null : "network_error"));
            }
        });
    }

    @Nullable
    public static JSONObject postJsonSync(String path, JSONObject body) {
        HttpURLConnection connection = null;
        try {
            final URL url = new URL(serverBaseUrl(path));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Connection", "close");
            connection.setDoOutput(true);
            final byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }
            final int code = connection.getResponseCode();
            final java.io.InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) {
                FileLog.d("LicenseManager: empty response from " + url);
                return null;
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8));
            final StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            if (response.length() == 0) {
                FileLog.d("LicenseManager: HTTP " + code + " empty body from " + url);
                return null;
            }
            return new JSONObject(response.toString());
        } catch (Exception e) {
            FileLog.e("LicenseManager: request failed " + serverBaseUrl(path), e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void scheduleRevalidate() {
        if (revalidateRunnable != null) {
            mainHandler.removeCallbacks(revalidateRunnable);
        }
        revalidateRunnable = () -> {
            validateStored(null);
            mainHandler.postDelayed(revalidateRunnable, REVALIDATE_MS);
        };
        mainHandler.postDelayed(revalidateRunnable, REVALIDATE_MS);
    }

    public static String getHardwareId() {
        // Sticky HWID: once saved, never change for this install (avoids hwid_mismatch on reopen).
        try {
            final android.content.SharedPreferences prefs = ApplicationLoader.applicationContext
                    .getSharedPreferences("larplicense", android.app.Activity.MODE_PRIVATE);
            final String stored = prefs.getString("hwid", "");
            if (!TextUtils.isEmpty(stored)) {
                return stored;
            }
            final String computed = computeHardwareId();
            prefs.edit().putString("hwid", computed).commit();
            return computed;
        } catch (Exception e) {
            FileLog.e(e);
            return computeHardwareId();
        }
    }

    private static String computeHardwareId() {
        try {
            final String androidId = Settings.Secure.getString(
                    ApplicationLoader.applicationContext.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (!TextUtils.isEmpty(androidId)) {
                return sha256Hex("mg-" + androidId);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return sha256Hex("mg-fallback-hwid");
    }

    public static void activate(String key, ResultCallback callback) {
        final String trimmed = key == null ? "" : key.trim();
        if (trimmed.isEmpty()) {
            if (callback != null) {
                callback.onResult(false, "empty_key");
            }
            return;
        }
        // /api/activate binds current IP + HWID after user entered the key.
        request("/api/activate", trimmed, (ok, message) -> {
            if (ok) {
                SharedConfig.setLarpLicenseKey(trimmed);
                setActive(true);
            } else if ("invalid_key".equals(message)
                    || "hwid_mismatch".equals(message)
                    || "ip_mismatch".equals(message)) {
                setActive(false);
            }
            // network_error: leave previous session as-is
            if (callback != null) {
                callback.onResult(ok, message);
            }
        }, false);
    }

    public static void validateStored(ResultCallback callback) {
        if (TextUtils.isEmpty(SharedConfig.larpLicenseKey)) {
            if (callback != null) {
                callback.onResult(false, "empty_key");
            }
            return;
        }
        // Validate the persisted key. The server accepts a known installation even if its IP changed.
        request("/api/validate", SharedConfig.larpLicenseKey, callback, true);
    }

    private static void request(String path, String key, ResultCallback callback) {
        request(path, key, callback, true);
    }

    private static void request(String path, String key, ResultCallback callback, boolean updateActiveFromResult) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            String message = "network_error";
            HttpURLConnection connection = null;
            try {
                final URL url = new URL(serverBaseUrl(path));
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Connection", "close");
                connection.setDoOutput(true);

                final JSONObject body = new JSONObject();
                body.put("key", key);
                body.put("hwid", getHardwareId());
                final byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(bytes);
                }

                final int code = connection.getResponseCode();
                final java.io.InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                if (stream == null) {
                    FileLog.d("LicenseManager: empty response HTTP " + code + " from " + url);
                    message = "network_error";
                } else {
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(
                            stream, StandardCharsets.UTF_8));
                    final StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    if (response.length() == 0) {
                        FileLog.d("LicenseManager: HTTP " + code + " empty body from " + url);
                        message = "network_error";
                    } else {
                        final JSONObject json = new JSONObject(response.toString());
                        ok = json.optBoolean("ok", false);
                        message = json.optString("message", ok ? "ok" : "bad_response");
                    }
                }
            } catch (Exception e) {
                FileLog.e("LicenseManager: activate/validate failed " + serverBaseUrl(path), e);
                message = "network_error";
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            final boolean success = ok;
            final String resultMessage = message;
            AndroidUtilities.runOnUIThread(() -> {
                if (updateActiveFromResult && path.contains("validate")) {
                    if (success) {
                        setActive(true);
                    } else if ("not_activated".equals(resultMessage)
                            && !TextUtils.isEmpty(SharedConfig.larpLicenseKey)) {
                        // First bind only — does not auto-add a new IP if already activated.
                        activate(SharedConfig.larpLicenseKey, callback);
                        return;
                    } else if ("ip_mismatch".equals(resultMessage)
                            || "hwid_mismatch".equals(resultMessage)) {
                        // Keep the saved key for diagnostics, but never bind it to another device.
                        setActive(false);
                    } else if ("invalid_key".equals(resultMessage)) {
                        SharedConfig.clearLarpLicense();
                        NotificationCenter.getGlobalInstance().postNotificationName(
                                NotificationCenter.mainUserInfoChanged);
                    }
                    // network_error → keep previous active + key (same IP offline)
                }
                if (callback != null) {
                    callback.onResult(success, resultMessage);
                }
            });
        });
    }

    public static boolean isActive() {
        return SharedConfig.larpLicenseActive && !TextUtils.isEmpty(SharedConfig.larpLicenseKey);
    }

    public static void ensureActivated(ResultCallback callback) {
        if (TextUtils.isEmpty(SharedConfig.larpLicenseKey)) {
            if (callback != null) {
                callback.onResult(false, "empty_key");
            }
            return;
        }
        if (!SharedConfig.larpLicenseActive) {
            SharedConfig.setLarpLicenseActive(true);
        }
        // Validate the saved key; a changed network is rebound without asking for the key again.
        validateStored((ok, message) -> {
            if (callback != null) {
                callback.onResult(ok || SharedConfig.larpLicenseActive, message);
            }
        });
    }

    private static void setActive(boolean active) {
        if (SharedConfig.larpLicenseActive == active) {
            return;
        }
        SharedConfig.setLarpLicenseActive(active);
        if (active) {
            GiftRelayManager.pollNow();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    LarpProfileRelay.publishSelf(a);
                }
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.mainUserInfoChanged);
    }

    private static String sha256Hex(String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return value;
        }
    }
}
