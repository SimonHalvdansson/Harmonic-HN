package com.simon.harmonichackernews.format

object DelimitedListPolicy {
    fun parseCommaSeparated(value: String?): List<String> = value.orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)
}
