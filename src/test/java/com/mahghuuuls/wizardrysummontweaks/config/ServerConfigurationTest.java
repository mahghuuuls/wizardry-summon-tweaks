package com.mahghuuuls.wizardrysummontweaks.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigurationTest {
    @TempDir Path directory;
    private void write(Path file, String text) throws Exception { Files.write(file, text.getBytes(StandardCharsets.UTF_8)); }

    @Test void shippedDefaultsAreGeneratedAndAllEnabled() throws Exception {
        Path file = directory.resolve("wizardrysummontweaks.cfg");
        List<String> errors = new ArrayList<>();
        ServerRules rules = new ServerConfiguration(file).activate(errors::add);
        assertTrue(rules.suppressSummonDrops && rules.protectPlayers && rules.protectSummons);
        assertTrue(errors.isEmpty());
        assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("world reopening"));
    }

    @Test void eachInvalidBooleanFallsBackWithoutReplacingFalseSiblings() throws Exception {
        String[] keys = {"suppressSummonDrops", "protectPlayers", "protectSummons"};
        for (int invalid = 0; invalid < 3; invalid++) {
            Path file = directory.resolve("values" + invalid + ".cfg");
            String text = "general {\n";
            for (int k = 0; k < 3; k++) text += "B:" + keys[k] + "=" + (k == invalid ? "broken" : "false") + "\n";
            text += "}\n";
            write(file, text);
            List<String> errors = new ArrayList<>();
            ServerRules rules = new ServerConfiguration(file).activate(errors::add);
            assertEquals(invalid == 0, rules.suppressSummonDrops);
            assertEquals(invalid == 1, rules.protectPlayers);
            assertEquals(invalid == 2, rules.protectSummons);
            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains(keys[invalid]));
            assertEquals(text, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }
    }

    @Test void activeSnapshotDoesNotChangeUntilActivation() throws Exception {
        Path file = directory.resolve("values.cfg");
        ServerConfiguration config = new ServerConfiguration(file);
        ServerRules original = config.activate(message -> fail(message));
        write(file, "general {\nB:protectPlayers=false\n}\n");
        assertTrue(original.protectPlayers);
        assertFalse(config.activate(message -> fail(message)).protectPlayers);
        assertTrue(original.protectPlayers);
    }

    @Test void malformedWholeFileRemainsUnchangedAndReportsOnce() throws Exception {
        Path file = directory.resolve("bad.cfg");
        String bad = "general {\nB:protectPlayers=false\n";
        write(file, bad);
        List<String> errors = new ArrayList<>();
        ServerRules rules = new ServerConfiguration(file).activate(errors::add);
        assertTrue(rules.protectPlayers && rules.protectSummons && rules.suppressSummonDrops);
        assertEquals(1, errors.size());
        assertEquals(bad, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
}
