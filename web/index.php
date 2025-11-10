<?php
declare(strict_types=1);

require_once __DIR__ . '/db.php';

$errors = [];
try {
    $pdo = get_pdo();
} catch (Throwable $e) {
    $pdo = null;
    $errors[] = $e->getMessage();
}

$range = $_GET['range'] ?? '7d';
$rangeOptions = [
    '24h' => 86400,
    '7d' => 604800,
    '30d' => 2592000,
    '90d' => 7776000,
    'all' => null,
];

if (!array_key_exists($range, $rangeOptions)) {
    $range = '7d';
}

$startTs = $rangeOptions[$range] !== null ? time() - (int)$rangeOptions[$range] : null;

$hostnameFilter = isset($_GET['hostname']) ? trim((string)$_GET['hostname']) : '';
$playerFilter = isset($_GET['player']) ? trim((string)$_GET['player']) : '';
$uuidFilter = isset($_GET['uuid']) ? preg_replace('/[^a-fA-F0-9-]/', '', (string)$_GET['uuid']) : '';
$uuidFilter = $uuidFilter !== '' ? strtolower(str_replace('-', '', $uuidFilter)) : '';

$summary = [
    'total' => 0,
    'unique_players' => 0,
    'unique_hosts' => 0,
];
$topHosts = [];
$recentJoins = [];
$hostnameBreakdown = [];
$playerBreakdown = [];
$uuidBreakdown = [];

if ($pdo instanceof PDO) {
    $summary = fetch_summary($pdo, $startTs);
    $topHosts = fetch_top_hosts($pdo, $startTs);
    $recentJoins = fetch_recent_joins($pdo, $startTs);

    if ($hostnameFilter !== '') {
        $hostnameBreakdown = fetch_hostname_details($pdo, $hostnameFilter, $startTs);
    }

    if ($playerFilter !== '') {
        $playerBreakdown = fetch_player_details($pdo, $playerFilter, $startTs);
    }

    if ($uuidFilter !== '') {
        $uuidBreakdown = fetch_uuid_details($pdo, $uuidFilter, $startTs);
    }
}

function fetch_summary(PDO $pdo, ?int $startTs): array
{
    $where = '';
    $params = [];
    if ($startTs !== null) {
        $where = 'WHERE ts >= :start';
        $params[':start'] = $startTs;
    }

    $stmt = $pdo->prepare("SELECT COUNT(*) AS total, COUNT(DISTINCT uuid) AS unique_players, COUNT(DISTINCT hostname) AS unique_hosts FROM joins $where");
    $stmt->execute($params);
    $row = $stmt->fetch();

    return $row ?: ['total' => 0, 'unique_players' => 0, 'unique_hosts' => 0];
}

function fetch_top_hosts(PDO $pdo, ?int $startTs): array
{
    $where = '';
    $params = [];
    if ($startTs !== null) {
        $where = 'WHERE ts >= :start';
        $params[':start'] = $startTs;
    }

    $sql = "SELECT hostname, COUNT(*) AS total, COUNT(DISTINCT uuid) AS unique_players, MAX(ts) AS last_join\n            FROM joins $where\n            GROUP BY hostname\n            ORDER BY total DESC\n            LIMIT 20";
    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    return $stmt->fetchAll();
}

function fetch_recent_joins(PDO $pdo, ?int $startTs): array
{
    $where = '';
    $params = [];
    if ($startTs !== null) {
        $where = 'WHERE ts >= :start';
        $params[':start'] = $startTs;
    }

    $sql = "SELECT hostname, uuid, player_name, ts FROM joins $where ORDER BY ts DESC LIMIT 25";
    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    return $stmt->fetchAll();
}

function fetch_hostname_details(PDO $pdo, string $hostname, ?int $startTs): array
{
    $params = [':hostname' => $hostname];
    $where = 'hostname = :hostname';
    if ($startTs !== null) {
        $where .= ' AND ts >= :start';
        $params[':start'] = $startTs;
    }

    $summarySql = "SELECT COUNT(*) AS total, COUNT(DISTINCT uuid) AS unique_players, MIN(ts) AS first_join, MAX(ts) AS last_join\n                   FROM joins WHERE $where";
    $summaryStmt = $pdo->prepare($summarySql);
    $summaryStmt->execute($params);
    $summary = $summaryStmt->fetch() ?: [];

    $recentSql = "SELECT uuid, player_name, ts FROM joins WHERE $where ORDER BY ts DESC LIMIT 100";
    $recentStmt = $pdo->prepare($recentSql);
    $recentStmt->execute($params);
    $recent = $recentStmt->fetchAll();

    return ['summary' => $summary, 'recent' => $recent];
}

function fetch_player_details(PDO $pdo, string $player, ?int $startTs): array
{
    $params = [':player' => strtolower($player)];
    $where = 'LOWER(player_name) = :player';
    if ($startTs !== null) {
        $where .= ' AND ts >= :start';
        $params[':start'] = $startTs;
    }

    $summarySql = "SELECT COUNT(*) AS total, COUNT(DISTINCT hostname) AS unique_hosts, MIN(ts) AS first_join, MAX(ts) AS last_join\n                   FROM joins WHERE $where";
    $summaryStmt = $pdo->prepare($summarySql);
    $summaryStmt->execute($params);
    $summary = $summaryStmt->fetch() ?: [];

    $recentSql = "SELECT hostname, uuid, ts FROM joins WHERE $where ORDER BY ts DESC LIMIT 100";
    $recentStmt = $pdo->prepare($recentSql);
    $recentStmt->execute($params);
    $recent = $recentStmt->fetchAll();

    return ['summary' => $summary, 'recent' => $recent];
}

function fetch_uuid_details(PDO $pdo, string $uuid, ?int $startTs): array
{
    $params = [':uuid' => $uuid];
    $where = 'uuid = :uuid';
    if ($startTs !== null) {
        $where .= ' AND ts >= :start';
        $params[':start'] = $startTs;
    }

    $summarySql = "SELECT COUNT(*) AS total, COUNT(DISTINCT hostname) AS unique_hosts, MIN(ts) AS first_join, MAX(ts) AS last_join\n                   FROM joins WHERE $where";
    $summaryStmt = $pdo->prepare($summarySql);
    $summaryStmt->execute($params);
    $summary = $summaryStmt->fetch() ?: [];

    $recentSql = "SELECT hostname, player_name, ts FROM joins WHERE $where ORDER BY ts DESC LIMIT 100";
    $recentStmt = $pdo->prepare($recentSql);
    $recentStmt->execute($params);
    $recent = $recentStmt->fetchAll();

    return ['summary' => $summary, 'recent' => $recent];
}

function format_ts(?int $ts): string
{
    if ($ts === null) {
        return '—';
    }
    $dt = new DateTimeImmutable('@' . $ts);
    return $dt->setTimezone(new DateTimeZone(date_default_timezone_get()))->format('Y-m-d H:i:s');
}

function e(?string $value): string
{
    return htmlspecialchars((string)$value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>MegaJoins Analytics</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body {
            font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: #0f172a;
            color: #e2e8f0;
            margin: 0;
            padding: 0;
        }
        header {
            background: #1e293b;
            padding: 24px 16px;
            text-align: center;
            border-bottom: 1px solid #334155;
        }
        h1 {
            margin: 0;
            font-size: 1.8rem;
        }
        main {
            max-width: 1100px;
            margin: 0 auto;
            padding: 24px 16px 48px;
        }
        section {
            margin-bottom: 32px;
            background: #1e293b;
            padding: 20px;
            border-radius: 12px;
            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.35);
        }
        section h2 {
            margin-top: 0;
            font-size: 1.4rem;
            color: #38bdf8;
        }
        .summary-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 16px;
        }
        .stat-card {
            background: #0f172a;
            padding: 18px;
            border-radius: 12px;
            text-align: center;
        }
        .stat-card h3 {
            margin: 0;
            font-size: 1rem;
            color: #94a3b8;
            letter-spacing: 0.02em;
        }
        .stat-card p {
            margin: 12px 0 0;
            font-size: 1.8rem;
            font-weight: 600;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 16px;
        }
        th, td {
            padding: 10px 12px;
            text-align: left;
            border-bottom: 1px solid #334155;
        }
        th {
            color: #e0f2fe;
            background: #0f172a;
        }
        tr:hover td {
            background: rgba(148, 163, 184, 0.08);
        }
        form {
            display: grid;
            gap: 12px;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        }
        label {
            display: flex;
            flex-direction: column;
            font-size: 0.9rem;
            color: #cbd5f5;
        }
        input[type="text"], select {
            margin-top: 6px;
            padding: 10px 12px;
            border-radius: 8px;
            border: 1px solid #334155;
            background: #0f172a;
            color: #e2e8f0;
        }
        input[type="submit"] {
            padding: 12px 20px;
            border-radius: 8px;
            border: none;
            background: linear-gradient(135deg, #38bdf8, #0ea5e9);
            color: #0f172a;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.1s ease-in-out;
        }
        input[type="submit"]:hover {
            transform: translateY(-1px);
        }
        .errors {
            background: #991b1b;
            border: 1px solid #fecaca;
            color: #fee2e2;
            padding: 12px 16px;
            border-radius: 8px;
            margin-bottom: 24px;
        }
        .grid-two {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
            gap: 18px;
        }
        .muted {
            color: #94a3b8;
            font-size: 0.85rem;
        }
    </style>
</head>
<body>
<header>
    <h1>MegaJoins Analytics</h1>
    <p class="muted">Realtime insights into player activity across the network</p>
</header>
<main>
    <?php if ($errors): ?>
        <div class="errors">
            <strong>Unable to connect to the database.</strong>
            <ul>
                <?php foreach ($errors as $error): ?>
                    <li><?= e($error) ?></li>
                <?php endforeach; ?>
            </ul>
        </div>
    <?php endif; ?>

    <section>
        <h2>Overview</h2>
        <form method="get" class="filters">
            <label>
                Time Range
                <select name="range">
                    <?php foreach ($rangeOptions as $key => $_): ?>
                        <option value="<?= e($key) ?>" <?= $key === $range ? 'selected' : '' ?>>
                            <?= e(strtoupper($key)) ?>
                        </option>
                    <?php endforeach; ?>
                </select>
            </label>
            <label>
                Hostname
                <input type="text" name="hostname" placeholder="play.example.com" value="<?= e($hostnameFilter) ?>">
            </label>
            <label>
                Player Name
                <input type="text" name="player" placeholder="Player123" value="<?= e($playerFilter) ?>">
            </label>
            <label>
                UUID
                <input type="text" name="uuid" placeholder="123e4567-e89b-12d3-a456-426614174000" value="<?= e($uuidFilter) ?>">
            </label>
            <div>
                <input type="submit" value="Update View">
            </div>
        </form>
        <div class="summary-grid">
            <div class="stat-card">
                <h3>Total Joins<?= $startTs ? ' (selected range)' : '' ?></h3>
                <p><?= number_format((int)$summary['total']) ?></p>
            </div>
            <div class="stat-card">
                <h3>Unique Players<?= $startTs ? ' (selected range)' : '' ?></h3>
                <p><?= number_format((int)$summary['unique_players']) ?></p>
            </div>
            <div class="stat-card">
                <h3>Active Hostnames<?= $startTs ? ' (selected range)' : '' ?></h3>
                <p><?= number_format((int)$summary['unique_hosts']) ?></p>
            </div>
        </div>
    </section>

    <section>
        <h2>Top Hostnames<?= $startTs ? ' (selected range)' : '' ?></h2>
        <div class="table-wrapper">
            <table>
                <thead>
                <tr>
                    <th>Hostname</th>
                    <th>Total Joins</th>
                    <th>Unique Players</th>
                    <th>Last Join</th>
                </tr>
                </thead>
                <tbody>
                <?php if (!$topHosts): ?>
                    <tr>
                        <td colspan="4" class="muted">No data available.</td>
                    </tr>
                <?php else: ?>
                    <?php foreach ($topHosts as $row): ?>
                        <tr>
                            <td><?= e($row['hostname']) ?></td>
                            <td><?= number_format((int)$row['total']) ?></td>
                            <td><?= number_format((int)$row['unique_players']) ?></td>
                            <td><?= format_ts($row['last_join'] !== null ? (int)$row['last_join'] : null) ?></td>
                        </tr>
                    <?php endforeach; ?>
                <?php endif; ?>
                </tbody>
            </table>
        </div>
    </section>

    <section>
        <h2>Recent Joins<?= $startTs ? ' (selected range)' : '' ?></h2>
        <div class="table-wrapper">
            <table>
                <thead>
                <tr>
                    <th>Timestamp</th>
                    <th>Hostname</th>
                    <th>Player</th>
                    <th>UUID</th>
                </tr>
                </thead>
                <tbody>
                <?php if (!$recentJoins): ?>
                    <tr>
                        <td colspan="4" class="muted">No join records found.</td>
                    </tr>
                <?php else: ?>
                    <?php foreach ($recentJoins as $row): ?>
                        <tr>
                            <td><?= format_ts((int)$row['ts']) ?></td>
                            <td><?= e($row['hostname']) ?></td>
                            <td><?= e($row['player_name']) ?></td>
                            <td><code><?= e($row['uuid']) ?></code></td>
                        </tr>
                    <?php endforeach; ?>
                <?php endif; ?>
                </tbody>
            </table>
        </div>
    </section>

    <?php if ($hostnameFilter !== ''): ?>
        <section>
            <h2>Hostname Details: <?= e($hostnameFilter) ?></h2>
            <?php $hostSummary = $hostnameBreakdown['summary'] ?? []; ?>
            <div class="summary-grid">
                <div class="stat-card">
                    <h3>Total Joins</h3>
                    <p><?= number_format((int)($hostSummary['total'] ?? 0)) ?></p>
                </div>
                <div class="stat-card">
                    <h3>Unique Players</h3>
                    <p><?= number_format((int)($hostSummary['unique_players'] ?? 0)) ?></p>
                </div>
                <div class="stat-card">
                    <h3>First Join</h3>
                    <p><?= format_ts(isset($hostSummary['first_join']) ? (int)$hostSummary['first_join'] : null) ?></p>
                </div>
                <div class="stat-card">
                    <h3>Last Join</h3>
                    <p><?= format_ts(isset($hostSummary['last_join']) ? (int)$hostSummary['last_join'] : null) ?></p>
                </div>
            </div>
            <div class="table-wrapper">
                <table>
                    <thead>
                    <tr>
                        <th>Timestamp</th>
                        <th>Player</th>
                        <th>UUID</th>
                    </tr>
                    </thead>
                    <tbody>
                    <?php if (empty($hostnameBreakdown['recent'])): ?>
                        <tr><td colspan="3" class="muted">No joins recorded for this hostname in the selected range.</td></tr>
                    <?php else: ?>
                        <?php foreach ($hostnameBreakdown['recent'] as $row): ?>
                            <tr>
                                <td><?= format_ts((int)$row['ts']) ?></td>
                                <td><?= e($row['player_name']) ?></td>
                                <td><code><?= e($row['uuid']) ?></code></td>
                            </tr>
                        <?php endforeach; ?>
                    <?php endif; ?>
                    </tbody>
                </table>
            </div>
        </section>
    <?php endif; ?>

    <?php if ($playerFilter !== ''): ?>
        <section>
            <h2>Player Details: <?= e($playerFilter) ?></h2>
            <?php $playerSummary = $playerBreakdown['summary'] ?? []; ?>
            <div class="summary-grid">
                <div class="stat-card">
                    <h3>Total Joins</h3>
                    <p><?= number_format((int)($playerSummary['total'] ?? 0)) ?></p>
                </div>
                <div class="stat-card">
                    <h3>Hostnames Visited</h3>
                    <p><?= number_format((int)($playerSummary['unique_hosts'] ?? 0)) ?></p>
                </div>
                <div class="stat-card">
                    <h3>First Join</h3>
                    <p><?= format_ts(isset($playerSummary['first_join']) ? (int)$playerSummary['first_join'] : null) ?></p>
                </div>
                <div class="stat-card">
                    <h3>Last Join</h3>
                    <p><?= format_ts(isset($playerSummary['last_join']) ? (int)$playerSummary['last_join'] : null) ?></p>
                </div>
            </div>
            <div class="table-wrapper">
                <table>
                    <thead>
                    <tr>
                        <th>Timestamp</th>
                        <th>Hostname</th>
                        <th>UUID</th>
                    </tr>
                    </thead>
                    <tbody>
                    <?php if (empty($playerBreakdown['recent'])): ?>
                        <tr><td colspan="3" class="muted">No joins recorded for this player in the selected range.</td></tr>
                    <?php else: ?>
                        <?php foreach ($playerBreakdown['recent'] as $row): ?>
                            <tr>
                                <td><?= format_ts((int)$row['ts']) ?></td>
                                <td><?= e($row['hostname']) ?></td>
                                <td><code><?= e($row['uuid']) ?></code></td>
                            </tr>
                        <?php endforeach; ?>
                    <?php endif; ?>
                    </tbody>
                </table>
            </div>
        </section>
    <?php endif; ?>

    <?php if ($uuidFilter !== ''): ?>
        <section>
            <h2>UUID Details: <?= e($uuidFilter) ?></h2>
            <?php $uuidSummary = $uuidBreakdown['summary'] ?? []; ?>
            <div class="summary-grid">
                <div class="stat-card">
                    <h3>Total Joins</h3>
                    <p><?= number_format((int)($uuidSummary['total'] ?? 0)) ?></p>
                </div>
                <div class="stat-card">
                    <h3>Hostnames Visited</h3>
                    <p><?= number_format((int)($uuidSummary['unique_hosts'] ?? 0)) ?></p>
                </div>
                <div class="stat-card">
                    <h3>First Join</h3>
                    <p><?= format_ts(isset($uuidSummary['first_join']) ? (int)$uuidSummary['first_join'] : null) ?></p>
                </div>
                <div class="stat-card">
                    <h3>Last Join</h3>
                    <p><?= format_ts(isset($uuidSummary['last_join']) ? (int)$uuidSummary['last_join'] : null) ?></p>
                </div>
            </div>
            <div class="table-wrapper">
                <table>
                    <thead>
                    <tr>
                        <th>Timestamp</th>
                        <th>Hostname</th>
                        <th>Player</th>
                    </tr>
                    </thead>
                    <tbody>
                    <?php if (empty($uuidBreakdown['recent'])): ?>
                        <tr><td colspan="3" class="muted">No joins recorded for this UUID in the selected range.</td></tr>
                    <?php else: ?>
                        <?php foreach ($uuidBreakdown['recent'] as $row): ?>
                            <tr>
                                <td><?= format_ts((int)$row['ts']) ?></td>
                                <td><?= e($row['hostname']) ?></td>
                                <td><?= e($row['player_name']) ?></td>
                            </tr>
                        <?php endforeach; ?>
                    <?php endif; ?>
                    </tbody>
                </table>
            </div>
        </section>
    <?php endif; ?>
</main>
</body>
</html>
