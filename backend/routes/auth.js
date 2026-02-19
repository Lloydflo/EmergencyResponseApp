import express from 'express';
import crypto from 'crypto';
import { randomInt } from 'crypto';
import pool from '../db.js';
import { sendOtpMail } from '../mail.js';

const router = express.Router();

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const OTP_EXPIRY_MINUTES = 5;

function normalizeEmail(email) {
  return String(email || '').trim().toLowerCase();
}

function hashOtp(otp) {
  return crypto.createHash('sha256').update(String(otp)).digest('hex');
}

function generateOtp() {
  return String(randomInt(0, 1000000)).padStart(6, '0');
}

async function findUserByEmail(email) {
  const [rows] = await pool.execute(
    'SELECT id, name, email FROM users WHERE email = ? LIMIT 1',
    [email]
  );
  return Array.isArray(rows) && rows.length > 0 ? rows[0] : null;
}

// POST /api/login - email-only account check
router.post('/login', async (req, res) => {
  try {
    const email = normalizeEmail(req.body?.email);
    if (!email) {
      return res.status(400).json({ success: false, message: 'Email required' });
    }
    if (!EMAIL_REGEX.test(email)) {
      return res.status(400).json({ success: false, message: 'Invalid email format' });
    }

    const user = await findUserByEmail(email);
    if (!user) {
      return res.status(401).json({ success: false, message: 'Account not found' });
    }

    return res.json({
      success: true,
      message: 'Login ok',
      user: { id: user.id, name: user.name, email: user.email }
    });
  } catch (err) {
    console.error('Login error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
});

// POST /api/send-otp
router.post('/send-otp', async (req, res) => {
  try {
    const email = normalizeEmail(req.body?.email);
    if (!email) {
      return res.status(400).json({ success: false, message: 'Email required' });
    }
    if (!EMAIL_REGEX.test(email)) {
      return res.status(400).json({ success: false, message: 'Invalid email format' });
    }

    const user = await findUserByEmail(email);
    if (!user) {
      return res.status(401).json({ success: false, message: 'Account not found' });
    }

    const otp = generateOtp();
    const otpHash = hashOtp(otp);

    await pool.execute(
      `INSERT INTO user_otps (user_id, email, otp_hash, expires_at)
       VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))`,
      [user.id, email, otpHash, OTP_EXPIRY_MINUTES]
    );

    await sendOtpMail(email, otp);

    return res.json({ success: true, message: 'OTP sent' });
  } catch (err) {
    console.error('Send OTP error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
});

// POST /api/verify-otp
router.post('/verify-otp', async (req, res) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const otp = String(req.body?.otp || '').trim();

    if (!email || !otp) {
      return res.status(400).json({ success: false, message: 'Email and OTP required' });
    }
    if (!EMAIL_REGEX.test(email)) {
      return res.status(400).json({ success: false, message: 'Invalid email format' });
    }
    if (!/^\d{6}$/.test(otp)) {
      return res.status(400).json({ success: false, message: 'Invalid OTP format' });
    }

    const user = await findUserByEmail(email);
    if (!user) {
      return res.status(401).json({ success: false, message: 'Account not found' });
    }

    const [otpRows] = await pool.execute(
      `SELECT id, otp_hash, expires_at
       FROM user_otps
       WHERE email = ? AND used_at IS NULL AND expires_at > NOW()
       ORDER BY created_at DESC
       LIMIT 1`,
      [email]
    );

    if (!Array.isArray(otpRows) || otpRows.length === 0) {
      return res.status(401).json({ success: false, message: 'OTP expired or not found' });
    }

    const otpRow = otpRows[0];
    const incomingHash = hashOtp(otp);
    if (incomingHash !== otpRow.otp_hash) {
      return res.status(401).json({ success: false, message: 'Invalid OTP' });
    }

    await pool.execute(
      'UPDATE user_otps SET used_at = NOW() WHERE id = ? AND used_at IS NULL',
      [otpRow.id]
    );

    return res.json({
      success: true,
      message: 'Verified',
      user: { id: user.id, name: user.name, email: user.email }
    });
  } catch (err) {
    console.error('Verify OTP error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
});

export default router;
