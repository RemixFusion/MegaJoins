<?php
// PDO connection helper that reuses the plugin's MySQL credentials.

declare(strict_types=1);

require_once __DIR__ . '/config.php';


/**
 * @return PDO
 */
function get_pdo(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }

    $mysql = get_mysql_config();
    $host = (string)($mysql['host'] ?? 'localhost');
    $port = (int)($mysql['port'] ?? 3306);
    $database = (string)($mysql['database'] ?? 'megajoins');
    $username = (string)($mysql['username'] ?? 'root');
    $password = (string)($mysql['password'] ?? '');

    $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $host, $port, $database);

    $propertyPairs = [];
    if (!empty($mysql['allow-public-key-retrieval'])) {
        $propertyPairs[] = 'allowPublicKeyRetrieval=true';
    }

    if (!empty($mysql['properties']) && is_array($mysql['properties'])) {
        foreach ($mysql['properties'] as $key => $value) {
            $propertyPairs[] = sprintf('%s=%s', $key, $value);
        }
    }

    if ($propertyPairs) {
        $dsn .= ';' . implode(';', $propertyPairs);
    }

    $options = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ];

    if (!empty($mysql['use-ssl'])) {
        // When SSL is requested we require the client to negotiate TLS. Servers without
        // certificates should disable SSL in the plugin config.
        if (defined('PDO::MYSQL_ATTR_SSL_MODE') && defined('PDO::MYSQL_ATTR_SSL_MODE_REQUIRED')) {
            $options[PDO::MYSQL_ATTR_SSL_MODE] = PDO::MYSQL_ATTR_SSL_MODE_REQUIRED;
        }
        if (defined('PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT')) {
            $options[PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT] = false;
        }
    }

    try {
        $pdo = new PDO($dsn, $username, $password, $options);
    } catch (PDOException $e) {
        throw new RuntimeException('Failed to connect to the MegaJoins database: ' . $e->getMessage(), 0, $e);
    }

    return $pdo;
}

