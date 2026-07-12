package com.simon.harmonichackernews.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** OpenRouter-backed model discovery and pricing for every supported cloud provider. */
public final class AiModelCatalog {
    public static final String PREF_BASE_URL = "pref_ai_summary_base_url";
    public static final String PREF_MODEL = "pref_ai_summary_model";

    private static final String MODELS_URL = "https://openrouter.ai/api/v1/models";
    private static final String MODEL_URL = "https://openrouter.ai/api/v1/model";
    private static final long TWELVE_MONTHS_SECONDS = 365L * 24L * 60L * 60L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<String, List<Model>> CACHE = new HashMap<>();

    private AiModelCatalog() {
    }

    public enum Sort {
        POPULAR("most-popular"),
        PRICE_LOW_TO_HIGH("pricing-low-to-high");

        final String apiValue;

        Sort(String apiValue) {
            this.apiValue = apiValue;
        }
    }

    public interface ModelsCallback {
        void onSuccess(List<Model> models);

        void onError(String message);
    }

    public interface ModelCallback {
        void onSuccess(Model model);

        void onError(String message);
    }

    @Nullable
    public static Call fetchModels(AiSummaryProviders.Provider provider, Sort sort,
                                   ModelsCallback callback) {
        String cacheKey = provider.id + ":" + sort.apiValue;
        synchronized (CACHE) {
            List<Model> cached = CACHE.get(cacheKey);
            if (cached != null) {
                MAIN_HANDLER.post(() -> callback.onSuccess(cached));
                return null;
            }
        }

        HttpUrl.Builder urlBuilder = HttpUrl.get(MODELS_URL).newBuilder()
                .addQueryParameter("output_modalities", "text")
                .addQueryParameter("sort", sort.apiValue);
        if (provider.catalogProvider != null) {
            urlBuilder.addQueryParameter("providers", provider.catalogProvider);
        }

        Request request = new Request.Builder().url(urlBuilder.build()).build();
        Call call = NetworkComponent.getOkHttpClientInstance().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!call.isCanceled()) {
                    MAIN_HANDLER.post(() -> callback.onError(readableError(e)));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        postHttpError(callback, closeableResponse.code());
                        return;
                    }

                    JSONArray data = new JSONObject(closeableResponse.body().string())
                            .getJSONArray("data");
                    List<Model> models = parseModels(data, provider);
                    if (models.isEmpty()) {
                        MAIN_HANDLER.post(() -> callback.onError("No compatible text models found"));
                        return;
                    }
                    List<Model> immutableModels = Collections.unmodifiableList(models);
                    synchronized (CACHE) {
                        CACHE.put(cacheKey, immutableModels);
                    }
                    MAIN_HANDLER.post(() -> callback.onSuccess(immutableModels));
                } catch (Exception e) {
                    MAIN_HANDLER.post(() -> callback.onError("OpenRouter returned invalid model data"));
                }
            }
        });
        return call;
    }

    @Nullable
    public static Call resolveModel(AiSummaryProviders.Provider provider, String enteredModelId,
                                    ModelCallback callback) {
        String openRouterId = AiSummaryProviders.toOpenRouterModelId(provider, enteredModelId);
        if (provider.catalogAuthor != null && openRouterId.contains("/")
                && !openRouterId.startsWith(provider.catalogAuthor + "/")) {
            MAIN_HANDLER.post(() -> callback.onError(
                    "That OpenRouter ID belongs to a different provider"));
            return null;
        }
        Model cached = findCachedModel(openRouterId);
        if (cached != null) {
            MAIN_HANDLER.post(() -> callback.onSuccess(cached));
            return null;
        }

        int separator = openRouterId.indexOf('/');
        if (separator <= 0 || separator >= openRouterId.length() - 1) {
            MAIN_HANDLER.post(() -> callback.onError("Use an OpenRouter ID in provider/model-name format"));
            return null;
        }

        HttpUrl url = HttpUrl.get(MODEL_URL).newBuilder()
                .addPathSegment(openRouterId.substring(0, separator))
                .addPathSegment(openRouterId.substring(separator + 1))
                .build();
        Request request = new Request.Builder().url(url).build();
        Call call = NetworkComponent.getOkHttpClientInstance().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!call.isCanceled()) {
                    MAIN_HANDLER.post(() -> callback.onError(readableError(e)));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        MAIN_HANDLER.post(() -> callback.onError(
                                closeableResponse.code() == 404
                                        ? "Price not found on OpenRouter"
                                        : "Price unavailable (HTTP " + closeableResponse.code() + ")"));
                        return;
                    }
                    JSONObject data = new JSONObject(closeableResponse.body().string())
                            .getJSONObject("data");
                    Model model = parseModel(data, provider);
                    MAIN_HANDLER.post(() -> callback.onSuccess(model));
                } catch (Exception e) {
                    MAIN_HANDLER.post(() -> callback.onError("Price data could not be read"));
                }
            }
        });
        return call;
    }

    /** Selects the requested first-run default without ever baking a model slug into the app. */
    public static void ensureInitialDefault(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        if (prefs.contains(PREF_MODEL)) {
            return;
        }

        fetchModels(AiSummaryProviders.OPENAI, Sort.PRICE_LOW_TO_HIGH, new ModelsCallback() {
            @Override
            public void onSuccess(List<Model> models) {
                SharedPreferences latestPrefs = PreferenceManager.getDefaultSharedPreferences(appContext);
                String baseUrl = latestPrefs.getString(PREF_BASE_URL,
                        AiSummaryProviders.getDefaultBaseUrl());
                AiSummaryProviders.Provider selectedProvider =
                        AiSummaryProviders.getProviderForBaseUrl(baseUrl);
                if (latestPrefs.contains(PREF_MODEL) || selectedProvider == null
                        || !AiSummaryProviders.PROVIDER_OPENROUTER.equals(selectedProvider.id)) {
                    return;
                }

                long cutoff = System.currentTimeMillis() / 1000L - TWELVE_MONTHS_SECONDS;
                Model selected = cheapestModel(models, cutoff);
                if (selected == null) {
                    selected = cheapestModel(models, Long.MIN_VALUE);
                }
                if (selected != null) {
                    latestPrefs.edit()
                            .putString(PREF_BASE_URL, AiSummaryProviders.getDefaultBaseUrl())
                            .putString(PREF_MODEL, selected.openRouterId)
                            .apply();
                }
            }

            @Override
            public void onError(String message) {
                // The initializer retries on a future launch while the model preference is absent.
            }
        });
    }

    /** Chooses a dynamic low-cost model after switching direct providers. */
    public static void ensureProviderDefault(Context context, AiSummaryProviders.Provider provider) {
        Context appContext = context.getApplicationContext();
        fetchModels(provider, Sort.PRICE_LOW_TO_HIGH, new ModelsCallback() {
            @Override
            public void onSuccess(List<Model> models) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
                AiSummaryProviders.Provider currentProvider = AiSummaryProviders.getProviderForBaseUrl(
                        prefs.getString(PREF_BASE_URL, AiSummaryProviders.getDefaultBaseUrl()));
                if (prefs.contains(PREF_MODEL) || currentProvider == null
                        || !provider.id.equals(currentProvider.id)) {
                    return;
                }
                Model selected = cheapestModel(models, Long.MIN_VALUE);
                if (selected != null) {
                    prefs.edit().putString(PREF_MODEL, selected.requestId).apply();
                }
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private static List<Model> parseModels(JSONArray data, AiSummaryProviders.Provider provider) {
        Map<String, Model> uniqueModels = new LinkedHashMap<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) {
                continue;
            }
            Model model = parseModel(item, provider);
            if (provider.catalogAuthor != null
                    && !model.openRouterId.startsWith(provider.catalogAuthor + "/")) {
                continue;
            }
            uniqueModels.put(model.requestId, model);
        }
        return new ArrayList<>(uniqueModels.values());
    }

    private static Model parseModel(JSONObject item, AiSummaryProviders.Provider provider) {
        String openRouterId = item.optString("id", "");
        String requestId = AiSummaryProviders.toProviderModelId(provider, openRouterId);
        JSONObject pricing = item.optJSONObject("pricing");
        double inputPrice = parsePrice(pricing, "prompt");
        double outputPrice = parsePrice(pricing, "completion");
        return new Model(
                openRouterId,
                requestId,
                item.optString("name", requestId),
                item.optLong("created", 0L),
                inputPrice,
                outputPrice,
                item.optLong("context_length", 0L));
    }

    @Nullable
    private static Model findCachedModel(String openRouterId) {
        synchronized (CACHE) {
            for (List<Model> models : CACHE.values()) {
                for (Model model : models) {
                    if (model.openRouterId.equals(openRouterId)) {
                        return model;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static Model cheapestModel(List<Model> models, long createdAfter) {
        Model cheapest = null;
        for (Model model : models) {
            if (model.created < createdAfter || !model.hasPrices()) {
                continue;
            }
            if (cheapest == null || model.totalTokenPrice() < cheapest.totalTokenPrice()
                    || (model.totalTokenPrice() == cheapest.totalTokenPrice()
                    && model.created > cheapest.created)) {
                cheapest = model;
            }
        }
        return cheapest;
    }

    private static double parsePrice(@Nullable JSONObject pricing, String key) {
        if (pricing == null || !pricing.has(key)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(pricing.optString(key));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static void postHttpError(ModelsCallback callback, int responseCode) {
        MAIN_HANDLER.post(() -> callback.onError(
                "Could not load models (HTTP " + responseCode + ")"));
    }

    private static String readableError(IOException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Could not reach OpenRouter"
                : "Could not reach OpenRouter: " + message;
    }

    public static final class Model {
        public final String openRouterId;
        public final String requestId;
        public final String name;
        public final long created;
        public final double inputPrice;
        public final double outputPrice;
        public final long contextLength;

        Model(String openRouterId, String requestId, String name, long created,
              double inputPrice, double outputPrice, long contextLength) {
            this.openRouterId = openRouterId;
            this.requestId = requestId;
            this.name = name;
            this.created = created;
            this.inputPrice = inputPrice;
            this.outputPrice = outputPrice;
            this.contextLength = contextLength;
        }

        public boolean hasPrices() {
            return !Double.isNaN(inputPrice) && !Double.isNaN(outputPrice);
        }

        public boolean isFree() {
            return hasPrices() && inputPrice == 0d && outputPrice == 0d;
        }

        double totalTokenPrice() {
            return inputPrice + outputPrice;
        }

        public String formattedInputPrice() {
            return formatPerMillion(inputPrice);
        }

        public String formattedOutputPrice() {
            return formatPerMillion(outputPrice);
        }

        private static String formatPerMillion(double perTokenPrice) {
            if (Double.isNaN(perTokenPrice)) {
                return "—";
            }
            double perMillion = perTokenPrice * 1_000_000d;
            if (perMillion == 0d) {
                return "$0";
            }
            String formatted = String.format(Locale.US, "%.4f", perMillion);
            int decimal = formatted.indexOf('.');
            while (formatted.length() > decimal + 3 && formatted.endsWith("0")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
            return "$" + formatted;
        }
    }
}
