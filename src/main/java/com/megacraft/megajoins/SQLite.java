package com.megacraft.megajoins;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class SQLite implements JoinStorage {

    private Connection conn;
    private final File file;

    public SQLite(File folder, String name) throws Exception {
        if (!folder.exists()) folder.mkdirs();
        this.file = new File(folder, name);
    }

    @Override
    public void init() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS joins (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "hostname TEXT NOT NULL," +
                    "uuid TEXT NOT NULL," +
                    "player_name TEXT NOT NULL," +
                    "ts INTEGER NOT NULL)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_joins_ts ON joins(ts)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_joins_host_ts ON joins(hostname, ts)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_joins_uuid_ts ON joins(uuid, ts)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_joins_player_name_ts ON joins(player_name, ts)");
        }
    }

    @Override
    public void shutdown() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public void logJoinSync(String hostname, String uuidTrimLower, String playerName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO joins(hostname, uuid, player_name, ts) VALUES (?,?,?,?)")) {
            ps.setString(1, hostname);
            ps.setString(2, uuidTrimLower);
            ps.setString(3, playerName);
            ps.setLong(4, System.currentTimeMillis() / 1000);
            ps.executeUpdate();
        }
    }

    @Override
    public Map<String,Integer> queryCountsSince(long start) throws Exception {
        final String sql = "SELECT hostname, COUNT(*) AS c FROM joins WHERE ts >= ? GROUP BY hostname";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String,Integer> out = new HashMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                return out;
            }
        }
    }

    @Override
    public Map<String,Integer> queryUniqueCountsSince(long start) throws Exception {
        final String sql = "SELECT hostname, COUNT(DISTINCT uuid) AS c FROM joins WHERE ts >= ? GROUP BY hostname";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String,Integer> out = new HashMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                return out;
            }
        }
    }

    @Override
    public Map<String,Integer> queryByUuidSince(String uuidTrimLower, long start) throws Exception {
        final String sql = "SELECT hostname, COUNT(*) AS c FROM joins WHERE uuid = ? AND ts >= ? GROUP BY hostname";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidTrimLower);
            ps.setLong(2, start);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String,Integer> out = new HashMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                return out;
            }
        }
    }

    @Override
    public Map<String,Integer> queryByUuidPrefixSince(String uuidTrimLowerPrefix, long start) throws Exception {
        final String sql = "SELECT hostname, COUNT(*) AS c FROM joins WHERE uuid LIKE ? AND ts >= ? GROUP BY hostname";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidTrimLowerPrefix + "%");
            ps.setLong(2, start);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String,Integer> out = new HashMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
                return out;
            }
        }
    }

    @Override
    public String lookupUuidByPlayerName(String playerName) throws Exception {
        final String sql = "SELECT uuid FROM joins WHERE player_name = ? ORDER BY ts DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                return null;
            }
        }
    }
}
