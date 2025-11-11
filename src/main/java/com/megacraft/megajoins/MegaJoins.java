package com.megacraft.megajoins;

import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public final class MegaJoins extends Plugin implements Listener {

    private final Map<String, Integer> currentCounts = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerHost = new ConcurrentHashMap<>();
    private JoinStorage db;

    // Async executors
    private ExecutorService dbExec;
    private ExecutorService lookupExec;

    @Override
    public void onEnable() {
        Configuration config = loadConfig();
        Configuration storage = config.getSection("storage");
        try {
            db = createStorage(storage);
            db.init();
        } catch (Exception e) {
            getLogger().severe("Failed to init storage: " + e.getMessage());
            e.printStackTrace();
        }
        dbExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MegaJoins-DB");
            t.setDaemon(true);
            return t;
        });
        lookupExec = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "MegaJoins-LOOKUP");
            t.setDaemon(true);
            return t;
        });

        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new MegaJoinsCommand(this));
        getLogger().info("MegaJoins enabled.");
    }

    @Override
    public void onDisable() {
        currentCounts.clear();
        playerHost.clear();
        if (db != null) {
            db.shutdown();
        }
        if (dbExec != null) {
            dbExec.shutdown();
            try { dbExec.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
        if (lookupExec != null) {
            lookupExec.shutdown();
            try { lookupExec.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        PendingConnection conn = event.getPlayer().getPendingConnection();
        InetSocketAddress vhost = conn.getVirtualHost();
        String host = (vhost != null ? vhost.getHostString() : "unknown");
        if (host == null || host.isEmpty()) host = "unknown";
        host = host.toLowerCase();

        UUID onlineUuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        String onlineUuidTrim = IdUtil.normalizeUuidTrimmed(onlineUuid.toString());

        playerHost.put(onlineUuid, host);
        currentCounts.merge(host, 1, Integer::sum);

        final String fHost = host;
        final String fUuid = onlineUuidTrim;
        final String fName = name;
        final JoinStorage storage = db;
        if (storage == null) {
            return;
        }

        Runnable task = () -> {
            try {
                storage.logJoinSync(fHost, fUuid, fName);
            } catch (Exception e) {
                getLogger().warning("Failed to log join: " + e.getMessage());
            }
        };

        ExecutorService executor = dbExec;
        if (executor != null && !executor.isShutdown()) {
            executor.execute(task);
        } else {
            getProxy().getScheduler().runAsync(this, task);
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String host = playerHost.remove(uuid);
        if (host == null) host = "unknown";
        currentCounts.compute(host, (h, c) -> (c == null || c <= 1) ? null : c - 1);
    }

    public Map<String,Integer> getCurrentCounts() {
        return new ConcurrentHashMap<>(currentCounts);
    }

    public JoinStorage getDb() { return db; }

    public ExecutorService getLookupExec() { return lookupExec; }

    private Configuration loadConfig() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().warning("Unable to create data folder: " + dataFolder.getAbsolutePath());
        }
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                } else {
                    getLogger().warning("Default config.yml not found in jar.");
                }
            } catch (IOException e) {
                getLogger().warning("Failed to save default config: " + e.getMessage());
            }
        }
        try {
            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            getLogger().severe("Failed to load config.yml: " + e.getMessage());
            return new Configuration();
        }
    }

    private JoinStorage createStorage(Configuration storage) throws Exception {
        if (storage == null) {
            return new SQLite(getDataFolder(), "data.db");
        }
        String type = storage.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        switch (type) {
            case "mysql": {
                Configuration mysql = storage.getSection("mysql");
                if (mysql == null) {
                    throw new IllegalStateException("storage.mysql section missing");
                }
                String host = mysql.getString("host", "localhost");
                int port = mysql.getInt("port", 3306);
                String database = mysql.getString("database", "megajoins");
                String user = mysql.getString("username", "root");
                String pass = mysql.getString("password", "");
                boolean useSsl = mysql.getBoolean("use-ssl", true);
                boolean allowPublicKey = mysql.getBoolean("allow-public-key-retrieval", false);
                int maxPool = 5;
                Configuration pool = mysql.getSection("pool");
                if (pool != null) {
                    maxPool = pool.getInt("max-size", maxPool);
                }
                Map<String, String> props = new HashMap<>();
                Configuration propsSection = mysql.getSection("properties");
                if (propsSection != null) {
                    for (String key : propsSection.getKeys()) {
                        props.put(key, String.valueOf(propsSection.get(key)));
                    }
                }
                getLogger().info("Using MySQL storage at " + host + ":" + port + "/" + database);
                return new MySQL(host, port, database, user, pass, useSsl, allowPublicKey, maxPool, props);
            }
            case "sqlite":
            default: {
                Configuration sqlite = storage.getSection("sqlite");
                String fileName = "data.db";
                if (sqlite != null) {
                    fileName = sqlite.getString("file", fileName);
                }
                getLogger().info("Using SQLite storage at " + fileName);
                return new SQLite(getDataFolder(), fileName);
            }
        }
    }
}
