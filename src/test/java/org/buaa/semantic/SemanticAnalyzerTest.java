package org.buaa.semantic;

import org.buaa.ast.Ast;
import org.buaa.diagnostic.DiagnosticEngine;
import org.buaa.lexer.Lexer;
import org.buaa.parser.Parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 语义回归使用独立的预期输出，覆盖各错误类别及跨阶段错误恢复。 */
public final class SemanticAnalyzerTest {
    private static int passed;
    private static Path work;

    private record Result(Ast.CompUnit ast, SemanticResult semantic, DiagnosticEngine diagnostics) { }

    public static void main(String[] args) throws Exception {
        work = Files.createTempDirectory("sysy-semantic-tests-");
        symbolOutput();
        bindingsAndScopes();
        storageAndConstants();
        allErrorsTogether();
        typeErrors();
        namesAndCalls();
        returnsAndControls();
        printfAndSwitch();
        longExpression();
        System.out.println("Passed " + passed + " semantic analyzer tests.");
    }

    private static Result analyze(String source) {
        DiagnosticEngine diagnostics = new DiagnosticEngine();
        Ast.CompUnit ast = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer(diagnostics);
        SemanticResult semantic = analyzer.analyze(ast);
        check(analyzer.analyze(ast) == semantic, "repeated analysis reuses the result");
        return new Result(ast, semantic, diagnostics);
    }

    private static Result valid(String source) throws Exception {
        Result result = analyze(source);
        equal("", errors(result), "valid program diagnostics");
        passed++;
        return result;
    }

    /** 每个带 // error X 标注的行恰好期望一个 X 错误，未标注的行不应报错。 */
    private static Result invalid(String source) throws Exception {
        StringBuilder expected = new StringBuilder();
        String[] lines = source.split("\n");
        for (int i = 0; i < lines.length; i++) {
            int marker = lines[i].indexOf("// error ");
            if (marker >= 0) {
                expected.append(i + 1).append(' ').append(lines[i].charAt(marker + 9)).append('\n');
            }
        }
        check(!expected.isEmpty(), "invalid fixture must specify errors");
        Result result = analyze(source);
        equal(expected.toString(), errors(result), "diagnostics for:\n" + source);
        passed++;
        return result;
    }

    private static void symbolOutput() throws Exception {
        Result result = valid("""
                const int ci=1, cia[2]={1};
                int vi, via[2];
                const char cc='a', cca[2]="a";
                char vc, vca[2];
                void f(int p, char q, int a[], char b[]) {
                    static int si, sia[2];
                    static char sc, sca[2];
                    { const int inner=2; }
                    int after;
                }
                int g(){return 1;}
                char h(){return 'a';}
                int main(){int m; {} {char n;} return 0;}
                """);
        equal("""
                1 ci ConstInt
                1 cia ConstIntArray
                1 vi Int
                1 via IntArray
                1 cc ConstChar
                1 cca ConstCharArray
                1 vc Char
                1 vca CharArray
                1 f VoidFunc
                1 g IntFunc
                1 h CharFunc
                2 p Int
                2 q Char
                2 a IntArray
                2 b CharArray
                2 si StaticInt
                2 sia StaticIntArray
                2 sc StaticChar
                2 sca StaticCharArray
                2 after Int
                3 inner ConstInt
                6 m Int
                8 n Char
                """, result.semantic().symbolOutput(), "scope order, declaration order and all 15 type names");
        equal(8, result.semantic().scopes().size(), "empty scopes still consume an id");
        check(result.semantic().scopes().get(0).lookupLocal("main") == null, "main is not a symbol");
        equal(4, result.semantic().activationRecords().size(), "main still has an activation record");
    }

    private static void bindingsAndScopes() throws Exception {
        Result result = valid("""
                int x;
                int f(int p) {
                    x=p;
                    {char x='a'; x='b';}
                    x=p;
                    return f(p);
                }
                int main(){return 0;}
                """);
        List<Ast.BlockItem> body = result.ast().functions().get(0).body().items();
        Symbol global = result.semantic().symbolOf(result.ast().declarations().get(0).definitions().get(0));
        Ast.Assignment before = (Ast.Assignment) body.get(0);
        Ast.Block nested = (Ast.Block) body.get(1);
        Ast.Assignment inside = (Ast.Assignment) nested.items().get(1);
        Ast.Assignment after = (Ast.Assignment) body.get(2);
        check(result.semantic().symbolOf(before.target()) == global, "global binding before shadow");
        check(result.semantic().symbolOf(inside.target()) != global, "nested shadow binding");
        check(result.semantic().symbolOf(after.target()) == global, "global binding restored after pop");
        Ast.CallExpr call = (Ast.CallExpr) ((Ast.ReturnStmt) body.get(3)).value();
        check(result.semantic().symbolOf(call) == result.semantic().symbolOf(result.ast().functions().get(0)),
                "self recursion resolves to the current function");
        equal(2, result.semantic().scopes().get(2).parentId(), "nested scope parent");
    }

    private static void storageAndConstants() throws Exception {
        Result result = valid("""
                const char wrap=(char)255+(char)1, neg=-(char)1, product=(char)200*(char)2;
                const char text[4]="a\\n", shortText[1]="ab", exactText[2]="ab";
                const int n=(int)text[1]/2, zeros[3]={1};
                int global[n], empty;
                const char tab='\\t', cr='\\r', nul='\\0', slash='\\\\', quote='\\'';
                int f(int p, char a[]) {
                    static int count, saved[n]={2};
                    char local[3];
                    {int nested; static char count='x';}
                    const int localConst=3;
                    int dynamic=p+1;
                    return p;
                }
                int main(){return 0;}
                """);
        Scope global = result.semantic().scopes().get(0);
        equal(0, initial(global, "wrap", 0), "char addition wraps");
        equal(255, initial(global, "neg", 0), "char negation wraps");
        equal(144, initial(global, "product", 0), "char multiplication wraps");
        equal(10, initial(global, "text", 1), "string newline decoded");
        equal(0, initial(global, "text", 3), "string zero fill");
        equal(List.of(97), global.lookupLocal("shortText").initialValue().explicitValues(), "string truncation");
        equal(List.of(97, 98), global.lookupLocal("exactText").initialValue().explicitValues(),
                "exact string has no extra terminator");
        equal(5, initial(global, "n", 0), "constant array access and cast folding");
        equal(0, initial(global, "zeros", 2), "constant array zero fill");
        equal(0, initial(global, "global", 4), "global variable zero fill");
        equal(9, initial(global, "tab", 0), "tab escape");
        equal(13, initial(global, "cr", 0), "carriage return escape");
        equal(0, initial(global, "nul", 0), "zero escape");
        equal(92, initial(global, "slash", 0), "backslash escape");
        equal(39, initial(global, "quote", 0), "quote escape");
        ActivationRecord frame = result.semantic().activationRecords().get(0);
        equal(11L, frame.slotCount(), "header + two parameters + local array + nested + const + dynamic");
        equal(6, frame.slots().size(), "only automatic objects belong to the frame");
        check(frame.slots().get(1).indirect(), "array parameter passes an address");
        equal(3L, frame.slots().get(0).offset(), "parameter follows header");
        Scope function = result.semantic().scopes().get(1);
        Symbol count = function.lookupLocal("count");
        equal(StorageSlot.Area.STATIC_LOCAL, result.semantic().storageOf(count).area(), "static storage area");
        equal(0, initial(function, "count", 0), "static implicit zero");
        equal(0, initial(function, "saved", 4), "static partial array zero fill");
        check(initial(function, "local", 0) == null, "automatic uninitialized value remains unknown");
        check(initial(function, "dynamic", 0) == null, "runtime initializer remains unknown");
        Symbol nestedStatic = result.semantic().scopes().get(2).lookupLocal("count");
        check(result.semantic().storageOf(count).offset() != result.semantic().storageOf(nestedStatic).offset(),
                "same static spelling in different scopes has different storage");
        valid("const int huge[1000000000]={7}; int main(){return 0;}");
    }

    private static void allErrorsTogether() throws Exception {
        Result result = invalid("""
                const int c=1;
                int c; // error b
                void v(){
                    return 1; // error f
                }
                int missing(){
                    ;
                } // error g
                int f(int a){return a;}
                int main(){
                    unknown=0; // error c
                    f(); // error d
                    f('a'); // error e
                    c=2; // error h
                    int x=0 // error i
                    int a[2; // error k
                    if(x & 1); // error a
                    f(1; // error j
                    printf("%d"); // error l
                    break; // error m
                    switch(x){
                        case 1:;
                        case 1:; // error n
                    }
                    return 0;
                }
                """);
        equal(14L, errors(result).lines().count(), "all a-n errors in one program");
    }

    private static void typeErrors() throws Exception {
        String prefix = """
                void v(){return;}
                int f(int i, char c, int a[], char b[]){return i;}
                int main(){
                    int i=0, a[2]; char c='a', b[2];
                """;
        String[] statements = {
                "i=c;", "c=i;", "i=1+'a';", "c=c+1;", "i=v();", "i=a;", "a=b;", "a=a;",
                "i=a[c];", "i=i[0];", "c=c[0];", "int bad['a'];", "char bad=1;", "int bad='a';",
                "int bad[2]={1,'a'};", "char bad[2]={'a',1};", "int bad[2]=\"ab\";",
                "i=f;", "i();", "a();", "f(c,c,a,b);", "f(i,i,a,b);", "f(i,c,b,b);",
                "f(i,c,a,a);", "f(i,c,i,b);", "f(i,c,a,c);", "f(a,c,a,b);", "f(i,b,a,b);",
                "i=-a;", "i=a+a;", "v()+v();", "i=(int)a;", "if(a);", "if(v());",
                "while(b);", "if(i<c);", "if(i&&c);", "if(!a);", "switch(a){}",
                "printf(\"%d\",c);", "printf(\"%c\",i);", "printf(\"%s\",a);", "printf(\"%s\",c);"
        };
        for (String statement : statements) {
            invalid(prefix + "    " + statement + " // error e\nreturn 0;\n}\n");
        }
        valid(prefix + """
                    i=(int)c; c=(char)i; i=f(i,c,a,b); i=f(a[0],b[0],(a),(b));
                    c=c+c; c=c*c; c=-c; c=+c;
                    if(c<'z' && c!='\\0');
                    if(i<=1 || i>=0 && !i);
                    printf("%d%c%s",i,c,b);
                    v(); a; b; ;
                    return 0;
                }
                """);
    }

    private static void namesAndCalls() throws Exception {
        invalid("""
                int x;
                int x(){return 1;} // error b
                int f(int a, char a){ // error b
                    int a; // error b
                    return 1;
                }
                char f(){ // error b
                    undefined=1; // error c
                    return 1; // error e
                }
                int main(){
                    later=1; // error c
                    int later;
                    {int hidden;}
                    hidden=2; // error c
                    f(); // error d
                    f(1,'a',2); // error d
                    {int f; f(1,'a');} // error e
                    return 0;
                }
                """);
        invalid("""
                int f(){return later();} // error c
                int later(){return 1;}
                int main(){return 0;}
                """);
        invalid("""
                const int a[2]={};
                const char c='a';
                int main(){
                    a[0]=1; // error h
                    c='b'; // error h
                    return 0;
                }
                """);
        valid("""
                int x;
                void f(){x=1; int x=x; {char x='a';} x=2;}
                int main(){f(); {int x;} {char x;} return 0;}
                """);
        invalid("""
                int main(){
                    int x=missing+1; // error c
                    unknown(another); // error c
                    return 0;
                }
                """);
    }

    private static void returnsAndControls() throws Exception {
        invalid("""
                void v(){return;}
                void f(){return v();} // error f
                int bare(){return;} // error e
                char wrong(){return 1;} // error e
                int wrong2(){return 'a';} // error e
                int empty(){} // error g
                int branches(){if(1)return 1;else return 2;} // error g
                int nested(){{return 1;}} // error g
                int trailing(){return 1;;} // error g
                int main(){
                    break; // error m
                    continue; // error m
                    switch(1){case 1:break; default:continue;} // error m
                    while(1){switch(1){default:continue;} break;}
                    break; // error m
                    return 0;
                }
                """);
        invalid("int main(){} // error g\n");
        valid("""
                void f(){return;}
                char c(){return 'a';}
                int main(){
                    while(1){while(0)continue; switch(1){case 1:break;default:continue;} break;}
                    switch('a'){case 'a': {int x;} break; default:;}
                    return 0;
                }
                """);
    }

    private static void printfAndSwitch() throws Exception {
        invalid("""
                int main(){
                    printf("%d%c%s"); // error l
                    printf("plain",1); // error l
                    switch(1){case 'a':;} // error e
                    switch('a'){case 97:;} // error e
                    switch(1){case 1:;case 1:;} // error n
                    switch(1){default:;default:;} // error n
                    switch('\\n'){case '\\n':;case '\\n':;} // error n
                    return 0;
                }
                """);
        Result result = valid("""
                int main(){
                    char text[3]="ab";
                    printf("plain\\n"); printf("%d%c%s",1,'a',text);
                    switch(1){case 1:switch(1){case 1:break;default:;} break; default:;}
                    switch(1){case 1: {int x;} default:{int y;}}
                    return 0;
                }
                """);
        equal(4, result.semantic().scopes().size(), "only explicit Blocks create scopes in switch");
    }

    private static void longExpression() throws Exception {
        Result result = valid("const int sum=1" + "+1".repeat(10000) + "; int main(){return 0;}");
        equal(10001, initial(result.semantic().scopes().get(0), "sum", 0), "long expression constant folding");
    }

    private static Integer initial(Scope scope, String name, int index) {
        return scope.lookupLocal(name).initialValue().valueAt(index);
    }

    private static String errors(Result result) throws Exception {
        Path file = work.resolve("errors.txt");
        result.diagnostics().writeTo(file);
        return Files.readString(file).replace("\r\n", "\n");
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
