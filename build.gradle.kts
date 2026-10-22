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

val regressionTests = mapOf(
    "parserTest" to "org.buaa.parser.ParserTest",
    "semanticModelTest" to "org.buaa.semantic.SemanticModelTest",
    "semanticTest" to "org.buaa.semantic.SemanticAnalyzerTest",
    "compilerTest" to "org.buaa.CompilerTest"
).map { (taskName, testClass) ->
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = "Run $testClass using only the JDK."
        dependsOn(tasks.testClasses)
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set(testClass)
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        })
    }
}

tasks.test {
    // 以下测试使用 main 自行断言，不参与 JUnit 测试发现。
    exclude("org/buaa/parser/ParserTest*.class")
    exclude("org/buaa/semantic/*Test*.class")
    exclude("org/buaa/CompilerTest*.class")
}

tasks.check {
    dependsOn(regressionTests)
}
