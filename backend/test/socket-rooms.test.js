import test from "node:test";
import assert from "node:assert/strict";
import { RoomManager } from "../src/room-manager.js";
import { attachRoomSocket } from "../src/socket-rooms.js";

class Socket {
  constructor(id) { this.id=id; this.data={}; this.handlers={}; this.sent=[]; this.broadcasts=[]; }
  on(name, fn) { this.handlers[name]=fn; }
  emit(name, data) { this.sent.push({name,data}); }
  trigger(name, data) { this.handlers[name]?.(data); }
  join() {}
  leave() {}
  to(room) { return { emit:(name,data)=>this.broadcasts.push({room,name,data}) }; }
  disconnect() { this.trigger("disconnect"); }
  last(name) { return this.sent.filter(x=>x.name===name).at(-1)?.data; }
}
class IO {
  constructor() { this.broadcasts=[]; this.sockets={sockets:new Map()}; }
  to(room) { return {emit:(name,data)=>this.broadcasts.push({room,name,data})}; }
}
function fixture() {
  let i=0; const codes=["NEXORA7"];
  const world=new RoomManager({codeGenerator:()=>codes[i++]||"NEXT2233"});
  const io=new IO(), activeSockets=new Map(), online=new Map();
  const connect=(id)=>{const socket=new Socket(id);io.sockets.sockets.set(id,socket);const player=world.joinWorld(id,id);attachRoomSocket({io,socket,world,player,activeSockets,online});return socket;};
  return {world,io,connect};
}

test("socket create/join events enforce invite-code membership",()=>{
  const {world,connect}=fixture(), host=connect("host"), guest=connect("guest");
  host.trigger("room:create",{}); const room=host.last("room:created");
  guest.trigger("room:join",{code:room.code.toLowerCase()});
  assert.equal(guest.last("room:joined").id,room.id);
  assert.equal(world.getRoomSummary(room.id).playerCount,2);
});

test("socket chat is scoped to the current private room",()=>{
  const {io,connect}=fixture(), host=connect("host"), guest=connect("guest");
  host.trigger("room:create",{}); const room=host.last("room:created");
  guest.trigger("room:join",{code:room.code}); io.broadcasts.length=0;
  host.trigger("chat:message",{message:"hello room"});
  const chat=io.broadcasts.filter(x=>x.name==="chat:message");
  assert.equal(chat.length,1); assert.equal(chat[0].room,room.id); assert.equal(chat[0].data.message,"hello room");
});

test("socket disconnect transfers host and closes the empty room",()=>{
  const {world,io,connect}=fixture(), host=connect("host"), guest=connect("guest");
  host.trigger("room:create",{}); const room=host.last("room:created"); guest.trigger("room:join",{code:room.code});
  host.disconnect(); assert.equal(world.getRoomSummary(room.id).hostId,"guest");
  assert.equal(io.broadcasts.some(x=>x.name==="room:host_changed"&&x.data.hostId==="guest"),true);
  guest.disconnect(); assert.equal(world.getRoomSummary(room.id),null);
});
