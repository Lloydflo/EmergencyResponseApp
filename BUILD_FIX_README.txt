BUILD FIX

1. Delete this obsolete duplicate file from an older project copy, if present:
   app/src/main/java/com/ers/emergencyresponseapp/Backuprequestsheet.kt

2. Keep only this canonical file:
   app/src/main/java/com/ers/emergencyresponseapp/home/composables/BackupRequestSheet.kt

Both files declare the same package and top-level symbols, which causes the
BACKUP_RESOURCES, DepartmentSelectionDialog, BackupResourceSheet, and
ResourceChip conflicts.

3. AppPullToRefresh.kt was corrected. The previous version supplied the Box
content both as a named argument and as a trailing lambda.

4. HomeScreen.kt now gives explicit lambda parameter types for the backup dialog.

The compileSdk 36 / Android Gradle Plugin message is a warning, not the cause of
this compilation failure.
