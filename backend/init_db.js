import { run } from "./db.js";

export async function initDb() {
  // Users table
  await run(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT,
      email TEXT UNIQUE NOT NULL
    )
  `);

  // OTP table (replacement for user_otps)
  await run(`
    CREATE TABLE IF NOT EXISTS user_otps (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      email TEXT NOT NULL,
      otp_hash TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      expires_at INTEGER NOT NULL,
      used_at INTEGER,
      FOREIGN KEY(user_id) REFERENCES users(id)
    )
  `);

  await run(`CREATE INDEX IF NOT EXISTS idx_user_otps_email ON user_otps(email)`);
}