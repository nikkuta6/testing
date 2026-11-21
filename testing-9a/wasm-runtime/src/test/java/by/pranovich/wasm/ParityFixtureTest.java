package by.pranovich.wasm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exports actual JVM results for all source fixtures, for comparison with the compiled Wasm binary. */
class ParityFixtureTest {
    @Test
    void exportExistingLanguageFixtures() throws Exception {
        Path sources = Path.of(System.getProperty("languageTestSources"));
        Set<String> programs = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(sources)) {
            for (Path file : paths.filter(p -> p.toString().endsWith("Test.java")).sorted().toList()) {
                String name = sources.relativize(file).toString().replace('\\', '.').replace('/', '.').replaceAll("\\.java$", "");
                Class<?> type = Class.forName(name);
                for (var method : type.getDeclaredMethods()) {
                    ValueSource values = method.getAnnotation(ValueSource.class);
                    if (values != null) programs.addAll(java.util.List.of(values.strings()));
                    if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
                            && Stream.class.isAssignableFrom(method.getReturnType())) {
                        method.setAccessible(true);
                        try (Stream<?> stream = (Stream<?>) method.invoke(null)) {
                            stream.forEach(value -> {
                                Object first = value instanceof Arguments args ? args.get()[0] : value;
                                if (first instanceof String source) programs.add(source);
                            });
                        }
                    }
                }
            }
        }
        // Additional cross-platform regressions and complete debugger/graphics examples.
        programs.add("print(5 <= 1, 5 < 1, 5 > 1, 5 >= 1, 5 == 1, 5 <> 1);");
        programs.add("canvas(100, 80); color(255, 0, 0); line(0, 0, 10, 20); circle(30, 30, 10); clear();");
        assertTrue(programs.size() > 300, "The regression fixtures were not discovered");
        StringBuilder json = new StringBuilder("[");
        int count = 0;
        for (String source : programs) {
            if (count++ > 0) json.append(',');
            json.append("{\"source\":").append(RunService.quote(source))
                    .append(",\"expected\":").append(RunService.run(source, true)).append('}');
        }
        Path output = Path.of(System.getProperty("parityOutput"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.append(']'), StandardCharsets.UTF_8);
        System.out.println("Exported " + count + " JVM fixtures to " + output);
    }
}
