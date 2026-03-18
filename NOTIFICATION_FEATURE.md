# New Incident Notification Feature

## Overview
I've implemented a pop-up notification system that appears at the top of the HomeScreen when a responder receives a new assigned incident.

## Implementation Details

### 1. **Notification State Management**
Added state variables to track notification visibility and content:
```kotlin
var showNewIncidentNotification by remember { mutableStateOf(false) }
var newIncidentMessage by remember { mutableStateOf("") }
```

### 2. **Automatic Detection**
A `LaunchedEffect` watches for changes to the currently assigned incident candidate (the first matching assignment):
```kotlin
LaunchedEffect(assignedCandidateForRole.firstOrNull()?.id) {
    val inc = assignedCandidateForRole.firstOrNull() ?: return@LaunchedEffect
    newIncidentMessage = "New ${inc.type} incident assigned: ${inc.location.ifBlank { "Unknown location" }}"
    showNewIncidentNotification = true
    // Auto-dismiss after 5 seconds
    delay(5000L)
    showNewIncidentNotification = false
}
```

### 3. **Visual Design**
The notification is a **Card** overlay with:
- **Green background** (`Color(0xFF1B5E20)`) to indicate importance
- **Emergency icon** (hospital icon)
- **Bold title**: "New Incident Assigned!"
- **Details**: Shows incident type and location
- **Dismiss button**: Manual dismiss option
- **Auto-dismiss**: Automatically hides after 5 seconds

### 4. **Animation**
Smooth entrance and exit animations:
- **Entry**: Slides down from top + fade in
- **Exit**: Slides up + fade out

### 5. **User Interaction**
- **Tap to dismiss**: Click anywhere on the notification
- **Long press to dismiss**: Alternative dismiss method
- **Button dismiss**: Explicit dismiss button with checkmark icon

## Visual Position
The notification appears:
- At the **top center** of the screen
- Above all other content (overlay)
- With padding from edges (95% width)
- 16dp from the top

## How It Works

1. When a new incident is assigned to `assignedIncident` (via the `acceptHandler`)
2. The `LaunchedEffect` triggers
3. Creates a message with incident details
4. Shows the notification card with animation
5. After 5 seconds, automatically dismisses with animation
6. User can manually dismiss at any time

## Testing

To test this feature:
1. Start the app and navigate to HomeScreen
2. Accept an incoming emergency request
3. The green notification should slide down from the top
4. It will show the incident type and location
5. After 5 seconds, it will slide back up
6. Or tap/click to dismiss immediately

## Code Location
- **File**: `app/src/main/java/com/ers/emergencyresponseapp/HomeScreen.kt`
- **Lines**: 
  - State variables: ~199-201
  - Detection logic: ~382-390
  - UI component: ~1012-1067

## Dependencies Used
- `AnimatedVisibility` - For smooth animations
- `slideInVertically`, `slideOutVertically` - Slide animations
- `fadeIn`, `fadeOut` - Opacity transitions
- Material 3 components (Card, Icon, etc.)

## Future Enhancements
Consider adding:
- Sound notification
- Vibration feedback
- Notification history/log
- Custom notification styles per incident type
- Priority-based notification colors
