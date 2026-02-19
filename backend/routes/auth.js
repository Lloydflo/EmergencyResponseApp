import express from 'express';
import pool from '../db.js';

const router = express.Router();

// POST /api/login - email-only login (no password)
router.post('/login', async (req, res) => {
  try {
    const { email } = req.body || {};
    if (!email || typeof email !== 'string') {
      return res.status(400).json({ success: false, message: 'Email required' });
    }

    const emailTrim = email.trim();
    if (!/^\S+@\S+\.\S+$/.test(emailTrim)) {
      return res.status(400).json({ success: false, message: 'Invalid email format' });
    }

    const sql = 'SELECT id, name, email FROM users WHERE email = ? LIMIT 1';
    const [rows] = await pool.execute(sql, [emailTrim]);

    if (!Array.isArray(rows) || rows.length === 0) {
      return res.status(401).json({ success: false, message: 'Account not found' });
    }

    const user = rows[0];
    const safeUser = { id: user.id, name: user.name, email: user.email };
    return res.json({ success: true, message: 'Login ok', user: safeUser });
  } catch (err) {
    console.error('Login error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
});

export default router;
