import express from "express";
import crypto from "crypto";
import { randomInt } from "crypto";
import { get, run } from "../db.js";
import { sendOtpMail } from "../mail.js";

const router = express.Router();

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const OTP_EXPIRY_MINUTES = 5;

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function hashOtp(otp) {
  return crypto.createHash("sha256").update(String(otp)).digest("hex");
}

function generateOtp() {
  return String(randomInt(0, 1000000)).padStart(6, "0");
}

async function findUserByEmail(email) {
  return await get("SELECT id, name, email FROM users WHERE email = ? LIMIT 1", [email]);
}

// POST /api/login - email-only account check
router.post("/login", async (req, res) => {
  try {
    const email = normalizeEmail(req.body?.email);
    if (!email) return res.status(400).json({ success: false, message: "Email required" });
    if (!EMAIL_REGEX.test(email)) return res.status(400).json({ success: false, message: "Invalid email format" });

    const user = await findUserByEmail(email);
    if (!user) return res.status(401).json({ success: false, message: "Account not found" });

    return res.json({ success: true, message: "Login ok", user });
  } catch (err) {
    console.error("Login error:", err);
    return res.status(500).json({ success: false, message: "Server error" });
  }
});

// POST /api/send-otp
router.post("/send-otp", async (req, res) => {
  try {
    const email = normalizeEmail(req.body?.email);
    if (!email) return res.status(400).json({ success: false, message: "Email required" });
    if (!EMAIL_REGEX.test(email)) return res.status(400).json({ success: false, message: "Invalid email format" });

    const user = await findUserByEmail(email);
    if (!user) return res.status(401).json({ success: false, message: "Account not found" });

    const otp = generateOtp();
    const otpHash = hashOtp(otp);

    const now = Date.now();
    const expiresAt = now + OTP_EXPIRY_MINUTES * 60 * 1000;

    await run(
      `INSERT INTO user_otps (user_id, email, otp_hash, created_at, expires_at, used_at)
       VALUES (?, ?, ?, ?, ?, NULL)`,
      [user.id, email, otpHash, now, expiresAt]
    );

    await sendOtpMail(email, otp);

    return res.json({ success: true, message: "OTP sent" });
  } catch (err) {
    console.error("Send OTP error:", err);
    return res.status(500).json({ success: false, message: "Server error" });
  }
});

// POST /api/verify-otp
router.post("/verify-otp", async (req, res) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const otp = String(req.body?.otp || "").trim();

    if (!email || !otp) return res.status(400).json({ success: false, message: "Email and OTP required" });
    if (!EMAIL_REGEX.test(email)) return res.status(400).json({ success: false, message: "Invalid email format" });
    if (!/^\d{6}$/.test(otp)) return res.status(400).json({ success: false, message: "Invalid OTP format" });

    const user = await findUserByEmail(email);
    if (!user) return res.status(401).json({ success: false, message: "Account not found" });

    const now = Date.now();
    const otpRow = await get(
      `SELECT id, otp_hash, expires_at
       FROM user_otps
       WHERE email = ? AND used_at IS NULL AND expires_at > ?
       ORDER BY created_at DESC
       LIMIT 1`,
      [email, now]
    );

    if (!otpRow) return res.status(401).json({ success: false, message: "OTP expired or not found" });

    const incomingHash = hashOtp(otp);
    if (incomingHash !== otpRow.otp_hash) {
      return res.status(401).json({ success: false, message: "Invalid OTP" });
    }

    await run("UPDATE user_otps SET used_at = ? WHERE id = ? AND used_at IS NULL", [now, otpRow.id]);

    return res.json({ success: true, message: "Verified", user });
  } catch (err) {
    console.error("Verify OTP error:", err);
    return res.status(500).json({ success: false, message: "Server error" });
  }
});

export default router;