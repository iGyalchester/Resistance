package com.resistance.mvc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two files describe the tracker's tables: the dev/CI init script
 * (drops, creates, seeds) and the idempotent schema the services apply on
 * AWS (CREATE TABLE IF NOT EXISTS, nothing else). This test keeps their
 * CREATE TABLE bodies identical so a column added to one cannot be
 * forgotten in the other.
 */
class SchemaFilesInSyncTests {

    private static final Path DEV_INIT =
            Path.of("../../infrastructure/config/db-init/02-job-tracker.sql");
    private static final Path IDEMPOTENT =
            Path.of("../../shared/shared-models/src/main/resources/db/job-tracker-schema.sql");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE TABLE(?: IF NOT EXISTS)? `(\\w+)` \\((.*?)\\) ENGINE=", Pattern.DOTALL);

    @Test
    void idempotentSchemaMatchesDevInitScript() throws IOException {
        Map<String, String> dev = tables(DEV_INIT);
        Map<String, String> aws = tables(IDEMPOTENT);

        assertThat(aws.keySet()).containsExactlyElementsOf(dev.keySet());
        dev.forEach((table, body) ->
                assertThat(aws.get(table)).as("columns of %s", table).isEqualTo(body));
    }

    @Test
    void idempotentSchemaNeverDestroysData() throws IOException {
        String sql = Files.readString(IDEMPOTENT, StandardCharsets.UTF_8).toUpperCase();

        assertThat(sql).doesNotContain("DROP ").doesNotContain("INSERT ").doesNotContain("TRUNCATE ");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS");
    }

    private static Map<String, String> tables(Path file) throws IOException {
        String sql = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = CREATE_TABLE.matcher(sql);
        while (m.find()) {
            result.put(m.group(1), m.group(2).replaceAll("\\s+", " ").trim());
        }
        assertThat(result).as("CREATE TABLE statements in %s", file).isNotEmpty();
        return result;
    }
}
