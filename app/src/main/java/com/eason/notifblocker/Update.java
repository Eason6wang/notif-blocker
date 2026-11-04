package com.eason.notifblocker;

import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Pure helpers for the GitHub-release updater (no Android classes, so they are unit-tested). */
final class Update {
    static final String REPO = "Eason6wang/notif-blocker";
    static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String MANIFEST_ASSET = "update.json";
    static final String APK_ASSET = "notif-blocker.apk";
    static final long MAX_APK_BYTES = 50L * 1024 * 1024;

    final long versionCode;
    final String versionName;
    final String sha256;
    final long size;
    final String apkUrl;

    Update(long versionCode, String versionName, String sha256, long size, String apkUrl) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.sha256 = sha256;
        this.size = size;
        this.apkUrl = apkUrl;
    }

    boolean isNewerThan(long installedVersionCode) {
        return versionCode > installedVersionCode;
    }

    /** {manifestUrl, apkUrl} from a GitHub release JSON, or null when either asset is missing. */
    static String[] assetUrls(String releaseJson) throws JSONException {
        JSONArray assets = new JSONObject(releaseJson).getJSONArray("assets");
        String manifest = null, apk = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.getString("name");
            if (name.equals(MANIFEST_ASSET)) manifest = asset.getString("browser_download_url");
            else if (name.equals(APK_ASSET)) apk = asset.getString("browser_download_url");
        }
        return manifest == null || apk == null ? null : new String[] {manifest, apk};
    }

    static Update parse(String manifestJson, String apkUrl) throws JSONException {
        JSONObject value = new JSONObject(manifestJson);
        long code = value.getLong("versionCode");
        String name = value.getString("versionName");
        String sha = value.getString("sha256").toLowerCase(Locale.ROOT);
        long size = value.getLong("size");
        if (code <= 0 || name.isEmpty() || name.length() > 64) throw new JSONException("Bad version");
        if (!sha.matches("[0-9a-f]{64}")) throw new JSONException("Bad checksum");
        if (size <= 0 || size > MAX_APK_BYTES) throw new JSONException("Bad size");
        if (!apkUrl.startsWith("https://")) throw new JSONException("Bad download URL");
        return new Update(code, name, sha, size, apkUrl);
    }
}
