package com.megacraft.megajoins;

import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public final class MegaJoins extends Plugin implements Listener {

    private final Map<String, Integer> currentCounts = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerHost = new ConcurrentHashMap<>();
    private SQLite db;

    private ExecutorService dbExec;

    @Override
    public void onEnable() {
        try {
            db = new SQLite(getDataFolder(), "data.db");
            db.init();
        } catch (Exception e) {
            getLogger().severe("Failed to init SQLite: " + e.getMessage());
            e.printStackTrace();
        }
        dbExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MegaJoins-DB");
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
        if (dbExec != null) {
            dbExec.shutdown();
            try { dbExec.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
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
        String offlineUuidTrim = com.megacraft.megajoins.IdUtil.offlineUuidTrimmed(name);

        playerHost.put(onlineUuid, host);
        currentCounts.merge(host, 1, Integer::sum);

        // capture effectively-final copies for lambda
        final String fHost = host;
        final String fUuid = offlineUuidTrim;
        final String fName = name;

        if (dbExec != null) {
            dbExec.execute(() -> {
                try {
                    db.logJoinSync(fHost, fUuid, fName);
                } catch (Exception e) {
                    getLogger().warning("Failed to log join: " + e.getMessage());
                }
            });
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

    public SQLite getDb() { return db; }
}
