# Install and Test — Version 17.5 (Code 28)

## Important

Do not install either APK bundled in an older archive. Those APKs were built
before the responder reliability and stable-route changes. Build this source
again from Android Studio and verify version **17.5 (28)** before testing.

## Clean build

1. Extract this project into a new folder such as `EmergencyResponseApp_v17_5`.
2. Open that exact folder in Android Studio.
3. Confirm `app/build.gradle.kts` shows version name `17.5` and code `28`.
4. Select **Build > Clean Project**, then **Build > Rebuild Project**.
5. Uninstall the older test build from the device, or confirm Android is
   installing code 28 over an older code.
6. Run the `app` configuration directly from Android Studio.

The source archive does not contain map-provider credentials. The app safely
falls back to OpenStreetMap raster tiles when `MAPTILER_API_KEY` is blank. If
your team uses MapTiler, put the restricted key in your user-level Gradle file
(`%USERPROFILE%\.gradle\gradle.properties` on Windows), not in the project that
you submit or share. Rotate the MapTiler and ORS keys that appeared in the
original project/history before using either service again.

Recommended Windows commands from the project root:

```text
gradlew.bat clean testDebugUnitTest
gradlew.bat :app:compileDebugKotlin
gradlew.bat lintDebug
gradlew.bat assembleDebug
```

## Visible verification

- Home > Account Settings shows **Emergency Response App 17.5 (28)**.
- An assigned incident shows only its valid action:
  **Start Response**, **Resume Navigation**, or **Complete Incident**.
- The map summary says **Original route locked** after its first route.
- Moving more than 25 metres does not replace the route.
- While a traffic alternative is pending, the current route remains visible.
- A valid route returned by the alternative-route server replaces the current
  route and shows **Approved alternative route active • route locked**.
- **Restore Original Route** restores the first route without another OSRM call.
- Back > **Exit Map — Keep Responding** leaves tracking active. The incident card
  shows **Resume Navigation**, and the tracking notification opens the same map.
- Turn off mobile data while already responding. **Resume Navigation** must open
  the stored route without requesting or replacing its geometry.

## Reliability checks

- An incident with missing/invalid coordinates cannot start navigation and
  never opens coordinates from another incident.
- Completion is offered only while status is **On Scene**. Simulate a failed
  upload and verify the photo and notes remain in the dialog for **Retry**.
- Disaster and General incidents appear in Active Incidents and the type counts.
- With multiple assignments, Request Backup requires selecting the incident;
  the submitted request must contain that incident ID.
- Arrival confirmation requires a live GPS fix with accuracy of 50 metres or
  better and uses a 40–50 metre accuracy-aware radius.
- On Android 12 or newer, a first route start requests approximate and precise
  location together. Deny precise location and verify no status/route starts.
- Revoke location permission while Home is backgrounded, return to the app, and
  verify the route action asks for permission again before changing status.
- Return an alternative polyline whose first or last point is more than 60 m
  from the submitted start/destination. It must be rejected and the current
  route must remain selected.
- Keep a responder stationary for more than five seconds and verify Firebase
  `live_locations/.../updatedAt` continues to advance while SQL route history
  does not accumulate duplicate stationary points.
- With incident B actively navigating, complete incident A. B's foreground
  tracking notification and locked route must remain active.
- Start an After-Action Report, close it with **Keep Draft & Close**, then reopen
  it and verify the unfinished fields return. A server/load failure must show a
  retry message without replacing the last successful incidents, reports, or
  resource requests with a false empty list.

## Coordination regression checks

The 17.4 reply, copy, typing, private reactions, drafts, and delivered/read
features remain included. Department reactions intentionally remain disabled
until the authenticated PHP/MySQL endpoint is available.
