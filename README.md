# Dictate Keyboard

An Android keyboard with a big mic key. Tap the mic, speak, and your words are typed into
WhatsApp or any other app. Free: uses the phone's own speech recogniser, no account or API key.

## Get it onto your phone (one time)

1. On a PC, unzip this folder.
2. On github.com (signed in as yannollie): **New repository**, name it `dictate-keyboard`, create it.
3. On the new repo page choose **uploading an existing file**, drag in EVERYTHING inside the
   unzipped folder (including the hidden `.github` folder), then **Commit changes**.
4. Open the **Actions** tab. A run called "Build APK" starts by itself and takes about 4 minutes.
   A green tick means it worked.
5. On your phone open the repo > **Releases** > the newest "Dictate Keyboard build" > tap
   `app-debug.apk` > install (allow "install unknown apps" for your browser if asked).
6. Open the **Dictate Keyboard** app and do its three buttons: allow microphone, turn the
   keyboard on, choose it as current keyboard.

## Using it

In any text box, bring up the keyboard, tap **Speak**, talk, tap **Stop**. Text appears after each
pause. The globe key switches back to Gboard. Letter keys are there as a backup.

Say: "full stop", "comma", "question mark", "exclamation mark", "new line", "new paragraph".
"um", "uh", "erm" are removed. Add your own corrections in the app under **My words**.

## If the build goes red

Open the failed run, tap the red step, and send a screenshot of the error lines.

## Files

- `app/src/main/java/com/yann/dictate/DictateIME.kt` – the keyboard
- `app/src/main/java/com/yann/dictate/TextCleaner.kt` – the clean-up rules (25 automated checks pass)
- `app/src/main/java/com/yann/dictate/SetupActivity.kt` – the setup screen and "My words"
- `.github/workflows/build.yml` – builds the APK in the cloud
- `tools/CleanerCheck.kt` – the checks for the clean-up rules
