package com.eason.notifblocker;

import org.json.JSONException;
import org.json.JSONObject;

/** A notification the listener has seen, kept so the user can tap it to create a rule. */
final class Seen {
    final long time;
    final String pkg;
    final String label;
    final String title;
    final String text;
    final String channel;
    final boolean ongoing;
    final boolean clearable;
    /** "" (not blocked), "dismissed", "snoozed" or "still showing". */
    final String result;

    Seen(long time, String pkg, String label, String title, String text, String channel, boolean ongoing, boolean clearable, String result) {
        this.time = time;
        this.pkg = pkg;
        this.label = label;
        this.title = title;
        this.text = text;
        this.channel = channel;
        this.ongoing = ongoing;
        this.clearable = clearable;
        this.result = result;
    }

    Seen withResult(String value) {
        return new Seen(time, pkg, label, title, text, channel, ongoing, clearable, value);
    }

    boolean sameAs(String otherPkg, String otherTitle) {
        return pkg.equals(otherPkg) && title.equals(otherTitle);
    }

    boolean blocked() {
        return result.length() > 0;
    }

    /** Filters shown above the Recent list. */
    enum Filter {
        ALL("All"), BLOCKED("Blocked"), NOT_SWIPEABLE("Not swipeable");

        final String label;

        Filter(String label) {
            this.label = label;
        }

        boolean accepts(Seen entry) {
            switch (this) {
                case BLOCKED: return entry.blocked();
                case NOT_SWIPEABLE: return !entry.clearable;
                default: return true;
            }
        }
    }

    String flags() {
        StringBuilder out = new StringBuilder();
        if (ongoing) out.append("ongoing");
        if (!clearable) out.append(out.length() > 0 ? ", " : "").append("not swipeable");
        if (result.length() > 0) out.append(out.length() > 0 ? " \u00b7 " : "").append("blocked: ").append(result);
        return out.toString();
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("time", time).put("pkg", pkg).put("label", label).put("title", title)
            .put("text", text).put("channel", channel).put("ongoing", ongoing).put("clearable", clearable)
            .put("result", result);
    }

    static Seen fromJson(JSONObject json) {
        return new Seen(json.optLong("time"), json.optString("pkg"), json.optString("label"), json.optString("title"),
            json.optString("text"), json.optString("channel"), json.optBoolean("ongoing"),
            json.optBoolean("clearable", true), json.optString("result"));
    }
}
