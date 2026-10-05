# Health Raw

Native Kotlin / Jetpack Compose Android app to inspect raw Health Connect health and fitness records. Android 9+; Android 14+ provides Health Connect in the system, while Android 9–13 requires the Health Connect provider app.

## Use

1. Install the APK and open Health Raw.
2. Choose a record type and grant its read permission.
3. Set inclusive local dates in YYYY-MM-DD format, then tap Read records.
4. Inspect any record's JSON. Filter loaded entries by source package.
5. Load additional pages (100 records each). Export saves **all loaded records** from the selected query, regardless of the source filter. The export explicitly marks whether more pages remain.

38 supported types cover activity, sleep, heart rate and its samples, body measurements, nutrition, hydration, vital signs and reproductive health. Read-only: no writes, deletes, background reads, networking, telemetry, database, or automatic exports. Data remains in memory until the process ends or the query changes; permission revocation clears the current query on resume. Explorer screenshots and Android backups are disabled.

Older data requires optional history permission where the feature is available; otherwise Health Connect normally allows reads from 30 days before the first permission grant onward. Missing records may mean the originating app has not written them, permission is absent, or the date range is restricted.

Raw JSON is a representation of public Jetpack SDK record properties, including IDs, source package, timestamps, offsets, device metadata, units and sample arrays. It is not a database or protobuf dump. Numeric enum values retain their SDK meanings. Records from different source apps may overlap; do not sum them as deduplicated totals. This version does not request exercise route access or include experimental medical/FHIR, mindfulness, skin temperature or activity intensity APIs.

Exports use Android's document picker; the destination can be local storage or a cloud provider chosen by the user. Exported JSON contains sensitive information and is managed by the user. No data is logged. Privacy rationale is accessible from both Health Connect permission surfaces and the app.

## Build

JDK 17 and Android SDK 36:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon --max-workers=2
```

Set your SDK path in local.properties or ANDROID_HOME. Debug APK: app/build/outputs/apk/debug/app-debug.apk. The delivered APK is debug-signed for personal installation; there is no production signing configuration or Play Store publication. Play distribution requires the relevant Health Connect declarations and policy review.

## Device verification

Verify granted/denied/partial permission access, revocation while away, Android 13 provider installation and Android 14+ built-in provider, empty queries, sample-rich heart-rate/sleep entries, multi-page export, history unavailable/granted, document-picker cancellation and export failure. Unit tests validate original values, offsets, metadata and individual sample preservation. Build/lint cannot verify provider behavior without a real device.

API references: https://developer.android.com/health-and-fitness/health-connect/get-started and https://developer.android.com/health-and-fitness/health-connect/read-data
