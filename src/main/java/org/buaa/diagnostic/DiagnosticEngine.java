package org.buaa.diagnostic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class DiagnosticEngine {

    /*
     * TreeMap 自动按照行号升序排列。
     *
     * 文法保证每一行最多只有一个错误。
     */
    private final Map<Integer, ErrorType> errors =
            new TreeMap<>();

    public void report(
            int line,
            ErrorType type
    ) {
        errors.putIfAbsent(line, type);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void writeTo(Path path)
            throws IOException {

        StringBuilder output =
                new StringBuilder();

        for (Map.Entry<Integer, ErrorType> entry
                : errors.entrySet()) {

            output
                    .append(entry.getKey())
                    .append(' ')
                    .append(entry.getValue().code())
                    .append(System.lineSeparator());
        }

        Files.writeString(
                path,
                output.toString()
        );
    }
}