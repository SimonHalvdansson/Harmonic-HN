package com.simon.harmonichackernews.platform

/**
 * Source-compatible iOS names for the common stored services. NSUserDefaults is selected by
 * supplying [com.simon.harmonichackernews.settings.IosKeyValueStore] at bootstrap time; all
 * bookmark and history semantics now live in commonMain.
 */
typealias IosBookmarkStore = StoredBookmarkStore

typealias IosHistoryStore = StoredHistoryStore
