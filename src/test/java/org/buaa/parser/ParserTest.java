package org.buaa.parser;

import org.buaa.ast.Ast;
import org.buaa.diagnostic.DiagnosticEngine;
import org.buaa.lexer.Lexer;
import org.buaa.lexer.Token;
import org.buaa.lexer.TokenType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** 无第三方依赖的回归测试；断言不依赖 JVM 的 -ea 开关。 */
public final class ParserTest {
    private static int passed;
    private static Path work;

    private record Result(Parser parser, Ast.CompUnit ast, DiagnosticEngine diagnostics) {
    }

    @FunctionalInterface
    private interface Test {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        work = Files.createTempDirectory("sysy-parser-tests-");
        test("exact minimal output", ParserTest::minimalOutput);
        test("left-recursive output", ParserTest::expressionOutput);
        test("precedence, associativity and casts", ParserTest::expressionAst);
        test("complete grammar", ParserTest::completeGrammar);
        test("dangling else and shared LVal prefix", ParserTest::statements);
        test("source positions and immutable AST", ParserTest::positions);
        missingDelimiters();
        test("combined lexical and syntax errors", ParserTest::combinedErrors);
        test("semantic errors preserve AST", ParserTest::semanticErrors);
        test("long left-associative expression", ParserTest::longExpression);
        test("compiler output files", ParserTest::compilerFiles);
        System.out.println("Passed " + passed + " parser tests.");
    }

    private static void test(String name, Test test) throws Exception {
        try {
            test.run();
            passed++;
        } catch (Exception | AssertionError error) {
            throw new AssertionError("Failed: " + name, error);
        }
    }

    private static Result analyze(String source) {
        DiagnosticEngine diagnostics = new DiagnosticEngine();
        List<Token> tokens = new Lexer(source, diagnostics).tokenize();
        Parser parser = new Parser(tokens, diagnostics);
        Ast.CompUnit ast = parser.parse();
        String output = parser.output();
        String terminals = output.lines().filter(line -> !line.startsWith("<"))
                .collect(Collectors.joining("\n"));
        String expected = tokens.stream().filter(token -> token.type() != TokenType.EOF)
                .map(token -> token.type() + " " + token.text()).collect(Collectors.joining("\n"));
        equal(expected, terminals, "every real token must occur once, in source order");
        check(!output.contains("<Decl>") && !output.contains("<BType>")
                && !output.contains("<BlockItem>"), "omit the three excluded nonterminals");
        check(output.endsWith("<CompUnit>\n"), "must finish the compilation unit");
        check(parser.parse() == ast, "repeated parse must reuse the AST");
        equal(output, parser.output(), "repeated output must not append tokens");
        return new Result(parser, ast, diagnostics);
    }

    private static Result valid(String source) {
        Result result = analyze(source);
        check(!result.diagnostics().hasErrors(), "unexpected diagnostic");
        return result;
    }

    private static void minimalOutput() {
        String expected = """
                INTTK int
                MAINTK main
                LPARENT (
                RPARENT )
                LBRACE {
                RETURNTK return
                INTCON 0
                <Number>
                <PrimaryExp>
                <UnaryExp>
                <MulExp>
                <AddExp>
                <Exp>
                SEMICN ;
                <Stmt>
                RBRACE }
                <Block>
                <MainFuncDef>
                <CompUnit>
                """;
        equal(expected, valid("int main(){return 0;}").parser().output(), "golden output");
    }

    private static void expressionOutput() {
        String expected = """
                INTCON 1
                <Number>
                <PrimaryExp>
                <UnaryExp>
                <MulExp>
                <AddExp>
                PLUS +
                INTCON 2
                <Number>
                <PrimaryExp>
                <UnaryExp>
                <MulExp>
                MULT *
                INTCON 3
                <Number>
                <PrimaryExp>
                <UnaryExp>
                <MulExp>
                <AddExp>
                MINU -
                INTCON 4
                <Number>
                <PrimaryExp>
                <UnaryExp>
                <MulExp>
                <AddExp>
                <Exp>
                """;
        String output = valid("int main(){1+2*3-4;return 0;}").parser().output();
        equal(expected, output.substring(output.indexOf("INTCON 1"), output.indexOf("SEMICN")),
                "left-recursive reductions must precede the next operator");
        output = valid("int main(){if(1||2||3&&4==5!=6<7>8<=9>=10);return 0;}")
                .parser().output();
        equal(3L, output.lines().filter("<LOrExp>"::equals).count(), "LOr reductions");
        equal(4L, output.lines().filter("<LAndExp>"::equals).count(), "LAnd reductions");
        equal(6L, output.lines().filter("<EqExp>"::equals).count(), "Eq reductions");
        equal(10L, output.lines().filter("<RelExp>"::equals).count(), "Rel reductions");
    }

    private static void expressionAst() {
        Result result = valid("int main(){20-6-2*3; (int)-'a'; if(1+2*3<8==1&&0||!1); return 0;}");
        List<Ast.BlockItem> items = result.ast().mainFunction().body().items();
        Ast.BinaryExpr subtraction = (Ast.BinaryExpr) ((Ast.ExprStmt) items.get(0)).expression();
        equal(TokenType.MINU, subtraction.operator().type(), "outer subtraction");
        equal(TokenType.MINU, ((Ast.BinaryExpr) subtraction.left()).operator().type(), "left association");
        equal(TokenType.MULT, ((Ast.BinaryExpr) subtraction.right()).operator().type(), "multiply binds first");
        Ast.CastExpr cast = (Ast.CastExpr) ((Ast.ExprStmt) items.get(1)).expression();
        check(cast.operand() instanceof Ast.UnaryExpr, "cast operand is a UnaryExp");
        Ast.BinaryExpr or = (Ast.BinaryExpr) ((Ast.IfStmt) items.get(2)).condition();
        equal(TokenType.OR, or.operator().type(), "OR is the outermost operator");
        Ast.BinaryExpr and = (Ast.BinaryExpr) or.left();
        equal(TokenType.AND, and.operator().type(), "AND precedence");
        Ast.BinaryExpr eq = (Ast.BinaryExpr) and.left();
        equal(TokenType.EQL, eq.operator().type(), "equality precedence");
        equal(TokenType.LSS, ((Ast.BinaryExpr) eq.left()).operator().type(), "relational precedence");
        check(or.right() instanceof Ast.UnaryExpr, "unary not");
    }

    private static void completeGrammar() {
        Result result = valid("""
                const int N=3, M=2, Z=0;
                const int ca[3]={1,2,3}, ce[1]={};
                const char cc='a', cs[3]="ab", cl[2]={'a','b'};
                int ga[3]={1,2,3}, ge[1]={}, gv;
                char gs[3]="ab";
                void empty() {}
                void ping() { return; }
                int one(int n) { return n; }
                char identity(char c) { return c; }
                int calc(int a[], char b[], int n, char c) {
                    static int calls, x=1, y=2;
                    static char saved='a';
                    const int local=1;
                    int i=0, v[3]={1,2,3}, uninitialized;
                    char text[3]="ab", copy=identity(c);
                    ; i; a[i]+one(n)*2; ping(); one(a[i]);
                    i=+-+n+v[(i+1)];
                    if (!i || n!=0 && n<=3) if (n>0) i=i+1; else i=i-1;
                    while(i<3 && n>=1 || n==0) {
                        i=i+1;
                        if(i==2) continue;
                        break;
                    }
                    switch(i) {
                        case 0:
                        case 1: i=i+1; break;
                        default: {int t=0; i=t;}
                    }
                    switch(c) {case 'a': break; default: ;}
                    switch(i) {}
                    printf("plain");
                    printf("%d %c %s\\n", i, c, b);
                    calls=calls+1;
                    saved=(char)(i+1);
                    return (int)c+n/2%2-i*2;
                }
                int main(){calc(ga,gs,N,cc); empty(); return 0;}
                """);
        Set<String> expected = Set.of("CompUnit", "ConstDecl", "ConstDef", "ConstInitVal",
                "VarDecl", "VarDef", "InitVal", "FuncDef", "MainFuncDef", "FuncType", "FuncFParams",
                "FuncFParam", "Block", "Stmt", "CaseStmt", "Exp", "Cond", "LVal", "PrimaryExp",
                "Number", "UnaryExp", "UnaryOp", "FuncRParams", "MulExp", "AddExp", "RelExp",
                "EqExp", "LAndExp", "LOrExp", "ConstExp");
        Set<String> actual = result.parser().output().lines().filter(line -> line.startsWith("<"))
                .map(line -> line.substring(1, line.length() - 1)).collect(Collectors.toSet());
        equal(expected, actual, "all printable grammar components");
        equal(5, result.ast().declarations().size(), "global declarations");
        equal(5, result.ast().functions().size(), "ordinary functions");
        Ast.Function calc = result.ast().functions().get(4);
        check(calc.parameters().get(0).array() && calc.parameters().get(1).array(), "array formals");
        check(((Ast.Declaration) calc.body().items().get(0)).isStatic(), "static declarations");
        Ast.Definition string = result.ast().declarations().get(4).definitions().get(0);
        check(string.initializer() instanceof Ast.StringInitializer, "string initializer");
    }

    private static void statements() {
        Result result = valid("int main(){int a[2],b; if(1)if(2)b=1;else b=2; a[b]=b; a[b]+b*2; return 0;}");
        List<Ast.BlockItem> items = result.ast().mainFunction().body().items();
        Ast.IfStmt outer = (Ast.IfStmt) items.get(1);
        check(outer.elseBranch() == null, "outer if has no else");
        check(((Ast.IfStmt) outer.thenBranch()).elseBranch() instanceof Ast.Assignment,
                "else binds to inner if");
        check(((Ast.Assignment) items.get(2)).target().index() instanceof Ast.LValue, "array assignment");
        check(((Ast.ExprStmt) items.get(3)).expression() instanceof Ast.BinaryExpr, "array expression");
    }

    private static void positions() {
        Result result = valid("// heading\r\nint main() {\r\n  return 0;\r\n}\r\n");
        equal(2, result.ast().span().start().line(), "root starts after comment");
        equal(4, result.ast().span().end().line(), "root ends at brace");
        equal(2, result.ast().span().end().column(), "exclusive brace end");
        Ast.ReturnStmt statement = (Ast.ReturnStmt) result.ast().mainFunction().body().items().get(0);
        equal(3, statement.span().start().line(), "return line");
        equal(3, statement.span().start().column(), "return column");
        equal(10, statement.value().span().start().column(), "literal column");
        try {
            result.ast().mainFunction().body().items().clear();
            throw new AssertionError("AST lists must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void missingDelimiters() throws Exception {
        String[][] cases = {
                {"const int c=1\nint main(){return 0;}", "1 i\n"},
                {"int c\nint main(){return 0;}", "1 i\n"},
                {"int main(){\nint a[2;\nreturn 0;}", "2 k\n"},
                {"const int a[2={1,2};\nint main(){return 0;}", "1 k\n"},
                {"int f(int a[){return 1;}\nint main(){return 0;}", "1 k\n"},
                {"int f( {return 1;}\nint main(){return 0;}", "1 j\n"},
                {"int f(int a {return a;}\nint main(){return 0;}", "1 j\n"},
                {"int main( {return 0;}", "1 j\n"},
                {"int f(){return 1;}\nint main(){f(;\nreturn 0;}", "2 j\n"},
                {"int f(int a){return a;}\nint main(){f(1;\nreturn 0;}", "2 j\n"},
                {"int main(){int a=1;\n(a+1;\nreturn 0;}", "2 j\n"},
                {"int main(){\n(int 'a';\nreturn 0;}", "2 j\n"},
                {"int main(){\nif(1 {;}\nreturn 0;}", "2 j\n"},
                {"int main(){\nwhile(1 {break;}\nreturn 0;}", "2 j\n"},
                {"int main(){\nswitch(1 {case 1:break;}\nreturn 0;}", "2 j\n"},
                {"int main(){\nprintf(\"ok\";\nreturn 0;}", "2 j\n"},
                {"int main(){\nprintf(\"%d\",1;\nreturn 0;}", "2 j\n"},
                {"int main(){\nprintf(\"ok\")\nreturn 0;}", "2 i\n"},
                {"int main(){int a[2];\na[0=1;\nreturn 0;}", "2 k\n"},
                {"int main(){int a[2];\na[0+1;\nreturn 0;}", "2 k\n"},
                {"int main(){int a;\na=1\nreturn 0;}", "2 i\n"},
                {"int main(){int a;\na\nreturn 0;}", "2 i\n"},
                {"int main(){while(1){\nbreak\n}return 0;}", "2 i\n"},
                {"int main(){while(1){\ncontinue\n}return 0;}", "2 i\n"},
                {"void f(){\nreturn\n}\nint main(){return 0;}", "2 i\n"},
                {"int main(){\nreturn 0\n}", "2 i\n"},
                {"int main(){int a=1\n/* comment */\n\nreturn 0;}", "1 i\n"},
                {"int main(){\n(int\n'a';\nreturn 0;}", "2 j\n"}
        };
        for (int i = 0; i < cases.length; i++) {
            String[] sample = cases[i];
            test("missing delimiter " + (i + 1), () -> {
                Result result = analyze(sample[0]);
                equal(sample[1], errors(result), "missing delimiter diagnostic");
                check(lastItem(result) instanceof Ast.ReturnStmt, "recovery retains final return");
            });
        }
    }

    private static void combinedErrors() throws Exception {
        Result result = analyze("""
                int helper(int a[) { return 1; }
                int main( {
                    int x = 1
                    int a[2 = {1, 2};
                    if (x & 1) x = 2;
                    while (x | 0) break;
                    x = (int 'a';
                    a[0 = helper(a);
                    printf("%d", x;
                    return 0
                }
                """);
        equal("1 k\n2 j\n3 i\n4 k\n5 a\n6 a\n7 j\n8 k\n9 j\n10 i\n", errors(result),
                "all errors, sorted despite lexer running first");
        check(lastItem(result) instanceof Ast.ReturnStmt, "AST continues after every error");
        check(result.parser().output().contains("AND &\n") && result.parser().output().contains("OR |\n"),
                "lexical errors keep their original spelling");
    }

    private static void semanticErrors() {
        Result result = valid("""
                int f(int x) {return x;}
                int f(int x) {undefined=2; return 'a';}
                int main(){
                    const int c=1;
                    c=2;
                    break;
                    continue;
                    f();
                    switch(1){case 1:;case 1:;default:;default:;}
                    return 0;
                }
                """);
        equal(2, result.ast().functions().size(), "redefined function body is retained");
        check(lastItem(result) instanceof Ast.ReturnStmt, "semantic errors do not stop syntax analysis");
    }

    private static void longExpression() {
        Result result = valid("int main(){1" + "+1".repeat(10000) + ";return 0;}");
        Ast.Expr expression = ((Ast.ExprStmt) result.ast().mainFunction().body().items().get(0)).expression();
        int operators = 0;
        while (expression instanceof Ast.BinaryExpr binary) {
            operators++;
            expression = binary.left();
        }
        equal(10000, operators, "left-recursive rules must not use linear call-stack depth");
    }

    private static void compilerFiles() throws Exception {
        Path directory = Files.createDirectory(work.resolve("compiler"));
        Files.writeString(directory.resolve("testfile.txt"), "int main(){return 0;}");
        Files.writeString(directory.resolve("error.txt"), "stale");
        Files.writeString(directory.resolve("lexer.txt"), "stale");
        runCompiler(directory);
        equal(valid("int main(){return 0;}").parser().output(),
                Files.readString(directory.resolve("parser.txt")), "entry point parser output");
        check(!Files.exists(directory.resolve("error.txt")), "clear stale errors");
        check(!Files.exists(directory.resolve("lexer.txt")), "clear legacy lexer output");
        Files.writeString(directory.resolve("testfile.txt"),
                "int main(){\nint x=1\nif(x & 1)x=2;\nreturn 0;}");
        runCompiler(directory);
        equal("2 i\n3 a\n", normalize(Files.readString(directory.resolve("error.txt"))),
                "entry point runs parser even after lexical errors");
        check(!Files.exists(directory.resolve("parser.txt")), "clear stale parser output");
        Files.writeString(directory.resolve("testfile.txt"), "int main(){return 0;}");
        runCompiler(directory);
        check(!Files.exists(directory.resolve("error.txt")), "clear error file after correction");
    }

    private static void runCompiler(Path directory) throws Exception {
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(entry -> Path.of(entry).toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator));
        Path log = directory.resolve("process.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, "Compiler").directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Compiler did not terminate");
        }
        equal(0, process.exitValue(), "Compiler process: " + Files.readString(log));
    }

    private static Ast.BlockItem lastItem(Result result) {
        List<Ast.BlockItem> items = result.ast().mainFunction().body().items();
        return items.get(items.size() - 1);
    }

    private static String errors(Result result) throws Exception {
        Path file = work.resolve("diagnostics.txt");
        result.diagnostics().writeTo(file);
        return normalize(Files.readString(file));
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + "\nExpected: " + expected + "\nActual: " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
