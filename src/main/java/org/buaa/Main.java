package org.buaa;

import org.buaa.diagnostic.DiagnosticEngine;
import org.buaa.lexer.Lexer;
import org.buaa.lexer.Token;
import org.buaa.parser.Parser;
import org.buaa.semantic.SemanticAnalyzer;
import org.buaa.semantic.SemanticResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    private static final Path INPUT_FILE =
            Path.of("testfile.txt");

    private static final Path LEXER_OUTPUT =
            Path.of("lexer.txt");

    private static final Path PARSER_OUTPUT =
            Path.of("parser.txt");

    private static final Path SYMBOL_OUTPUT =
            Path.of("symbol.txt");

    private static final Path ERROR_OUTPUT =
            Path.of("error.txt");

    public static void main(String[] args) {

        try {
            String source =
                    Files.readString(INPUT_FILE);

            DiagnosticEngine diagnostics =
                    new DiagnosticEngine();

            Lexer lexer =
                    new Lexer(source, diagnostics);

            List<Token> tokens =
                    lexer.tokenize();

            // 三个阶段共享诊断；即使已有词法/语法错误，也继续分析完整 AST。
            Parser parser = new Parser(tokens, diagnostics);
            SemanticResult semantic = new SemanticAnalyzer(diagnostics).analyze(parser.parse());

            // 当前阶段输出符号或错误，清理先前阶段的输出。
            Files.deleteIfExists(LEXER_OUTPUT);
            Files.deleteIfExists(PARSER_OUTPUT);

            if (diagnostics.hasErrors()) {

                diagnostics.writeTo(ERROR_OUTPUT);

                Files.deleteIfExists(SYMBOL_OUTPUT);

            } else {

                semantic.writeSymbolsTo(SYMBOL_OUTPUT);

                /*
                 * 防止上一次运行遗留 error.txt。
                 */
                Files.deleteIfExists(ERROR_OUTPUT);
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to read or write file",
                    e
            );
        }
    }

}
