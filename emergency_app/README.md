# Emergency Application PHP Backend

This is a simple PHP + MySQL/MariaDB backend you can run locally and call from your Android app over the same Wi‑Fi/LAN.

## Database
- Name: `emergency_app`
- Default MySQL/MariaDB port used by this project: `3307` (override with `DB_PORT` env var)

Tables:
- `users (id INT AI PK, email VARCHAR UNIQUE, password VARCHAR)`
- `otps (id INT AI PK, user_id INT FK->users.id, otp_code VARCHAR, created_at TIMESTAMP)`

You can create the tables by:
- Importing `schema.sql` in phpMyAdmin, OR
- Calling `init_db.php` (creates tables only)

## Endpoints (JSON)

All endpoints accept **POST JSON** and return JSON.

- `POST /login.php` — checks if email exists
  - body: `{ "email": "user@example.com" }`

- `POST /send_otp.php` — checks if email exists, generates OTP, inserts into `otps`
  - body: `{ "email": "user@example.com" }`

- `POST /verify_otp.php` — verify OTP for a user (last 5 minutes)
  - body: `{ "email": "user@example.com", "otp_code": "123456" }`

- `GET /health.php` — quick connectivity check (returns JSON)

## Config

`connect.php` reads environment variables:
- `DB_HOST` (default `127.0.0.1`)
- `DB_PORT` (default `3307`)
- `DB_NAME` (default `emergency_app`)
- `DB_USER` (default `root`)
- `DB_PASS` (default empty)

Optional:
- `RETURN_OTP=true` (development only: includes OTP in the JSON response of `/send_otp.php`)

## MariaDB ERROR 1064 (VISIBLE keyword)

If your SQL script contains the `VISIBLE` keyword (some MySQL 8 scripts do), MariaDB will throw ERROR 1064.
Use `schema.sql` provided here (its MariaDB compatible and does **not** use `VISIBLE`).

## Troubleshooting: "Value <!DOCTYPE ... cannot be converted to JSONObject"

That error means your app received **HTML** (usually a 404 page or PHP error page) instead of JSON.

Fix checklist:
1. Make sure Apache is serving the folder from `C:\xampp\htdocs\emergency_app\`
2. Start Apache + MySQL in XAMPP
3. Verify in a browser on your PC:
   - `http://localhost/emergency_app/health.php`  must return JSON
   - `http://192.168.1.11/emergency_app/health.php`  must return JSON
4. If it still returns HTML, check:
   - `C:\xampp\apache\logs\error.log`
   - `C:\xampp\php\logs\php_error_log`
