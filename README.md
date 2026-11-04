# Notification Blocker

Small Android app that dismisses notifications matching rules (for example Samsung's "Voicemail" and "Charging"
notices that have no off switch). It uses Notification Access, so a notification may flash briefly before it is removed.
System notifications that are marked non-swipeable may resist; the app shows the outcome ("dismissed", "snoozed" or
"still showing") for each blocked notification in the Recent list.

## Install and set up
1. Download `notif-blocker.apk` from the latest release and install it.
2. Open the app, tap **Turn on notification access**. If the switch is greyed out: App info > ⋮ > **Allow restricted settings**.
3. Tap **Allow unrestricted battery use** so Samsung does not stop it.
4. Default rules: title contains `voicemail` or `charging`. Tap any item under **Recent notifications** to block it.

## Updates
**Check for updates** reads the latest GitHub release (`update.json` + `notif-blocker.apk`), verifies the SHA-256 and
opens the system installer. First time, allow "install unknown apps" for this app.

## Build and release
- Unit tests and APK: `./gradlew testDebugUnitTest assembleRelease` (JDK 21, `ANDROID_HOME` set).
- Release: `scripts/release.sh` (signed with `~/.config/notif-blocker/keystore.properties`; keep a backup of that key,
  a different key cannot update an installed app).
