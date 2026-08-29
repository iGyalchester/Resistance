package com.resistance.shared.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal CSV helpers used by the ETL modules. Supports quoted fields
 * with embedded commas and doubled quotes; no external dependency needed
 * for the simple files this project processes.
 */
public final class CsvUtils {

    private CsvUtils() {
    }

    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null) {
            return fields;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }

    public static boolean isBlankLine(String line) {
        return line == null || line.isBlank();
    }
}
