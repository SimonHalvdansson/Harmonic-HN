package com.simon.harmonichackernews.data

import kotlinx.serialization.Serializable

@Serializable
data class NitterInfo(
    val text: String? = null,
    val userName: String? = null,
    val userTag: String? = null,
    val date: String? = null,
    val replyCount: String? = null,
    val reposts: String? = null,
    val likes: String? = null,
    val imgSrc: String? = null,
    val hasVideo: Boolean = false,
    val beforeUserName: String? = null,
    val beforeUserTag: String? = null,
    val beforeText: String? = null,
    val beforeDate: String? = null,
    val beforeImgSrc: String? = null,
)
