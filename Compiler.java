import org.buaa.Main;

/**
 * 评测平台入口：文件名和类名必须为 Compiler，且不声明 package。
 * 转发到 Gradle 源码目录中的现有编译器入口，仅依赖 JDK 17 和项目源码。
 */
public class Compiler {

    public static void main(String[] args) {
        Main.main(args);
    }
}
