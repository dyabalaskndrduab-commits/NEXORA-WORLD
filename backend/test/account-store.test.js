import test from "node:test";
import assert from "node:assert/strict";
import {
  DuplicateUsernameError, MemoryAccountStore, PostgresAccountStore, createAccountStore
} from "../src/account-store.js";
const sample = {
  id: "d2b3f9b8-3f9f-4d3c-ae8a-8a9a9db6b201",
  username: "player_1",
  passwordHash: "$2a$12$test-hash"
};
test("memory account store normalizes names and returns stored account", async () => {
  const store = new MemoryAccountStore();
  const created = await store.create({ ...sample, username: "PLAYER_1" });
  assert.equal(created.username, "player_1");
  assert.deepEqual(await store.findByUsername("PLAYER_1"), created);
});
test("memory account store rejects duplicate usernames", async () => {
  const store = new MemoryAccountStore();
  await store.create(sample);
  await assert.rejects(
    store.create({ ...sample, id: "e3c4f0a9-4a0a-4e4d-bf9b-0b0a0ab0c302" }),
    DuplicateUsernameError
  );
});
test("PostgreSQL account store uses parameterized username lookup", async () => {
  const calls = [];
  const pool = {
    async query(sql, params) {
      calls.push({ sql, params });
      return { rows: [{ id: sample.id, username: sample.username, passwordHash: sample.passwordHash }] };
    },
    async end() {}
  };
  const store = new PostgresAccountStore(pool);
  const result = await store.findByUsername("PLAYER_1");
  assert.equal(result.id, sample.id);
  assert.deepEqual(calls[0].params, ["player_1"]);
  assert.match(calls[0].sql, /WHERE username = \$1/);
});
test("PostgreSQL account store maps unique constraint violations", async () => {
  const pool = {
    async query() { throw Object.assign(new Error("duplicate"), { code: "23505" }); },
    async end() {}
  };
  const store = new PostgresAccountStore(pool);
  await assert.rejects(store.create(sample), DuplicateUsernameError);
});
test("production refuses to fall back to volatile memory accounts", async () => {
  await assert.rejects(
    createAccountStore({ databaseUrl: "", nodeEnv: "production" }),
    /DATABASE_URL is required in production/
  );
});
test("production requires TLS for PostgreSQL", async () => {
  await assert.rejects(
    createAccountStore({
      databaseUrl: "postgresql://example.invalid/nexora",
      nodeEnv: "production",
      databaseSsl: "disable"
    }),
    /DATABASE_SSL=require/
  );
});
