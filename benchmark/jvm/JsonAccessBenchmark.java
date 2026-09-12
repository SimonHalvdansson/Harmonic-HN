import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.serialization.JsonArray;
import com.simon.harmonichackernews.serialization.JsonObject;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Standalone, opt-in JVM microbenchmark. Run through run-json-access.py. */
public final class JsonAccessBenchmark {
    private static final JsonObject[] objects = new JsonObject[64];
    private static final JsonArray[] arrays = new JsonArray[64];
    private static final JsonObject[] fallbacks = new JsonObject[64];
    private static final String[] summaries = new String[64];
    private static volatile long sink;
    private static volatile Object objectSink;
    private static final String[] cases = {
        "objectGetInteger", "arrayGetInteger", "objectOptInt", "objectOptLong",
        "objectMismatch", "arrayMismatch", "objectMatch", "cachedStory",
        "objectOptIntFallback", "objectOptLongFallback"
    };

    static {
        String[] unusualNumbers = {
            "1e2", "1.5", "null", "\"+42\"", "\"٤٢\"", "true", "{}", "9223372036854775808"
        };
        for (int i = 0; i < objects.length; i++) {
            objects[i] = new JsonObject("{\"id\":" + (43000000 + i)
                + ",\"time\":" + (1700000000L + i) + ",\"value\":{\"id\":" + i + "}}");
            arrays[i] = new JsonArray("[" + (43000000 + i) + "]");
            fallbacks[i] = new JsonObject("{\"value\":" + unusualNumbers[i & 7] + "}");
            summaries[i] = "{\"cache_version\":1,\"id\":" + (43000000 + i)
                + ",\"type\":\"story\",\"title\":\"A cached story " + i
                + "\",\"author\":\"reader\",\"points\":237,\"created_at_i\":1700000000,"
                + "\"descendants\":81,\"kids\":[43000100,43000101,43000102],"
                + "\"url\":\"https://example.com/article\"}";
        }
    }

    private static long run(String name, int operations) {
        long checksum = 0;
        switch (name) {
            case "objectGetInteger":
                for (int i = 0; i < operations; i++) {
                    Object value = objects[i & 63].get("id");
                    objectSink = value; // Make boxed results escape, as the public Any? API permits.
                    checksum += (Long) value;
                }
                break;
            case "arrayGetInteger":
                for (int i = 0; i < operations; i++) {
                    Object value = arrays[i & 63].get(0);
                    objectSink = value;
                    checksum += (Long) value;
                }
                break;
            case "objectOptInt":
                for (int i = 0; i < operations; i++) checksum += objects[i & 63].optInt("id", 0);
                break;
            case "objectOptLong":
                for (int i = 0; i < operations; i++) checksum += objects[i & 63].optLong("time", 0L);
                break;
            case "objectOptIntFallback":
                for (int i = 0; i < operations; i++) checksum += fallbacks[i & 63].optInt("value", 0);
                break;
            case "objectOptLongFallback":
                for (int i = 0; i < operations; i++) checksum += fallbacks[i & 63].optLong("value", 0L);
                break;
            case "objectMismatch":
                for (int i = 0; i < operations; i++) {
                    if (objects[i & 63].optJSONObject("id") == null) checksum++;
                }
                break;
            case "arrayMismatch":
                for (int i = 0; i < operations; i++) {
                    if (objects[i & 63].optJSONArray("id") == null) checksum++;
                }
                break;
            case "objectMatch":
                for (int i = 0; i < operations; i++) {
                    JsonObject value = objects[i & 63].optJSONObject("value");
                    objectSink = value;
                    checksum += value.length();
                }
                break;
            case "cachedStory":
                for (int i = 0; i < operations; i++) {
                    Story story = new Story();
                    if (!JSONParser.INSTANCE.updateStoryWithCachedStorySummary(story, summaries[i & 63])) {
                        throw new AssertionError("Fixture failed to parse");
                    }
                    objectSink = story;
                    checksum += story.getId() + story.getScore() + story.getKids().length;
                }
                break;
            default: throw new IllegalArgumentException(name);
        }
        sink = checksum;
        return checksum;
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        ThreadMXBean allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        // Each process rotates case order to reduce systematic order effects.
        int rotation = Integer.parseInt(args[0]);
        String[] selectedCases = args.length > 1 ? args[1].split(",") : cases;
        for (int index = 0; index < selectedCases.length; index++) {
            String name = selectedCases[(index + rotation) % selectedCases.length];
            long deadline = System.nanoTime() + 1_000_000_000L;
            do { run(name, 1024); } while (System.nanoTime() < deadline);
            long start = System.nanoTime();
            run(name, 8192);
            long elapsed = System.nanoTime() - start;
            int operations = (int) Math.max(1024, Math.min(50_000_000L,
                200_000_000L * 8192 / Math.max(1, elapsed)));
            double[] times = new double[7];
            double[] bytes = new double[7];
            for (int sample = 0; sample < times.length; sample++) {
                long beforeBytes = allocation.getThreadAllocatedBytes(thread);
                start = System.nanoTime();
                run(name, operations);
                times[sample] = (System.nanoTime() - start) / (double) operations;
                bytes[sample] = (allocation.getThreadAllocatedBytes(thread) - beforeBytes) / (double) operations;
            }
            System.out.printf("{\"case\":\"%s\",\"operationsPerSample\":%d,\"nsPerOp\":%s,\"bytesPerOp\":%s}%n",
                name, operations, Arrays.toString(times), Arrays.toString(bytes));
        }
    }
}
