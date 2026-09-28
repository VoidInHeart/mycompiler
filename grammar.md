# SysY Grammar

> 本文件从《2026 编译技术实验文法定义及相关说明》中提炼所有与文法、词法记号、文法语义约束及文法错误处理直接相关的内容。尽量沿用原文语句与规则格式；重复出现的文法规则只保留一份，并将后文约束归并到对应部分。

## 1. 文法覆盖要求

测试程序是为了测试编译器而编写的符合文法规则的 SysY 语言程序，在实验的“文法解读作业”中需要同学们编写测试程序。测试程序需覆盖文法中所有的语法规则（即每一条推导规则的每一个候选项都要被覆盖），并满足文法的语义约束（换而言之，测试程序应该是正确的）。

## 2. EBNF 表示约定

SysY 语言的文法采用扩展的 Backus 范式（EBNF，Extended Backus-Naur Form）表示，其中：

- 符号 `[...]` 表示方括号内包含的为可选项。
- 符号 `{...}` 表示花括号内包含的为可重复 0 次或多次的项。
- 终结符或者是由单引号括起的串，或者是 Ident、IntConst、StringConst 这样的记号。
- 所有类似 `'main'` 这样的用单引号括起的字符串都是保留的关键字。
- SysY 语言的文法中，`CompUnit` 为开始符号。

> 重要：建议同时对照文法的语义约束。

## 3. SysY 完整文法

```text
编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef // 1.是否存在Decl 2.是否存在FuncDef

声明 Decl → ConstDecl | VarDecl // 覆盖两种声明
常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';' // 1.花括号内重复0次 2.花括号内重复多次(花括号内重复>=2次，即，至少出现3次ConstDef)
基本类型 BType → 'int' | 'char' // 覆盖两种基本类型
常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // 包含普通变量、一维数组两种情况
常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}' | StringConst // 1.常表达式初值 2.一维数组初值 3. 字符串初值

变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';' // 1.花括号内重复0次 2.花括号内重复多次(花括号内重复>=2次，即，至少出现3次VarDef) 3. static 存在 4. static 不存在
变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // 包含普通常量、一维数组定义
变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}' | StringConst // 1.表达式初值 2.一维数组初值 3. 字符串初值

函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // 1.无形参 2.有形参
主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // 存在main函数
函数类型 FuncType → 'void' | 'int' | 'char' // 覆盖三种类型的函数
函数形参表 FuncFParams → FuncFParam { ',' FuncFParam } // 1.花括号内重复0次 2.花括号内重复多次(花括号内重复>=2次，即，至少出现3次FuncFParam)
函数形参 FuncFParam → BType Ident ['[' ']'] // 1.普通变量 2.一维数组变量

语句块 Block → '{' { BlockItem } '}' // 1.花括号内重复0次 2.花括号内重复多次(花括号内重复>=2次)
语句块项 BlockItem → Decl | Stmt // 覆盖两种语句块项

语句 Stmt → LVal '=' Exp ';' // 每种类型的语句都要覆盖
| [Exp] ';' // 有无Exp两种情况；printf函数调用
| Block
| 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // 1.有else 2.无else
| 'while' '(' Cond ')' Stmt // while循环语句
| 'switch' '(' Exp ')' '{' { CaseStmt } '}' // switch语句，需覆盖不同case情况
| 'break' ';'
| 'continue' ';'
| 'return' [Exp] ';' // 1.有Exp 2.无Exp
| 'printf' '(' StringConst { ',' Exp } ')' ';' // 1.有Exp 2.无Exp

Case语句 CaseStmt → 'case' Number ':' { Stmt } | 'default' ':' { Stmt } // 1.case语句 2.default语句

表达式 Exp → AddExp // 存在即可
条件表达式 Cond → LOrExp // 存在即可
左值表达式 LVal → Ident ['[' Exp ']'] // 1.普通变量、常量 2.一维数组
基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number // 1. 嵌套括号表达式 2. 左值表达式 3. 数值
数值 Number → IntConst | CharConst // 存在即可，覆盖整型常量和字符常量
一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp | '(' BType ')' UnaryExp // 1.PrimaryExp 2.函数调用且无 FuncRParams 3.函数调用且存在 FuncRParams 4.UnaryOp UnaryExp 5.显式类型转换
单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中 // 三种均需覆盖
函数实参表达式 FuncRParams → Exp { ',' Exp } // 1.花括号内重复0次 2.花括号内重复多次(花括号内重复>=2次，即，至少出现3次Exp) 3.Exp需要覆盖数组传参和数组元素传参

乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp // 1.UnaryExp 2.* 3./ 4.% 均需覆盖
加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp // 1.MulExp 2.+ 需覆盖 3.- 需覆盖
关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp // 1.AddExp 2.< 3.> 4.<= 5.>= 均需覆盖
相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp // 1.RelExp 2.== 3.!= 均需覆盖
逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp // 1.EqExp 2.&& 均需覆盖
逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp // 1.LAndExp 2.|| 均需覆盖
常量表达式 ConstExp → AddExp 注：使用的 Ident 必须是常量 // 存在即可
```

## 4. 词法记号与终结符规范

### 4.1 标识符 Ident

SysY 语言中标识符 Ident 的规范如下 ( identifier ) ：

```text
identifier → identifier-nondigit
    | identifier identifier-nondigit
    | identifier digit
```

其中，identifier-nondigit 为下划线或大小写字母，digit 为0到9的数字。

同名标识符的约定：

- 全局变量（常量）和局部变量（常量）的作用域可以重叠，重叠部分局部变量（常量）优先；
- 同名局部变量的作用域不能重叠，即同一个 Block 内不能有同名的标识符（常量/变量）；
- 在不同的作用域下，SysY 语言中变量（常量）名可以与函数名相同；
- 保留关键字不能作为标识符。

### 4.2 注释

SysY 语言中注释的规范与 C 语言一致，如下：

- 单行注释：以序列 `//` 开始，直到换行符结束，不包括换行符。
- 多行注释：以序列 `/*` 开始，直到第一次出现 `*/` 时结束，包括结束处 `*/`。

### 4.3 数值常量 IntConst

SysY 语言中数值常量可以是整型数 IntConst，其规范如下（对应 integer-const）：

```text
整型常量 integer-const → decimal-const | 0
decimal-const → nonzero-digit | decimal-const digit
```

nonzero-digit 为1至9的数字。

### 4.4 字符常量 CharConst

SysY 语言中字符常量可以是 CharConst，其规范如下：

```text
字符常量 CharConst → '\'' <CharElement> '\''
<CharElement> → <NormalChar> | <EscapeChar>
<NormalChar> → 除 '\''、'\\' 外的可见ASCII字符，即：ASCII码范围[0x20, 0x7E]内的字符，但不包含0x27和0x5C
<EscapeChar> → '\\n' | '\\t' | '\\r' | '\\0' | '\\\\' | '\\\''
```

字符常量用于表示单个字符，其值为对应字符的 ASCII 码值。字符常量可以赋给 char 类型变量，但不能直接赋给 int 类型变量，除非使用显式类型转换。

### 4.5 字符串常量 StringConst

SysY 语言中 StringConst 的定义如下：

```text
<FormatChar> → %d | %c | %s
<NormalChar> → 十进制编码为32,33,40-126的ASCII字符，'\'（编码92）出现当且仅当为'\n'
<Char> → <FormatChar> | <NormalChar>
<StringConst> → '"'{<Char>}'"'
```

字符串中仅可能会出现一种转义字符 `'\n'`，用以标注此处换行，其他转义情况无需考虑。

printf 函数中，%d 用于输出 int 类型值，%c 用于输出 char 类型值，%s 用于输出 string(char[]) 类型值。

printf 的使用方法为：

```text
'printf' '(' <StringConst> {, <Exp>} ')'
```

其中 StringConst 为字符串常量终结符。

## 5. 与文法直接相关的语义约束

符合上述文法的程序集合是合法的 SysY 语言程序集合的超集。下面进一步给出 SysY 语言的语义约束。

### 5.1 static 关键字

1. static 关键字用于修饰静态局部变量，不会在全局变量定义中出现。
2. 用 static 修饰的变量声明，即使在声明时未赋初值，编译器也会把它初始化为0。
3. 用 static 修饰的变量声明若有初始值，其初始值保证在编译期内可求出。
4. static 关键字指定的变量只初始化一次，并在之后调用该函数时保留其状态，离开函数时不会被销毁。

### 5.2 类型约束

1. 为降低教学复杂度，SysY 语言中 int 和 char 是两种不同的基本类型，不允许直接进行混合运算，但允许进行显式的类型转换，转换后类型一致则可以混合运算。
2. 当表达式中出现多类型（包括 int/char/int[]/char[]/void 任意类型）混合运算（且未正确使用显式类型转换）时，应报类型不匹配错误。
3. char 类型变量可以单独使用，也可以进行 char 类型之间的运算，运算结果仍为 char 类型。
4. int 类型变量可以单独使用，也可以进行 int 类型之间的运算，运算结果仍为 int 类型。
5. 在赋值语句中，左值和右值的类型必须一致，即 int 变量只能赋 int 值，char 变量只能赋 char 值。
6. 数组下标、数组长度只能使用 int 类型的表达式，不能使用 char 类型的表达式。
7. 函数参数传递时，实参类型必须与形参类型完全一致。
8. 函数返回值类型必须与函数声明的返回类型一致。
9. char 类型定义为 unsigned char（无符号8位整数），取值范围为 0 ~ 255。
10. char 类型运算发生溢出时，采用 模256回绕（wrap-around） 方式处理。例如：255 + 1 的结果为 0，0 - 1 的结果为 255。
11. char 类型变量的值范围为 0 ~ 255，其中 0 ~ 127 对应标准 ASCII 字符，可通过字符常量赋值；128 ~ 255 只能通过运算溢出或直接赋数值产生。
12. 当字符串常量用于初始化 char 或 char[] 时，应将字符串的字节序列（按转义处理后得到的字节）逐字节复制到数组中；若数组长度大于字符串长度，则剩余元素置 0；若数组长度 N 小于字符串长度，则只复制字符串的前 N 个字节，不要额外添加 `\0`。
13. 显式类型转换语法 `(BType) UnaryExp` 用于将基本类型表达式进行显式转换。被转换的表达式 UnaryExp 的类型必须是基本类型 int 或 char。转换后表达式的类型为目标基本类型 BType。

### 5.3 编译单元 CompUnit

```text
编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef
声明 Decl → ConstDecl | VarDecl
```

1. CompUnit 的顶层变量/常量声明语句（对应 Decl ）、函数定义（对应 FuncDef ）都不可以重复定义同名标识符（ Ident ），即便标识符的类型不同也不允许。
2. CompUnit 的变量/常量/函数声明的作用域从该声明处开始到文件结尾。

### 5.4 常量定义 ConstDef

```text
常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal
```

1. ConstDef 用于定义符号常量。ConstDef 中的 Ident 为常量的标识符，在 Ident 后、= 之前是可选的一维数组以及一维数组长度的定义部分，在 = 之后是初始值。常量必须有对应的初始值。
2. ConstDef 的一维数组以及一维数组长度的定义部分不存在时，表示定义单个变量。
3. ConstDef 的一维数组以及一维数组长度的定义部分存在时，表示定义数组。其语义和 C 语言一致，每维的下界从 0 编号。
4. ConstDef 中表示各维长度的 ConstExp 都必须是整型常量表达式。

### 5.5 变量定义 VarDef

```text
变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal
```

变量的定义与常量基本一致，但是变量可以没有初始值部分，此时其运行时实际初值未定义。

### 5.6 初值 ConstInitVal / InitVal

```text
常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}' | StringConst
变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}' | StringConst
```

1. 任何常量的初值表达式必须是常量表达式 ConstExp。
2. 常量必须有初始值，常量数组不需要每一个元素都有初始值，但是未赋值的部分编译器需要将其置0。
3. 全局变量的初值表达式也必须是常量表达式 ConstExp。但局部变量的初值表达式是 Exp，可以使用已经声明的变量。
4. 对于单个的常量或变量，初始值也必须是单个的表达式。（表达式包括单个数字、单个变量、单个常量等情况）
5. 对于全局变量，如果没有给出初始值，那么应该全部置 0 ，局部变量不需要。

### 5.7 函数形参 FuncFParam 与实参 FuncRParams

```text
函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
函数实参表 FuncRParams → Exp { ',' Exp }
```

1. FuncFParam 定义一个函数的一个形式参数。当 Ident 后面的可选部分存在时，表示数组定义。
2. 函数实参的语法是 Exp。对于普通变量，遵循按值传递；对于数组类型的参数，则形参接收的是实参数组的地址，并通过地址间接访问实参数组中的元素。
3. 函数调用时要保证实参类型和形参类型一致。

### 5.8 函数定义 FuncDef

```text
函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
```

1. FuncDef 表示函数定义。其中的 FuncType 指明返回类型。当返回类型为 int 时，函数内所有分支都应当含有带有 Exp 的 return 语句。不含有 return 语句的分支的返回值未定义。当返回值类型为 void 时，函数内只能出现不带返回值的 return 语句。
2. FuncFParams 的语义如前文。

### 5.9 语句块 Block

1. Block 表示语句块。语句块会创建作用域，语句块内声明的常量和变量的生存期在该语句块内。
2. 语句块内可以再次定义与语句块外同名的变量或常量 (通过 Decl 语句)，其作用域从定义处开始到该语句块尾结束，它隐藏语句块外的同名变量或常量。
3. 保证所有测试程序中有返回值的函数 Block 的最后一句一定会显式的给出 return 语句，否则就当作“无返回语句”的错误处理。
4. main 函数的返回值只能为常数0。

### 5.10 语句 Stmt

1. Stmt 中的 if 类型语句遵循就近匹配，即，在没有使用大括号明确界定代码块范围的情况下，一个 else 语句总是与它上方距离它最近的、且尚未配对的 if 语句结合（配对）。
2. 单个 Exp 可以作为 Stmt。这个 Exp 会被求值（这可能产生副作用），但是所求的值会被丢弃。
3. while 语句中，Cond 为循环条件，Stmt 为循环体。当 Cond 为真时，执行 Stmt，否则跳出循环。
4. switch 语句中，Exp 为 switch 表达式，其值用于与各个 case 的 Number 进行匹配。
5. switch 语句中的每个 case 后必须有 Number 作为匹配值。Number 的类型必须与 switch 表达式 Exp 的类型一致：若 Exp 为 int 类型，Number 必须为 int 常量（IntConst）；若 Exp 为 char 类型，Number 必须为 char 常量（CharConst）。
6. switch 语句中最多可以有一个 default 语句，当所有 case 都不匹配时执行 default 后的语句。
7. break 语句可以出现在循环语句（while）或 switch 语句中，用于跳出循环或 switch 结构。
8. continue 语句只能出现在循环语句（while）中，用于跳过当前迭代，继续下一次循环。
9. switch 语句中，break 用于跳出 switch 结构，若某个 case 后没有 break，则继续执行下一个 case 的语句。

### 5.11 左值 LVal

1. LVal 表示具有左值的表达式，可以为变量或者某个数组元素。
2. 当 LVal 表示数组时，方括号个数必须和数组变量的维数相同（即定位到元素）。
3. 当 LVal 表示单个变量时，不能出现后面的方括号。

### 5.12 Exp 与 Cond

1. Exp 在 SysY 中代表算术表达式；Cond 代表条件表达式，故它定义为 LOrExp。前者的单目运算符中不出现'!'，后者可以出现。
2. LVal 必须是当前作用域内、该 Exp 语句之前有定义的变量或常量；对于赋值号左边的 LVal 必须是变量。
3. 函数调用形式是 `Ident '(' FuncRParams ')'`，其中的 FuncRParams 表示实际参数。实际参数的类型和个数必须与 Ident 对应的函数定义的形参完全匹配。
4. SysY 中算符的优先级与结合性与 C 语言一致，在上面的 SysY 文法中已体现出优先级与结合性的定义。

### 5.13 一元表达式 UnaryExp

1. 相邻两个 UnaryOp 不能相同，如 `int a = ++-i;`，但是 `int a = +-+i;` 是可行的。
2. UnaryOp 为 ! 只能出现在条件表达式中。
3. 显式类型转换 `(BType) UnaryExp` 中的 BType 只能是基本类型 int 或 char。

### 5.14 常量表达式 ConstExp

1. ConstExp 在 SysY 中代表可在编译期求值的表达式，可以是 int 型表达式或 char 型表达式。
2. ConstExp 在编译期内是可被求值的，且需要符合 C 语言标准中“整型常量表达式（integer constant expression）”的约束：其操作数只能为整型常量、字符常量，不能访问其它变量、不能进行函数调用（即使访问的变量、进行的函数调用理论上是能在编译期求值的）。
3. ConstExp 中不能出现 int 和 char 类型的混合运算，必须保证类型一致性（如果使用了显式类型转换，则按照转换后的目标类型进行一致性检查）。
4. ConstExp 主要用于以下场景：
   - 数组定义时指定各维长度（此时必须是 int 型常量表达式）
   - 常量定义时的初始值
   - 全局变量的初始值
5. 常量表达式中允许包含显式类型转换 `(BType) UnaryExp`，且在编译期对其进行求值（即支持常量折叠，例如将字符常量强转为 int，或将整型常量强转为 char，转换后的值在编译期直接计算出来）。

## 6. 难度分级中涉及的文法范围

- C：不包括任何与数组有关的部分，if 与 while 中的条件表达式也只有一个表达式（即不出现 && 和 || ），不考察短路求值的规则，不考察 char 类型与 switch 语句。
- B：在 C 级的基础上，新增数组，包括数组定义，数组元素的使用、数组传参等，新增 char 类型与 switch 语句。
- A：在 B 级的基础上，新增复杂条件的运算和判断，要遵守短路求值的规则。

## 7. 文法符号与错误类型对应

错误类别 a 为词法分析中会出现的错误；错误类别 i、j、k 为语法分析中会出现的错误；剩余错误类别均为语义分析中会出现的错误。

```text
编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef
声明 Decl → ConstDecl | VarDecl
常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';' // i
基本类型 BType → 'int' | 'char'
常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // b, k
常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}'
变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';' // i
变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // b, k
变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // b, e, g, j
主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // g, j
函数类型 FuncType → 'void' | 'int' | 'char'
函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
函数形参 FuncFParam → BType Ident ['[' ']'] // b, k
语句块 Block → '{' { BlockItem } '}'
语句块项 BlockItem → Decl | Stmt
语句 Stmt → LVal '=' Exp ';' // e, h, i
| [Exp] ';' // i
| Block
| 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // j
| 'while' '(' Cond ')' Stmt // j
| 'switch' '(' Exp ')' '{' { CaseStmt } '}' // j
| 'break' ';' | 'continue' ';' // i, m
| 'return' [Exp] ';' // e, f, i
| 'printf' '(' StringConst { ',' Exp } ')' ';' // e, l
Case语句 CaseStmt → 'case' Number ':' { Stmt } | 'default' ':' { Stmt } // e, n
表达式 Exp → AddExp
条件表达式 Cond → LOrExp
左值表达式 LVal → Ident ['[' Exp ']'] // c, k
基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number // j
数值 Number → IntConst | CharConst
一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp | '(' BType ')' UnaryExp // c, d, e, j
单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
函数实参表 FuncRParams → Exp { ',' Exp }
乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp // a
逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp // a
常量表达式 ConstExp → AddExp 注：使用的 Ident 必须是常量
```

### 7.1 各错误类别的文法含义

| 类别码 | 错误 | 与文法相关的说明 |
|---|---|---|
| a | 非法符号 | 出现 `&` 和 `\|` 时，应将其当做 `&&` 与 `||` 进行处理，但是记录单词名称时仍记录 `&` 和 `|`。对应 LAndExp / LOrExp。 |
| b | 名字重定义 | 函数名或者变量名在当前作用域下重复定义。对应 ConstDef、VarDef、FuncDef、FuncFParam 中的 Ident。 |
| c | 未定义的名字 | 使用了未定义的标识符。对应 LVal、UnaryExp 中的 Ident。 |
| d | 函数参数个数不匹配 | 函数调用语句中，参数个数与函数定义中的参数个数不匹配。对应 `UnaryExp → Ident '(' [FuncRParams] ')'`。 |
| e | 类型不匹配 | 包括混合运算、返回类型、赋值、函数实参与形参、switch-case 类型、printf 格式类型、函数标识符与变量相互误用等类型不匹配。 |
| f | 无返回值的函数存在不匹配的 return 语句 | 对应 `Stmt → 'return' [Exp] ';'`。 |
| g | 有返回值的函数缺少 return 语句 | 只需要考虑函数末尾是否存在 return 语句，无需考虑控制流。对应 FuncDef / MainFuncDef。 |
| h | 不能改变常量的值 | LVal 为常量时，不能对其修改。对应赋值语句。 |
| i | 缺少分号 | Stmt、ConstDecl 及 VarDecl 中的 `;`。 |
| j | 缺少右小括号 `)` | 函数调用、函数定义、Stmt、PrimaryExp 及显式类型转换中的 `)`。 |
| k | 缺少右中括号 `]` | 数组定义（ConstDef, VarDef, FuncFParam）和使用（LVal）中的 `]`。 |
| l | printf 中格式字符与表达式个数不匹配 | 对应 `'printf' '(' StringConst {',' Exp} ')' ';'`。 |
| m | 在非循环块中使用 break 和 continue 语句 | break 可以出现在 while 循环或 switch 语句中，continue 只能出现在 while 循环中。 |
| n | switch 语句中 case 和 default 重复 | switch 语句中出现重复的 case 常量值，或者出现多个 default 分支。 |

## 8. 错误处理中特别涉及语法分析的说明

1. 错误 i，j，k 类型中的“前一个非终结符”强调的是在文法规则里出现在 `; ) ]` 之前的非终结符号，在分析中处理的是该非终结符产生的终结符号串的最后一个符号，也就是 `; ) ]` 本应该正常出现的位置的上一个单词。
2. 所有错误都不会出现恶意换行的情况，包括字符、字符串中的换行符、函数调用等等。所谓恶意换行是指会使得错误处理难以正确进行的换行。
3. 其他类型的错误，错误的行号以能够断定发现出错的第一个符号的行号为准。
4. 为了避免因为测试程序中的错误导致出现语法二义性，使得语法树以错误的方式建立，保证所有会导致语法树不能正确建立的错误都不会出现。
5. 每一行中最多只有一个错误。
6. 同一作用域下函数和变量之间不能有相同的名字。
7. 对于一个名字重定义的函数，也应该完整分析函数内部是否具有其它错误。
8. 对于调用函数时，参数类型不匹配一共有以下几种情况的不匹配：
   - 传递数组给变量。
   - 传递变量给数组。
   - 传递 int 类型给 char 类型参数。
   - 传递 char 类型给 int 类型参数。
9. 对于 e 类型错误，包含所有类型不匹配的错误，本文法共5种类型，分别是 int、char、int[]、char[]、void。对于不符合下述类型匹配规则的错误，均应当返回 e 类型错误：
   - 不同类型不能进行任何混合运算，包括赋值、算数运算、关系运算。
   - 函数调用时，实参类型必须与形参类型完全匹配。
   - 函数返回值类型必须与函数定义时声明的返回值类型完全匹配。
   - switch 语句中，case 标签的常量表达式类型必须与 switch 表达式的类型完全匹配，且必须是 int 或 char。
   - 数组下标必须是 int 类型。
   - 不能将非数组类型变量进行数组访问。
10. 对于显式类型转换 `(BType) UnaryExp`，测试用例保证其操作数 UnaryExp 必须是基本类型（int 或 char），且目标类型 BType 也是基本类型（int 或 char），不测试对数组或 void 等非基本类型的显式转换。如果显式类型转换缺少右小括号 `)`，则应报告 j 类型错误，其报错行号为前一个非终结符 BType（即关键字 int 或 char）所在的行号。
11. 测试程序保证不出现未提到的错误形式。
