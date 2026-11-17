package com.mahghuuuls.wizardrysummontweaks;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class CoreBoundaryTest {
    @Test void optionalAndClientTypesStayOutOfCoreAndDevelopmentToolsOutOfProduct() throws Exception {
        Path root = Paths.get("src/main/java");
        List<Path> sources;
        try (Stream<Path> files = Files.walk(root)) {
            sources = files.filter(file -> file.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertFalse(sources.isEmpty());
        for (Path source : sources) {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            assertFalse(text.contains("import net.minecraft.client."), source.toString());
            assertFalse(java.util.regex.Pattern.compile("(?m)^import [^;\\r\\n]*(?:threatworks|agenttesttoolkit|devtool)\\.").matcher(text).find(), source.toString());
            if (!source.toString().replace('\\', '/').contains("/compat/")) {
                assertFalse(java.util.regex.Pattern.compile("(?m)^import [^;\\r\\n]*(?:electroblob|wizardryutils|spellbundle|mod_lavacow)\\.").matcher(text).find(), source.toString());
            }
        }
    }
}
