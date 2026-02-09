<?php

declare(strict_types=1);

require_once __DIR__ . '/utils.php';
api_bootstrap();

require_once __DIR__ . '/connect.php';

// Only allow POST to avoid accidental execution via browser caches, etc.
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    json_response(['status' => 'error', 'message' => 'Method not allowed'], 405);
}

// NOTE: connect.php already connects to the DB_NAME. If the DB doesn't exist yet,
// this endpoint is mainly useful when DB_NAME already exists and we only need tables.

try {
    $conn->query(
        "CREATE TABLE IF NOT EXISTS users (
            id INT AUTO_INCREMENT PRIMARY KEY,
            email VARCHAR(255) NOT NULL UNIQUE,
            password VARCHAR(255) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    );

    $conn->query(
        "CREATE TABLE IF NOT EXISTS otps (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            otp_code VARCHAR(10) NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_otps_user_id (user_id),
            CONSTRAINT fk_otps_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    );

    json_response(['status' => 'success', 'message' => 'Database initialized']);
} catch (Throwable $e) {
    error_log((string) $e);
    json_response(['status' => 'error', 'message' => 'Initialization failed'], 500);
}
