# NEXORA WORLD

A mobile-first 2.5D social virtual world project.

## Current milestone

This repository contains the Android client foundation, a lightweight realtime backend starter, and a GitHub Actions workflow for producing a debug APK.

### Android build

- Android Studio: open this repository as a project.
- JDK: 17
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9

The GitHub Actions workflow builds `app/build/outputs/apk/debug/app-debug.apk` on pushes and pull requests.

## Scope and status

The first milestone focuses on a playable local city scene and the project/build pipeline. It is not yet a production multiplayer game. Persistent accounts, authoritative world state, moderation, content delivery, payments, and production operations require further implementation and testing.

## Backend

See `backend/README.md`. The backend is a development starter and must be deployed behind HTTPS with persistent storage and production security configuration before public use.
