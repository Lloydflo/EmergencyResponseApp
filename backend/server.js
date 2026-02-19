import 'dotenv/config';
import express from 'express';
import mysql from 'mysql2/promise';

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '256kb' }));

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '192.168.1.7';

function requireEnv(name, fallback) {
  const value = process.env[name] ?? fallback;
  if (!value || String(value).trim() === '') {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return String(value);
}

const pool = mysql.createPool({
  host: requireEnv('DB_HOST', '127.0.0.1'),
  port: Number(process.env.DB_PORT || 3306),
  user: requireEnv('DB_USER', 'root'),
  password: requireEnv('DB_PASS', ''),
  database: requireEnv('DB_NAME', 'emergency_application'),
  waitForConnections: true,
  connectionLimit: Number(process.env.DB_CONN_LIMIT || 10),
  queueLimit: 0
});

app.get('/', (req, res) => {
  res.json({ ok: true, service: 'backend', now: new Date().toISOString() });
});

app.get('/db-health', async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT 1 AS ok');
    res.json({ ok: true, db: rows?.[0]?.ok === 1 });
  } catch (err) {
    res.status(500).json({ ok: false, message: 'Database connection failed' });
  }
});

app.use((req, res) => res.status(404).json({ ok: false, message: 'Not found' }));

app.use((err, req, res, next) => {
  console.error('[unhandled]', err);
  res.status(500).json({ ok: false, message: 'Internal server error' });
});

async function start() {
  await pool.query('SELECT 1');
  app.listen(PORT, HOST, () => {
    console.log(`Server listening on http://${HOST}:${PORT}`);
  });
}

start().catch((e) => {
  console.error('Startup failed:', e?.message || e);
  process.exitCode = 1;
});
