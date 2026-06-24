package com.simon.harmonichackernews.network;

import androidx.annotation.Nullable;

import java.util.Locale;

public class AiSummaryProviders {
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_OPENROUTER = "openrouter";
    public static final String PROVIDER_GOOGLE = "google";

    public static final Provider OPENAI = new Provider(
            PROVIDER_OPENAI,
            "OpenAI",
            "https://api.openai.com/v1",
            "gpt-5.4-nano",
            new Model[]{
                    new Model("GPT-5.4 Nano", "gpt-5.4-nano"),
                    new Model("GPT-5.4 Mini", "gpt-5.4-mini"),
                    new Model("GPT-5.5", "gpt-5.5")
            });

    public static final Provider ANTHROPIC = new Provider(
            PROVIDER_ANTHROPIC,
            "Anthropic",
            "https://api.anthropic.com/v1",
            "claude-haiku-4-5",
            new Model[]{
                    new Model("Claude Haiku 4.5", "claude-haiku-4-5"),
                    new Model("Claude Sonnet 4.6", "claude-sonnet-4-6")
            });

    public static final Provider OPENROUTER = new Provider(
            PROVIDER_OPENROUTER,
            "OpenRouter",
            "https://openrouter.ai/api/v1",
            "openai/gpt-5.4-nano",
            new Model[]{
                    new Model("GPT 5.4 Nano", "openai/gpt-5.4-nano"),
                    new Model("GPT 5.4 Mini", "openai/gpt-5.4-mini"),
                    new Model("Claude Haiku 4.5", "anthropic/claude-haiku-4.5"),
                    new Model("Gemini 3.1 Flash Lite", "google/gemini-3.1-flash-lite"),
                    new Model("DeepSeek V4 Flash", "deepseek/deepseek-v4-flash"),
                    new Model("Qwen3.5 Flash", "qwen/qwen3.5-flash-02-23"),
                    new Model("Qwen3.7 Plus", "qwen/qwen3.7-plus"),
                    new Model("Step 3.7 Flash", "stepfun/step-3.7-flash"),
                    new Model("Nemotron 3 Nano", "nvidia/nemotron-3-nano-30b-a3b"),
                    new Model("MiniMax M3", "minimax/minimax-m3")
            });

    public static final Provider GOOGLE = new Provider(
            PROVIDER_GOOGLE,
            "Google",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "gemini-3.1-flash-lite",
            new Model[]{
                    new Model("Gemini 3.1 Flash Lite", "gemini-3.1-flash-lite"),
                    new Model("Gemini 3 Flash Preview", "gemini-3-flash-preview"),
                    new Model("Gemini 3.5 Flash", "gemini-3.5-flash")
            });

    public static final Provider[] PROVIDERS = new Provider[]{
            OPENAI,
            ANTHROPIC,
            OPENROUTER,
            GOOGLE
    };

    private AiSummaryProviders() {
    }

    public static Provider getDefaultProvider() {
        return OPENAI;
    }

    public static String getDefaultBaseUrl() {
        return getDefaultProvider().baseUrl;
    }

    public static String getDefaultModel() {
        return getDefaultProvider().defaultModel;
    }

    @Nullable
    public static Provider getProviderForBaseUrl(@Nullable String baseUrl) {
        String normalizedUrl = normalizeUrl(baseUrl);
        if (normalizedUrl.isEmpty()) {
            return getDefaultProvider();
        }

        for (Provider provider : PROVIDERS) {
            if (normalizeUrl(provider.baseUrl).equals(normalizedUrl)) {
                return provider;
            }
        }

        String lowerUrl = normalizedUrl.toLowerCase(Locale.US);
        if (lowerUrl.contains("api.openai.com")) {
            return OPENAI;
        } else if (lowerUrl.contains("api.anthropic.com")) {
            return ANTHROPIC;
        } else if (lowerUrl.contains("openrouter.ai")) {
            return OPENROUTER;
        } else if (lowerUrl.contains("generativelanguage.googleapis.com")) {
            return GOOGLE;
        }
        return null;
    }

    public static String getDefaultModelForBaseUrl(@Nullable String baseUrl) {
        Provider provider = getProviderForBaseUrl(baseUrl);
        return provider != null ? provider.defaultModel : getDefaultModel();
    }

    public static String getModelForRequest(@Nullable String baseUrl, @Nullable String model) {
        Provider provider = getProviderForBaseUrl(baseUrl);
        String requestModel = model == null ? "" : model.trim();
        if (requestModel.isEmpty()) {
            return provider != null ? provider.defaultModel : getDefaultModel();
        }

        if (provider == null || PROVIDER_OPENROUTER.equals(provider.id)) {
            return requestModel;
        }

        String providerPrefix = provider.id + "/";
        if (requestModel.startsWith(providerPrefix)) {
            return requestModel.substring(providerPrefix.length());
        }
        return requestModel;
    }

    public static boolean isAnthropicBaseUrl(@Nullable String baseUrl) {
        Provider provider = getProviderForBaseUrl(baseUrl);
        return provider != null && PROVIDER_ANTHROPIC.equals(provider.id);
    }

    public static String normalizeUrl(@Nullable String url) {
        String normalized = url == null ? "" : url.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static class Provider {
        public final String id;
        public final String label;
        public final String baseUrl;
        public final String defaultModel;
        public final Model[] models;

        Provider(String id, String label, String baseUrl, String defaultModel, Model[] models) {
            this.id = id;
            this.label = label;
            this.baseUrl = baseUrl;
            this.defaultModel = defaultModel;
            this.models = models;
        }
    }

    public static class Model {
        public final String label;
        public final String id;

        Model(String label, String id) {
            this.label = label;
            this.id = id;
        }
    }
}
