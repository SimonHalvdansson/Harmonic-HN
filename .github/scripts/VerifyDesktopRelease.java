import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Run with the processed release JARs as the only classpath, using the JDK source launcher. */
public class VerifyDesktopRelease {
    private static final ClassLoader LOADER = VerifyDesktopRelease.class.getClassLoader();

    public static void main(String[] args) throws Exception {
        verifyApplicationBytecode();
        Properties metadata = new Properties();
        try (InputStream input = resource("harmonic-desktop.properties")) {
            metadata.load(input);
        }
        require(!metadata.getProperty("versionName", "").isBlank(), "Missing release version");
        require(Integer.parseInt(metadata.getProperty("versionCode", "0")) > 0,
                "Missing release version code");
        try (InputStream ignored = resource("harmonic-app-icon.png")) {}

        verifyService("coil3.util.FetcherServiceLoaderTarget");
        verifyService("coil3.util.DecoderServiceLoaderTarget");
        verifyService("io.ktor.client.HttpClientEngineContainer");
        verifyService("kotlinx.coroutines.internal.MainDispatcherFactory");

        // Settings maps stored glass profile names through this dependency's Enum.valueOf.
        verifyEnum(Class.forName("dev.chrisbanes.haze.glass.SurfaceProfile"));

        // Exercise the tokenizer's real field access covered by the narrow warning exception.
        Class<?> tokenizerType = Class.forName("com.hrm.latex.parser.tokenizer.LatexTokenizer");
        Object tokenizer = tokenizerType.getConstructor(String.class, int.class)
                .newInstance("x^2 + \\frac{1}{2} + \\text{hello world}", 0);
        require(!((java.util.List<?>) tokenizerType.getMethod("tokenize").invoke(tokenizer)).isEmpty(),
                "Inline math tokenization failed");

        // Structure fields and their FieldOrder annotation are read reflectively by JNA,
        // including on hosts where the Windows credential API itself cannot be loaded.
        Structure credential = (Structure) Class.forName(
                "com.simon.harmonichackernews.app.WindowsCredential")
                .getConstructor().newInstance();
        require(credential.size() > 0, "Invalid native credential layout");

        verifyNativeLibrary("harmonic-local-ai",
                "com.simon.harmonichackernews.summary.HarmonicLlamaApi", true);
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            verifyNativeLibrary("harmonic-mac-webview",
                    "com.simon.harmonichackernews.desktop.MacWebViewApi", false);
        } else if (os.contains("win")) {
            // Initialize SWT's JNI layer without opening a browser or a window.
            Class.forName("org.eclipse.swt.internal.win32.OS");
        }
        System.out.println("Release bytecode, enums, metadata, service providers, JNA structures, and native bindings passed");
    }

    private static void verifyApplicationBytecode() throws Exception {
        int count = 0;
        for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) {
            if (!entry.endsWith(".jar")) continue;
            try (JarFile jar = new JarFile(entry)) {
                for (var entries = jar.entries(); entries.hasMoreElements();) {
                    String name = entries.nextElement().getName();
                    if (!name.startsWith("com/simon/harmonichackernews/") || !name.endsWith(".class")) continue;
                    Class<?> type = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, LOADER);
                    // Loading alone is lazy; resolving members forces JVM bytecode verification.
                    // Do not initialize platform-specific classes on the wrong operating system.
                    type.getDeclaredMethods();
                    type.getDeclaredConstructors();
                    if (type.isEnum()) verifyEnum(type);
                    count++;
                }
            }
        }
        require(count > 0, "No processed application classes found");
        System.out.println("Verified " + count + " processed application classes");
    }

    private static void verifyEnum(Class<?> type) throws Exception {
        Object[] constants = type.getEnumConstants();
        require(constants != null, "Missing enum values() for " + type.getName());
        var valueOf = type.getMethod("valueOf", String.class);
        valueOf.setAccessible(true);
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            require(valueOf.invoke(null, name) == constant,
                    "Broken enum lookup for " + type.getName() + "." + name);
        }
    }

    private static void verifyService(String serviceName) throws Exception {
        Class<?> service = Class.forName(serviceName);
        int count = 0;
        for (Object provider : ServiceLoader.load(service, LOADER)) {
            require(service.isInstance(provider), "Invalid provider for " + serviceName);
            count++;
        }
        require(count > 0, "No release providers for " + serviceName);
    }

    private static void verifyNativeLibrary(String name, String interfaceName, boolean testSession)
            throws Exception {
        Path directory = Files.createTempDirectory("harmonic-release-native-");
        Path path = directory.resolve(System.mapLibraryName(name));
        try (InputStream input = resource("native/" + path.getFileName())) {
            Files.copy(input, path);
        }
        try (NativeLibrary library = NativeLibrary.getInstance(path.toString())) {
            Class<? extends Library> api = Class.forName(interfaceName).asSubclass(Library.class);
            for (var method : api.getDeclaredMethods()) {
                library.getFunction(method.getName());
            }
            if (testSession) {
                Library binding = Native.load(path.toString(), api);
                api.getMethod("harmonic_llama_backend_initialize", Pointer.class, Pointer.class)
                        .invoke(binding, null, null);
                Pointer engine = (Pointer) api.getMethod("harmonic_llama_create").invoke(binding);
                require(engine != null, "Native inference session allocation failed");
                try {
                    api.getMethod("harmonic_llama_close", Pointer.class).invoke(binding, engine);
                } finally {
                    api.getMethod("harmonic_llama_destroy", Pointer.class).invoke(binding, engine);
                }
            }
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
    }

    private static InputStream resource(String name) {
        InputStream input = LOADER.getResourceAsStream(name);
        require(input != null, "Missing release resource: " + name);
        return input;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
