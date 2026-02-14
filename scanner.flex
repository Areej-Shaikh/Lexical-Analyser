import java.io.FileReader;
import java.io.IOException;

%%

%class Yylex
%unicode
%line
%column
%type Token
%public

%{
private Token token(TokenType type, String lexeme) {
    return new Token(type, lexeme, yyline + 1, yycolumn + 1);
}
%}

/* ---------- MACROS ---------- */
DIGIT       = [0-9]
INT         = {DIGIT}+
FLOAT       = {DIGIT}+"."{DIGIT}{1,6}([eE][+-]?{DIGIT}+)?
ID          = [A-Z][a-z0-9_]{0,30}
WS          = [ \t\r\n]+

/* Keywords */
KEYWORD     = start|finish|loop|condition|declare|output|input|function|return|break|continue|else

/* Boolean */
BOOLEAN     = true|false

/* Operators */
OP_MULTI    = "**"|"=="|"!="|"<="|">="|"&&"|"||"|"++"|"--"|"+="|"-="|"*="|"/="
OP_SINGLE   = [+\-*/%=!<>]

/* Punctuators */
PUNCT       = [(){}\[\],;:]

/* Comments */
SLCOMMENT   = "##"[^\n]*
MLCOMMENT   = "#*"([^*]|\*+[^*#])*"*#"

%%

/* ---------- 1. MULTI-LINE COMMENTS ---------- */
{MLCOMMENT}   { return token(TokenType.SINGLE_LINE_COMMENT, yytext()); }

/* ---------- 2. SINGLE-LINE COMMENTS ---------- */
{SLCOMMENT}   { return token(TokenType.SINGLE_LINE_COMMENT, yytext()); }

/* ---------- 3. MULTI-CHARACTER OPERATORS ---------- */
{OP_MULTI}    { return token(TokenType.OPERATOR, yytext()); }

/* ---------- 4. KEYWORDS ---------- */
{KEYWORD}     { return token(TokenType.IDENTIFIER, yytext()); }

/* ---------- 5. BOOLEAN ---------- */
{BOOLEAN}     { return token(TokenType.BOOLEAN_EXPRESSION, yytext()); }

/* ---------- 6. IDENTIFIERS ---------- */
{ID}          { return token(TokenType.IDENTIFIER, yytext()); }

/* ---------- 7. FLOAT BEFORE INT ---------- */
{FLOAT}       { return token(TokenType.FLOAT_LITERAL, yytext()); }

/* ---------- 8. INTEGER ---------- */
{INT}         { return token(TokenType.INTEGER_LITERAL, yytext()); }

/* ---------- 9. STRING LITERALS ---------- */
\u0022([^\u0022\\\n]|\\[\u0022\\ntr])*\u0022   { return token(TokenType.IDENTIFIER, yytext()); }

/* ---------- 10. CHAR LITERALS ---------- */
\'([^\'\\\n]|\\[\'\\ntr])\'                    { return token(TokenType.IDENTIFIER, yytext()); }

/* ---------- 11. SINGLE-CHARACTER OPERATORS ---------- */
{OP_SINGLE}   { return token(TokenType.OPERATOR, yytext()); }

/* ---------- 12. PUNCTUATORS ---------- */
{PUNCT}       { return token(TokenType.PUNCTUATOR, yytext()); }

/* ---------- 13. WHITESPACE ---------- */
{WS}          { /* skip */ }

/* ---------- INVALID TOKENS ---------- */
.             { return token(TokenType.ERROR, yytext()); }