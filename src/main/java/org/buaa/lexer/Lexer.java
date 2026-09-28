package org.buaa.lexer;

import org.buaa.diagnostic.DiagnosticEngine;
import org.buaa.diagnostic.ErrorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {

    /*
     * ============================================================
     * 关键字表
     * ============================================================
     */
    private static final Map<String, TokenType> KEYWORDS =
            Map.ofEntries(

                    Map.entry("char", TokenType.CHARTK),
                    Map.entry("void", TokenType.VOIDTK),
                    Map.entry("int", TokenType.INTTK),

                    Map.entry("continue", TokenType.CONTINUETK),
                    Map.entry("break", TokenType.BREAKTK),
                    Map.entry("default", TokenType.DEFAULTTK),
                    Map.entry("return", TokenType.RETURNTK),
                    Map.entry("while", TokenType.WHILETK),
                    Map.entry("case", TokenType.CASETK),
                    Map.entry("else", TokenType.ELSETK),
                    Map.entry("if", TokenType.IFTK),
                    Map.entry("switch", TokenType.SWITCHTK),

                    Map.entry("printf", TokenType.PRINTFTK),

                    Map.entry("static", TokenType.STATICTK),
                    Map.entry("const", TokenType.CONSTTK),

                    Map.entry("main", TokenType.MAINTK)
            );

    /*
     * ============================================================
     * Lexer 状态
     * ============================================================
     */

    /**
     * 完整源程序。
     */
    private final String source;

    /**
     * 错误处理模块。
     */
    private final DiagnosticEngine diagnostics;

    /**
     * 已生成的 Token。
     */
    private final List<Token> tokens =
            new ArrayList<>();

    /**
     * 当前字符在 source 中的下标。
     */
    private int index = 0;

    /**
     * 当前行号。
     *
     * 从 1 开始。
     */
    private int line = 1;

    /**
     * 当前列号。
     *
     * 从 1 开始。
     */
    private int column = 1;

    /*
     * ============================================================
     * Constructor
     * ============================================================
     */

    public Lexer(
            String source,
            DiagnosticEngine diagnostics
    ) {
        this.source = source;
        this.diagnostics = diagnostics;
    }

    /*
     * ============================================================
     * Public API
     * ============================================================
     */

    /**
     * 对整个源文件执行词法分析。
     */
    public List<Token> tokenize() {

        while (!isAtEnd()) {

            /*
             * 先跳过：
             *
             * 空白符
             * 单行注释
             * 多行注释
             */
            skipWhitespaceAndComments();

            if (isAtEnd()) {
                break;
            }

            scanToken();
        }

        /*
         * 添加 EOF。
         */
        tokens.add(
                new Token(
                        TokenType.EOF,
                        "",
                        line,
                        column
                )
        );

        return List.copyOf(tokens);
    }

    /*
     * ============================================================
     * Token 扫描
     * ============================================================
     */

    private void scanToken() {

        /*
         * 保存当前 Token 开始位置。
         */
        int startIndex = index;
        int startLine = line;
        int startColumn = column;

        char c = peek();

        /*
         * --------------------------------------------------------
         * Identifier / Keyword
         * --------------------------------------------------------
         */
        if (isIdentifierStart(c)) {

            scanIdentifier(
                    startIndex,
                    startLine,
                    startColumn
            );

            return;
        }

        /*
         * --------------------------------------------------------
         * Integer Constant
         * --------------------------------------------------------
         */
        if (isDigit(c)) {

            scanInteger(
                    startIndex,
                    startLine,
                    startColumn
            );

            return;
        }

        /*
         * --------------------------------------------------------
         * Character Constant
         * --------------------------------------------------------
         */
        if (c == '\'') {

            scanCharConstant(
                    startIndex,
                    startLine,
                    startColumn
            );

            return;
        }

        /*
         * --------------------------------------------------------
         * String Constant
         * --------------------------------------------------------
         */
        if (c == '"') {

            scanStringConstant(
                    startIndex,
                    startLine,
                    startColumn
            );

            return;
        }

        /*
         * --------------------------------------------------------
         * Operators / Delimiters
         * --------------------------------------------------------
         */

        switch (c) {

            /*
             * +
             */
            case '+' -> {

                advance();

                addToken(
                        TokenType.PLUS,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * -
             */
            case '-' -> {

                advance();

                addToken(
                        TokenType.MINU,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * *
             */
            case '*' -> {

                advance();

                addToken(
                        TokenType.MULT,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * /
             *
             * 注释已经在 skipWhitespaceAndComments()
             * 中处理。
             */
            case '/' -> {

                advance();

                addToken(
                        TokenType.DIV,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * %
             */
            case '%' -> {

                advance();

                addToken(
                        TokenType.MOD,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * =
             * ==
             */
            case '=' -> {

                advance();

                if (match('=')) {

                    addToken(
                            TokenType.EQL,
                            startIndex,
                            startLine,
                            startColumn
                    );

                } else {

                    addToken(
                            TokenType.ASSIGN,
                            startIndex,
                            startLine,
                            startColumn
                    );
                }
            }

            /*
             * !
             * !=
             */
            case '!' -> {

                advance();

                if (match('=')) {

                    addToken(
                            TokenType.NEQ,
                            startIndex,
                            startLine,
                            startColumn
                    );

                } else {

                    addToken(
                            TokenType.NOT,
                            startIndex,
                            startLine,
                            startColumn
                    );
                }
            }

            /*
             * <
             * <=
             */
            case '<' -> {

                advance();

                if (match('=')) {

                    addToken(
                            TokenType.LEQ,
                            startIndex,
                            startLine,
                            startColumn
                    );

                } else {

                    addToken(
                            TokenType.LSS,
                            startIndex,
                            startLine,
                            startColumn
                    );
                }
            }

            /*
             * >
             * >=
             */
            case '>' -> {

                advance();

                if (match('=')) {

                    addToken(
                            TokenType.GEQ,
                            startIndex,
                            startLine,
                            startColumn
                    );

                } else {

                    addToken(
                            TokenType.GRE,
                            startIndex,
                            startLine,
                            startColumn
                    );
                }
            }

            /*
             * &&
             *
             * 特殊规定：
             *
             * 单独出现 & 时：
             *
             * 1. 报 a 类错误
             * 2. 仍然按照 && 处理
             * 3. Token.text 保持 "&"
             */
            case '&' -> {

                advance();

                if (match('&')) {

                    addToken(
                            TokenType.AND,
                            startIndex,
                            startLine,
                            startColumn
                    );

                } else {

                    diagnostics.report(
                            startLine,
                            ErrorType.ILLEGAL_SYMBOL
                    );

                    /*
                     * 注意：
                     *
                     * 即使只有一个 &，
                     * TokenType 仍然是 AND。
                     */
                    addToken(
                            TokenType.AND,
                            startIndex,
                            startLine,
                            startColumn
                    );
                }
            }

            /*
             * ||
             *
             * 单独出现 | 的处理方式与 & 相同。
             */
            case '|' -> {

                advance();

                if (match('|')) {

                    addToken(
                            TokenType.OR,
                            startIndex,
                            startLine,
                            startColumn
                    );

                } else {

                    diagnostics.report(
                            startLine,
                            ErrorType.ILLEGAL_SYMBOL
                    );

                    addToken(
                            TokenType.OR,
                            startIndex,
                            startLine,
                            startColumn
                    );
                }
            }

            /*
             * (
             */
            case '(' -> {

                advance();

                addToken(
                        TokenType.LPARNET,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * )
             */
            case ')' -> {

                advance();

                addToken(
                        TokenType.RPARNET,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * [
             */
            case '[' -> {

                advance();

                addToken(
                        TokenType.LBRACK,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * ]
             */
            case ']' -> {

                advance();

                addToken(
                        TokenType.RBRACK,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * {
             */
            case '{' -> {

                advance();

                addToken(
                        TokenType.LBRACE,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * }
             */
            case '}' -> {

                advance();

                addToken(
                        TokenType.RBRACE,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * ,
             */
            case ',' -> {

                advance();

                addToken(
                        TokenType.COMMA,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * :
             */
            case ':' -> {

                advance();

                addToken(
                        TokenType.COLON,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * ;
             */
            case ';' -> {

                advance();

                addToken(
                        TokenType.SEMICN,
                        startIndex,
                        startLine,
                        startColumn
                );
            }

            /*
             * 根据实验说明：
             *
             * 测试程序不会出现未规定的错误形式。
             *
             * 所以这里不自行创建新的错误类别。
             */
            default -> advance();
        }
    }

    /*
     * ============================================================
     * Identifier / Keyword
     * ============================================================
     */

    private void scanIdentifier(
            int startIndex,
            int startLine,
            int startColumn
    ) {

        /*
         * 第一个字符已经确认满足：
         *
         * letter | _
         */
        advance();

        /*
         * 后续字符可以是：
         *
         * letter
         * digit
         * _
         */
        while (!isAtEnd()
                && isIdentifierPart(peek())) {

            advance();
        }

        String text =
                source.substring(startIndex, index);

        /*
         * 如果出现在关键字表中：
         *
         * int -> INTTK
         * if  -> IFTK
         *
         * 否则：
         *
         * abc -> IDENFR
         */
        TokenType type =
                KEYWORDS.getOrDefault(
                        text,
                        TokenType.IDENFR
                );

        tokens.add(
                new Token(
                        type,
                        text,
                        startLine,
                        startColumn
                )
        );
    }

    /*
     * ============================================================
     * Integer Constant
     * ============================================================
     */

    private void scanInteger(
            int startIndex,
            int startLine,
            int startColumn
    ) {

        /*
         * 文法：
         *
         * integer-const
         *     -> decimal-const
         *      | 0
         *
         * decimal-const
         *     -> nonzero-digit
         *      | decimal-const digit
         *
         * 因此 0 是单独的一种形式。
         */
        if (peek() == '0') {

            advance();

        } else {

            /*
             * 第一位必然是 1~9。
             */
            while (!isAtEnd()
                    && isDigit(peek())) {

                advance();
            }
        }

        addToken(
                TokenType.INTCON,
                startIndex,
                startLine,
                startColumn
        );
    }

    /*
     * ============================================================
     * Character Constant
     * ============================================================
     */

    private void scanCharConstant(
            int startIndex,
            int startLine,
            int startColumn
    ) {

        /*
         * 消费开头的 '
         */
        advance();

        if (!isAtEnd()) {

            /*
             * EscapeChar
             *
             * \n
             * \t
             * \r
             * \0
             * \\
             * \'
             */
            if (peek() == '\\') {

                advance();

                if (!isAtEnd()) {
                    advance();
                }

            } else {

                /*
                 * NormalChar
                 */
                advance();
            }
        }

        /*
         * 消费结束的 '
         *
         * 测试保证不会出现文档未定义的
         * 字符常量错误形式。
         */
        if (!isAtEnd()
                && peek() == '\'') {

            advance();
        }

        addToken(
                TokenType.CHARCON,
                startIndex,
                startLine,
                startColumn
        );
    }

    /*
     * ============================================================
     * String Constant
     * ============================================================
     */

    private void scanStringConstant(
            int startIndex,
            int startLine,
            int startColumn
    ) {

        /*
         * 消费开头的 "
         */
        advance();

        while (!isAtEnd()) {

            char c = peek();

            /*
             * 字符串结束。
             */
            if (c == '"') {

                advance();
                break;
            }

            /*
             * 文法中字符串只需要考虑：
             *
             * \n
             *
             * 为了保证反斜杠后的字符不会
             * 被错误识别成字符串结束符，
             * 连续消费两个字符。
             */
            if (c == '\\') {

                advance();

                if (!isAtEnd()) {
                    advance();
                }

                continue;
            }

            /*
             * 普通字符
             */
            advance();
        }

        addToken(
                TokenType.STRCON,
                startIndex,
                startLine,
                startColumn
        );
    }

    /*
     * ============================================================
     * Whitespace / Comments
     * ============================================================
     */

    private void skipWhitespaceAndComments() {

        while (!isAtEnd()) {

            char c = peek();

            /*
             * ----------------------------------------------------
             * 空白符
             * ----------------------------------------------------
             */
            if (isWhitespace(c)) {

                advance();
                continue;
            }

            /*
             * ----------------------------------------------------
             * //
             *
             * 单行注释。
             *
             * 一直到换行符之前。
             * ----------------------------------------------------
             */
            if (c == '/'
                    && peekNext() == '/') {

                /*
                 * 消费 //
                 */
                advance();
                advance();

                while (!isAtEnd()
                        && peek() != '\n'
                        && peek() != '\r') {

                    advance();
                }

                continue;
            }

            /*
             * ----------------------------------------------------
             * block comment
             *
             * 从斜杠星号开始，
             * 到第一次出现星号斜杠结束。
             * ----------------------------------------------------
             */
            if (c == '/'
                    && peekNext() == '*') {

                /*
                 * 消费开头两个字符。
                 */
                advance();
                advance();

                while (!isAtEnd()) {

                    if (peek() == '*'
                            && peekNext() == '/') {

                        /*
                         * 消费结尾两个字符。
                         */
                        advance();
                        advance();

                        break;
                    }

                    advance();
                }

                continue;
            }

            /*
             * 当前不是空白，也不是注释。
             */
            break;
        }
    }

    /*
     * ============================================================
     * Token 创建
     * ============================================================
     */

    private void addToken(
            TokenType type,
            int startIndex,
            int startLine,
            int startColumn
    ) {

        /*
         * Token.text 永远保存源码原文。
         *
         * 例如：
         *
         * &&
         * &
         * "hello\n"
         * '\n'
         */
        String text =
                source.substring(
                        startIndex,
                        index
                );

        tokens.add(
                new Token(
                        type,
                        text,
                        startLine,
                        startColumn
                )
        );
    }

    /*
     * ============================================================
     * Character Access
     * ============================================================
     */

    /**
     * 查看当前字符，不消费。
     */
    private char peek() {

        if (isAtEnd()) {
            return '\0';
        }

        return source.charAt(index);
    }

    /**
     * 查看下一个字符，不消费。
     */
    private char peekNext() {

        if (index + 1 >= source.length()) {
            return '\0';
        }

        return source.charAt(index + 1);
    }

    /**
     * 如果当前字符等于 expected，
     * 则消费它。
     */
    private boolean match(char expected) {

        if (isAtEnd()) {
            return false;
        }

        if (source.charAt(index) != expected) {
            return false;
        }

        advance();

        return true;
    }

    /**
     * 消费一个字符，并维护：
     *
     * index
     * line
     * column
     */
    private char advance() {

        if (isAtEnd()) {
            return '\0';
        }

        char c = source.charAt(index);

        index++;

        /*
         * Windows:
         *
         * \r\n
         */
        if (c == '\r') {

            /*
             * 如果后面紧跟 \n，
             * 一起消费。
             */
            if (!isAtEnd()
                    && source.charAt(index) == '\n') {

                index++;
            }

            line++;
            column = 1;

            return '\n';
        }

        /*
         * Linux / macOS:
         *
         * \n
         */
        if (c == '\n') {

            line++;
            column = 1;

        } else {

            column++;
        }

        return c;
    }

    /*
     * ============================================================
     * Character Classification
     * ============================================================
     */

    private boolean isAtEnd() {
        return index >= source.length();
    }

    /**
     * identifier-nondigit：
     *
     * _
     * a-z
     * A-Z
     *
     * 不使用 Character.isLetter，
     * 因为它会接受中文、日文等 Unicode 字符，
     * 而文法明确要求大小写英文字母。
     */
    private boolean isIdentifierStart(char c) {

        return c == '_'
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z');
    }

    /**
     * Identifier 后续字符：
     *
     * identifier-nondigit
     * digit
     */
    private boolean isIdentifierPart(char c) {

        return isIdentifierStart(c)
                || isDigit(c);
    }

    private boolean isDigit(char c) {

        return c >= '0'
                && c <= '9';
    }

    private boolean isWhitespace(char c) {

        return c == ' '
                || c == '\t'
                || c == '\n'
                || c == '\r'
                || c == '\f';
    }
}
