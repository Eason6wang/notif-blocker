package com.eason.notifblocker;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Rules, recently seen notifications and the blocked counter, all in one SharedPreferences file. */
final class Store {
    static final String FILE = "blocker";
    private static final String RULES = "rules";
    private static final String SEEN = "seen";
    private static final String BLOCKED = "blocked";
    private static final int MAX_SEEN = 60;

    private Store() {}

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static synchronized List<Rule> rules(Context context) {
        SharedPreferences prefs = prefs(context);
        List<Rule> out = new ArrayList<>();
        if (!prefs.contains(RULES)) {
            out.add(new Rule("", "voicemail", true));
            out.add(new Rule("", "charging", true));
            saveRules(context, out);
            return out;
        }
        try {
            JSONArray array = new JSONArray(prefs.getString(RULES, "[]"));
            for (int i = 0; i < array.length(); i++) out.add(Rule.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    static synchronized void saveRules(Context context, List<Rule> rules) {
        JSONArray array = new JSONArray();
        try {
            for (Rule rule : rules) array.put(rule.toJson());
        } catch (Exception ignored) {
        }
        prefs(context).edit().putString(RULES, array.toString()).apply();
    }

    /** Returns false when the rule is empty or already present. */
    static synchronized boolean addRule(Context context, Rule rule) {
        if (rule.isEmpty()) return false;
        List<Rule> rules = rules(context);
        for (Rule existing : rules) if (existing.sameTarget(rule)) return false;
        rules.add(rule);
        saveRules(context, rules);
        return true;
    }

    static synchronized void removeRule(Context context, int index) {
        List<Rule> rules = rules(context);
        if (index < 0 || index >= rules.size()) return;
        rules.remove(index);
        saveRules(context, rules);
    }

    static synchronized void setEnabled(Context context, int index, boolean enabled) {
        List<Rule> rules = rules(context);
        if (index < 0 || index >= rules.size()) return;
        rules.set(index, rules.get(index).withEnabled(enabled));
        saveRules(context, rules);
    }

    static synchronized Rule firstMatch(Context context, String pkg, String title) {
        for (Rule rule : rules(context)) if (rule.matches(pkg, title)) return rule;
        return null;
    }

    static synchronized List<Seen> seen(Context context) {
        List<Seen> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(SEEN, "[]"));
            for (int i = 0; i < array.length(); i++) out.add(Seen.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Newest first; an older entry with the same app and title is replaced. */
    static synchronized void recordSeen(Context context, Seen entry) {
        List<Seen> list = seen(context);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).sameAs(entry.pkg, entry.title)) list.remove(i);
        list.add(0, entry);
        while (list.size() > MAX_SEEN) list.remove(list.size() - 1);
        saveSeen(context, list);
    }

    static synchronized void updateResult(Context context, String pkg, String title, String result) {
        List<Seen> list = seen(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).sameAs(pkg, title)) {
                list.set(i, list.get(i).withResult(result));
                break;
            }
        }
        saveSeen(context, list);
    }

    static synchronized void clearSeen(Context context) {
        saveSeen(context, new ArrayList<Seen>());
    }

    private static void saveSeen(Context context, List<Seen> list) {
        JSONArray array = new JSONArray();
        try {
            for (Seen entry : list) array.put(entry.toJson());
        } catch (Exception ignored) {
        }
        prefs(context).edit().putString(SEEN, array.toString()).apply();
    }

    static synchronized int blockedCount(Context context) {
        return prefs(context).getInt(BLOCKED, 0);
    }

    static synchronized void countBlocked(Context context) {
        SharedPreferences prefs = prefs(context);
        prefs.edit().putInt(BLOCKED, prefs.getInt(BLOCKED, 0) + 1).apply();
    }
}
