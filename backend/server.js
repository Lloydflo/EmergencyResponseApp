import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import authRouter from './routes/auth.js';
import pool from './db.js';

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '256kb' }));

// CORS: allow all origins for testing on LAN; in production restrict origins
app.use(cors());

const PORT = Number(process.env.PORT || 3000);
// bind to all interfaces by default so the server is reachable from other devices on the LAN
const HOST = process.env.HOST || '0.0.0.0';

app.get('/health', (req, res) => res.json({ ok: true }));

app.get('/health/db', async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT 1 AS ok');
    res.json({ ok: true, db: rows[0] });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message || String(e) });
  }
});

// mount auth routes under /api
app.use('/api', authRouter);

// global 404 -> JSON
app.use((req, res) => res.status(404).json({ success: false, message: 'Not found' }));

// global error handler
app.use((err, req, res, next) => {
  console.error('Unhandled error:', err);
  res.status(500).json({ success: false, message: 'Server error' });
});

async function start() {
  // quick DB check
  try {
    await pool.query('SELECT 1');
  } catch (e) {
    console.error('DB connectivity check failed:', e.message || e);
    // still start server so health endpoint can be used to debug; but you may choose to exit instead
    // process.exitCode = 1; return;
  }

  app.listen(PORT, HOST, () => {
    console.log(`Server listening on http://${HOST}:${PORT}`);
  });
}

start().catch(e => {
  console.error('Startup failed:', e);
  process.exitCode = 1;
});
