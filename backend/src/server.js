import express from "express";
import cors from "cors";
import http from "node:http";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { Server } from "socket.io";

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: process.env.CORS_ORIGIN?.split(",") ?? "*" } });
const PORT = Number(process.env.PORT || 3000);
const JWT_SECRET = process.env.JWT_SECRET || "development-only-change-me";
const users = new Map();
const online = new Map();

app.use(cors());
app.use(express.json({ limit: "32kb" }));

app.get("/health", (_req, res) => res.json({ ok: true, service: "nexora-world", online: online.size }));

function issueToken(user) {
  return jwt.sign({ sub: user.id, username: user.username }, JWT_SECRET, { expiresIn: "7d" });
}

app.post("/api/auth/register", async (req, res) => {
  const username = String(req.body?.username || "").trim().toLowerCase();
  const password = String(req.body?.password || "");
  if (!/^[a-z0-9_]{3,20}$/.test(username) || password.length < 8) {
    return res.status(400).json({ error: "Username must be 3-20 letters/numbers/underscores and password at least 8 characters." });
  }
  if (users.has(username)) return res.status(409).json({ error: "Username already exists." });
  const user = { id: crypto.randomUUID(), username, passwordHash: await bcrypt.hash(password, 12) };
  users.set(username, user);
  return res.status(201).json({ token: issueToken(user), user: { id: user.id, username } });
});

app.post("/api/auth/login", async (req, res) => {
  const username = String(req.body?.username || "").trim().toLowerCase();
  const password = String(req.body?.password || "");
  const user = users.get(username);
  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
    return res.status(401).json({ error: "Invalid username or password." });
  }
  return res.json({ token: issueToken(user), user: { id: user.id, username } });
});

function authenticateSocket(socket, next) {
  try {
    const token = socket.handshake.auth?.token;
    if (!token) return next(new Error("Authentication required"));
    socket.user = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    next(new Error("Invalid or expired token"));
  }
}

io.use(authenticateSocket);
io.on("connection", (socket) => {
  const player = { id: socket.user.sub, username: socket.user.username, x: 0.5, y: 0.66 };
  online.set(socket.id, player);
  socket.emit("world:snapshot", [...online.values()]);
  socket.broadcast.emit("player:joined", player);

  socket.on("player:move", (position) => {
    const x = Number(position?.x), y = Number(position?.y);
    if (!Number.isFinite(x) || !Number.isFinite(y)) return;
    player.x = Math.max(0, Math.min(1, x));
    player.y = Math.max(0, Math.min(1, y));
    socket.broadcast.emit("player:moved", player);
  });

  socket.on("room:join", (roomId) => {
    if (typeof roomId !== "string" || !/^[a-zA-Z0-9_-]{1,40}$/.test(roomId)) return;
    for (const room of socket.rooms) if (room !== socket.id) socket.leave(room);
    socket.join(roomId);
    socket.emit("room:joined", { roomId });
  });

  socket.on("chat:message", (payload) => {
    const roomId = [...socket.rooms].find((room) => room !== socket.id);
    const message = String(payload?.message || "").trim().slice(0, 300);
    if (!roomId || !message) return;
    io.to(roomId).emit("chat:message", { username: player.username, message, sentAt: Date.now() });
  });

  socket.on("disconnect", () => {
    online.delete(socket.id);
    socket.broadcast.emit("player:left", { id: player.id });
  });
});

server.listen(PORT, "0.0.0.0", () => console.log(`NEXORA backend listening on ${PORT}`));
