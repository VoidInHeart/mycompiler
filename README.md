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

无词法错误时输出 `lexer.txt`；有词法错误时输出 `error.txt`。
输出文件位于运行命令的当前目录。

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
        ├── diagnostic/
        ├── lexer/
        └── source/
```

此时可在提交目录执行 `javac -encoding UTF-8 Compiler.java` 和 `java Compiler`。
只提交顶层 `Compiler.java` 会缺少 `org.buaa` 类；这些是项目源码，不是第三方包。

本地也可以继续使用 Gradle；在 PowerShell 中设置 `$env:JAVA_HOME = 'D:/tool/java17'`
后执行 `./gradlew.bat classes`，会同时编译顶层入口和 `src/main/java` 中的类。
