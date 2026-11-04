# fun activity

An Android app that uploads new call recordings from a folder you choose to a **Call Recordings**
folder in your own Google Drive. It does not record calls. It has no server of its own and no
analytics, and it needs no root.

| | |
|---|---|
| Package | `com.funactivity.ppmr` |
| Version | 0.1.0 |
| Min / target SDK | 26 / 37 |
| Stack | Kotlin, Compose + Material 3, Hilt, Room, DataStore, WorkManager, OkHttp |

## Setup

You need Android Studio (or JDK 17+ and the Android SDK) and a Google account. Allow about 10
minutes for the Google Cloud steps.

### 1. Create a Google Cloud project

1. Open <https://console.cloud.google.com/projectcreate>, name the project, and create it.
2. Make sure the new project is selected in the top bar for the remaining steps.

### 2. Enable the Google Drive API

Open [the Drive API page](https://console.cloud.google.com/apis/library/drive.googleapis.com) and
click **Enable**.

### 3. Configure the OAuth consent screen

1. Open **Google Auth Platform** (<https://console.cloud.google.com/auth/overview>) and click
   **Get started**.
2. Enter an app name and your email, choose **External** as the audience, and finish the wizard.
3. Under **Data Access**, click **Add or remove scopes** and add
   `https://www.googleapis.com/auth/drive.file`. This is the only Drive scope the app uses. It gives
   access only to files and folders the app creates.
4. Under **Audience**, leave the publishing status as **Testing** and add your Google account under
   **Test users**. Personal use can stay in this mode.

### 4. Create two OAuth clients

Create both at <https://console.cloud.google.com/auth/clients>. Current Google sign-in needs the
pair: the Android client proves the app's identity, and the Web client ID is what the app sends when
it asks for sign-in.

**Android client**

1. Click **Create client** and choose **Android**.
2. Package name: `com.funactivity.ppmr`
3. SHA-1 certificate fingerprint: get your debug key's value with the command below.

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

Nothing from this client goes in the project. Google matches it by package name and SHA-1. A release
build, or a debug build from another computer, has a different SHA-1 and needs its own Android client.

**Web client**

1. Click **Create client** and choose **Web application**.
2. No URLs are needed. Create it and copy the client ID.

### 5. Add the client ID to the project

Add the **Web** client ID to `local.properties` in the project root. That file is not committed to
git.

```properties
GOOGLE_WEB_CLIENT_ID=1234567890-abcdefg.apps.googleusercontent.com
```

This is the only credential the project needs. Without it the app still builds and runs, but sign-in
shows a message pointing here.

### 6. Build and install a debug APK

Connect a phone with USB debugging on, then run:

```bash
./gradlew :app:installDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk` if you would rather copy it over yourself.
You can also open the project in Android Studio and press Run. The phone needs Google Play services
and a Google account.

### 7. First run

The app walks you through three steps: sign in with the test user from step 3, pick your recordings
folder, and finish. While the consent screen is in Testing mode, Google shows an "unverified app"
warning; choose **Continue**.

## Testing with sample audio

Make a test folder and add a sample file. The app does not play or inspect the audio, so random bytes
with an audio extension are enough for a test.

```bash
adb shell mkdir -p /sdcard/Music/CallTest
```

```bash
head -c 200000 /dev/urandom > sample1.m4a
```

```bash
adb push sample1.m4a /sdcard/Music/CallTest/
```

Pick `Music/CallTest` as the folder in the app, wait for the minimum file age (30 seconds by
default), and tap **Sync now**. The file should appear in Drive under **Call Recordings**.

`adb push` does not tell Android's media index about the file, so automatic detection only sees it at
the next periodic scan. To trigger detection the way a recorder app would, tell the media scanner:

```bash
adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary
```

### Checks against the acceptance criteria

| Check | How |
|---|---|
| No duplicates | Tap **Sync now** again. The file count in Drive does not change. |
| Renamed copy skipped | Copy a synced file under a new name in the folder and sync. It shows "Skipped: the same recording is already in Drive". |
| Offline recovery | Turn on airplane mode, add a file, and tap **Sync now**. The home screen says there is no connection. Turn airplane mode off and the upload finishes by itself. |
| Wi-Fi only | With Wi-Fi off and mobile data on, the home screen says it is waiting for Wi-Fi until you turn the setting off. |
| Reboot | Restart the phone without opening the app and add a file. It uploads within about 15 minutes. |

Run the unit tests with:

```bash
./gradlew :app:testDebugUnitTest
```

## How it works

```
MediaStore audio change ─┐
15-minute periodic scan ─┤
App start / Sync now ────┼─► ScanWorker ─► Room queue ─► UploadWorker ─► Drive
Retry / too-new timer ───┘                    ▲              │
                                              └── retry ◄────┘
```

- **Detection.** A WorkManager content-URI trigger watches MediaStore audio even when the app is not
  running. The periodic scan covers folders MediaStore does not index. Android may delay the periodic
  scan when the phone is idle (Doze).
- **Identity.** A file is the same file if its name, size and modified time match. Files up to 64 MB
  also get a SHA-256 content hash, so a renamed copy is not uploaded again. Larger files are matched
  by identity only.
- **Lost responses and reinstalls.** The identity is stored on the Drive file as an app property and
  checked before each upload. If an upload finished but its response was lost, or the app was
  reinstalled, the file is not uploaded twice.
- **Uploads.** Drive resumable uploads in 2 MiB chunks. Brief errors resume within a chunk from the
  offset Drive confirms. If a run is interrupted altogether, that file restarts from the beginning on
  the next run; Drive discards incomplete uploads, so no partial file appears.
- **Retries.** Each file gets up to 5 attempts, with waits of 30 seconds, then 1, 2 and 4 minutes.
  Losing the network uses up no attempt. A full Drive fails right away. **Sync now** or tapping the
  failed row tries again.
- **Sign-in.** Credential Manager identifies the account, then Google Identity's `AuthorizationClient`
  grants Drive access. Google Play services holds and refreshes tokens; the app stores only your
  email address.
- **Background limits.** Uploads try to run as a foreground service, so the notification shows only
  while uploading. Android can refuse this from the background. The upload then runs as normal
  background work, and WorkManager resumes the queue if it is stopped.

### Project layout

```
app/src/main/java/com/funactivity/ppmr/
  ui/       Compose screens and view models (onboarding, home, settings, auth)
  domain/   Models, repository interfaces, use cases, pure sync rules
  data/     Room and DataStore (local), Drive REST (remote), SAF (files), Google auth
  sync/     Workers, scheduler, notifications, connectivity, progress
  di/       Hilt modules
```

### Library choices

- **Drive REST over OkHttp** rather than the `google-api-services-drive` client. The Java client adds
  a lot of dependency weight, and its Android credential helper depends on the deprecated
  `GoogleSignIn` API. This choice is about five API calls of code.
- **Credential Manager + `AuthorizationClient`** rather than `GoogleSignInClient`, which Google has
  deprecated.

## Known limitations

- **"Wi-Fi only" really means "unmetered".** A Wi-Fi hotspot marked as metered counts as mobile data
  and waits, and unmetered Ethernet is allowed. This is the check WorkManager offers.
- **Android folder restrictions.** Android 11 and later block picking the top level of storage, the
  `Download` root and `Android/data`. A recorder that saves into a private folder cannot be reached by
  design, so point it at a shared folder if it allows that.
- **`.3gp` detection.** Android may index `.3gp` as video, which the audio trigger does not watch. These
  files are still found by the periodic scan and **Sync now**.
- **Files deleted from Drive stay deleted.** The app remembers what it uploaded, so it does not put
  back a file you removed from Drive.
- **Switching Google accounts** clears the upload records, so recordings still in the folder upload
  to the new account's Drive. Signing back in to the same account does not.

## Publishing and legal notes

- The `drive.file` scope keeps verification light, but distributing beyond your test users means
  publishing the consent screen, and a release signing key needs its own Android client.
- Laws on recording calls and storing those recordings vary by place, and many require the other
  person's consent. Google Play also restricts apps that deal with call data. The app only handles
  files your phone has already saved, but you are responsible for how they were made.
