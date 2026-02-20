import "dotenv/config";
import express from "express";
import cors from "cors";
import authRouter from "./routes/auth.js";
import { initDb } from "./init_db.js";

const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "256kb" }));
app.use(cors());

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || "0.0.0.0";

app.get("/health", (req, res) => res.json({ ok: true }));

app.use("/api", authRouter);

app.use((req, res) => res.status(404).json({ success: false, message: "Not found" }));

app.use((err, req, res, next) => {
  console.error("Unhandled error:", err);
  res.status(500).json({ success: false, message: "Server error" });
});

async function start() {
  // init sqlite tables
  try {
    await initDb();
  } catch (e) {
    console.error("SQLite init failed:", e?.message || e);
  }

  app.listen(PORT, HOST, () => {
    console.log(`Server listening on http://${HOST}:${PORT}`);
  });
}

start().catch((e) => {
  console.error("Startup failed:", e);
  process.exitCode = 1;
});