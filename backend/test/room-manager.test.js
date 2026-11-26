import test from "node:test";
import assert from "node:assert/strict";
import { DEFAULT_ROOM_ID, RoomManager } from "../src/room-manager.js";

function createManager(codes = ["NEXORA7", "ROOM2233"]) {
  let now = 1_000;
  let index = 0;
  const world = new RoomManager({
    codeGenerator: () => codes[index++] ?? `CODE${index}99`,
    clock: () => now
  });
  return { world, advance: ms => { now += ms; }, now: () => now };
}

test("players enter the public plaza and snapshots are room-scoped", () => {
  const { world } = createManager();
  const host = world.joinWorld("account-1", "  Mia  ");
  world.joinWorld("account-2", "Zed");
  assert.equal(host.username, "Mia");
  assert.equal(host.roomId, DEFAULT_ROOM_ID);
  assert.equal(world.getRoomSnapshot(DEFAULT_ROOM_ID).length, 2);
});

test("private rooms use invite codes and stay out of the public directory", () => {
  const { world } = createManager();
  world.joinWorld("host", "Host");
  const created = world.createPrivateRoom("host", { name: "Friends", maxPlayers: 4 });
  assert.equal(created.code, "NEXORA7");
  assert.equal(created.room.visibility, "private");
  assert.equal(world.getPublicRooms().length, 1);
  assert.equal(world.getPublicRooms()[0].id, DEFAULT_ROOM_ID);
  assert.equal(world.getPublicRooms().some(room => room.id === created.room.id), false);
});

test("invite codes are case-insensitive and capacity is enforced", () => {
  const { world } = createManager();
  world.joinWorld("host", "Host");
  world.joinWorld("guest", "Guest");
  world.joinWorld("late", "Late");
  const created = world.createPrivateRoom("host", { maxPlayers: 2 });
  const joined = world.joinPrivateRoom("guest", " nexora7 ");
  assert.equal(joined.player.roomId, created.player.roomId);
  assert.equal(joined.room.playerCount, 2);
  assert.throws(() => world.joinPrivateRoom("late", created.code), { code: "ROOM_FULL" });
  assert.throws(() => world.joinPrivateRoom("late", "UNKNOWN1"), { code: "ROOM_NOT_FOUND" });
});

test("room capacity is limited to 2 through 12 players", () => {
  const { world } = createManager();
  world.joinWorld("host", "Host");
  assert.throws(() => world.createPrivateRoom("host", { maxPlayers: 1 }), { code: "INVALID_CAPACITY" });
  assert.throws(() => world.createPrivateRoom("host", { maxPlayers: 13 }), { code: "INVALID_CAPACITY" });
});

test("leaving transfers host ownership and deletes the empty private room", () => {
  const { world } = createManager();
  world.joinWorld("host", "Host");
  world.joinWorld("guest", "Guest");
  const created = world.createPrivateRoom("host", {});
  world.joinPrivateRoom("guest", created.code);
  const hostLeaves = world.leaveRoom("host");
  assert.equal(hostLeaves.previousRoomHostId, "guest");
  assert.equal(world.getRoomSummary(created.player.roomId).hostId, "guest");
  const guestLeaves = world.leaveRoom("guest");
  assert.equal(guestLeaves.previousRoomClosed, true);
  assert.equal(world.getRoomSummary(created.player.roomId), null);
  assert.equal(world.codeIndex.has(created.code), false);
});

test("a reconnect with the same stable account ID preserves its room membership", () => {
  const { world } = createManager();
  world.joinWorld("host", "Host");
  const created = world.createPrivateRoom("host", {});
  const resumed = world.joinWorld("host", "Host Reconnected");
  assert.equal(resumed.roomId, created.player.roomId);
  assert.equal(world.getRoomSummary(created.player.roomId).playerCount, 1);
  assert.equal(resumed.username, "Host Reconnected");
});

test("movement accepts plausible steps and rejects teleporting", () => {
  const { world, advance, now } = createManager();
  world.joinWorld("p1", "Player");
  advance(100);
  const moved = world.movePlayer("p1", 0.504, 0.66, { now: now() });
  assert.equal(moved.x, 0.504);
  advance(100);
  assert.throws(() => world.movePlayer("p1", 0.95, 0.66, { now: now() }), { code: "MOVEMENT_TOO_FAST" });
  assert.equal(world.getPlayerSnapshot("p1").x, 0.504);
});

test("movement rejects an in-bounds step faster than the 3D client can travel", () => {
  const { world, advance, now } = createManager();
  world.joinWorld("p1", "Player");
  advance(100);
  assert.throws(() => world.movePlayer("p1", 0.53, 0.66, { now: now() }), { code: "MOVEMENT_TOO_FAST" });
  assert.equal(world.getPlayerSnapshot("p1").x, 0.5);
});

test("movement cannot use invalid or out-of-world coordinates", () => {
  const { world, advance, now } = createManager();
  world.joinWorld("p1", "Player");
  advance(100);
  assert.throws(() => world.movePlayer("p1", Number.NaN, 0.66, { now: now() }), { code: "INVALID_POSITION" });
  assert.throws(() => world.movePlayer("p1", 1.2, 0.66, { now: now() }), { code: "INVALID_POSITION" });
});

test("disconnecting a host transfers ownership or closes the room", () => {
  const { world } = createManager();
  world.joinWorld("host", "Host");
  world.joinWorld("guest", "Guest");
  const created = world.createPrivateRoom("host", {});
  world.joinPrivateRoom("guest", created.code);
  const removed = world.removePlayer("host");
  assert.equal(removed.previousRoomHostId, "guest");
  assert.equal(world.getRoomSummary(created.player.roomId).hostId, "guest");
  const last = world.removePlayer("guest");
  assert.equal(last.previousRoomClosed, true);
  assert.equal(world.getRoomSummary(created.player.roomId), null);
});
