package org.buaa;

import org.buaa.diagnostic.DiagnosticEngine;
import org.buaa.lexer.Lexer;
import org.buaa.lexer.Token;
import org.buaa.lexer.TokenType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    private static final Path INPUT_FILE =
            Path.of("testfile.txt");

    private static final Path LEXER_OUTPUT =
            Path.of("lexer.txt");

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

            /*
             * 如果存在词法错误：
             *
             * 只输出 error.txt
             */
            if (diagnostics.hasErrors()) {

                diagnostics.writeTo(ERROR_OUTPUT);

                /*
                 * 防止上一次运行遗留 lexer.txt。
                 */
                Files.deleteIfExists(LEXER_OUTPUT);

            } else {

                /*
                 * 正确程序：
                 *
                 * 只输出 lexer.txt
                 */
                writeLexerResult(tokens);

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

    private static void writeLexerResult(
            List<Token> tokens
    ) throws IOException {

        StringBuilder output =
                new StringBuilder();

        for (Token token : tokens) {

            /*
             * EOF 只是编译器内部 Token，
             * 不属于题目要求的词法分析输出。
             */
            if (token.type() == TokenType.EOF) {
                continue;
            }

            output
                    .append(token.type())
                    .append(' ')
                    .append(token.text())
                    .append(System.lineSeparator());
        }

        Files.writeString(
                LEXER_OUTPUT,
                output.toString()
        );
    }
}