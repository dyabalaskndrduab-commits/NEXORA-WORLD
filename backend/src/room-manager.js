import crypto from "node:crypto";

export const DEFAULT_ROOM_ID = "nexora-plaza";
export const MIN_PRIVATE_ROOM_SIZE = 2;
export const MAX_PRIVATE_ROOM_SIZE = 12;

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const MAX_PRIVATE_ROOMS = 100;
const MAX_MOVE_SPEED = 0.06; // about 8.4 city units per second
const MAX_MOVE_STEP = 0.02; // hard cap, even after a long idle period
const MOVE_SLACK = 0.001; // small allowance for frame/network jitter
const MAX_MOVE_ELAPSED_SECONDS = 0.25;

function cleanText(value, fallback, maxLength) {
  const cleaned = String(value ?? "")
    .replace(/[\u0000-\u001F\u007F-\u009F]/g, "")
    .trim()
    .slice(0, maxLength);
  return cleaned || fallback;
}

function normalizeCode(value) {
  return String(value ?? "").replace(/[\s-]/g, "").toUpperCase();
}

function createInviteCode() {
  const bytes = crypto.randomBytes(8);
  return [...bytes].map(byte => CODE_ALPHABET[byte & 31]).join("");
}

export class RoomManager {
  constructor({ codeGenerator = createInviteCode, clock = Date.now } = {}) {
    this.codeGenerator = codeGenerator;
    this.clock = clock;
    this.players = new Map();
    this.rooms = new Map();
    this.codeIndex = new Map();
    this.rooms.set(DEFAULT_ROOM_ID, {
      id: DEFAULT_ROOM_ID,
      name: "NEXORA Plaza",
      visibility: "public",
      maxPlayers: 100,
      hostId: null,
      members: new Set(),
      createdAt: this.clock()
    });
  }

  joinWorld(playerId, username) {
    const id = String(playerId ?? "").trim();
    if (!id || id.length > 80) throw this.error("INVALID_PLAYER", "معرّف اللاعب غير صالح");

    const existing = this.players.get(id);
    if (existing) {
      existing.username = cleanText(username, existing.username, 24);
      return this.playerSnapshot(existing);
    }

    const now = this.clock();
    const player = {
      id,
      username: cleanText(username, "Explorer", 24),
      x: 0.5,
      y: 0.66,
      roomId: DEFAULT_ROOM_ID,
      lastMoveAt: now,
      joinedAt: now
    };
    this.players.set(id, player);
    this.rooms.get(DEFAULT_ROOM_ID).members.add(id);
    return this.playerSnapshot(player);
  }

  createPrivateRoom(playerId, { name, maxPlayers } = {}) {
    const player = this.requirePlayer(playerId);
    const capacity = maxPlayers === undefined ? 8 : Number(maxPlayers);
    if (!Number.isInteger(capacity) || capacity < MIN_PRIVATE_ROOM_SIZE || capacity > MAX_PRIVATE_ROOM_SIZE) {
      throw this.error("INVALID_CAPACITY", `سعة الغرفة يجب أن تكون بين ${MIN_PRIVATE_ROOM_SIZE} و${MAX_PRIVATE_ROOM_SIZE}`);
    }

    const privateCount = [...this.rooms.values()].filter(room => room.visibility === "private").length;
    if (privateCount >= MAX_PRIVATE_ROOMS) throw this.error("ROOM_LIMIT", "تم بلوغ الحد الأقصى للغرف الخاصة حالياً");

    let code = null;
    for (let attempt = 0; attempt < 12; attempt += 1) {
      const candidate = normalizeCode(this.codeGenerator());
      if (/^[A-Z2-9]{6,10}$/.test(candidate) && !this.codeIndex.has(candidate)) {
        code = candidate;
        break;
      }
    }
    if (!code) throw this.error("CODE_GENERATION_FAILED", "تعذر إنشاء رمز دعوة فريد؛ حاول مرة أخرى");

    const room = {
      id: `private-${code}`,
      code,
      name: cleanText(name, "Private Room", 32),
      visibility: "private",
      maxPlayers: capacity,
      hostId: player.id,
      members: new Set(),
      createdAt: this.clock()
    };
    this.rooms.set(room.id, room);
    this.codeIndex.set(code, room.id);

    const previousRoomId = player.roomId;
    const detached = this.detach(player);
    player.roomId = room.id;
    room.members.add(player.id);

    return {
      player: this.playerSnapshot(player),
      previousRoomId,
      previousRoomClosed: detached.closed,
      previousRoomHostId: detached.newHostId,
      room: this.roomSnapshot(room),
      code,
      snapshot: this.getRoomSnapshot(room.id)
    };
  }

  joinPrivateRoom(playerId, rawCode) {
    const player = this.requirePlayer(playerId);
    const code = normalizeCode(rawCode);
    const roomId = this.codeIndex.get(code);
    const room = roomId && this.rooms.get(roomId);
    if (!room) throw this.error("ROOM_NOT_FOUND", "رمز الدعوة غير صحيح أو أن الغرفة لم تعد موجودة");
    if (player.roomId === room.id) {
      return {
        player: this.playerSnapshot(player),
        previousRoomId: room.id,
        room: this.roomSnapshot(room),
        code,
        snapshot: this.getRoomSnapshot(room.id)
      };
    }
    if (room.members.size >= room.maxPlayers) throw this.error("ROOM_FULL", "الغرفة وصلت إلى الحد الأقصى للاعبين");

    const previousRoomId = player.roomId;
    const detached = this.detach(player);
    player.roomId = room.id;
    room.members.add(player.id);
    return {
      player: this.playerSnapshot(player),
      previousRoomId,
      previousRoomClosed: detached.closed,
      previousRoomHostId: detached.newHostId,
      room: this.roomSnapshot(room),
      code,
      snapshot: this.getRoomSnapshot(room.id)
    };
  }

  leaveRoom(playerId) {
    const player = this.requirePlayer(playerId);
    const previousRoomId = player.roomId;
    if (previousRoomId === DEFAULT_ROOM_ID) {
      return {
        player: this.playerSnapshot(player),
        previousRoomId,
        room: this.roomSnapshot(this.rooms.get(DEFAULT_ROOM_ID)),
        snapshot: this.getRoomSnapshot(DEFAULT_ROOM_ID)
      };
    }

    const detached = this.detach(player);
    player.roomId = DEFAULT_ROOM_ID;
    this.rooms.get(DEFAULT_ROOM_ID).members.add(player.id);
    return {
      player: this.playerSnapshot(player),
      previousRoomId,
      previousRoomClosed: detached.closed,
      previousRoomHostId: detached.newHostId,
      room: this.roomSnapshot(this.rooms.get(DEFAULT_ROOM_ID)),
      snapshot: this.getRoomSnapshot(DEFAULT_ROOM_ID)
    };
  }

  movePlayer(playerId, rawX, rawY, { now = this.clock() } = {}) {
    const player = this.requirePlayer(playerId);
    const x = Number(rawX);
    const y = Number(rawY);
    if (!Number.isFinite(x) || !Number.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) {
      throw this.error("INVALID_POSITION", "إحداثيات الحركة خارج حدود العالم");
    }

    const elapsed = Math.max(0, Math.min(MAX_MOVE_ELAPSED_SECONDS, (now - player.lastMoveAt) / 1000));
    const allowedDistance = Math.min(MAX_MOVE_SPEED * elapsed + MOVE_SLACK, MAX_MOVE_STEP);
    const distance = Math.hypot(x - player.x, y - player.y);
    if (distance > allowedDistance) {
      throw this.error("MOVEMENT_TOO_FAST", "الحركة أسرع من الحد المسموح؛ أعد مزامنة موقع اللاعب");
    }

    player.x = x;
    player.y = y;
    player.lastMoveAt = now;
    return this.playerSnapshot(player);
  }

  removePlayer(playerId) {
    const player = this.players.get(String(playerId));
    if (!player) return null;
    const snapshot = this.playerSnapshot(player);
    const detached = this.detach(player);
    this.players.delete(player.id);
    return {
      player: snapshot,
      previousRoomId: snapshot.roomId,
      previousRoomClosed: detached.closed,
      previousRoomHostId: detached.newHostId
    };
  }

  getPlayerSnapshot(playerId) {
    const player = this.players.get(String(playerId));
    return player ? this.playerSnapshot(player) : null;
  }

  getRoomSnapshot(roomId) {
    const room = this.rooms.get(roomId);
    if (!room) return [];
    return [...room.members]
      .map(id => this.players.get(id))
      .filter(Boolean)
      .map(player => this.playerSnapshot(player));
  }

  getRoomSummary(roomId) {
    const room = this.rooms.get(roomId);
    return room ? this.roomSnapshot(room) : null;
  }

  getPublicRooms() {
    return [...this.rooms.values()]
      .filter(room => room.visibility === "public")
      .map(room => this.roomSnapshot(room));
  }

  playerSnapshot(player) {
    return {
      id: player.id,
      username: player.username,
      x: player.x,
      y: player.y,
      roomId: player.roomId
    };
  }

  roomSnapshot(room) {
    return {
      id: room.id,
      name: room.name,
      visibility: room.visibility,
      maxPlayers: room.maxPlayers,
      playerCount: room.members.size,
      hostId: room.hostId
    };
  }

  requirePlayer(playerId) {
    const player = this.players.get(String(playerId));
    if (!player) throw this.error("PLAYER_NOT_IN_WORLD", "يجب دخول العالم أولاً");
    return player;
  }

  detach(player) {
    const room = this.rooms.get(player.roomId);
    if (!room) return { closed: false, newHostId: null };
    room.members.delete(player.id);
    if (room.visibility !== "private") return { closed: false, newHostId: null };
    if (room.members.size === 0) {
      this.rooms.delete(room.id);
      this.codeIndex.delete(room.code);
      return { closed: true, newHostId: null };
    }
    if (room.hostId === player.id) {
      room.hostId = room.members.values().next().value;
      return { closed: false, newHostId: room.hostId };
    }
    return { closed: false, newHostId: null };
  }

  error(code, message) {
    const error = new Error(message);
    error.code = code;
    return error;
  }
}
