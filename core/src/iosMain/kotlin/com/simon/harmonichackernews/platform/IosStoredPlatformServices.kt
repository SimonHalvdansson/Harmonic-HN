package com.simon.harmonichackernews.platform

/**
 * Source-compatible iOS names for the common stored services. DataStore is supplied by
 * supplying [com.simon.harmonichackernews.settings.DataStoreKeyValueStore] at bootstrap time; all
 * history semantics now live in commonMain.
 */
typealias IosHistoryStore = StoredHistoryStore
