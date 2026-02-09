<?php

declare(strict_types=1);

require_once __DIR__ . '/utils.php';

/**
 * connect.php
 * Shared database connection for all endpoints.
 */

// Content-Type is set by api_bootstrap(); keep this file output-free.

function env(string $key, string $default = ''): string {
    $value = getenv($key);
    if ($value === false) {
        return $default;
    }

    // Some environments set variables to an empty string; treat that as missing.
    if (trim((string) $value) === '') {
        return $default;
    }

    return (string) $value;
}

$DB_HOST = env('DB_HOST', '127.0.0.1');
$DB_PORT = (int) env('DB_PORT', '3306');
$DB_NAME = env('DB_NAME', 'emergency_application');
$DB_USER = env('DB_USER', 'root');
$DB_PASS = env('DB_PASS', 'password123');

mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);

try {
    $conn = new mysqli($DB_HOST, $DB_USER, $DB_PASS, $DB_NAME, $DB_PORT);
    $conn->set_charset('utf8mb4');
} catch (mysqli_sql_exception $e) {
    error_log('DB connection failed: ' . $e->getMessage());

    // Safe diagnostics for local development (does not include password)
    json_response([
        'status' => 'error',
        'message' => 'Database connection failed',
        'diagnostics' => [
            'db_host' => $DB_HOST,
            'db_port' => $DB_PORT,
            'db_name' => $DB_NAME,
            'db_user' => $DB_USER,
            'mysql_errno' => $e->getCode(),
        ],
    ], 500);
}
