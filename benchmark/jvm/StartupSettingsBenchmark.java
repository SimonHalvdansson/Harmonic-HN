import com.simon.harmonichackernews.network.NitterInstance;
import com.simon.harmonichackernews.settings.AppSettings;
import com.simon.harmonichackernews.settings.AppSettingsRepository;
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore;
import com.simon.harmonichackernews.settings.ReadingPreferences;
import com.simon.harmonichackernews.settings.StoredSettingsMutator;
import com.simon.harmonichackernews.settings.StoredUserSettings;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Production before/after jars; run with run-json-access.py --harness <this file>. */
public final class StartupSettingsBenchmark {
    private static final String[] defaults = new String[64];
    private static final String[] custom = new String[64];
    private static final StoredUserSettings[] readers = new StoredUserSettings[2];
    private static final AppSettingsRepository[] repositories = new AppSettingsRepository[2];
    private static volatile Object objectSink;
    private static volatile long sink;
    private static final String[] cases = {
        "defaultUrl", "customUrl", "readingDefaults", "readingCustom",
        "snapshotDefaults", "snapshotCustom"
    };

    static {
        for (int i = 0; i < defaults.length; i++) {
            // Include strings loaded from storage, not just an interned constant.
            defaults[i] = new String("https://nitter.net".toCharArray());
            custom[i] = "https://nitter" + i + ".example.org";
        }
        for (int i = 0; i < readers.length; i++) {
            InMemoryKeyValueStore store = new InMemoryKeyValueStore();
            if (i == 1) store.putString("pref_nitter_instance_url", custom[0]);
            readers[i] = new StoredUserSettings(
                store, store.getChanges(), () -> "material_light", false, true);
            repositories[i] = new AppSettingsRepository(readers[i], new StoredSettingsMutator(store));
        }
        String[] inputs = {
            defaults[0], " https://nitter.net/ ", "HTTPS://NITTER.NET", "https://nitter.net:443",
            custom[0], "http://localhost:8080/", "https://nitter.net.evil.org",
            "https://nitter.net/path", "https://user:password@nitter.net", "https://",
            "https://nitter.net?x=1", "https://nitter.net#fragment", "https://nitter.net:99999",
            "https://bad host.org", "https://nitter.net\\path", "ftp://nitter.net", ""
        };
        for (String value : inputs) {
            String expected = NitterInstance.INSTANCE.normalize(value);
            if (expected == null) expected = NitterInstance.DEFAULT_URL;
            if (!expected.equals(NitterInstance.INSTANCE.effectiveUrl(value))) {
                throw new AssertionError("Normalization changed: " + value);
            }
        }
        for (int i = 0; i < readers.length; i++) {
            String expected = i == 0 ? NitterInstance.DEFAULT_URL : custom[0];
            if (!expected.equals(repositories[i].snapshot().getReading().getNitterInstanceUrl())) {
                throw new AssertionError("Settings fixture failed");
            }
        }
    }

    private static void run(String name, int operations) {
        long checksum = 0;
        switch (name) {
            case "defaultUrl":
            case "customUrl": {
                String[] values = name.equals("defaultUrl") ? defaults : custom;
                for (int i = 0; i < operations; i++) {
                    String value = NitterInstance.INSTANCE.effectiveUrl(values[i & 63]);
                    objectSink = value;
                    checksum += value.length();
                }
                break;
            }
            case "readingDefaults":
            case "readingCustom": {
                StoredUserSettings reader = readers[name.equals("readingDefaults") ? 0 : 1];
                for (int i = 0; i < operations; i++) {
                    ReadingPreferences value = reader.getReading();
                    objectSink = value;
                    checksum += value.getNitterInstanceUrl().length();
                }
                break;
            }
            case "snapshotDefaults":
            case "snapshotCustom": {
                AppSettingsRepository repository = repositories[name.equals("snapshotDefaults") ? 0 : 1];
                for (int i = 0; i < operations; i++) {
                    AppSettings value = repository.snapshot();
                    objectSink = value;
                    checksum += value.getReading().getNitterInstanceUrl().length();
                }
                break;
            }
            default: throw new IllegalArgumentException(name);
        }
        sink = checksum;
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        ThreadMXBean allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
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
