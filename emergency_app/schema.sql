-- MariaDB / MySQL compatible schema (no VISIBLE keyword)

CREATE DATABASE IF NOT EXISTS emergency_app
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE emergency_app;

CREATE TABLE IF NOT EXISTS users (
  id INT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS otps (
  id INT NOT NULL AUTO_INCREMENT,
  user_id INT NOT NULL,
  otp_code VARCHAR(10) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_otps_user_id (user_id),
  CONSTRAINT fk_otps_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
