package org.buaa;

import org.buaa.diagnostic.DiagnosticEngine;
import org.buaa.lexer.Lexer;
import org.buaa.lexer.Token;
import org.buaa.parser.Parser;

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

            // 词法报错不能提前终止；完整 AST 留给后续语义分析继续使用。
            Parser parser = new Parser(tokens, diagnostics);
            parser.parse();

            // 当前阶段输出 parser.txt，清理上一阶段的词法输出。
            Files.deleteIfExists(LEXER_OUTPUT);

            if (diagnostics.hasErrors()) {

                diagnostics.writeTo(ERROR_OUTPUT);

                /*
                 * 防止上一次运行遗留 parser.txt。
                 */
                Files.deleteIfExists(PARSER_OUTPUT);

            } else {

                /*
                 * 正确程序：
                 *
                 * 只输出 parser.txt
                 */
                parser.writeTo(PARSER_OUTPUT);

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
