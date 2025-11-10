<?php
// Utility for loading the plugin's configuration and exposing database settings
// to the analytics frontend. The YAML structure is simple enough that we can
// parse it without external dependencies.

declare(strict_types=1);

const CONFIG_FALLBACKS = [
    '/config.yml',
    '/../config.yml',
    '/../plugins/MegaJoins/config.yml',
    '/../src/main/resources/config.yml',
];

/**
 * Resolve the actual configuration path.
 */
function resolve_config_path(): string
{
    $env = getenv('MEGAJOINS_CONFIG');
    if ($env && is_string($env) && $env !== '' && is_file($env)) {
        return $env;
    }

    foreach (CONFIG_FALLBACKS as $relative) {
        $candidate = realpath(__DIR__ . $relative);
        if ($candidate !== false && is_file($candidate)) {
            return $candidate;
        }
    }

    throw new RuntimeException('MegaJoins config.yml was not found. Set MEGAJOINS_CONFIG to the file path.');
}

/**
 * Load the plugin configuration from config.yml and return it as a nested
 * associative array.
 *
 * @return array<string, mixed>
 */
function load_plugin_config(): array
{
    static $config = null;
    if ($config !== null) {
        return $config;
    }

    $path = resolve_config_path();
    $contents = file($path, FILE_IGNORE_NEW_LINES);
    if ($contents === false) {
        throw new RuntimeException('Failed to read plugin configuration.');
    }

    $root = [];
    $stack = [&$root];
    $indentStack = [0];

    foreach ($contents as $rawLine) {
        if ($rawLine === '' || preg_match('/^\s*#/', $rawLine)) {
            continue; // Skip comments and empty lines
        }

        $indent = strspn($rawLine, ' ');
        $line = trim($rawLine);
        if ($line === '') {
            continue;
        }

        while (count($indentStack) > 1 && $indent < end($indentStack)) {
            array_pop($indentStack);
            array_pop($stack);
        }

        if (!str_contains($line, ':')) {
            continue; // Ignore malformed lines gracefully
        }

        [$key, $value] = array_map('trim', explode(':', $line, 2));

        if ($value === '') {
            $stack[count($stack) - 1][$key] = [];
            $stack[] = &$stack[count($stack) - 1][$key];
            $indentStack[] = $indent + 2;
            continue;
        }

        $stack[count($stack) - 1][$key] = interpret_scalar($value);
    }

    $config = $root;
    return $config;
}

/**
 * Convert the scalar string value from the YAML file into a native PHP type.
 *
 * @param string $value
 * @return mixed
 */
function interpret_scalar(string $value)
{
    $value = trim($value, "\t\r\n");

    if ($value === 'true' || $value === 'false') {
        return $value === 'true';
    }

    if (strlen($value) >= 2) {
        $first = $value[0];
        $last = $value[strlen($value) - 1];
        if (($first === "\"" && $last === "\"") || ($first === "'" && $last === "'")) {
            return stripcslashes(substr($value, 1, -1));
        }
    }

    if (is_numeric($value)) {
        return str_contains($value, '.') ? (float)$value : (int)$value;
    }

    return $value;
}

/**
 * Retrieve the MySQL configuration from the plugin config.
 *
 * @return array<string, mixed>
 */
function get_mysql_config(): array
{
    $config = load_plugin_config();
    if (!isset($config['storage']['type'])) {
        throw new RuntimeException('Storage type not configured in config.yml.');
    }

    $type = strtolower((string)$config['storage']['type']);
    if ($type !== 'mysql') {
        throw new RuntimeException('The plugin is not configured for MySQL storage.');
    }

    if (!isset($config['storage']['mysql']) || !is_array($config['storage']['mysql'])) {
        throw new RuntimeException('MySQL settings are missing from config.yml.');
    }

    return $config['storage']['mysql'];
}

