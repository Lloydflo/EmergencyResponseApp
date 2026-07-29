# Merge status

The connectivity/offline changes are already integrated into this project. Do **not** run `git apply EmergencyResponseApp_connectivity_offline.patch` again.

Included integration:

- validated internet-state observer;
- offline banner;
- truthful assigned-incident loading/offline/server-error/empty states;
- responder-scoped last-known-good cache;
- polling pause and automatic refresh after reconnection;
- offline protection for server-dependent Home actions;
- local logout and cache clearing even when remote cleanup fails;
- professional email-login UI, with the existing OTP verification block retained;
- looping entry video and the in-app usage guide.

Generated directories and machine-specific files were intentionally removed from this clean copy: `.git`, `.gradle`, `.idea`, `.kotlin`, `app/build`, `app/release`, and `local.properties`.

## Open and build on Windows

1. Extract the ZIP to a new folder, for example `C:\Users\IT_ll\StudioProjects\EmergencyResponseApp_Merged`.
2. Open that folder in Android Studio.
3. Allow Gradle Sync to finish. Android Studio will create or update `local.properties` for your Android SDK.
4. Use **Build > Clean Project**, then **Build > Rebuild Project**.
5. The debug APK is normally generated at `app\build\outputs\apk\debug\app-debug.apk`.

If a stale Kotlin/Gradle daemon error appears, close Android Studio, run `gradlew.bat --stop` in the project folder, delete the project-local `.gradle`, `.kotlin`, and `app\build` folders, then reopen Android Studio and rebuild.
