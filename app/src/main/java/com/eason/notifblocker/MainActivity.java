package com.eason.notifblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

/** Three tabs: recent notifications, block rules, and settings (setup status and the updater). */
public class MainActivity extends Activity {
    private enum Tab {
        RECENT("Recent"), RULES("Rules"), SETTINGS("Settings");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private LinearLayout root;
    private LinearLayout tabBar;
    private Tab tab = Tab.RECENT;
    private Update available;
    private File downloaded;
    private String updateStatus = "";
    private boolean busy;
    private Seen.Filter filter = Seen.Filter.ALL;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> runOnUiThread(this::render);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            int saved = state.getInt("tab", 0);
            if (saved >= 0 && saved < Tab.values().length) tab = Tab.values()[saved];
        }
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(16));
        scroll.addView(root);
        frame.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        frame.addView(tabBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(frame);
        checkForUpdate(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("tab", tab.ordinal());
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
        boolean enabled = listenerEnabled();
        boolean batteryOk = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        renderTabBar(!enabled || !batteryOk || available != null);
        switch (tab) {
            case RULES: renderRules(); break;
            case SETTINGS: renderSettings(enabled, batteryOk); break;
            default: renderRecent(enabled);
        }
    }

    private void renderTabBar(boolean attention) {
        tabBar.removeAllViews();
        tabBar.setBackground(rounded(0x14888888, 0, 0));
        for (Tab option : Tab.values()) {
            boolean selected = option == tab;
            TextView item = new TextView(this);
            item.setText(option.label + (option == Tab.SETTINGS && attention ? " \u25cf" : ""));
            item.setGravity(Gravity.CENTER);
            item.setTextSize(15);
            item.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setPadding(0, dp(16), 0, dp(16));
            item.setTextColor(selected ? 0xFF1A73E8 : chipTextColor());
            item.setAlpha(selected ? 1f : 0.75f);
            item.setOnClickListener(v -> {
                tab = option;
                render();
            });
            tabBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private void renderRecent(boolean enabled) {
        title("Recent");
        if (!enabled) {
            LinearLayout banner = new LinearLayout(this);
            banner.setOrientation(LinearLayout.VERTICAL);
            banner.setPadding(dp(12), dp(10), dp(12), dp(10));
            banner.setBackground(rounded(0x26C26401, dp(12), 0));
            TextView text = new TextView(this);
            text.setText("\u274c Notification access is off, so nothing is blocked. Tap to fix it in Settings.");
            banner.addView(text);
            banner.setOnClickListener(v -> {
                tab = Tab.SETTINGS;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            root.addView(banner, params);
        }
        body("Tap a notification to block it. Not swipeable ones may resist; the result shows after a block.");
        List<Seen> seen = Store.seen(this);
        if (seen.isEmpty()) {
            body(enabled ? "Nothing seen yet." : "Nothing to show until notification access is on.");
            return;
        }
        root.addView(filterBar());
        int shown = 0;
        for (Seen entry : seen) {
            if (!filter.accepts(entry)) continue;
            shown++;
            root.addView(card(entry));
        }
        if (shown == 0) body("No " + filter.label.toLowerCase(java.util.Locale.ROOT) + " notifications.");
        button("Clear list", () -> Store.clearSeen(this));
    }

    private void renderRules() {
        title("Rules");
        body("Notifications that match an enabled rule are dismissed as soon as they appear.");
        body("Blocked so far: " + Store.blockedCount(this));
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
        body("Tip: add a rule faster by tapping a notification on the Recent tab.");
    }

    private void renderSettings(boolean enabled, boolean batteryOk) {
        title("Settings");
        section("Setup");
        body(enabled ? "\u2705 Notification access is on" + (BlockerService.connected ? "" : " (starting\u2026)")
            : "\u274c Notification access is off. Turn it on for this app.");
        button(enabled ? "Notification access settings" : "Turn on notification access",
            () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        if (!enabled) {
            body("If the switch is greyed out (sideloaded app), open App info, tap \u22ee and choose \u201cAllow restricted settings\u201d, then try again.");
            button("Open App info", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        }
        if (!batteryOk) {
            body("\u26a0\ufe0f Battery optimization may stop the blocker in the background.");
            button("Allow unrestricted battery use", () -> startActivity(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
        } else {
            body("\u2705 Battery use is unrestricted");
        }

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

    private LinearLayout filterBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(4), 0, dp(8));
        for (Seen.Filter option : Seen.Filter.values()) {
            boolean selected = option == filter;
            TextView chip = new TextView(this);
            chip.setText(option.label);
            chip.setTextSize(14);
            chip.setPadding(dp(14), dp(6), dp(14), dp(6));
            chip.setTextColor(selected ? Color.WHITE : chipTextColor());
            chip.setBackground(rounded(selected ? 0xFF1A73E8 : Color.TRANSPARENT, dp(16), selected ? 0 : 0x66888888));
            chip.setOnClickListener(v -> {
                filter = option;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(6);
            bar.addView(chip, params);
        }
        return bar;
    }

    /** One notification: app and time on top, then title, message text and status tags. */
    private LinearLayout card(Seen entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(Color.TRANSPARENT, dp(12), 0x55888888));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);

        LinearLayout top = new LinearLayout(this);
        TextView app = new TextView(this);
        app.setText(entry.label.toUpperCase(java.util.Locale.ROOT));
        app.setTextSize(12);
        app.setAlpha(0.65f);
        app.setSingleLine(true);
        app.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(app, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView time = new TextView(this);
        time.setText(DateUtils.getRelativeTimeSpanString(entry.time));
        time.setTextSize(12);
        time.setAlpha(0.65f);
        top.addView(time);
        card.addView(top);

        TextView name = new TextView(this);
        name.setText(entry.title.isEmpty() ? "(no title)" : entry.title);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(16);
        name.setPadding(0, dp(2), 0, 0);
        card.addView(name);

        if (!entry.text.isEmpty()) {
            TextView text = new TextView(this);
            text.setText(entry.text);
            text.setTextSize(14);
            text.setAlpha(0.8f);
            text.setMaxLines(2);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(text);
        }

        LinearLayout tags = new LinearLayout(this);
        tags.setPadding(0, dp(6), 0, 0);
        if (entry.blocked()) tags.addView(tag("Blocked: " + entry.result, 0xFF188038));
        if (entry.ongoing) tags.addView(tag("Ongoing", 0xFF5F6368));
        if (!entry.clearable) tags.addView(tag("Not swipeable", 0xFFC26401));
        if (tags.getChildCount() > 0) card.addView(tags);

        card.setOnClickListener(v -> seenDialog(entry));
        return card;
    }

    private TextView tag(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(color);
        view.setPadding(dp(8), dp(2), dp(8), dp(2));
        view.setBackground(rounded((color & 0x00FFFFFF) | 0x26000000, dp(6), 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (stroke != 0) drawable.setStroke(Math.max(1, dp(1)), stroke);
        return drawable;
    }

    private int chipTextColor() {
        TextView probe = new TextView(this);
        return probe.getCurrentTextColor();
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
        view.setPadding(0, dp(16), 0, dp(4));
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
