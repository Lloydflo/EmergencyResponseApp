import 'dotenv/config';
import mysql from 'mysql2/promise';

const config = {
  host: process.env.DB_HOST || '127.0.0.1',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASS || '',
  database: process.env.DB_NAME || 'emergency_application'
};

try {
  const conn = await mysql.createConnection(config);
  const [rows] = await conn.query('SELECT 1 AS ok');
  console.log('DB_OK', rows);
  await conn.end();
} catch (e) {
  console.error('DB_FAIL', e?.code || '', e?.message || e);
  process.exitCode = 1;
}
