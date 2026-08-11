# Install and Test — Version 17.4 (Code 27)

> Historical Coordination-only guide. For the latest responder workflow and
> stable navigation build, use `INSTALL_AND_TEST_V17_5.md`.

## Important

Do not install the old APK that came from the previous archive. It was built
from version 17.0 and does not contain the coordination update. Build this
project again from Android Studio.

## Clean installation

1. Extract the archive into a new folder named `EmergencyResponseApp_v17_4`.
2. Open that exact folder in Android Studio.
3. Confirm that `app/build.gradle.kts` shows version name `17.4` and version
   code `27`.
4. Confirm that this file exists:
   `app/src/main/java/com/ers/emergencyresponseapp/coordination/model/ReplyMessageCodec.kt`.
5. Select **Build > Clean Project**, then **Build > Rebuild Project**.
6. Uninstall the older Emergency Response App from the test device/emulator.
7. Run the `app` configuration directly from Android Studio.

## Visible verification

Open Coordination and enter a responder or department chat. The channel strip
must show **Long-press for Reply/Copy**. If that hint is missing, a different
project or old APK is still running.

## Feature tests

- **Reply and Copy:** long-press any message. Private chat also shows reactions.
- **Quoted reply:** select Reply, type a message, and send. A quote card appears
  inside the outgoing bubble.
- **Draft autosave:** type without sending, return to the inbox, then reopen the
  same conversation.
- **Typing status:** use two responder accounts/devices in the same private chat.
- **Delivered/read status:** send a private message while the recipient device is
  online and has a valid FCM token.

Department reactions remain unavailable until the PHP/MySQL group API supports
authenticated reaction storage and returns reactions with group history.
