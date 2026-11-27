export class DuplicateUsernameError extends Error {
  constructor() {
    super("Username already exists.");
    this.name = "DuplicateUsernameError";
    this.code = "USERNAME_TAKEN";
  }
}
export class MemoryAccountStore {
  constructor() { this.kind = "memory"; this.byUsername = new Map(); }
  async findByUsername(username) {
    return this.byUsername.get(String(username).toLowerCase()) ?? null;
  }
  async create(user) {
    const username = String(user.username).toLowerCase();
    if (this.byUsername.has(username)) throw new DuplicateUsernameError();
    const account = { id: String(user.id), username, passwordHash: String(user.passwordHash) };
    this.byUsername.set(username, account);
    return { ...account };
  }
  async ping() { return true; }
  async close() {}
}
export class PostgresAccountStore {
  constructor(pool) {
    if (!pool || typeof pool.query !== "function") throw new TypeError("A PostgreSQL-compatible pool is required.");
    this.pool = pool;
    this.kind = "postgres";
  }
  async ping() {
    await this.pool.query("SELECT 1");
    await this.pool.query("SELECT id FROM nexora_accounts LIMIT 0");
    return true;
  }
  async findByUsername(username) {
    const result = await this.pool.query(
      \`SELECT id, username, password_hash AS "passwordHash"
       FROM nexora_accounts WHERE username = $1 LIMIT 1\`,
      [String(username).toLowerCase()]
    );
    return result.rows[0] ?? null;
  }
  async create(user) {
    try {
      const result = await this.pool.query(
        \`INSERT INTO nexora_accounts (id, username, password_hash)
         VALUES ($1, $2, $3)
         RETURNING id, username, password_hash AS "passwordHash"\`,
        [String(user.id), String(user.username).toLowerCase(), String(user.passwordHash)]
      );
      return result.rows[0];
    } catch (error) {
      if (error?.code === "23505") throw new DuplicateUsernameError();
      throw error;
    }
  }
  async close() { await this.pool.end(); }
}
export async function createAccountStore({
  databaseUrl = process.env.DATABASE_URL,
  nodeEnv = process.env.NODE_ENV,
  databaseSsl = process.env.DATABASE_SSL,
  databaseSslRejectUnauthorized = process.env.DATABASE_SSL_REJECT_UNAUTHORIZED
} = {}) {
  if (!databaseUrl) {
    if (nodeEnv === "production") {
      throw new Error("DATABASE_URL is required in production; refusing to start with volatile account storage.");
    }
    return new MemoryAccountStore();
  }
  if (nodeEnv === "production" && databaseSsl !== "require") {
    throw new Error("Set DATABASE_SSL=require in production so PostgreSQL connections use TLS.");
  }
  const { Pool } = await import("pg");
  const pool = new Pool({
    connectionString: databaseUrl,
    ...(databaseSsl === "require" ? {
      ssl: { rejectUnauthorized: databaseSslRejectUnauthorized !== "false" }
    } : {})
  });
  const store = new PostgresAccountStore(pool);
  try {
    await store.ping();
  } catch (error) {
    await store.close().catch(() => {});
    if (error?.code === "42P01") {
      throw new Error("PostgreSQL is reachable but nexora_accounts is missing. Run npm run migrate before starting the server.");
    }
    throw error;
  }
  return store;
}
