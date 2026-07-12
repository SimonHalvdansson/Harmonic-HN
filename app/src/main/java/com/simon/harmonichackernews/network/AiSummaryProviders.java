package com.simon.harmonichackernews.network;

import androidx.annotation.Nullable;

import java.util.Locale;

public final class AiSummaryProviders {
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_OPENROUTER = "openrouter";
    public static final String PROVIDER_GOOGLE = "google";

    public static final Provider OPENAI = new Provider(
            PROVIDER_OPENAI,
            "OpenAI",
            "https://api.openai.com/v1",
            "OpenAI",
            "openai");

    public static final Provider ANTHROPIC = new Provider(
            PROVIDER_ANTHROPIC,
            "Anthropic",
            "https://api.anthropic.com/v1",
            "Anthropic",
            "anthropic");

    public static final Provider OPENROUTER = new Provider(
            PROVIDER_OPENROUTER,
            "OpenRouter",
            "https://openrouter.ai/api/v1",
            null,
            null);

    public static final Provider GOOGLE = new Provider(
            PROVIDER_GOOGLE,
            "Google",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "Google AI Studio",
            "google");

    public static final Provider[] PROVIDERS = new Provider[]{
            OPENAI,
            ANTHROPIC,
            OPENROUTER,
            GOOGLE
    };

    private AiSummaryProviders() {
    }

    public static Provider getDefaultProvider() {
        return OPENROUTER;
    }

    public static String getDefaultBaseUrl() {
        return getDefaultProvider().baseUrl;
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

    public static String getModelForRequest(@Nullable String baseUrl, @Nullable String model) {
        Provider provider = getProviderForBaseUrl(baseUrl);
        String requestModel = model == null ? "" : model.trim();
        if (provider == null || PROVIDER_OPENROUTER.equals(provider.id)) {
            return requestModel;
        }
        return toProviderModelId(provider, requestModel);
    }

    public static String toProviderModelId(Provider provider, @Nullable String openRouterModelId) {
        String modelId = openRouterModelId == null ? "" : openRouterModelId.trim();
        if (PROVIDER_OPENROUTER.equals(provider.id) || provider.catalogAuthor == null) {
            return modelId;
        }

        String prefix = provider.catalogAuthor + "/";
        if (modelId.startsWith(prefix)) {
            modelId = modelId.substring(prefix.length());
        }

        // OpenRouter routing variants are not part of direct-provider model IDs. Match only
        // known suffixes so direct IDs such as OpenAI fine-tunes beginning with "ft:" survive.
        String[] routingVariants = new String[]{
                ":free", ":floor", ":nitro", ":online", ":extended", ":exacto"
        };
        for (String routingVariant : routingVariants) {
            if (modelId.endsWith(routingVariant)) {
                modelId = modelId.substring(0, modelId.length() - routingVariant.length());
                break;
            }
        }
        return modelId;
    }

    public static String toOpenRouterModelId(Provider provider, @Nullable String providerModelId) {
        String modelId = providerModelId == null ? "" : providerModelId.trim();
        if (modelId.isEmpty() || PROVIDER_OPENROUTER.equals(provider.id)
                || provider.catalogAuthor == null || modelId.contains("/")) {
            return modelId;
        }
        return provider.catalogAuthor + "/" + modelId;
    }

    public static String translateModelId(Provider oldProvider, Provider newProvider,
                                          @Nullable String modelId) {
        String openRouterId = toOpenRouterModelId(oldProvider, modelId);
        if (openRouterId.isEmpty()) {
            return "";
        }
        if (PROVIDER_OPENROUTER.equals(newProvider.id)) {
            return openRouterId;
        }
        if (newProvider.catalogAuthor != null
                && openRouterId.startsWith(newProvider.catalogAuthor + "/")) {
            return toProviderModelId(newProvider, openRouterId);
        }
        return "";
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

    public static final class Provider {
        public final String id;
        public final String label;
        public final String baseUrl;
        @Nullable public final String catalogProvider;
        @Nullable public final String catalogAuthor;

        Provider(String id, String label, String baseUrl,
                 @Nullable String catalogProvider, @Nullable String catalogAuthor) {
            this.id = id;
            this.label = label;
            this.baseUrl = baseUrl;
            this.catalogProvider = catalogProvider;
            this.catalogAuthor = catalogAuthor;
        }
    }
}
