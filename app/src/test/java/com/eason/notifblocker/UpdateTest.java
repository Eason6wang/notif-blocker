package com.eason.notifblocker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

public class UpdateTest {
    private static final String SHA = "a".repeat(64);

    @Test
    public void findsBothAssets() throws Exception {
        String json = "{\"assets\":[{\"name\":\"update.json\",\"browser_download_url\":\"https://x/u\"},"
            + "{\"name\":\"notif-blocker.apk\",\"browser_download_url\":\"https://x/a\"}]}";
        assertArrayEquals(new String[] {"https://x/u", "https://x/a"}, Update.assetUrls(json));
    }

    @Test
    public void missingAssetGivesNull() throws Exception {
        assertNull(Update.assetUrls("{\"assets\":[{\"name\":\"update.json\",\"browser_download_url\":\"https://x/u\"}]}"));
    }

    @Test
    public void parsesManifestAndComparesVersions() throws Exception {
        Update update = Update.parse("{\"versionCode\":5,\"versionName\":\"1.0.5\",\"sha256\":\"" + SHA.toUpperCase()
            + "\",\"size\":1000}", "https://x/a");
        assertEquals(SHA, update.sha256);
        assertTrue(update.isNewerThan(4));
        assertFalse(update.isNewerThan(5));
    }

    @Test(expected = JSONException.class)
    public void rejectsBadChecksum() throws Exception {
        Update.parse("{\"versionCode\":5,\"versionName\":\"1\",\"sha256\":\"zz\",\"size\":1000}", "https://x/a");
    }

    @Test(expected = JSONException.class)
    public void rejectsPlainHttpDownload() throws Exception {
        Update.parse("{\"versionCode\":5,\"versionName\":\"1\",\"sha256\":\"" + SHA + "\",\"size\":1000}", "http://x/a");
    }
}
