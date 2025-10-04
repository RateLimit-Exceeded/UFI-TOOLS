# Project Overview

- **Purpose**: Android companion for ZTE portable WiFi (e.g., MU300/F50) that exposes remote management, SMS, OTA, and diagnostic tooling via an embedded web UI and background services.
- **Architecture**: Single Gradle `app` module built with Kotlin + Jetpack Compose. An embedded Ktor server (`KanoWebServer`) serves static assets and APIs to the front-end, which also runs inside the APK assets directory. Services handle ADB, boot receivers, and background tasks.
- **Front-End**: `app/frontEnd` contains the PWA-style HTML/JS assets and Node tooling. Build script copies (and optionally obfuscates) `public/` scripts into `app/src/main/assets`.
- **Key Directories**:
  - `app/src/main/java/com/minikano/f50_sms`: Kotlin sources grouped into `configs`, `modules`, `ui`, `utils`.
  - `app/src/main/assets`: Packaged web assets served by the internal server.
  - `app/frontEnd/public`: Human-readable JS/CSS/HTML sources for the web client.
  - `.github/workflows`: CI definitions for Android build/release pipelines.
- **Notable Docs**: `README.md` (English) and `README_zh_CN.md` describe feature set and usage. `API_Doc.md` details device API endpoints.