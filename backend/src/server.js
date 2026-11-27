import crypto from "node:crypto";
import express from "express";
import cors from "cors";
import http from "node:http";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { Server } from "socket.io";
import { RoomManager } from "./room-manager.js";
import { attachRoomSocket } from "./socket-rooms.js";
import { createAccountStore, DuplicateUsernameError } from "./account-store.js";

const PORT = Number(process.env.PORT || 3000);
const JWT_SECRET = process.env.JWT_SECRET || "development-only-change-me-please-rotate-32";
if (process.env.NODE_ENV === "production" && (!process.env.JWT_SECRET || JWT_SECRET.length < 32)) {
  throw new Error("Set a strong JWT_SECRET (at least 32 characters) in production.");
}
const app = express();
const server = http.createServer(app);
const allowedOrigins = (process.env.CORS_ORIGIN || "*").split(",").map(value => value.trim()).filter(Boolean);
const origin = allowedOrigins.includes("*") ? "*" : allowedOrigins;
const io = new Server(server, { cors: { origin } });
const world = new RoomManager();
const accountStore = await createAccountStore();
const online = new Map();
const activeSockets = new Map();
const authAttempts = new Map();

app.use(cors({ origin }));
app.use(express.json({ limit: "32kb" }));

function authRateLimit(req, res, next) {
  const key = req.ip || req.socket.remoteAddress || "unknown";
  const now = Date.now();
  let bucket = authAttempts.get(key);
  if (!bucket || bucket.resetAt <= now) {
    bucket = { count: 0, resetAt: now + 60_000 };
    authAttempts.set(key, bucket);
  }
  if (bucket.count >= 10) {
    res.set("Retry-After", String(Math.ceil((bucket.resetAt - now) / 1000)));
    return res.status(429).json({ error: "Too many authentication attempts. Try again in one minute." });
  }
  bucket.count += 1;
  next();
}
const issueToken = user => jwt.sign(
  { sub: user.id, username: user.username }, JWT_SECRET, { expiresIn: "7d" }
);
app.get("/health", (_req, res) => res.json({
  ok: true, service: "nexora-world", online: world.players.size,
  activeRooms: world.rooms.size, accountStorage: accountStore.kind
}));

app.post("/api/auth/register", authRateLimit, async (req, res) => {
  const username = String(req.body?.username || "").trim().toLowerCase();
  const password = String(req.body?.password || "");
  const bytes = Buffer.byteLength(password, "utf8");
  if (!/^[a-z0-9_]{3,20}$/.test(username) || bytes < 8 || bytes > 72) {
    return res.status(400).json({ error: "Username must be 3-20 letters/numbers/underscores and password 8-72 bytes." });
  }
  try {
    if (await accountStore.findByUsername(username)) {
      return res.status(409).json({ error: "Username already exists." });
    }
    const user = {
      id: crypto.randomUUID(), username,
      passwordHash: await bcrypt.hash(password, 12)
    };
    const created = await accountStore.create(user);
    return res.status(201).json({
      token: issueToken(created), user: { id: created.id, username: created.username }
    });
  } catch (error) {
    if (error instanceof DuplicateUsernameError || error?.code === "USERNAME_TAKEN") {
      return res.status(409).json({ error: "Username already exists." });
    }
    console.error("Account registration failed:", error);
    return res.status(503).json({ error: "Account service is temporarily unavailable." });
  }
});
app.post("/api/auth/login", authRateLimit, async (req, res) => {
  const username = String(req.body?.username || "").trim().toLowerCase();
  const password = String(req.body?.password || "");
  try {
    const user = await accountStore.findByUsername(username);
    if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
      return res.status(401).json({ error: "Invalid username or password." });
    }
    return res.json({
      token: issueToken(user), user: { id: user.id, username: user.username }
    });
  } catch (error) {
    console.error("Account login failed:", error);
    return res.status(503).json({ error: "Account service is temporarily unavailable." });
  }
});

io.use((socket, next) => {
  try {
    const token = socket.handshake.auth?.token;
    if (!token) return next(new Error("Authentication required"));
    socket.user = jwt.verify(token, JWT_SECRET);
    if (!socket.user?.sub || !socket.user?.username) return next(new Error("Invalid account identity"));
    next();
  } catch { next(new Error("Invalid or expired token")); }
});
io.on("connection", socket => {
  const player = world.joinWorld(String(socket.user.sub), socket.user.username);
  attachRoomSocket({ io, socket, world, player, activeSockets, online });
});
server.listen(PORT, "0.0.0.0", () => {
  console.log(\`NEXORA backend listening on \${PORT} (account storage: \${accountStore.kind})\`);
});
async function shutdown(signal) {
  console.log(\`Received \${signal}; closing NEXORA backend.\`);
  io.close();
  await accountStore.close();
  process.exit(0);
}
process.once("SIGINT", () => { void shutdown("SIGINT"); });
process.once("SIGTERM", () => { void shutdown("SIGTERM"); });
