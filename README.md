# NEXORA WORLD

A mobile-first 2.5D social virtual world project.

## Current milestone: 0.2.0 — Playable City Plaza

The Android client now includes a playable local exploration loop:

- Touch joystick movement with animated avatar.
- Collectible glowing crystals that respawn.
- A five-crystal exploration quest with a coin and XP reward.
- XP and level progression.
- A roaming plaza visitor (Milo) with a one-time interaction reward.
- An interact action near Milo and the fountain.
- A temporary speed boost from the fountain.
- On-screen coin, quest, level, toast, and pause HUD.

This is still a single-device gameplay milestone. The collectibles, currency, quest progress, and NPC state are local to the app and reset when the app process is restarted. They are not yet synchronized to a server or other players.

## Android build

- Android Studio project.
- JDK 17
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Build variant: `debug`

GitHub Actions builds `app/build/outputs/apk/debug/app-debug.apk` on pushes and pull requests.

## Next milestones

1. Persistent player accounts and profiles.
2. Server-authoritative multiplayer movement and shared rooms.
3. Real-time chat, friends, invitations, and private rooms.
4. Avatar customization, inventory, shops, and saved progression.
5. Moderation, reporting, and production security.

## Backend

See `backend/README.md`. The backend is a development starter and must be deployed behind HTTPS with persistent storage and production security configuration before public use.
