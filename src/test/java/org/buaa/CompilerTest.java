package org.buaa;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** 真正启动评测入口，验证文件协议和连续运行时的旧文件清理。 */
public final class CompilerTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("sysy-compiler-tests-");
        for (String name : List.of("symbol.txt", "error.txt", "parser.txt", "lexer.txt")) {
            Files.writeString(directory.resolve(name), "stale");
        }
        Files.writeString(directory.resolve("testfile.txt"), """
                const int N=2;
                int f(int p){{char c;} int x; return p;}
                int main(){static char text[N]="ab"; return 0;}
                """);
        runCompiler(directory);
        equal("""
                1 N ConstInt
                1 f IntFunc
                2 p Int
                2 x Int
                3 c Char
                4 text StaticCharArray
                """, read(directory, "symbol.txt"), "valid symbol output");
        absent(directory, "error.txt", "parser.txt", "lexer.txt", "llvm_ir.txt");

        Files.writeString(directory.resolve("testfile.txt"), """
                const int c=1;
                int c;
                void v(){return 1;}
                int missing(){}
                int f(int x){return x;}
                int main(){
                    unknown=1;
                    f();
                    f('a');
                    c=2;
                    int x=1
                    int a[2;
                    if(x & 1);
                    f(1;
                    printf("%d");
                    break;
                    switch(x){case 1:;case 1:;}
                    return 0;
                }
                """);
        runCompiler(directory);
        equal("2 b\n3 f\n4 g\n7 c\n8 d\n9 e\n10 h\n11 i\n12 k\n13 a\n14 j\n15 l\n16 m\n17 n\n",
                read(directory, "error.txt"), "all error categories are sorted across all phases");
        absent(directory, "symbol.txt", "parser.txt", "lexer.txt", "llvm_ir.txt");

        Files.writeString(directory.resolve("testfile.txt"), "int main(){return 0;}");
        runCompiler(directory);
        equal("", read(directory, "symbol.txt"), "main-only program produces an empty symbol file");
        absent(directory, "error.txt", "parser.txt", "lexer.txt", "llvm_ir.txt");
        System.out.println("Passed 3 compiler file protocol tests.");
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

    private static String read(Path directory, String file) throws Exception {
        return Files.readString(directory.resolve(file)).replace("\r\n", "\n");
    }

    private static void absent(Path directory, String... names) {
        for (String name : names) {
            if (Files.exists(directory.resolve(name))) {
                throw new AssertionError("Unexpected output file: " + name);
            }
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + "\nExpected: " + expected + "\nActual: " + actual);
        }
    }
}
