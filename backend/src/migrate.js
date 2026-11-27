import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error("DATABASE_URL is required to run migrations.");
if (process.env.NODE_ENV === "production" && process.env.DATABASE_SSL !== "require") {
  throw new Error("Set DATABASE_SSL=require before running production migrations.");
}
const { Pool } = await import("pg");
const pool = new Pool({
  connectionString: databaseUrl,
  ...(process.env.DATABASE_SSL === "require" ? {
    ssl: { rejectUnauthorized: process.env.DATABASE_SSL_REJECT_UNAUTHORIZED !== "false" }
  } : {})
});
const migrationVersion = "001_accounts";
const migrationPath = fileURLToPath(new URL("../migrations/001_accounts.sql", import.meta.url));
const migrationSql = await readFile(migrationPath, "utf8");
const client = await pool.connect();
try {
  await client.query("BEGIN");
  await client.query("SELECT pg_advisory_xact_lock($1)", [1313165391]);
  await client.query("CREATE TABLE IF NOT EXISTS nexora_schema_migrations (version VARCHAR(100) PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW())");
  const existing = await client.query(
    "SELECT version FROM nexora_schema_migrations WHERE version = $1",
    [migrationVersion]
  );
  if (existing.rowCount > 0) {
    await client.query("COMMIT");
    process.stdout.write(`Migration ${migrationVersion} already applied.\\n`);
  } else {
    await client.query(migrationSql);
    await client.query("INSERT INTO nexora_schema_migrations (version) VALUES ($1)", [migrationVersion]);
    await client.query("COMMIT");
    process.stdout.write(`Applied migration ${migrationVersion}.\\n`);
  }
} catch (error) {
  await client.query("ROLLBACK");
  throw error;
} finally {
  client.release();
  await pool.end();
}
