<?php

declare(strict_types=1);

require_once __DIR__ . '/utils.php';
api_bootstrap();

require_once __DIR__ . '/connect.php';

// CORS for development (same Wi‑Fi testing)
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    json_response(['status' => 'error', 'message' => 'Method not allowed'], 405);
}

$body = read_json_body();
$email = normalize_email($body['email'] ?? null);

if (!is_valid_email($email)) {
    json_response(['status' => 'error', 'message' => 'Invalid email'], 400);
}

// Check if email exists
$stmt = $conn->prepare('SELECT id FROM users WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) LIMIT 1');
$stmt->bind_param('s', $email);
$stmt->execute();
$result = $stmt->get_result();
$user = $result->fetch_assoc();
$stmt->close();

if (!$user) {
    json_response(['status' => 'error', 'message' => 'Account not found'], 200);
}

json_response(['status' => 'success', 'message' => 'Account found', 'user_id' => (int) $user['id']], 200);
