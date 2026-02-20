import nodemailer from "nodemailer";
import "dotenv/config";

const smtpHost = process.env.SMTP_HOST;
const smtpPort = Number(process.env.SMTP_PORT || 587);
const smtpUser = process.env.SMTP_USER;
const smtpPass = process.env.SMTP_PASS;
const smtpFrom = process.env.SMTP_FROM || smtpUser || "no-reply@example.com";

// Optional: enable debug logs only when you want
const DEBUG_SMTP = process.env.DEBUG_SMTP === "true";

if (DEBUG_SMTP) {
  console.log("SMTP_HOST=", smtpHost);
  console.log("SMTP_PORT=", smtpPort);
  console.log("SMTP_USER=", smtpUser);
  console.log("SMTP_PASS=", smtpPass ? "(set)" : "(missing)");
}

if (!smtpHost || !smtpPort) {
  throw new Error("SMTP config missing: SMTP_HOST/SMTP_PORT");
}

const transporter = nodemailer.createTransport({
  host: smtpHost,
  port: smtpPort,
  secure: smtpPort === 465, // true for 465, false for 587/STARTTLS
  auth: smtpUser
    ? {
        user: smtpUser,
        pass: smtpPass,
      }
    : undefined,
});

// Optional but helpful on deploy: verify connection at startup
export async function verifySmtp() {
  if (!smtpUser) {
    // if you intentionally run without SMTP (e.g., dev), skip verify
    return;
  }
  try {
    await transporter.verify();
    if (DEBUG_SMTP) console.log("SMTP verified OK");
  } catch (err) {
    console.error("SMTP verify failed:", err?.message || err);
    // Don’t throw here if you still want server to run; up to you:
    // throw err;
  }
}

export async function sendOtpMail(toEmail, otp) {
  if (!smtpUser || !smtpPass) {
    throw new Error("SMTP auth missing: SMTP_USER/SMTP_PASS");
  }

  try {
    await transporter.sendMail({
      from: smtpFrom,
      to: toEmail,
      subject: "Your EmergencyResponseApp OTP",
      text: `Your OTP is ${otp}. It expires in 5 minutes.`,
      html: `<p>Your OTP is <b>${otp}</b>. It expires in 5 minutes.</p>`,
    });
  } catch (err) {
    // Better logging for Render
    console.error("sendOtpMail failed:", err?.message || err);
    throw err;
  }
}