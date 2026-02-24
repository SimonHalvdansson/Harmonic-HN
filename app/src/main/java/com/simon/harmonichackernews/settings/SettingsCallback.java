package com.simon.harmonichackernews.settings;

public interface SettingsCallback {
    void onRequestRestart();
    void onRequestFullRestart();
    boolean isTwoPane();
}
