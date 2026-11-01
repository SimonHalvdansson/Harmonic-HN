import com.google.ai.edge.litertlm.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.List;
import java.util.HashMap;
import com.simon.harmonichackernews.categorization.ArticleEmbeddingInput;

/** Produces real vectors with the exact mobile model/runtime; keep the model outside the repo. */
public class EmbedArticles {
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 5) throw new IllegalArgumentException("Usage: EmbedArticles MODEL CORPUS OUTPUT [document|classification|clustering] [REUSE_JSON]");
        String format = args.length >= 4 ? args[3] : "document";
        if (!List.of("document", "classification", "clustering").contains(format)) throw new IllegalArgumentException("Unknown input format");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var stream = new java.security.DigestInputStream(Files.newInputStream(Path.of(args[0])), digest)) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
        }
        var modelHash = java.util.HexFormat.of().formatHex(digest.digest());
        var corpus = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var reused = new HashMap<String, JsonElement>();
        if (args.length == 5) for (var element : JsonParser.parseString(Files.readString(Path.of(args[4]))).getAsJsonArray()) {
            var row = element.getAsJsonObject();
            if (!row.has("model_sha256") || !row.get("model_sha256").getAsString().equals(modelHash)) continue;
            reused.put(row.get("input").getAsString(), row.get("embedding"));
            if (row.has("title_input")) reused.put(row.get("title_input").getAsString(), row.get("title_embedding"));
        }
        var vectors = new JsonArray();
        var cache = Path.of(args[2]).toAbsolutePath().getParent().resolve("embedding-cache");
        Files.createDirectories(cache);
        var config = new EmbeddingEngineConfig(args[0], new Backend.CPU(1, null), null, null,
            cache.toString(), null, 256);
        var engine = new EmbeddingEngine(config);
        engine.initialize();
        try (engine) {
            for (var element : corpus.getAsJsonArray("examples")) {
                var example = element.getAsJsonObject();
                var title = ArticleEmbeddingInput.INSTANCE.headline(example.get("title").getAsString());
                var description = ArticleEmbeddingInput.INSTANCE.details(title, example.get("description").getAsString());
                var input = input(title, description, format);
                var titleInput = input(title, "", format);
                var result = new JsonObject();
                result.add("hn_id", example.get("hn_id"));
                result.addProperty("input", input);
                result.addProperty("input_format", format.equals("document") ? ArticleEmbeddingInput.VERSION : format);
                result.addProperty("model_sha256", modelHash);
                result.addProperty("source_sha256", java.util.HexFormat.of().formatHex(digest.digest(
                    (example.get("title").getAsString() + "\u0000" + example.get("description").getAsString()).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                result.add("embedding", embedding(engine, input, reused, gson));
                // Also measure title-only behavior, which the app uses before preview metadata arrives.
                result.addProperty("title_input", titleInput);
                result.add("title_embedding", embedding(engine, titleInput, reused, gson));
                vectors.add(result);
                System.out.println("Embedded HN " + example.get("hn_id") + " (" + vectors.size() + ")");
            }
        }
        Files.writeString(Path.of(args[2]), gson.toJson(vectors));
    }

    private static JsonElement embedding(EmbeddingEngine engine, String input, HashMap<String, JsonElement> reused, Gson gson) {
        return reused.computeIfAbsent(input, key -> gson.toJsonTree(engine.computeEmbedding(
            List.of(new InputData.Text(key)), new EmbeddingOptions(true, null, 768)).getEmbedding()));
    }

    private static String input(String title, String description, String format) {
        if (format.equals("document")) return ArticleEmbeddingInput.INSTANCE.format(title, description);
        return "task: " + format + " | query: " + title + (description.isBlank() ? "" : "\n\n" + description);
    }

}
