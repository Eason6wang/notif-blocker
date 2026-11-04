package com.eason.notifblocker;

import android.app.Notification;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/** Dismisses every posted notification that matches a rule, and records what it saw. */
public class BlockerService extends NotificationListenerService {
    private static final String TAG = "NotifBlocker";
    private static final long SNOOZE_MS = 60L * 60 * 1000;
    private static final long VERIFY_DELAY_MS = 1500;

    static volatile boolean connected;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onListenerConnected() {
        connected = true;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification sbn : active) handle(sbn);
        } catch (Exception error) {
            Log.w(TAG, "initial sweep failed", error);
        }
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        // The system unbinds listeners now and then; ask to be bound again.
        try {
            requestRebind(new ComponentName(this, BlockerService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn);
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        try {
            Notification notification = sbn.getNotification();
            String pkg = sbn.getPackageName();
            String title = str(notification.extras.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(notification.extras.getCharSequence(Notification.EXTRA_TEXT));
            Rule hit = Store.firstMatch(this, pkg, title);
            Store.recordSeen(this, new Seen(System.currentTimeMillis(), pkg, label(pkg), title, text,
                str(notification.getChannelId()), sbn.isOngoing(), sbn.isClearable(), hit == null ? "" : "pending"));
            if (hit == null) return;
            final String key = sbn.getKey();
            Store.countBlocked(this);
            cancelNotification(key);
            handler.postDelayed(() -> verify(key, pkg, title), VERIFY_DELAY_MS);
        } catch (Exception error) {
            Log.w(TAG, "handle failed", error);
        }
    }

    /** cancelNotification is a request; check it worked and fall back to snoozing, then record the outcome. */
    private void verify(String key, String pkg, String title) {
        try {
            if (!stillShowing(key)) {
                Store.updateResult(this, pkg, title, "dismissed");
                return;
            }
            snoozeNotification(key, SNOOZE_MS);
            handler.postDelayed(() -> {
                try {
                    Store.updateResult(this, pkg, title, stillShowing(key) ? "still showing" : "snoozed");
                } catch (Exception ignored) {
                }
            }, VERIFY_DELAY_MS);
        } catch (Exception error) {
            Store.updateResult(this, pkg, title, "still showing");
        }
    }

    private boolean stillShowing(String key) {
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return false;
        for (StatusBarNotification sbn : active) if (key.equals(sbn.getKey())) return true;
        return false;
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception error) {
            return pkg;
        }
    }

    private static String str(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
