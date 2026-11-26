# Changelog

## 0.5.0 — Android social client and server-authoritative rooms
- Added public and private rooms with cryptographically random invite codes, 2–12 player capacity, host transfer and empty-room cleanup.
- Scoped player snapshots, movement broadcasts and chat to each room; added normalized movement bounds and speed validation.
- Added authentication rate limiting, production JWT secret guard and Node.js room/socket tests.
- Added Android register/login UI with configurable backend URL and AES-GCM token storage in Android Keystore.
- Connected Android to Socket.IO snapshots, remote avatar rendering, authoritative movement correction, room controls and room-scoped chat.
- Added normalized city-coordinate conversion tests.
- The backend remains in-memory and requires deployment; two-device gameplay is not yet physically verified.

## 0.4.0 — Expanded city districts
- Expanded the playable ground and movement boundary to a 140 × 140 city area.
- Added wider avenues, residential towers, retail buildings, two pocket parks and two destination landmarks.
- Added collision-aware movement around main building footprints and extended the camera far plane.
- Reduced per-building window geometry for mobile GPU performance.
