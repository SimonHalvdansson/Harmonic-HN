package com.simon.harmonichackernews.summary

enum class LocalModelRuntime {
    GEMINI_NANO,
    APPLE_FOUNDATION_MODELS,
    LITERT_LM,
}

enum class LocalModelBrand(val artworkKey: String) {
    SYSTEM("ic_auto_awesome"),
    GOOGLE("model_logo_google"),
    NVIDIA("model_logo_nvidia"),
    MISTRAL("model_logo_mistral"),
    LIQUID("model_logo_liquid"),
    QWEN("model_logo_qwen"),
}

data class LocalModelDefinition(
    val id: String,
    val displayName: String,
    val parameterSize: String,
    val quantization: String,
    val brand: LocalModelBrand,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val downloadable: Boolean,
    val runtime: LocalModelRuntime,
    val contextTokens: Int,
    val supportsSummarization: Boolean = true,
)

/** Portable, immutable catalog. Platform shells only map [LocalModelBrand] to artwork. */
object LocalModelCatalog {
    const val MODEL_GEMINI_NANO = "gemini-nano"
    const val MODEL_E2B = "gemma-4-e2b"
    const val MODEL_E4B = "gemma-4-e4b"
    const val MODEL_NEMOTRON_4B = "nemotron-3-nano-4b"
    const val MODEL_MINISTRAL_3B = "ministral-3-3b"
    const val MODEL_LFM3_17B = "lfm-3-1.7b"
    const val MODEL_QWEN_2B = "qwen-3.5-2b"
    const val MODEL_EMBEDDING_GEMMA_2 = "embeddinggemma-2-text-270m"

    /** Prevent persisted background work from downloading a retired or replaced artifact. */
    fun isCurrentDownload(id: String, url: String, fileName: String, sizeBytes: Long): Boolean =
        models.any {
            it.downloadable && it.id == id && it.url == url &&
                it.fileName == fileName && it.sizeBytes == sizeBytes
        }

    val embeddingModel = LocalModelDefinition(
        id = MODEL_EMBEDDING_GEMMA_2,
        displayName = "EmbeddingGemma 2",
        parameterSize = "270M text",
        quantization = "Mixed precision",
        brand = LocalModelBrand.GOOGLE,
        fileName = "embeddinggemma-2-text-270m.litertlm",
        url = "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/" +
            "9be6e8b90982095dc05c2bd162e4b954ee4dbac7/embeddinggemma-2-text-270m.litertlm?download=true",
        sizeBytes = 164_626_432L,
        downloadable = true,
        runtime = LocalModelRuntime.LITERT_LM,
        contextTokens = 256,
        supportsSummarization = false,
    )

    val models: List<LocalModelDefinition> = listOf(
        LocalModelDefinition(
            MODEL_GEMINI_NANO,
            "Gemini Nano",
            "System managed",
            "",
            LocalModelBrand.GOOGLE,
            "",
            "",
            0L,
            false,
            LocalModelRuntime.GEMINI_NANO,
            0,
        ),
        LocalModelDefinition(
            MODEL_E2B,
            "Gemma 4 E2B",
            "2B effective",
            "QAT 2/4/8-bit",
            LocalModelBrand.GOOGLE,
            "gemma-4-E2B-it-qat-mobile.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "361a4010ad6d88fc5c86e148e333c0342b99763d/gemma-4-E2B-it.litertlm?download=true",
            2_588_147_712L,
            true,
            LocalModelRuntime.LITERT_LM,
            4096,
        ),
        LocalModelDefinition(
            MODEL_E4B,
            "Gemma 4 E4B",
            "4B effective",
            "4-bit per-channel",
            LocalModelBrand.GOOGLE,
            "gemma-4-E4B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/" +
                "9695417f248178c63a9f318c6e0c56cb917cb837/gemma-4-E4B-it.litertlm?download=true",
            3_654_467_584L,
            true,
            LocalModelRuntime.LITERT_LM,
            4096,
        ),
        LocalModelDefinition(
            MODEL_NEMOTRON_4B,
            "Nemotron 3 Nano 4B",
            "4B",
            "INT8; float conv/scan",
            LocalModelBrand.NVIDIA,
            "Nemotron-3-Nano-4B_int8.litertlm",
            "https://huggingface.co/litert-community/Nemotron-3-Nano-4B/resolve/" +
                "91d59fd2dc7451fbc1f124cd3e7ddfd8ce28fc18/Nemotron-3-Nano-4B_int8.litertlm?download=true",
            4_126_697_184L,
            true,
            LocalModelRuntime.LITERT_LM,
            4096,
        ),
        LocalModelDefinition(
            MODEL_MINISTRAL_3B,
            "Ministral 3 3B",
            "3.4B",
            "INT4 block-32; INT8 embedding",
            LocalModelBrand.MISTRAL,
            "Ministral-3-3B-Instruct-2512_q4_block32_ekv4096.litertlm",
            "https://huggingface.co/litert-community/Ministral-3-3B-Instruct-2512/resolve/" +
                "c26821f18bff3fc81921f1d2ae502df3a16c4a4f/Ministral-3-3B-Instruct-2512_q4_block32_ekv4096.litertlm?download=true",
            2_342_895_616L,
            true,
            LocalModelRuntime.LITERT_LM,
            4096,
        ),
        LocalModelDefinition(
            MODEL_LFM3_17B,
            "LFM3 1.7B",
            "1.7B",
            "INT4 MLP gate/up; INT8 other weights",
            LocalModelBrand.LIQUID,
            "LFM3-1.7B_mixed_int4_ekv4096.litertlm",
            "https://huggingface.co/litert-community/LFM3-1.7B-LiteRT/resolve/" +
                "9bbda264c623f30c905b9c247761ee7463f2b00d/LFM3-1.7B_mixed_int4_ekv4096.litertlm?download=true",
            1_441_629_120L,
            true,
            LocalModelRuntime.LITERT_LM,
            4096,
        ),
        LocalModelDefinition(
            MODEL_QWEN_2B,
            "Qwen 3.5 2B",
            "2B",
            "INT8; float conv/delta/gates",
            LocalModelBrand.QWEN,
            "Qwen3.5-2B_int8.litertlm",
            "https://huggingface.co/litert-community/Qwen3.5-2B/resolve/" +
                "a2877bbd21faf6179e5ffb5a7d66e7111fde01be/Qwen3.5-2B_int8.litertlm?download=true",
            2_119_744_416L,
            true,
            LocalModelRuntime.LITERT_LM,
            4096,
        ),
    ) + embeddingModel
}
