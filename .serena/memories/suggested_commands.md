# Suggested Commands

- `./gradlew assembleDebug` / `./gradlew assembleRelease`: Build the Android APKs (release depends on front-end build).
- `./gradlew test` and `./gradlew lint`: Run unit tests and Android lint checks when touching Kotlin code.
- `cd app/frontEnd && npm install`: Install Node dependencies for the web assets.
- `cd app/frontEnd && npm run dev`: Launch the Express dev server with proxying to a live device at `http://localhost:3000`.
- `cd app/frontEnd && npm run build`: Copy/obfuscate `public/` assets into `app/src/main/assets` (Gradle also triggers this via the `npmBuild` task).
- `./gradlew clean`: Reset Gradle build outputs if assets or dependencies fall out of sync.
- On Windows, you can substitute `./gradlew` with `gradlew.bat`.