import Database from "better-sqlite3";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const dbPath = process.env.SQLITE_PATH || path.join(__dirname, "app.db");
export const db = new Database(dbPath);

// helpers similar to before
export function run(sql, params = []) {
  const stmt = db.prepare(sql);
  const info = stmt.run(params);
  return Promise.resolve({ lastID: info.lastInsertRowid, changes: info.changes });
}

export function get(sql, params = []) {
  const stmt = db.prepare(sql);
  const row = stmt.get(params);
  return Promise.resolve(row || null);
}

export function all(sql, params = []) {
  const stmt = db.prepare(sql);
  const rows = stmt.all(params);
  return Promise.resolve(rows || []);
}