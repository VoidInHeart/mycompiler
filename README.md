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

编译器依次运行词法、语法、语义分析；正确程序输出 `symbol.txt`，
有任何阶段的错误则仅输出 `error.txt`。输出文件位于运行命令的当前目录。
本阶段不生成 LLVM IR，也不执行源程序。

## 语义分析、符号表与活动记录

`SemanticAnalyzer` 按源码顺序遍历 AST，使用栈式符号表解析标识符。
全局作用域编号为 1，每进入一个新作用域就递增，退出后不复用编号；
函数形参与函数体最外层局部声明共用作用域，嵌套 Block 创建新作用域。
空 Block 也消耗编号；switch 本身不是文法中的 Block，其 case 内显式 Block 才创建作用域。
函数先声明再检查函数体，支持直接递归；后面才声明的名字不可见。

`symbol.txt` 每行格式为 `作用域序号 名字 类型名称`，字段间一个空格。
按作用域编号升序输出，同一作用域按声明顺序输出，使用作业规定的 15 种类型名称。
`main` 是关键字，不进入符号表；main 的函数体仍建立作用域和活动记录。

运行时函数 `get_int/get_char/get_string/put_int/put_char/put_str` 可直接调用，
并兼容配套库的 `put_string` 名称。`RuntimeLibrary` 提供独立签名表，
与普通函数一样检查实参数量、类型及返回类型，但不输出库符号，不消耗作用域编号或活动记录。
这些约定见 [grammar.md](grammar.md) 第 0 节；原始 PDF 复核结果见
[docs/semantic-audit.md](docs/semantic-audit.md)。

语义阶段检查 b–h、l–n，和词法 a、语法 i/j/k 共用 `DiagnosticEngine`。
名字重定义的函数体也会继续分析；三阶段错误汇总后按行号升序输出。
错误程序不输出符号文件；每次运行清理旧的另一种结果以及 `lexer.txt`、`parser.txt`。

活动记录使用目标机无关的逻辑槽位描述，包含调用者链接、返回地址、可选返回值、
形参和各层 Block 的自动局部对象。数组形参占一个地址槽，局部数组占其长度对应的槽数。
全局和静态局部对象保存在独立的静态存储表，不随函数活动记录退出而释放。
这里保存的是布局描述，并未指定机器字节偏移、调用 ABI 或实际运行时调用栈。

`new SemanticAnalyzer(diagnostics).analyze(ast)` 返回 `SemanticResult`，可以查询：

- `scopes()`：所有历史作用域及其中的符号。
- `symbolOf(node)`：声明、形参、函数、LVal 和函数调用绑定的符号。
- `expressionInfo(expr)`：表达式类型及可选编译期常量值。
- `activationRecords()` / `staticStorage()` / `storageOf(symbol)`：函数布局和对象存储位置。

常量折叠支持字符转义、显式转换、char 模 256 回绕和常量数组元素读取；
初值保留字符串截断、部分初始化补零、全局/静态对象默认零初始化的信息。
普通未初始化局部变量和运行时表达式的值保持未知。
详细设计及错误定位规则见 [docs/semantic-analysis.md](docs/semantic-analysis.md)。

## 语法分析与 AST

`Parser` 使用 Java 递归调用栈实现递归下降分析，覆盖 `grammar.md` 中的完整文法。
表达式按优先级分层，左递归部分用循环消除，AST 中保留左结合关系。
`Parser.output()` / `Parser.writeTo(path)` 仍可输出语法分析结果，用于单独调试语法阶段，
评测入口现在不输出 `parser.txt`。语法结果按源码顺序输出 `类别码 原文`，在每次文法归约后输出 `<非终结符>`；
按课程输出约定省略 `<Decl>`、`<BType>`、`<BlockItem>`，不输出 EOF。
例如 `1 + 2 + 3` 会在每次加法归约时输出 `<AddExp>`，而非只在表达式末尾输出一次。

词法和语法阶段共享 `DiagnosticEngine`：即使词法已报错也继续语法分析。
缺少 `;`、`)`、`]` 分别记录 i/j/k，行号取缺失位置之前最后一个实际 token 的行号，
按插入标点恢复，不消耗后续 token；错误按行号升序写入 `error.txt`。

`new Parser(tokens, diagnostics).parse()` 返回 `Ast.CompUnit`。
AST 区分声明、函数、语句、初值、表达式，包含原始 token 和从 1 开始、左闭右开的源码范围；
列表不可变，可选子节点为 `null`。正常分析及上述错误恢复均会生成完整 AST，供后续语义分析使用。
`Parser` 自身不检查语义错误，交由后续 `SemanticAnalyzer` 处理。
文法保证之外的结构性错误（如缺少左花括号）会抛出
带源码位置的异常，不编造未规定的错误类别。

回归测试仅依赖 JDK 17：

```powershell
$javaSources = @(Get-ChildItem src/main/java,src/test/java -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& 'D:/tool/java17/bin/javac.exe' --release 17 -encoding UTF-8 -d build/semantic-tests Compiler.java @javaSources
$testClasspath = (Resolve-Path build/semantic-tests).Path
& 'D:/tool/java17/bin/java.exe' -cp $testClasspath org.buaa.parser.ParserTest
& 'D:/tool/java17/bin/java.exe' -cp $testClasspath org.buaa.semantic.SemanticModelTest
& 'D:/tool/java17/bin/java.exe' -cp $testClasspath org.buaa.semantic.SemanticAnalyzerTest
& 'D:/tool/java17/bin/java.exe' -cp $testClasspath org.buaa.CompilerTest
```

也可执行 `./gradlew.bat check`，会包含上述四组测试；可以通过
`parserTest`、`semanticModelTest`、`semanticTest`、`compilerTest` 分别执行。

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
        ├── semantic/
        └── source/
```

此时可在提交目录执行 `javac -encoding UTF-8 Compiler.java` 和 `java Compiler`。
只提交顶层 `Compiler.java` 会缺少 `org.buaa` 类；这些是项目源码，不是第三方包。

本地也可以继续使用 Gradle；在 PowerShell 中设置 `$env:JAVA_HOME = 'D:/tool/java17'`
后执行 `./gradlew.bat classes`，会同时编译顶层入口和 `src/main/java` 中的类。
