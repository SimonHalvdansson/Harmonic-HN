package com.simon.harmonichackernews.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Immutable sorted primitive table for allocation-free blocked-host lookups. */
public final class AdHostBlocklist {
    private static final int FILE_MAGIC = 0x48414431; // HAD1
    private static final int MAX_HOST_COUNT = 1_000_000;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final AdHostBlocklist EMPTY = new AdHostBlocklist(new long[0]);

    private final long[] sortedHostHashes;

    private AdHostBlocklist(long[] sortedHostHashes) {
        this.sortedHostHashes = sortedHostHashes;
    }

    @NonNull
    public static AdHostBlocklist empty() {
        return EMPTY;
    }

    @NonNull
    public static AdHostBlocklist read(@NonNull InputStream inputStream) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(inputStream))) {
            if (input.readInt() != FILE_MAGIC) {
                throw new IOException("Invalid ad host blocklist header");
            }
            int count = input.readInt();
            if (count < 0 || count > MAX_HOST_COUNT) {
                throw new IOException("Invalid ad host blocklist size: " + count);
            }

            byte[] encodedHashes = new byte[Math.multiplyExact(count, Long.BYTES)];
            input.readFully(encodedHashes);
            if (input.read() != -1) {
                throw new IOException("Unexpected data after ad host blocklist");
            }

            long[] hashes = new long[count];
            ByteBuffer.wrap(encodedHashes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .asLongBuffer()
                    .get(hashes);
            for (int i = 0; i < count; i++) {
                if (i > 0 && hashes[i - 1] >= hashes[i]) {
                    throw new IOException("Ad host blocklist is not strictly sorted");
                }
            }
            return count == 0 ? EMPTY : new AdHostBlocklist(hashes);
        }
    }

    public boolean isEmpty() {
        return sortedHostHashes.length == 0;
    }

    public int size() {
        return sortedHostHashes.length;
    }

    public boolean contains(@Nullable String host) {
        return host != null && Arrays.binarySearch(sortedHostHashes, hash(host)) >= 0;
    }

    private static long hash(String host) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < host.length(); i++) {
            hash ^= host.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }

}
