# EmergencyResponseApp Backend (Node + MySQL)

Minimal backend that connects to MySQL and exposes JSON-only health endpoints.

## Setup

1) Copy env file:

```powershell
Copy-Item .env.example .env
```

2) Install dependencies:

```powershell
npm install
```

3) Start server:

```powershell
npm start
```

## Endpoints

- `GET /` -> basic JSON response
- `GET /db-health` -> checks MySQL connectivity with `SELECT 1`

## Test DB connection

```powershell
npm run test:db
```
