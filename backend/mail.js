import nodemailer from 'nodemailer';

import 'dotenv/config';

console.log("SMTP_HOST=", process.env.SMTP_HOST);
console.log("SMTP_PORT=", process.env.SMTP_PORT);
console.log("SMTP_USER=", process.env.SMTP_USER);
console.log("SMTP_PASS=", process.env.SMTP_PASS ? "(set)" : "(missing)");

const smtpPort = Number(process.env.SMTP_PORT || 587);

const transporter = nodemailer.createTransport({
  host: process.env.SMTP_HOST,
  port: smtpPort,
  secure: smtpPort === 465,
  auth: process.env.SMTP_USER
    ? {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS
      }
    : undefined
});

export async function sendOtpMail(toEmail, otp) {
  const from = process.env.SMTP_FROM || process.env.SMTP_USER || 'no-reply@example.com';

  await transporter.sendMail({
    from:  from,
    to: toEmail,
    subject: 'Your EmergencyResponseApp OTP',
    text: `Your OTP is ${otp}. It expires in 5 minutes.`,
    html: `<p>Your OTP is <b>${otp}</b>. It expires in 5 minutes.</p>`
  });
}
