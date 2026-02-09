<?php

declare(strict_types=1);

require_once __DIR__ . '/utils.php';
api_bootstrap();

require_once __DIR__ . '/connect.php';

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    json_response(['status' => 'error', 'message' => 'Method not allowed'], 405);
}

// Very small diagnostics endpoint (safe to expose on LAN during dev)
$usersCount = 0;
$otpsCount = 0;

$usersRes = $conn->query('SELECT COUNT(*) AS c FROM users');
if ($usersRes) {
    $row = $usersRes->fetch_assoc();
    $usersCount = (int)($row['c'] ?? 0);
}

$otpsRes = $conn->query('SELECT COUNT(*) AS c FROM otps');
if ($otpsRes) {
    $row = $otpsRes->fetch_assoc();
    $otpsCount = (int)($row['c'] ?? 0);
}

json_response([
    'status' => 'success',
    'message' => 'OK',
    'db' => [
        'name' => getenv('DB_NAME') ?: 'emergency_app',
        'users' => $usersCount,
        'otps' => $otpsCount,
    ],
    'server_time' => gmdate('c'),
]);
