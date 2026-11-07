package com.simon.harmonichackernews

import com.google.mlkit.genai.embedding.*
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.simon.harmonichackernews.platform.*

/** Present only in Play/local-AI variants; the base and FOSS APKs never link ML Kit. */
class AndroidSystemArticleEmbeddingEngine : NativeArticleEmbeddingEngine {
    private var active: EmbeddingModel? = null
    override suspend fun model(): NativeEmbeddingModel? {
        active?.close()
        active = null
        val models = EmbeddingGeneration.getEmbeddingModels(EmbeddingOptions())
        try {
            val available = models.filter { it.checkStatus() == FeatureStatus.AVAILABLE }
            val model = available.firstOrNull { !it.isDeprecated() } ?: available.firstOrNull() ?: return null
            val metadata = model.getMetadata() ?: return null
            // ML Kit 1.0.0-alpha1 documents EmbeddingGemma 2 as its model. The SDK
            // exposes no display name; keep its opaque signature solely for cache identity.
            // https://developers.google.com/ml-kit/genai/embedding/android#supported_models_and_configuration
            val result = NativeEmbeddingModel(
                signature = "android-${model.getModelSignature()}",
                name = "Android AICore",
                dimensions = metadata.dimension,
                tokenLimit = model.getTokenLimit(),
                modelName = "EmbeddingGemma 2",
            )
            active = model
            return result
        } finally { models.filter { it !== active }.forEach { it.close() } }
    }

    override suspend fun embed(text: String): FloatArray {
        val model = checkNotNull(active)
        var input = text
        while (true) {
            try {
                return model.generateEmbedding(EmbeddingRequest(Content(listOf(TextPart(input))))).embedding.vector
            } catch (error: GenAiException) {
                when (error.errorCode) {
                    GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
                    GenAiException.ErrorCode.BUSY,
                    GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> throw EmbeddingQuotaException()
                    GenAiException.ErrorCode.REQUEST_TOO_LARGE -> {
                        // The system tokenizer is private. Retry only its explicit overflow error.
                        if (input.length < 2) throw error
                        var end = input.length * 3 / 4
                        if (input[end - 1].isHighSurrogate()) end--
                        if (end == 0) throw error
                        input = input.take(end).trimEnd()
                    }
                    else -> throw error
                }
            }
        }
    }
    override suspend fun close() { active?.close(); active = null }
}
