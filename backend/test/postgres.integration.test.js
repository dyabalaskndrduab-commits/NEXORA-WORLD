import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { randomBytes } from "node:crypto";
import { fileURLToPath } from "node:url";
const databaseUrl = process.env.DATABASE_URL;
test("PostgreSQL accounts survive a backend process restart", { skip: !databaseUrl }, async () => {
  const username = \`nexora_\${randomBytes(5).toString("hex")}\`;
  const password = "nexora-test-password-2026";
  const jwtSecret = "ci-only-secret-with-more-than-thirty-two-characters";
  const port = await getFreePort();
  const baseUrl = \`http://127.0.0.1:\${port}\`;
  let child;
  async function startServer() {
    child = spawn(process.execPath, ["src/server.js"], {
      cwd: fileURLToPath(new URL("..", import.meta.url)),
      env: {
        ...process.env, NODE_ENV: "test", DATABASE_URL: databaseUrl,
        DATABASE_SSL: process.env.DATABASE_SSL || "disable",
        JWT_SECRET: jwtSecret, PORT: String(port), CORS_ORIGIN: "http://localhost"
      },
      stdio: ["ignore", "pipe", "pipe"]
    });
    let output = "";
    child.stdout.setEncoding("utf8").on("data", chunk => { output += chunk; });
    child.stderr.setEncoding("utf8").on("data", chunk => { output += chunk; });
    await waitForHealth(baseUrl, () => output);
  }
  async function stopServer() {
    if (!child || child.exitCode !== null) return;
    const current = child;
    current.kill("SIGTERM");
    let timeout;
    try {
      await Promise.race([
        once(current, "exit"),
        new Promise((_, reject) => {
          timeout = setTimeout(() => reject(new Error("Backend did not stop cleanly")), 5000);
          timeout.unref();
        })
      ]);
    } finally { clearTimeout(timeout); }
    child = null;
  }
  try {
    await startServer();
    const health = await fetch(\`\${baseUrl}/health\`).then(response => response.json());
    assert.equal(health.accountStorage, "postgres");
    const registration = await fetch(\`\${baseUrl}/api/auth/register\`, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ username, password })
    });
    assert.equal(registration.status, 201);
    const registered = await registration.json();
    assert.ok(registered.user.id);
    await stopServer();
    await startServer();
    const login = await fetch(\`\${baseUrl}/api/auth/login\`, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ username, password })
    });
    assert.equal(login.status, 200);
    const loggedIn = await login.json();
    assert.equal(loggedIn.user.id, registered.user.id);
    const duplicate = await fetch(\`\${baseUrl}/api/auth/register\`, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ username, password })
    });
    assert.equal(duplicate.status, 409);
  } finally {
    await stopServer();
    const { Pool } = await import("pg");
    const pool = new Pool({
      connectionString: databaseUrl,
      ...(process.env.DATABASE_SSL === "require" ? { ssl: { rejectUnauthorized: true } } : {})
    });
    await pool.query("DELETE FROM nexora_accounts WHERE username = $1", [username]);
    await pool.end();
  }
});
async function getFreePort() {
  const probe = createServer();
  probe.listen(0, "127.0.0.1");
  await once(probe, "listening");
  const port = probe.address().port;
  await new Promise((resolve, reject) => probe.close(error => error ? reject(error) : resolve()));
  return port;
}
async function waitForHealth(url, readOutput) {
  const deadline = Date.now() + 10_000;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(\`\${url}/health\`);
      if (response.ok) return;
    } catch (error) { lastError = error; }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error(\`Backend did not become healthy. \${readOutput()} \${lastError?.message || ""}\`);
}
