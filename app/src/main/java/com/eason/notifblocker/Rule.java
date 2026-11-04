package com.eason.notifblocker;

import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** One block rule: an optional app package and an optional title keyword. Empty fields match anything. */
final class Rule {
    final String pkg;
    final String title;
    final boolean enabled;

    Rule(String pkg, String title, boolean enabled) {
        this.pkg = pkg == null ? "" : pkg.trim();
        this.title = title == null ? "" : title.trim();
        this.enabled = enabled;
    }

    /** A rule with neither field would block every notification, so it never matches and is refused on add. */
    boolean isEmpty() {
        return pkg.isEmpty() && title.isEmpty();
    }

    Rule withEnabled(boolean value) {
        return new Rule(pkg, title, value);
    }

    boolean sameTarget(Rule other) {
        return pkg.equals(other.pkg) && title.equalsIgnoreCase(other.title);
    }

    boolean matches(String notificationPkg, String notificationTitle) {
        if (!enabled || isEmpty()) return false;
        if (!pkg.isEmpty() && !pkg.equals(notificationPkg)) return false;
        if (!title.isEmpty()) {
            if (notificationTitle == null) return false;
            if (!notificationTitle.toLowerCase(Locale.ROOT).contains(title.toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    String describe() {
        String what = title.isEmpty() ? "Any notification" : "Title contains \u201c" + title + "\u201d";
        return what + " \u00b7 " + (pkg.isEmpty() ? "any app" : pkg);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("pkg", pkg).put("title", title).put("enabled", enabled);
    }

    static Rule fromJson(JSONObject json) {
        return new Rule(json.optString("pkg", ""), json.optString("title", ""), json.optBoolean("enabled", true));
    }
}
