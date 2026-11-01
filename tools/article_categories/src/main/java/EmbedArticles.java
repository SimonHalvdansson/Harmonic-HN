import com.google.ai.edge.litertlm.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.List;

/** Produces real vectors with the exact mobile model/runtime; keep the model outside the repo. */
public class EmbedArticles {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: EmbedArticles MODEL CORPUS OUTPUT");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var corpus = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
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
                var title = clean(example.get("title").getAsString(), 300);
                var description = clean(example.get("description").getAsString(), 900);
                var input = "title: " + title + " | text: " + (description.isBlank() ? title : description);
                var response = engine.computeEmbedding(List.of(new InputData.Text(input)), new EmbeddingOptions(true, null, 768));
                var result = new JsonObject();
                result.add("hn_id", example.get("hn_id"));
                result.addProperty("input", input);
                result.add("embedding", gson.toJsonTree(response.getEmbedding()));
                // Also measure title-only behavior, which the app uses before preview metadata arrives.
                result.add("title_embedding", gson.toJsonTree(engine.computeEmbedding(
                    List.of(new InputData.Text("title: " + title + " | text: " + title)),
                    new EmbeddingOptions(true, null, 768)).getEmbedding()));
                vectors.add(result);
                System.out.println("Embedded HN " + example.get("hn_id") + " (" + vectors.size() + ")");
            }
        }
        Files.writeString(Path.of(args[2]), gson.toJson(vectors));
    }

    private static String clean(String value, int maxLength) {
        var cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), maxLength));
    }
}
