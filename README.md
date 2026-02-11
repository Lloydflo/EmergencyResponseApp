# EmergencyResponseApp

This repository contains an Android app for emergency response workflows.

## Historical Route Analytics

The app includes a minimal "Historical Route Analytics" screen that summarizes recent route sessions.

### How it works
- A route session starts when a responder taps Navigate on an assigned incident.
- A route session completes when the responder taps On Scene.
- The app stores basic stats locally (start time, end time, straight-line distance, and duration).

### Try it
```
./gradlew :app:compileDebugKotlin --console=plain
```

Notes:
- Route stats are stored locally in SharedPreferences.
- No continuous background tracking is performed unless navigation is started.
