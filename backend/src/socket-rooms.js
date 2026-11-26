import { DEFAULT_ROOM_ID } from "./room-manager.js";

/** Attach authenticated room, movement, and chat events to one Socket.IO socket. */
export function attachRoomSocket({ io, socket, world, player, activeSockets, online }) {
  const playerId = player.id;
  const previousSocketId = activeSockets.get(playerId);
  activeSockets.set(playerId, socket.id);
  online.set(socket.id, player);
  if (previousSocketId && previousSocketId !== socket.id) {
    io.sockets.sockets.get(previousSocketId)?.disconnect(true);
  }

  const emitState = roomId => {
    socket.emit("room:state", world.getRoomSummary(roomId));
    socket.emit("world:snapshot", world.getRoomSnapshot(roomId));
  };
  const broadcastState = roomId => {
    const summary = world.getRoomSummary(roomId);
    if (summary) io.to(roomId).emit("room:state", summary);
  };
  const sendError = error => socket.emit("server:error", {
    code: error.code || "INTERNAL_ERROR",
    message: error.message || "حدث خطأ غير متوقع"
  });
  const notifyDeparture = result => {
    const oldRoom = result.previousRoomId;
    if (!oldRoom || oldRoom === result.player.roomId) return;
    socket.leave(oldRoom);
    socket.to(oldRoom).emit("player:left", { id: playerId });
    if (result.previousRoomHostId) {
      io.to(oldRoom).emit("room:host_changed", { hostId: result.previousRoomHostId });
    }
    if (result.previousRoomClosed) io.to(oldRoom).emit("room:closed", { roomId: oldRoom });
    else broadcastState(oldRoom);
  };

  socket.join(player.roomId);
  socket.emit("world:identity", { playerId });
  emitState(player.roomId);
  if (!previousSocketId) socket.to(player.roomId).emit("player:joined", player);
  broadcastState(player.roomId);

  socket.on("room:create", payload => {
    try {
      const result = world.createPrivateRoom(playerId, {
        name: payload?.name,
        maxPlayers: payload?.maxPlayers
      });
      notifyDeparture(result);
      socket.join(result.player.roomId);
      socket.emit("room:created", { ...result.room, code: result.code });
      broadcastState(result.player.roomId);
      emitState(result.player.roomId);
    } catch (error) { sendError(error); }
  });

  socket.on("room:join", payload => {
    try {
      const result = world.joinPrivateRoom(playerId, payload?.code);
      notifyDeparture(result);
      socket.join(result.player.roomId);
      if (result.previousRoomId !== result.player.roomId) {
        socket.to(result.player.roomId).emit("player:joined", result.player);
      }
      socket.emit("room:joined", { ...result.room, code: result.code });
      broadcastState(result.player.roomId);
      emitState(result.player.roomId);
    } catch (error) { sendError(error); }
  });

  socket.on("room:leave", () => {
    try {
      const result = world.leaveRoom(playerId);
      notifyDeparture(result);
      socket.join(DEFAULT_ROOM_ID);
      if (result.previousRoomId !== DEFAULT_ROOM_ID) {
        socket.to(DEFAULT_ROOM_ID).emit("player:joined", result.player);
      }
      socket.emit("room:joined", result.room);
      broadcastState(DEFAULT_ROOM_ID);
      emitState(DEFAULT_ROOM_ID);
    } catch (error) { sendError(error); }
  });

  socket.on("rooms:list", () => socket.emit("rooms:list", world.getPublicRooms()));

  socket.on("player:move", payload => {
    const now = Date.now();
    if (socket.data.lastMoveAt && now - socket.data.lastMoveAt < 50) return;
    socket.data.lastMoveAt = now;
    try {
      const moved = world.movePlayer(playerId, payload?.x, payload?.y, { now });
      socket.to(moved.roomId).emit("player:moved", moved);
    } catch (error) {
      socket.emit("player:corrected", world.getPlayerSnapshot(playerId));
      sendError(error);
    }
  });

  socket.on("chat:message", payload => {
    const current = world.players.get(playerId);
    if (!current) return;
    const now = Date.now();
    if (socket.data.lastChatAt && now - socket.data.lastChatAt < 700) return;
    socket.data.lastChatAt = now;
    const message = String(payload?.message || "")
      .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "")
      .trim()
      .slice(0, 240);
    if (message) io.to(current.roomId).emit("chat:message", {
      playerId,
      username: current.username,
      message,
      sentAt: now
    });
  });

  socket.on("disconnect", () => {
    online.delete(socket.id);
    if (activeSockets.get(playerId) !== socket.id) return;
    activeSockets.delete(playerId);
    const removed = world.removePlayer(playerId);
    if (!removed) return;
    socket.to(removed.previousRoomId).emit("player:left", { id: playerId });
    if (removed.previousRoomHostId) {
      io.to(removed.previousRoomId).emit("room:host_changed", { hostId: removed.previousRoomHostId });
    }
    if (removed.previousRoomClosed) io.to(removed.previousRoomId).emit("room:closed", { roomId: removed.previousRoomId });
    else broadcastState(removed.previousRoomId);
  });
}
