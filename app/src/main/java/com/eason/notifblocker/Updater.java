package com.eason.notifblocker;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/** Checks the latest GitHub release, downloads the APK, verifies its SHA-256 and hands it to the system installer. */
final class Updater {
    interface Callback<T> {
        void onResult(T value, String error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Updater() {}

    /** Result is null (no error) when the release has no newer build. */
    static void check(long installedVersionCode, Callback<Update> callback) {
        new Thread(() -> {
            Update found = null;
            String error = null;
            try {
                String[] urls = Update.assetUrls(fetchText(Update.LATEST_URL));
                if (urls == null) throw new IllegalStateException("The latest release has no APK yet");
                Update update = Update.parse(fetchText(urls[0]), urls[1]);
                if (update.isNewerThan(installedVersionCode)) found = update;
            } catch (Exception e) {
                error = message(e);
            }
            final Update result = found;
            final String failure = error;
            MAIN.post(() -> callback.onResult(result, failure));
        }, "update-check").start();
    }

    /** Downloads into the app cache and verifies size and checksum; the file is deleted on any mismatch. */
    static void download(Context context, Update update, Callback<File> callback) {
        final File target = new File(context.getCacheDir(), "update.apk");
        new Thread(() -> {
            File done = null;
            String error = null;
            try {
                HttpURLConnection connection = open(update.apkUrl);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long total = 0;
                try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    for (int read; (read = in.read(buffer)) > 0; ) {
                        total += read;
                        if (total > Update.MAX_APK_BYTES) throw new IllegalStateException("Download too large");
                        digest.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                }
                StringBuilder hex = new StringBuilder();
                for (byte b : digest.digest()) hex.append(String.format("%02x", b));
                if (total != update.size || !hex.toString().equals(update.sha256))
                    throw new IllegalStateException("Downloaded file failed the checksum");
                done = target;
            } catch (Exception e) {
                error = message(e);
                target.delete();
            }
            final File result = done;
            final String failure = error;
            MAIN.post(() -> callback.onResult(result, failure));
        }, "update-download").start();
    }

    /** Returns an error message, or null when the system install prompt was started. */
    static String install(Context context, File apk) {
        if (!context.getPackageManager().canRequestPackageInstalls()) {
            context.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "Allow \u201cinstall unknown apps\u201d for this app, then tap Install again.";
        }
        try {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            int id = installer.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
            try (PackageInstaller.Session session = installer.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("update.apk", 0, apk.length())) {
                    byte[] buffer = new byte[64 * 1024];
                    for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
                    session.fsync(out);
                }
                Intent result = new Intent(context, InstallReceiver.class);
                PendingIntent pending = PendingIntent.getBroadcast(context, id, result,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                session.commit(pending.getIntentSender());
            }
            return null;
        } catch (Exception e) {
            return message(e);
        }
    }

    private static String fetchText(String url) throws Exception {
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
            return out.toString("UTF-8");
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "notif-blocker");
        int code = connection.getResponseCode();
        if (code != 200) throw new IllegalStateException("GitHub answered HTTP " + code);
        return connection;
    }

    private static String message(Exception e) {
        String text = e.getMessage();
        return text == null || text.isEmpty() ? e.getClass().getSimpleName() : text;
    }
}
