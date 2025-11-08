package com.megacraft.megajoins;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

import java.util.*;
import java.util.stream.Collectors;

public class MegaJoinsCommand extends Command {

    private final MegaJoins plugin;

    public MegaJoinsCommand(MegaJoins plugin) {
        super("megajoins", "megajoins.admin", new String[]{});
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("megajoins.admin")) {
            sender.sendMessage(new TextComponent(ChatColor.RED + "You lack permission: megajoins.admin"));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "help":
                    sendHelp(sender);
                    return;
                case "current": {
                    Map<String,Integer> cur = plugin.getCurrentCounts();
                    sendDomainAndSubdomain(sender, "Current Online", cur, null);
                    return;
                }
                case "all": {
                    Map<String,Integer> counts = plugin.getDb().queryCountsSince(0);
                    sendDomainAndSubdomain(sender, "All-time Joins", counts, null);
                    return;
                }
                case "unique": {
                    if (args.length < 2) {
                        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "Usage: /megajoins unique <range|all>"));
                        return;
                    }
                    String rangeArg = args[1].toLowerCase(Locale.ROOT);
                    long start = rangeArg.equals("all") ? 0 : parseRange(rangeArg, System.currentTimeMillis()/1000);
                    if (start == -1) {
                        sender.sendMessage(new TextComponent(ChatColor.RED + "Invalid range: " + rangeArg));
                        return;
                    }
                    Map<String,Integer> unique = plugin.getDb().queryUniqueCountsSince(start);
                    sendDomainAndSubdomain(sender, "UNIQUE Joins " + (start==0?"(all)":("since "+rangeArg)), unique, null);
                    return;
                }
                case "player": {
                    if (args.length < 2) {
                        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "Usage: /megajoins player <name> [range]"));
                        return;
                    }
                    String name = args[1];
                    long start = 0;
                    if (args.length >= 3) {
                        start = parseRange(args[2].toLowerCase(Locale.ROOT), System.currentTimeMillis()/1000);
                        if (start == -1) {
                            sender.sendMessage(new TextComponent(ChatColor.RED + "Invalid range: " + args[2]));
                            return;
                        }
                    }
                    String uuidTrim = IdUtil.offlineUuidTrimmed(name);
                    Map<String,Integer> data = plugin.getDb().queryByUuidSince(uuidTrim, start);
                    sendDomainAndSubdomain(sender, "Joins for Player " + name + (start==0?" (all)":" ("+args[2]+")"), data, null);
                    return;
                }
                case "uuid": {
                    if (args.length < 2) {
                        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "Usage: /megajoins uuid <uuid|prefix> [range]"));
                        return;
                    }
                    String norm = IdUtil.normalizeUuidTrimmed(args[1]);
                    long start = 0;
                    if (args.length >= 3) {
                        start = parseRange(args[2].toLowerCase(Locale.ROOT), System.currentTimeMillis()/1000);
                        if (start == -1) {
                            sender.sendMessage(new TextComponent(ChatColor.RED + "Invalid range: " + args[2]));
                            return;
                        }
                    }
                    Map<String,Integer> data = plugin.getDb().queryByUuidPrefixSince(norm, start);
                    sendDomainAndSubdomain(sender, "Joins for UUID " + args[1] + (start==0?" (all)":" ("+args[2]+")"), data, null);
                    return;
                }
                case "domain": {
                    if (args.length < 2) {
                        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "Usage: /megajoins domain <domain|sub.domain>"));
                        return;
                    }
                    String host = args[1].toLowerCase(Locale.ROOT);
                    String domain = IdUtil.toDomain(host);

                    Map<String,Integer> allCounts = plugin.getDb().queryCountsSince(0);
                    Map<String,Integer> allUnique = plugin.getDb().queryUniqueCountsSince(0);

                    if (host.equals(domain)) {
                        Map<String,Integer> subsAll = expandSubdomains(allCounts, domain);
                        Map<String,Integer> subsUni = expandSubdomains(allUnique, domain);
                        int totalAll = sum(subsAll);
                        int totalUni = sum(subsUni);
                        sender.sendMessage(new TextComponent(ChatColor.GOLD + "Domain: " + domain));
                        sender.sendMessage(new TextComponent(ChatColor.AQUA + "All-time (domain total): " + ChatColor.GREEN + totalAll));
                        sender.sendMessage(new TextComponent(ChatColor.AQUA + "UNIQUE (domain total): " + ChatColor.GREEN + totalUni));
                        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "Per subdomain (all-time):"));
                        sendSortedMap(sender, subsAll);
                        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "Per subdomain (UNIQUE):"));
                        sendSortedMap(sender, subsUni);
                    } else {
                        int total = allCounts.getOrDefault(host, 0);
                        int uniq = allUnique.getOrDefault(host, 0);
                        sender.sendMessage(new TextComponent(ChatColor.GOLD + "Subdomain: " + host));
                        sender.sendMessage(new TextComponent(ChatColor.AQUA + "All-time: " + ChatColor.GREEN + total));
                        sender.sendMessage(new TextComponent(ChatColor.AQUA + "UNIQUE: " + ChatColor.GREEN + uniq));
                    }
                    return;
                }
                default: {
                    long start = parseRange(sub, System.currentTimeMillis()/1000);
                    if (start == -1) {
                        sender.sendMessage(new TextComponent(ChatColor.RED + "Unknown subcommand or invalid range."));
                        sendHelp(sender);
                        return;
                    }
                    Map<String,Integer> counts = plugin.getDb().queryCountsSince(start);
                    sendDomainAndSubdomain(sender, "Joins since " + sub, counts, null);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(new TextComponent(ChatColor.RED + "Error executing command."));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "MegaJoins Help (V1b)"));
        sender.sendMessage(new TextComponent(ChatColor.YELLOW + "All commands require " + ChatColor.WHITE + "megajoins.admin"));
        sender.sendMessage(new TextComponent(ChatColor.AQUA + "/megajoins current" + ChatColor.GRAY + " — current online by domain and subdomain"));
        sender.sendMessage(new TextComponent(ChatColor.AQUA + "/megajoins all" + ChatColor.GRAY + " — all-time joins by domain and subdomain"));
        sender.sendMessage(new TextComponent(ChatColor.AQUA + "/megajoins <N>[h|d|w|m|y]" + ChatColor.GRAY + " — joins in range by domain and subdomain"));
        sender.sendMessage(new TextComponent(ChatColor.AQUA + "/megajoins unique <N>[h|d|w|m|y]|all" + ChatColor.GRAY + " — UNIQUE joins by domain and subdomain"));
        sender.sendMessage(new TextComponent(ChatColor.AQUA + "/megajoins player <name> [range]" + ChatColor.GRAY + " — joins for a player by domain and subdomain"));
        sender.sendMessage(new TextComponent(ChatColor.AQUA + "/megajoins uuid <uuid|prefix> [range]" + ChatColor.GRAY + " — joins for a UUID by domain and subdomain"));
        sender.sendMessage(new TextComponent(ChatColor.AQUA + "/megajoins domain <domain|sub.domain>" + ChatColor.GRAY + " — domain/subdomain summary (all-time + UNIQUE)"));
    }

    private void sendDomainAndSubdomain(CommandSender sender, String title, Map<String,Integer> counts, String rangeLabel) {
        sender.sendMessage(new TextComponent(ChatColor.GOLD + title + (rangeLabel!=null?(" ("+rangeLabel+")"):"") + ":"));
        if (counts == null || counts.isEmpty()) {
            sender.sendMessage(new TextComponent(ChatColor.GRAY + "  (none)"));
            return;
        }
        Map<String,Integer> domainTotals = new HashMap<>();
        Map<String,Map<String,Integer>> domainSubs = new HashMap<>();
        for (Map.Entry<String,Integer> e : counts.entrySet()) {
            String host = e.getKey();
            int c = e.getValue();
            String domain = IdUtil.toDomain(host);
            domainTotals.merge(domain, c, Integer::sum);
            domainSubs.computeIfAbsent(domain, k -> new HashMap<>()).put(host, c);
        }
        List<Map.Entry<String,Integer>> domains = new ArrayList<>(domainTotals.entrySet());
        domains.sort((a,b)->{
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return (cmp!=0)?cmp:a.getKey().compareTo(b.getKey());
        });
        for (Map.Entry<String,Integer> d : domains) {
            sender.sendMessage(new TextComponent(ChatColor.AQUA + d.getKey() + ChatColor.GRAY + " -> " + ChatColor.GREEN + d.getValue()));
            Map<String,Integer> subs = domainSubs.get(d.getKey());
            List<Map.Entry<String,Integer>> subList = new ArrayList<>(subs.entrySet());
            subList.sort((a,b)->{
                int cmp = Integer.compare(b.getValue(), a.getValue());
                return (cmp!=0)?cmp:a.getKey().compareTo(b.getKey());
            });
            for (Map.Entry<String,Integer> s : subList) {
                sender.sendMessage(new TextComponent(ChatColor.DARK_AQUA + "  " + s.getKey() + ChatColor.GRAY + " -> " + ChatColor.GREEN + s.getValue()));
            }
        }
    }

    private void sendSortedMap(CommandSender sender, Map<String,Integer> map) {
        if (map.isEmpty()) {
            sender.sendMessage(new TextComponent(ChatColor.GRAY + "  (none)"));
            return;
        }
        List<Map.Entry<String,Integer>> list = new ArrayList<>(map.entrySet());
        list.sort((a,b)->{
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return (cmp!=0)?cmp:a.getKey().compareTo(b.getKey());
        });
        for (Map.Entry<String,Integer> e : list) {
            sender.sendMessage(new TextComponent(ChatColor.AQUA + "  " + e.getKey() + ChatColor.GRAY + " -> " + ChatColor.GREEN + e.getValue()));
        }
    }

    private Map<String,Integer> expandSubdomains(Map<String,Integer> hostCounts, String domain) {
        Map<String,Integer> out = new HashMap<>();
        for (Map.Entry<String,Integer> e : hostCounts.entrySet()) {
            if (IdUtil.toDomain(e.getKey()).equals(domain)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private int sum(Map<String,Integer> m) {
        int s = 0;
        for (int v : m.values()) s += v;
        return s;
    }

    private long parseRange(String s, long now) {
        try {
            long mult = 0;
            if (s.endsWith("h")) mult = 3600;
            else if (s.endsWith("d")) mult = 86400;
            else if (s.endsWith("w")) mult = 604800;
            else if (s.endsWith("m")) mult = 2592000;
            else if (s.endsWith("y")) mult = 31536000;
            else return -1;
            long n = Long.parseLong(s.substring(0, s.length()-1));
            if (n < 0) return -1;
            return now - (n * mult);
        } catch (Exception e) { return -1; }
    }
}
