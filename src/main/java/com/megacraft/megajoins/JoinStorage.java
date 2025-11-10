package com.megacraft.megajoins;

import java.util.Map;

public interface JoinStorage {
    void init() throws Exception;
    void shutdown();

    void logJoinSync(String hostname, String uuidTrimLower, String playerName) throws Exception;

    Map<String, Integer> queryCountsSince(long start) throws Exception;

    Map<String, Integer> queryUniqueCountsSince(long start) throws Exception;

    Map<String, Integer> queryByUuidSince(String uuidTrimLower, long start) throws Exception;

    Map<String, Integer> queryByUuidPrefixSince(String uuidTrimLowerPrefix, long start) throws Exception;
}
