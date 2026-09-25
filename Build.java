import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;

/** Offline, cross-platform build: java Build.java [build|test|clean]. Requires JDK 17+. */
public final class Build {
    private static final Path OUTPUT = Path.of("build");
    public static void main(String[] args) throws Exception {
        String task = args.length == 0 ? "build" : args[0];
        if (args.length > 1 || !List.of("build", "test", "clean").contains(task)) {
            System.err.println("Usage: java Build.java [build|test|clean]"); System.exit(2);
        }
        clean();
        if (task.equals("clean")) return;
        compile(Path.of("src/main/java"), OUTPUT.resolve("classes"), null);
        copyExamples();
        if (task.equals("test")) {
            compile(Path.of("src/test/java"), OUTPUT.resolve("test-classes"), OUTPUT.resolve("classes").toString());
            String executable = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            String classpath = OUTPUT.resolve("classes") + java.io.File.pathSeparator + OUTPUT.resolve("test-classes");
            int status = new ProcessBuilder(executable, "-Djava.awt.headless=true", "-cp", classpath,
                    "dev.sjavainspector.TestSuite").inheritIO().start().waitFor();
            if (status != 0) System.exit(status);
        }
        packageJar();
        System.out.println("Built build/flowlens.jar");
    }
    private static void compile(Path source, Path destination, String classpath) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Install JDK 17+; a Java runtime alone cannot compile sources.");
        Files.createDirectories(destination);
        List<String> options = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-Werror", "-d", destination.toString()));
        if (classpath != null) options.addAll(List.of("-classpath", classpath));
        try (var files = Files.walk(source)) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> options.add(p.toString()));
        }
        if (compiler.run(null, null, null, options.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("Compilation failed");
        }
    }
    private static void clean() throws IOException {
        if (!Files.exists(OUTPUT)) return;
        try (var paths = Files.walk(OUTPUT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    private static void copyExamples() throws IOException {
        Path target = OUTPUT.resolve("classes/examples"); Files.createDirectories(target);
        try (var files = Files.list(Path.of("examples"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sjava")).sorted().toList()) {
                Files.copy(file, target.resolve(file.getFileName()));
            }
        }
    }
    private static void packageJar() throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "dev.sjavainspector.cli.Main");
        try (var output = new JarOutputStream(Files.newOutputStream(OUTPUT.resolve("flowlens.jar")), manifest);
             var paths = Files.walk(OUTPUT.resolve("classes"))) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = OUTPUT.resolve("classes").relativize(file).toString().replace('\\', '/');
                JarEntry entry = new JarEntry(name); entry.setTime(0);
                output.putNextEntry(entry); Files.copy(file, output); output.closeEntry();
            }
        }
    }
}
