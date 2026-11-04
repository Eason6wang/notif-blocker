package com.eason.notifblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.List;

/** One screen: setup status, block rules, recently seen notifications, and the updater. */
public class MainActivity extends Activity {
    private LinearLayout root;
    private Update available;
    private File downloaded;
    private String updateStatus = "";
    private boolean busy;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> runOnUiThread(this::render);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int pad = dp(16);
            view.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        checkForUpdate(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listenerEnabled() && !BlockerService.connected) {
            try {
                NotificationListenerService.requestRebind(new ComponentName(this, BlockerService.class));
            } catch (Exception ignored) {
            }
        }
        Store.prefs(this).registerOnSharedPreferenceChangeListener(listener);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(listener);
    }

    private void render() {
        root.removeAllViews();
        title("Notification Blocker");
        body("Dismisses notifications that match a rule below, as soon as they appear.");

        // Setup
        section("Setup");
        boolean enabled = listenerEnabled();
        body(enabled ? "\u2705 Notification access is on" + (BlockerService.connected ? "" : " (starting\u2026)")
            : "\u274c Notification access is off. Turn it on for this app.");
        button(enabled ? "Notification access settings" : "Turn on notification access",
            () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        if (!enabled) {
            body("If the switch is greyed out (sideloaded app), open App info, tap \u22ee and choose \u201cAllow restricted settings\u201d, then try again.");
            button("Open App info", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        }
        PowerManager power = getSystemService(PowerManager.class);
        if (!power.isIgnoringBatteryOptimizations(getPackageName())) {
            body("\u26a0\ufe0f Battery optimization may stop the blocker in the background.");
            button("Allow unrestricted battery use", () -> startActivity(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
        } else {
            body("\u2705 Battery use is unrestricted");
        }
        body("Blocked so far: " + Store.blockedCount(this));

        // Rules
        section("Rules");
        List<Rule> rules = Store.rules(this);
        if (rules.isEmpty()) body("No rules yet.");
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            Rule rule = rules.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Switch toggle = new Switch(this);
            toggle.setChecked(rule.enabled);
            toggle.setText(rule.describe());
            toggle.setOnCheckedChangeListener((button, checked) -> Store.setEnabled(this, index, checked));
            row.addView(toggle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button remove = new Button(this);
            remove.setText("Remove");
            remove.setAllCaps(false);
            remove.setOnClickListener(v -> Store.removeRule(this, index));
            row.addView(remove);
            root.addView(row);
        }
        button("Add rule", this::addRuleDialog);

        // Recently seen
        section("Recent notifications");
        body("Tap one to block it. \u201cnot swipeable\u201d ones may resist; the result is shown after a block.");
        List<Seen> seen = Store.seen(this);
        if (seen.isEmpty()) body(enabled ? "Nothing seen yet." : "Turn on notification access to see them.");
        for (Seen entry : seen) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(8), 0, dp(8));
            TextView name = new TextView(this);
            name.setText(entry.title.isEmpty() ? "(no title)" : entry.title);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextSize(16);
            item.addView(name);
            TextView meta = new TextView(this);
            String flags = entry.flags();
            meta.setText(entry.label + " \u00b7 " + DateUtils.getRelativeTimeSpanString(entry.time) + (flags.isEmpty() ? "" : "\n" + flags));
            meta.setAlpha(0.7f);
            item.addView(meta);
            item.setOnClickListener(v -> seenDialog(entry));
            root.addView(item);
        }
        if (!seen.isEmpty()) button("Clear list", () -> Store.clearSeen(this));

        // Updates
        section("Updates");
        body("Version " + versionName() + " (" + versionCode() + ")");
        if (!updateStatus.isEmpty()) body(updateStatus);
        if (available != null && downloaded == null) {
            button("Download " + available.versionName, this::downloadUpdate);
        } else if (downloaded != null) {
            button("Install " + available.versionName, this::installUpdate);
        }
        button("Check for updates", () -> checkForUpdate(true));
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    private void addRuleDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText titleField = new EditText(this);
        titleField.setHint("Title contains (e.g. Voicemail)");
        EditText pkgField = new EditText(this);
        pkgField.setHint("App package (optional, any app if empty)");
        form.addView(titleField);
        form.addView(pkgField);
        new AlertDialog.Builder(this).setTitle("Add rule").setView(form)
            .setPositiveButton("Add", (d, w) -> addRule(new Rule(pkgField.getText().toString(), titleField.getText().toString(), true)))
            .setNegativeButton("Cancel", null).show();
    }

    private void seenDialog(Seen entry) {
        final boolean hasTitle = !entry.title.isEmpty();
        String[] items = {
            hasTitle ? "Block this title in " + entry.label : "Block all from " + entry.label,
            "Block this title in any app",
            "Block everything from " + entry.label,
            "Open " + entry.label + " notification settings"
        };
        new AlertDialog.Builder(this).setTitle(hasTitle ? entry.title : entry.label).setItems(items, (d, which) -> {
            if (which == 0) addRule(new Rule(entry.pkg, entry.title, true));
            else if (which == 1) addRule(new Rule("", entry.title, true));
            else if (which == 2) addRule(new Rule(entry.pkg, "", true));
            else openNotificationSettings(entry);
        }).setNegativeButton("Cancel", null).show();
    }

    private void addRule(Rule rule) {
        if (!Store.addRule(this, rule)) Toast.makeText(this, "Rule is empty or already exists", Toast.LENGTH_SHORT).show();
    }

    private void openNotificationSettings(Seen entry) {
        Intent intent = entry.channel.isEmpty()
            ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            : new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, entry.channel);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, entry.pkg);
        try {
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkForUpdate(boolean manual) {
        if (busy) return;
        busy = true;
        updateStatus = "Checking\u2026";
        if (manual) render();
        Updater.check(versionCode(), (update, error) -> {
            busy = false;
            available = update;
            downloaded = null;
            if (error != null) updateStatus = manual ? "Could not check: " + error : "";
            else if (update == null) updateStatus = manual ? "You have the latest version." : "";
            else updateStatus = "Update available: " + update.versionName;
            render();
        });
    }

    private void downloadUpdate() {
        if (busy || available == null) return;
        busy = true;
        updateStatus = "Downloading\u2026";
        render();
        Updater.download(this, available, (file, error) -> {
            busy = false;
            downloaded = file;
            updateStatus = error != null ? "Download failed: " + error : "Downloaded. Tap Install.";
            render();
        });
    }

    private void installUpdate() {
        String error = Updater.install(this, downloaded);
        if (error != null) {
            updateStatus = error;
            render();
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private boolean listenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName() + "/");
    }

    private long versionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.getLongVersionCode();
        } catch (Exception error) {
            return 0;
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception error) {
            return "?";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(26);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(view);
    }

    private void section(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(18);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(24), 0, dp(4));
        root.addView(view);
    }

    private void body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, dp(4), 0, dp(4));
        root.addView(view);
    }

    private void button(String text, Runnable action) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        root.addView(view);
    }
}
