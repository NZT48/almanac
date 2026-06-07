# Almanac

A small, sideloaded Android app for your own phone. Almanac reads health data
that Samsung Health (or any Health-Connect-compatible app) writes into
**Health Connect** and turns it into a plain-text weekly summary you can read,
keep, or share.

It is not a Play Store product — it's a personal tool meant to be installed
on a single device and used by a single person.

## What it does

- Reads **steps**, **weight**, and **sleep** from Health Connect for a date
  range you pick from the UI (defaults to the current Monday–Sunday week).
- Writes a human-readable `.txt` summary into the phone's **Downloads**
  folder, including totals, averages, and how many days you hit your step
  target.
- Lets you set a **daily step target** in Settings — it's used to compute the
  "days target hit" line in every export.
- Connects to **Notion** (optional). Paste an internal-integration token and
  the share URL of your Notion database in Settings, and Almanac
  can read rows from that database alongside Health Connect.
- Works fully offline for the Health Connect side. The Notion side is the
  only feature that needs internet access.
- Stores nothing in the cloud. Your API key is kept in encrypted on-device
  storage; the rest lives in normal app preferences.

## Setting up Notion (optional)

1. Create an internal integration at <https://www.notion.so/my-integrations>
   and copy the token (starts with `secret_` or `ntn_`).
2. In Notion, open your database, click `•••` → **Connections**,
   and add the integration from step 1. Without this step the API can't see
   the database even with a valid token.
3. In Notion, click **Share → Copy link** on the database.
4. In Almanac, open **Settings**, paste the token and the URL, save, and tap
   **Test connection**.

## Build and install

The app is built with Gradle. Run all commands from the project root.

### Debug build (sideload onto your own phone)

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

With the phone plugged in over USB (Developer Options → USB debugging on):

```bash
./gradlew installDebug
```

Or transfer the APK to the phone manually and open it from a file manager —
Android will prompt to allow installation from that source.

### Release build (signed APK)

After setting up a signing key once and adding the credentials to
`~/.gradle/gradle.properties` (see the wiki / setup notes):

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Install the same way as the debug APK:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Note: a debug and a release build can't coexist on the same device — they're
signed with different keys. Uninstall one before installing the other:

```bash
adb uninstall com.example.almanac
```

### Run the unit tests

```bash
./gradlew testDebugUnitTest
```
