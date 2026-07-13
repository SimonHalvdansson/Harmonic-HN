package com.simon.harmonichackernews.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Immutable primitive hash table for allocation-free blocked-host lookups. */
public final class AdHostBlocklist {
    private static final int INITIAL_TABLE_CAPACITY = 262_144;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final AdHostBlocklist EMPTY = new AdHostBlocklist(new long[0], 0, false);

    private final long[] hostHashes;
    private final int size;
    private final boolean containsZeroHash;

    private AdHostBlocklist(long[] hostHashes, int size, boolean containsZeroHash) {
        this.hostHashes = hostHashes;
        this.size = size;
        this.containsZeroHash = containsZeroHash;
    }

    @NonNull
    public static AdHostBlocklist empty() {
        return EMPTY;
    }

    @NonNull
    public static AdHostBlocklist read(@NonNull InputStream inputStream) throws IOException {
        Builder builder = new Builder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String host;
            while ((host = reader.readLine()) != null) {
                builder.add(hash(host));
            }
        }
        return builder.build();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public boolean contains(@Nullable String host) {
        if (host == null || size == 0) {
            return false;
        }
        long targetHash = hash(host);
        if (targetHash == 0) {
            return containsZeroHash;
        }

        int mask = hostHashes.length - 1;
        int index = indexFor(targetHash, mask);
        while (hostHashes[index] != 0) {
            if (hostHashes[index] == targetHash) {
                return true;
            }
            index = (index + 1) & mask;
        }
        return false;
    }

    private static int indexFor(long hash, int mask) {
        return ((int) (hash ^ (hash >>> 32))) & mask;
    }

    private static long hash(String host) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < host.length(); i++) {
            hash ^= host.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static final class Builder {
        private long[] table = new long[INITIAL_TABLE_CAPACITY];
        private int size;
        private boolean containsZeroHash;

        void add(long hash) {
            if (hash == 0) {
                if (!containsZeroHash) {
                    containsZeroHash = true;
                    size++;
                }
                return;
            }
            if ((size + 1) * 10 > table.length * 7) {
                resize();
            }
            if (insert(table, hash)) {
                size++;
            }
        }

        AdHostBlocklist build() {
            return size == 0 ? EMPTY : new AdHostBlocklist(table, size, containsZeroHash);
        }

        private void resize() {
            long[] largerTable = new long[table.length * 2];
            for (long hash : table) {
                if (hash != 0) {
                    insert(largerTable, hash);
                }
            }
            table = largerTable;
        }

        private static boolean insert(long[] table, long hash) {
            int mask = table.length - 1;
            int index = indexFor(hash, mask);
            while (table[index] != 0) {
                if (table[index] == hash) {
                    return false;
                }
                index = (index + 1) & mask;
            }
            table[index] = hash;
            return true;
        }
    }
}
