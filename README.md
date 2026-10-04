# mycompiler

评测入口为项目顶层、无 `package` 声明的 `Compiler.java`，其 `main` 方法调用
`src/main/java/org/buaa/Main.java` 中的 `org.buaa.Main.main(args)`。
编译器仅使用 JDK 标准库和项目自身的类，不依赖第三方包。

评测平台使用 Java 17；Gradle 工具链和 `--release` 均固定为 17。
现有代码中的 `record` 是 Java 17 支持的语言特性，不需要第三方包。

## 不使用 Gradle 编译运行

在项目根目录执行（先准备同目录下的 `testfile.txt`）：

```shell
javac --release 17 -encoding UTF-8 -d build/judge -sourcepath src/main/java Compiler.java
java -cp build/judge Compiler
```

本机 PowerShell 可显式使用 `D:/tool/java17`，避免 PATH 中的 Java 24：

```powershell
$javaSources = @(Get-ChildItem src/main/java -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& 'D:/tool/java17/bin/javac.exe' --release 17 -encoding UTF-8 -d build/judge Compiler.java @javaSources
& 'D:/tool/java17/bin/java.exe' -cp build/judge Compiler
```

无词法/语法错误时输出 `parser.txt`；有词法/语法错误时输出 `error.txt`。
输出文件位于运行命令的当前目录。

## 语法分析与 AST

`Parser` 使用 Java 递归调用栈实现递归下降分析，覆盖 `grammar.md` 中的完整文法。
表达式按优先级分层，左递归部分用循环消除，AST 中保留左结合关系。
`parser.txt` 按源码顺序输出 `类别码 原文`，在每次文法归约后输出 `<非终结符>`；
按课程输出约定省略 `<Decl>`、`<BType>`、`<BlockItem>`，不输出 EOF。
例如 `1 + 2 + 3` 会在每次加法归约时输出 `<AddExp>`，而非只在表达式末尾输出一次。

词法和语法阶段共享 `DiagnosticEngine`：即使词法已报错也继续语法分析。
缺少 `;`、`)`、`]` 分别记录 i/j/k，行号取缺失位置之前最后一个实际 token 的行号，
按插入标点恢复，不消耗后续 token；错误按行号升序写入 `error.txt`。
运行时清理另一种结果文件及旧的 `lexer.txt`，避免重复运行留下过期结果。

`new Parser(tokens, diagnostics).parse()` 返回 `Ast.CompUnit`。
AST 区分声明、函数、语句、初值、表达式，包含原始 token 和从 1 开始、左闭右开的源码范围；
列表不可变，可选子节点为 `null`。正常分析及上述错误恢复均会生成完整 AST，供后续语义分析使用。
本阶段不检查 b–h、l–n 等语义错误。文法保证之外的结构性错误（如缺少左花括号）会抛出
带源码位置的异常，不编造未规定的错误类别。

回归测试仅依赖 JDK 17：

```powershell
$javaSources = @(Get-ChildItem src/main/java,src/test/java -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& 'D:/tool/java17/bin/javac.exe' --release 17 -encoding UTF-8 -d build/parser-tests Compiler.java @javaSources
$testClasspath = (Resolve-Path build/parser-tests).Path
& 'D:/tool/java17/bin/java.exe' -cp $testClasspath org.buaa.parser.ParserTest
```

也可执行 `./gradlew.bat parserTest`；`check` 会包含这组测试。

## 提交目录

提交时必须同时包含 `Compiler.java`、`config.json` 和编译器源码。
提交包中不需要包含 `build/`、`.gradle/`、`.idea/` 或本地测试产物。
若平台递归编译全部 Java 源文件并用 `-d` 指定类文件输出目录，可以保留当前源码目录结构。
若平台只运行 `javac Compiler.java`，则需要将 `src/main/java/org` 复制到提交目录顶层，组成：

```text
提交目录/
├── Compiler.java
├── config.json
└── org/
    └── buaa/
        ├── Main.java
        ├── ast/
        ├── diagnostic/
        ├── lexer/
        ├── parser/
        └── source/
```

此时可在提交目录执行 `javac -encoding UTF-8 Compiler.java` 和 `java Compiler`。
只提交顶层 `Compiler.java` 会缺少 `org.buaa` 类；这些是项目源码，不是第三方包。

本地也可以继续使用 Gradle；在 PowerShell 中设置 `$env:JAVA_HOME = 'D:/tool/java17'`
后执行 `./gradlew.bat classes`，会同时编译顶层入口和 `src/main/java` 中的类。
