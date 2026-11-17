package com.mahghuuuls.wizardrysummontweaks.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the small Forge-style three-Boolean layout. Forge's Boolean getters coerce invalid values,
 * and its constructor can rename malformed files; parsing this layout preserves the edited input.
 */
public final class ServerConfiguration {
    private static final Pattern VALUE = Pattern.compile("B:([A-Za-z][A-Za-z0-9]*)\\s*=\\s*(.*)");
    private final Path file;

    public ServerConfiguration(Path file) { this.file = file; }

    public ServerRules activate(Consumer<String> report) {
        try {
            if (!Files.exists(file)) {
                Path parent = file.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.write(file, defaultFile().getBytes(StandardCharsets.UTF_8));
            }
            return parse(Files.readAllLines(file, StandardCharsets.UTF_8), report);
        } catch (IOException | IllegalArgumentException failure) {
            report.accept("Cannot load " + file.getFileName() + ": " + failure.getMessage()
                    + ". All three rules use true for this server opening; original input is preserved.");
            return ServerRules.DEFAULTS;
        }
    }

    static ServerRules parse(List<String> lines, Consumer<String> report) {
        Map<String, String> values = new HashMap<>();
        boolean inside = false;
        boolean seen = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("\uFEFF")) line = line.substring(1).trim();
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment).trim();
            if (line.isEmpty()) continue;
            if (line.equals("general {") || line.equals("general{")) {
                if (inside || seen) throw new IllegalArgumentException("duplicate general category");
                inside = true;
                seen = true;
            } else if (line.equals("}")) {
                if (!inside) throw new IllegalArgumentException("unexpected closing brace");
                inside = false;
            } else {
                Matcher match = VALUE.matcher(line);
                if (!inside || !match.matches()) throw new IllegalArgumentException("expected general { B:key=value } layout");
                if (values.put(match.group(1), match.group(2).trim()) != null) {
                    throw new IllegalArgumentException("duplicate setting " + match.group(1));
                }
            }
        }
        if (inside || !seen) throw new IllegalArgumentException("missing or unclosed general category");
        return new ServerRules(read(values, "suppressSummonDrops", report), read(values, "protectPlayers", report),
                read(values, "protectSummons", report));
    }

    private static boolean read(Map<String, String> values, String key, Consumer<String> report) {
        String raw = values.get(key);
        if (raw == null) return true;
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        // Bound the report even if an edited value contains an arbitrarily long string.
        String shown = raw.length() > 80 ? raw.substring(0, 80) + "..." : raw;
        report.accept("Invalid Boolean general." + key + "='" + shown + "'; active fallback is true until restart/reopen.");
        return true;
    }

    private static String defaultFile() {
        return "# Configuration file\n\n"
                + "# Changes apply at dedicated-server restart or singleplayer world reopening.\n"
                + "general {\n"
                + "    # Suppress affected summons' death items and XP, including supplied equipment.\n"
                + "    B:suppressSummonDrops=true\n\n"
                + "    # Prevent affected summons targeting or attacking players.\n"
                + "    B:protectPlayers=true\n\n"
                + "    # Prevent player attacks against affected summons; deliberate sacrifice still works.\n"
                + "    B:protectSummons=true\n"
                + "}\n";
    }
}
