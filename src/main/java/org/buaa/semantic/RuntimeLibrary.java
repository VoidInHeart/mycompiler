package org.buaa.semantic;

import org.buaa.lexer.Token;
import org.buaa.lexer.TokenType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.buaa.semantic.ValueType.*;

/**
 * PDF 第 1–2、7 页的运行时签名。它们参与名字和类型检查，但不是源程序中的声明，
 * 不进入用户 Scope，不消耗作用域编号，不生成活动记录，也不输出到 symbol.txt。
 */
public final class RuntimeLibrary {
    private RuntimeLibrary() { }

    static Map<String, Symbol> functions() {
        Map<String, Symbol> functions = new LinkedHashMap<>();
        define(functions, "get_int", INT);
        define(functions, "get_char", CHAR);
        define(functions, "get_string", VOID, CHAR_ARRAY, INT);
        define(functions, "put_int", VOID, INT);
        define(functions, "put_char", VOID, CHAR);
        define(functions, "put_str", VOID, CHAR_ARRAY);
        // PDF 表格名称和配套 libsysy.h 使用 put_string，原型/约束另写作 put_str。
        define(functions, "put_string", VOID, CHAR_ARRAY);
        return Map.copyOf(functions);
    }

    private static void define(Map<String, Symbol> functions, String name,
                               ValueType returnType, ValueType... parameters) {
        // 运行时签名没有源码位置；scopeId=1 表示全局可见，不代表加入全局输出表。
        functions.put(name, new Symbol(1, new Token(TokenType.IDENFR, name, 0, 0),
                Symbol.Kind.RUNTIME_FUNCTION, returnType, List.of(parameters), null));
    }
}
