plugins {
    id("java")
}

group = "org.buaa"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.compileJava {
    source("Compiler.java")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

val parserTest = tasks.register<JavaExec>("parserTest") {
    group = "verification"
    description = "Run parser and AST regression tests using only the JDK."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.buaa.parser.ParserTest")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}

tasks.test {
    // ParserTest 使用 main 自行断言，由 parserTest 运行，不参与 JUnit 测试发现。
    exclude("org/buaa/parser/ParserTest*.class")
}

tasks.check {
    dependsOn(parserTest)
}
