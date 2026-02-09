<?php

declare(strict_types=1);

require_once __DIR__ . '/utils.php';
api_bootstrap();

require_once __DIR__ . '/connect.php';

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
$otp = is_string($body['otp_code'] ?? null) ? trim($body['otp_code']) : '';

if (!is_valid_email($email)) {
    json_response(['status' => 'error', 'message' => 'Invalid email'], 400);
}

if (!preg_match('/^[0-9]{6}$/', $otp)) {
    json_response(['status' => 'error', 'message' => 'Invalid OTP'], 400);
}

// Find user id
$stmt = $conn->prepare('SELECT id FROM users WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) LIMIT 1');
$stmt->bind_param('s', $email);
$stmt->execute();
$result = $stmt->get_result();
$user = $result->fetch_assoc();
$stmt->close();

if (!$user) {
    json_response(['status' => 'error', 'message' => 'Account not found'], 200);
}

$userId = (int) $user['id'];

// Check OTP (valid within last 5 minutes)
$check = $conn->prepare(
    'SELECT id FROM otps WHERE user_id = ? AND otp_code = ? AND created_at >= (NOW() - INTERVAL 5 MINUTE) ORDER BY created_at DESC LIMIT 1'
);
$check->bind_param('is', $userId, $otp);
$check->execute();
$otpResult = $check->get_result();
$otpRow = $otpResult->fetch_assoc();
$check->close();

if (!$otpRow) {
    json_response(['status' => 'error', 'message' => 'Invalid OTP'], 200);
}

json_response(['status' => 'success', 'message' => 'OTP verified'], 200);
