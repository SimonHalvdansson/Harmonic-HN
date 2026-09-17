package com.simon.harmonichackernews.summary

enum class LocalModelRuntime {
    GEMINI_NANO,
    LITERT_LM,
    LLAMA_CPP,
}

enum class LocalModelBrand(val artworkKey: String) {
    GOOGLE("model_logo_google"),
    PRISM("model_logo_prism"),
    QWEN("model_logo_qwen"),
    NVIDIA("model_logo_nvidia"),
    MISTRAL("model_logo_mistral"),
    LIQUID("model_logo_liquid"),
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
)

/** Portable, immutable catalog. Platform shells only map [LocalModelBrand] to artwork. */
object LocalModelCatalog {
    const val MODEL_GEMINI_NANO = "gemini-nano"
    const val MODEL_E2B = "gemma-4-e2b"
    const val MODEL_E4B = "gemma-4-e4b"
    const val MODEL_BONSAI_17B = "bonsai-1.7b"
    const val MODEL_BONSAI_4B = "bonsai-4b"
    const val MODEL_BONSAI_8B = "bonsai-8b"
    const val MODEL_QWEN_08B = "qwen-3.5-0.8b"
    const val MODEL_NEMOTRON_4B = "nemotron-3-nano-4b"
    const val MODEL_MINISTRAL_3B = "ministral-3-3b"
    const val MODEL_LFM_12B = "lfm-2.5-1.2b"

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
            MODEL_BONSAI_17B,
            "Bonsai 1.7B",
            "1.7B",
            "Q1_0",
            LocalModelBrand.PRISM,
            "Bonsai-1.7B-Q1_0.gguf",
            "https://huggingface.co/prism-ml/Bonsai-1.7B-gguf/resolve/" +
                "210a9e99f79cb184909d49595906526eb2b3dd9a/Bonsai-1.7B-Q1_0.gguf?download=true",
            248_302_272L,
            true,
            LocalModelRuntime.LLAMA_CPP,
            4096,
        ),
        LocalModelDefinition(
            MODEL_BONSAI_4B,
            "Bonsai 4B",
            "4B",
            "Q1_0",
            LocalModelBrand.PRISM,
            "Bonsai-4B-Q1_0.gguf",
            "https://huggingface.co/prism-ml/Bonsai-4B-gguf/resolve/" +
                "78f2c2bacd0904ffaba24b4873ed975e5818354a/Bonsai-4B-Q1_0.gguf?download=true",
            572_270_624L,
            true,
            LocalModelRuntime.LLAMA_CPP,
            4096,
        ),
        LocalModelDefinition(
            MODEL_BONSAI_8B,
            "Bonsai 8B",
            "8B",
            "Q1_0",
            LocalModelBrand.PRISM,
            "Bonsai-8B-Q1_0.gguf",
            "https://huggingface.co/prism-ml/Bonsai-8B-gguf/resolve/" +
                "48516770dd04643643e9f9019a2a349cf26c5dbd/Bonsai-8B-Q1_0.gguf?download=true",
            1_158_654_496L,
            true,
            LocalModelRuntime.LLAMA_CPP,
            4096,
        ),
        LocalModelDefinition(
            MODEL_QWEN_08B,
            "Qwen 3.5 0.8B",
            "0.8B",
            "Q4_K_M",
            LocalModelBrand.QWEN,
            "Qwen3.5-0.8B-Q4_K_M.gguf",
            "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/" +
                "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0/Qwen3.5-0.8B-Q4_K_M.gguf?download=true",
            532_517_120L,
            true,
            LocalModelRuntime.LLAMA_CPP,
            2048,
        ),
        LocalModelDefinition(
            MODEL_NEMOTRON_4B,
            "Nemotron 3 Nano 4B",
            "4B",
            "Q4_K_M",
            LocalModelBrand.NVIDIA,
            "NVIDIA-Nemotron3-Nano-4B-Q4_K_M.gguf",
            "https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF/resolve/" +
                "18d83da545bdfde657afff71123d7ffc8965edfa/NVIDIA-Nemotron3-Nano-4B-Q4_K_M.gguf?download=true",
            2_837_072_864L,
            true,
            LocalModelRuntime.LLAMA_CPP,
            4096,
        ),
        LocalModelDefinition(
            MODEL_MINISTRAL_3B,
            "Ministral 3 3B",
            "3.4B",
            "Q4_K_M",
            LocalModelBrand.MISTRAL,
            "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
            "https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF/resolve/" +
                "eb599d408350ea2bb60452cb86be7c7b2fc28227/Ministral-3-3B-Instruct-2512-Q4_K_M.gguf?download=true",
            2_147_023_008L,
            true,
            LocalModelRuntime.LLAMA_CPP,
            4096,
        ),
        LocalModelDefinition(
            MODEL_LFM_12B,
            "LFM2.5 1.2B",
            "1.2B",
            "Q4_K_M",
            LocalModelBrand.LIQUID,
            "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/" +
                "047e06635fbe71469926b35ea414537245218200/LFM2.5-1.2B-Instruct-Q4_K_M.gguf?download=true",
            730_895_168L,
            true,
            LocalModelRuntime.LLAMA_CPP,
            4096,
        ),
    )
}
