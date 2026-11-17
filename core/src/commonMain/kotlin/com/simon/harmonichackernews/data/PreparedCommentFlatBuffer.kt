package com.simon.harmonichackernews.data

import com.google.flatbuffers.kotlin.ArrayReadWriteBuffer
import com.google.flatbuffers.kotlin.FlatBufferBuilder
import com.google.flatbuffers.kotlin.Offset
import com.simon.harmonichackernews.data.flatbuffers.CachedComment
import com.simon.harmonichackernews.data.flatbuffers.CachedCommentOffsetArray
import com.simon.harmonichackernews.data.flatbuffers.CachedStory
import com.simon.harmonichackernews.data.flatbuffers.CachedThread

/** Materializes immutable content once; no buffer-backed objects escape into presentation state. */
internal object PreparedCommentFlatBuffer {
    fun encode(thread: PreparedCommentThread): ByteArray {
        val builder = FlatBufferBuilder(1024)
        val story = thread.story
        val title = builder.createString(story.title)
        val type = builder.createString(story.type)
        val author = builder.createString(story.author)
        val storyTitle = builder.createString(story.storyTitle)
        val url = builder.createString(story.url)
        val text = builder.createString(story.text)
        val storyOffset = CachedStory.createCachedStory(builder, story.id, title, story.points,
            story.createdAtEpochSeconds, type, author, story.storyId, story.parentId, storyTitle, url, text)
        val comments = CachedCommentOffsetArray(thread.comments.size) { index ->
            val comment = thread.comments[index]
            val by = builder.createString(comment.author)
            val html = builder.createString(comment.html)
            val expandedHtml = comment.expandedHtml?.let(builder::createString) ?: Offset(0)
            CachedComment.createCachedComment(builder, comment.id, comment.parentId, by,
                comment.createdAtEpochSeconds, html, expandedHtml, comment.depth,
                comment.childCount, comment.subtreeEndExclusive)
        }
        val commentsOffset = CachedThread.createCommentsVector(builder, comments)
        val ranks = CachedThread.createRankedIdsVector(builder, thread.rankedIds.toIntArray())
        val digest = builder.createString(thread.sourceDigest)
        val summary = builder.createString(thread.summaryJson)
        val root = CachedThread.createCachedThread(builder, thread.schemaVersion,
            thread.textPreparationVersion, digest, storyOffset, commentsOffset, summary, ranks)
        CachedThread.finishCachedThreadBuffer(builder, root)
        return builder.sizedByteArray()
    }

    fun decode(bytes: ByteArray): PreparedCommentThread {
        // The upstream KMP runtime has no verifier. Check offsets/counts before native reads or
        // allocating lists/strings, even if a malformed file happens to have a valid checksum.
        PreparedCommentFlatBufferVerifier(bytes).verify()
        val root = CachedThread.asRoot(ArrayReadWriteBuffer(bytes))
        val story = requireNotNull(root.story)
        val cursor = CachedComment()
        return PreparedCommentThread(
            schemaVersion = root.schemaVersion,
            textPreparationVersion = root.textPreparationVersion,
            sourceDigest = requireNotNull(root.sourceDigest),
            story = PreparedCommentStory(story.id, requireNotNull(story.title), story.points,
                story.createdAtEpochSeconds, requireNotNull(story.type), requireNotNull(story.author),
                story.storyId, story.parentId, requireNotNull(story.storyTitle),
                requireNotNull(story.url), requireNotNull(story.text)),
            comments = List(root.commentsLength) { index ->
                val comment = requireNotNull(root.comments(cursor, index))
                PreparedCommentRecord(comment.id, comment.parentId, requireNotNull(comment.author),
                    comment.createdAtEpochSeconds, requireNotNull(comment.html), comment.expandedHtml,
                    comment.depth, comment.childCount, comment.subtreeEndExclusive)
            },
            summaryJson = requireNotNull(root.summaryJson),
            rankedIds = List(root.rankedIdsLength) { root.rankedIds(it) },
        )
    }
}
