package org.buaa.lexer;

public enum TokenType {
    IDENFR,     //Ident

    INTCON,     //IntConst
    CHARCON,    //CharConst
    STRCON,     //StringConst
    CHARTK,     //char
    VOIDTK,     //void
    INTTK,      //int

    CONTINUETK, //continue
    BREAKTK,    //break
    DEFAULTTK,  //default
    RETURNTK,   //return
    WHILETK,    //while
    CASETK,     //case
    ELSETK,     //else
    IFTK,       //if
    SWITCHTK,   //switch

    PRINTFTK,   //printf

    STATICTK,   //static
    CONSTTK,    //const

    MAINTK,     //main

    NEQ,        // !=
    LEQ,        // <=
    GEQ,        // >=
    EQL,        // ==
    LSS,        // <
    GRE,        // >

    OR,         // ||
    AND,        // &&
    NOT,        // !

    MOD,        // %
    PLUS,       // +
    MINU,       // -
    MULT,       // *
    DIV,        // /
    ASSIGN,     // =

    LPARNET,    // (
    RPARNET,    // )
    LBRACK,     // [
    RBRACK,     // ]
    LBRACE,     // {
    RBRACE,     // }

    COMMA,      // ,
    COLON,      // :
    SEMICN,     // ;

    EOF         // end of file
}
