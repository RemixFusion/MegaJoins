package com.megacraft.megajoins;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MySQL implements JoinStorage {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Properties dataSourceProperties;
    private final int maxPoolSize;
    private HikariDataSource dataSource;

    public MySQL(String host, int port, String database, String username, String password, boolean useSsl, boolean allowPublicKeyRetrieval, int maxPoolSize, Map<String, String> properties) {
        this.username = username;
        this.password = password;
        StringBuilder url = new StringBuilder();
        url.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);
        url.append("?useSSL=").append(useSsl);
        url.append("&serverTimezone=UTC");
        if (allowPublicKeyRetrieval) {
            url.append("&allowPublicKeyRetrieval=true");
        }
        this.jdbcUrl = url.toString();
        this.dataSourceProperties = new Properties();
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                this.dataSourceProperties.setProperty(entry.getKey(), entry.getValue());
            }
        }
        this.maxPoolSize = maxPoolSize;
    }

    @Override
    public void init() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("MegaJoins-MySQL");
        config.setMaximumPoolSize(Math.max(1, maxPoolSize));
        for (String key : dataSourceProperties.stringPropertyNames()) {
            config.addDataSourceProperty(key, dataSourceProperties.getProperty(key));
        }
        config.setConnectionTestQuery("SELECT 1");
        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS joins (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "hostname VARCHAR(255) NOT NULL," +
                    "uuid CHAR(32) NOT NULL," +
                    "player_name VARCHAR(64) NOT NULL," +
                    "ts BIGINT NOT NULL," +
                    "INDEX idx_joins_ts (ts)," +
                    "INDEX idx_joins_host_ts (hostname, ts)," +
                    "INDEX idx_joins_uuid_ts (uuid, ts)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public void logJoinSync(String hostname, String uuidTrimLower, String playerName) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO joins(hostname, uuid, player_name, ts) VALUES (?,?,?,?)")) {
            ps.setString(1, hostname);
            ps.setString(2, uuidTrimLower);
            ps.setString(3, playerName);
            ps.setLong(4, System.currentTimeMillis() / 1000);
            ps.executeUpdate();
        }
    }

    @Override
    public Map<String, Integer> queryCountsSince(long start) throws Exception {
        final String sql = "SELECT hostname, COUNT(*) AS c FROM joins WHERE ts >= ? GROUP BY hostname";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Integer> out = new HashMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                return out;
            }
        }
    }

    @Override
    public Map<String, Integer> queryUniqueCountsSince(long start) throws Exception {
        final String sql = "SELECT hostname, COUNT(DISTINCT uuid) AS c FROM joins WHERE ts >= ? GROUP BY hostname";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Integer> out = new HashMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                return out;
            }
        }
    }

    @Override
    public Map<String, Integer> queryByUuidSince(String uuidTrimLower, long start) throws Exception {
        final String sql = "SELECT hostname, COUNT(*) AS c FROM joins WHERE uuid = ? AND ts >= ? GROUP BY hostname";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidTrimLower);
            ps.setLong(2, start);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Integer> out = new HashMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                return out;
            }
        }
    }

    @Override
    public Map<String, Integer> queryByUuidPrefixSince(String uuidTrimLowerPrefix, long start) throws Exception {
        final String sql = "SELECT hostname, COUNT(*) AS c FROM joins WHERE uuid LIKE ? AND ts >= ? GROUP BY hostname";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidTrimLowerPrefix + "%");
            ps.setLong(2, start);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Integer> out = new HashMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                return out;
            }
        }
    }
}
