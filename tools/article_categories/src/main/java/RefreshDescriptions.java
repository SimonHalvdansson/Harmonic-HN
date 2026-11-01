import com.fleeksoft.ksoup.Ksoup;
import com.fleeksoft.ksoup.nodes.Document;
import com.google.gson.*;
import com.simon.harmonichackernews.network.HtmlDescriptionExtractor;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Refreshes reviewed records with the same generic HTML preview extractor as the app. */
public class RefreshDescriptions {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: RefreshDescriptions CORPUS OUTPUT");
        var corpus = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build();
        try (var executor = Executors.newFixedThreadPool(6)) {
            var tasks = new ArrayList<Future<?>>();
            for (var element : corpus.getAsJsonArray("examples")) {
                var row = element.getAsJsonObject();
                tasks.add(executor.submit(() -> refresh(client, row)));
            }
            for (var task : tasks) task.get();
        }
        corpus.addProperty("description_pipeline", "HtmlDescriptionExtractor.previewDescription; 600 characters; v1");
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(corpus));
    }

    private static void refresh(HttpClient client, JsonObject row) {
        if (row.has("description_review") && row.get("description_review").getAsString().equals("omit")) {
            row.addProperty("description", "");
            row.addProperty("description_source", "omitted_after_review");
            return;
        }
        var url = row.get("url").getAsString();
        String description = "", source = "unavailable";
        try {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (compatible; HarmonicCategoryReview/1.0)").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var body = response.body()) { bytes = body.readNBytes(1024 * 1024); }
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("HTTP " + response.statusCode());
            var type = response.headers().firstValue("content-type").orElse("");
            if (!type.contains("html")) throw new IllegalStateException("Non-HTML " + type);
            var charset = StandardCharsets.UTF_8;
            var match = java.util.regex.Pattern.compile("charset=[\"']?([^;\\s\"']+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(type);
            if (match.find()) try { charset = Charset.forName(match.group(1)); } catch (Exception ignored) { }
            // Ksoup handles entity decoding and paragraph fallback, exactly as in generic previews.
            Document document = Ksoup.INSTANCE.parse(new String(bytes, charset), response.uri().toString());
            var properties = new HashMap<String, String>();
            var names = new HashMap<String, String>();
            for (var meta : document.select("meta")) {
                properties.putIfAbsent(meta.attr("property"), meta.attr("content"));
                names.putIfAbsent(meta.attr("name"), meta.attr("content"));
            }
            String metadata = first(properties.get("og:description"), names.get("description"), names.get("twitter:description"));
            String title = first(properties.get("og:title"), names.get("twitter:title"), document.title(), row.get("title").getAsString());
            description = HtmlDescriptionExtractor.INSTANCE.previewDescription(metadata, document, title, row.get("title").getAsString());
            source = HtmlDescriptionExtractor.INSTANCE.isMeaningful(metadata, title, row.get("title").getAsString()) ? "metadata" : "body";
            if (!HtmlDescriptionExtractor.INSTANCE.isMeaningful(description, title, row.get("title").getAsString())) {
                description = "";
                source = "unavailable";
            }
            row.addProperty("description_final_url", response.uri().toString());
            row.remove("description_error");
        } catch (Exception error) {
            row.addProperty("description_error", error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        row.addProperty("description", description);
        row.addProperty("description_source", source);
        row.remove("description_truncated");
        System.out.println("Refreshed " + row.get("hn_id") + " " + source + " " + description.length());
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
