<?php

declare(strict_types=1);

/**
 * utils.php
 * JSON-only helpers for the API.
 */

function api_bootstrap(): void {
    // Don't leak warnings/notices as HTML.
    @ini_set('display_errors', '0');
    @ini_set('html_errors', '0');

    // Clear any accidental output buffering so responses are pure JSON.
    while (ob_get_level() > 0) {
        ob_end_clean();
    }

    // Always return JSON; prevent caching and MIME sniffing.
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');
    header('X-Content-Type-Options: nosniff');

    // Convert PHP errors to exceptions so we can return JSON instead of HTML warnings.
    set_error_handler(static function (int $severity, string $message, string $file, int $line): bool {
        if (!(error_reporting() & $severity)) {
            return false;
        }
        throw new ErrorException($message, 0, $severity, $file, $line);
    });

    set_exception_handler(static function (Throwable $e): void {
        error_log('[api] ' . $e);
        json_response(['status' => 'error', 'message' => 'Internal server error'], 500);
    });

    // Catch fatal errors (parse errors won't reach here, but runtime fatals will).
    register_shutdown_function(static function (): void {
        $err = error_get_last();
        if (!$err) {
            return;
        }

        $fatalTypes = [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR, E_USER_ERROR];
        if (!in_array($err['type'] ?? 0, $fatalTypes, true)) {
            return;
        }

        // If headers already sent, we still try to output JSON (best effort).
        if (!headers_sent()) {
            header('Content-Type: application/json; charset=utf-8');
        }

        error_log('[api:fatal] ' . ($err['message'] ?? 'fatal error'));
        echo json_encode(['status' => 'error', 'message' => 'Internal server error'], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    });
}

function json_response(array $payload, int $statusCode = 200): void {
    http_response_code($statusCode);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/** @return array<string,mixed> */
function read_json_body(): array {
    $raw = file_get_contents('php://input');
    if ($raw === false || trim($raw) === '') {
        return [];
    }

    try {
        /** @var mixed $decoded */
        $decoded = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
    } catch (JsonException $e) {
        json_response(['status' => 'error', 'message' => 'Invalid JSON'], 400);
    }

    if (!is_array($decoded)) {
        json_response(['status' => 'error', 'message' => 'JSON body must be an object'], 400);
    }

    /** @var array<string,mixed> $decoded */
    return $decoded;
}

function normalize_email(mixed $email): string {
    return is_string($email) ? strtolower(trim($email)) : '';
}

function is_valid_email(string $email): bool {
    return $email !== '' && strlen($email) <= 254 && (bool) filter_var($email, FILTER_VALIDATE_EMAIL);
}

function generate_otp6(): string {
    return str_pad((string) random_int(0, 999999), 6, '0', STR_PAD_LEFT);
}
