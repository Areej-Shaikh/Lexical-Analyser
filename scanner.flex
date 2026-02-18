/* User code section */

%%

%class Yylex
%unicode
%line
%column
%type Token
%public

%state COMMENT
%state STRING
%state CHARLIT

%{

private int commentDepth = 0;
private int commentStartLine = 0;
private StringBuilder stringBuffer = new StringBuilder();
private StringBuilder commentBuffer = new StringBuilder();
private boolean eofProcessed = false;

private Token token(TokenType type, String lexeme) {
    return new Token(type, lexeme, yyline + 1, yycolumn + 1);
}

private Token token(TokenType type, String lexeme, int endLine) {
    return new Token(type, lexeme, commentStartLine, 1, endLine);
}

%}

/* MACROS */

DIGIT     = [0-9]
INT       = {DIGIT}+
FLOAT     = {DIGIT}+"."{DIGIT}{1,6}([eE][+-]?{DIGIT}+)?
ID        = [A-Z][a-z0-9_]{0,30}
CAMELCASE = [A-Z][a-z]+[A-Z][a-zA-Z0-9_]*
WS        = [ \t\r\n]+

KEYWORD   = start|finish|loop|condition|declare|output|input|function|return|break|continue|else|if
UPPER_KEYWORD = START|FINISH|LOOP|CONDITION|DECLARE|OUTPUT|INPUT|FUNCTION|RETURN|BREAK|CONTINUE|ELSE|IF
BOOLEAN   = true|false

OP_MULTI  = "**"|"=="|"!="|"<="|">="|"&&"|"||"|"++"|"--"|"+="|"-="|"*="|"/="
OP_SINGLE = [+\-*/%=!<>]

PUNCT     = [(){}\[\],;:]

INVALID_ID = ([a-z][a-zA-Z0-9_]*)|([0-9]+[a-zA-Z][a-zA-Z0-9_]*)

%%

/* MULTI-LINE NESTED COMMENTS  */
<YYINITIAL> "#*" {
    commentStartLine = yyline + 1;
    commentDepth = 1;
    commentBuffer.setLength(0);
    commentBuffer.append(yytext());
    yybegin(COMMENT);
}

<COMMENT> "#*" {
    commentDepth++;
    commentBuffer.append(yytext());
}

<COMMENT> "*#" {
    commentDepth--;
    commentBuffer.append(yytext());
    if (commentDepth == 0) {
        int endLine = yyline + 1;
        yybegin(YYINITIAL);
        return token(TokenType.MULTI_LINE_COMMENT, commentBuffer.toString(), endLine);
    }
}

<COMMENT> <<EOF>> {
    if (eofProcessed) {
        eofProcessed = false;
        return null;
    }
    eofProcessed = true;
    yybegin(YYINITIAL);
    int endLine = yyline + 1;
    return token(TokenType.ERROR, "Unterminated multi-line comment", endLine);
}

<COMMENT> [^] {
    commentBuffer.append(yytext());
}

/* SINGLE-LINE COMMENT  */
<YYINITIAL> "##"[^\r\n]* {
    return token(TokenType.SINGLE_LINE_COMMENT, yytext());
}

/*  MULTI-CHAR OPERATORS*/
<YYINITIAL> {OP_MULTI}  { return token(TokenType.OPERATOR, yytext()); }


<YYINITIAL> \" {
    stringBuffer.setLength(0);
    stringBuffer.append("\"");
    yybegin(STRING);
}

/* ---------- STRING CONTENT ---------- */
/* Simple escaped characters */
<STRING> \\\"       { stringBuffer.append("\""); }
<STRING> \\\\       { stringBuffer.append("\\"); }
<STRING> \\n        { stringBuffer.append("\n"); }
<STRING> \\t        { stringBuffer.append("\t"); }
<STRING> \\r        { stringBuffer.append("\r"); }

/* Unicode escape */
<STRING> \\u[0-9a-fA-F]{4} { stringBuffer.append(yytext()); }

/* Any character except " or \ or newline */
<STRING> [^\x22\\\n\r]   { stringBuffer.append(yytext()); }

/* Unterminated string on newline */
<STRING> [\r\n] {
    yybegin(YYINITIAL);
    return token(TokenType.ERROR, "Unterminated string literal");
}

/* ---------- STRING CLOSE ---------- */
<STRING> \" {
    stringBuffer.append("\"");
    yybegin(YYINITIAL);
    return token(TokenType.STRING_LITERAL, stringBuffer.toString());
}

/* ---------- EOF BEFORE CLOSE ---------- */
<STRING> <<EOF>> {
    yybegin(YYINITIAL);
    return token(TokenType.ERROR, "Unterminated string literal");
}

/* UNRECOGNIZED CHARACTERS */
<YYINITIAL> \$[a-zA-Z0-9_]+ { return token(TokenType.ERROR, yytext()); }
<YYINITIAL> [\"\$]      { return token(TokenType.ERROR, yytext()); }

/* UPPERCASE KEYWORDS  */
<YYINITIAL> {UPPER_KEYWORD} { return token(TokenType.ERROR, yytext()); }

/* KEYWORDS*/
<YYINITIAL> {KEYWORD}   { return token(TokenType.IDENTIFIER, yytext()); }

/*  BOOLEAN  */
<YYINITIAL> {BOOLEAN}   { return token(TokenType.BOOLEAN_EXPRESSION, yytext()); }

/*  IDENTIFIER */
<YYINITIAL> [A-Z][a-zA-Z0-9_]{31}[a-zA-Z0-9_]* { return token(TokenType.ERROR, yytext()); }
<YYINITIAL> {CAMELCASE} { return token(TokenType.ERROR, yytext()); }
<YYINITIAL> {ID}        { return token(TokenType.IDENTIFIER, yytext()); }

/*  MALFORMED DECIMALS */
<YYINITIAL> [+-]?{DIGIT}+"."{DIGIT}{1,6}([eE][+-]?{DIGIT}+)?("."[0-9]+)+ {
    return token(TokenType.ERROR, yytext());
}

/*  FLOAT  */
<YYINITIAL> [+-]{DIGIT}+"."{DIGIT}{1,6}([eE][+-]?{DIGIT}+)? {
    return token(TokenType.FLOAT_LITERAL, yytext());
}
<YYINITIAL> {FLOAT}     { return token(TokenType.FLOAT_LITERAL, yytext()); }

/*  INTEGER  */
<YYINITIAL> [+-]{DIGIT}+ { return token(TokenType.INTEGER_LITERAL, yytext()); }
<YYINITIAL> {INT}        { return token(TokenType.INTEGER_LITERAL, yytext()); }

/*CHARACTER LITERALS  */
<YYINITIAL> \'([^\'\\]|\\[\'\\ntr])\' {
    return token(TokenType.CHAR_LITERAL, yytext());
}

/* Malformed character literals */
<YYINITIAL> \' {
    stringBuffer.setLength(0);
    yybegin(CHARLIT);
}

<CHARLIT> \\[\'\\ntr] {
    stringBuffer.append(yytext());
}

<CHARLIT> \' {
    yybegin(YYINITIAL);
    return token(TokenType.ERROR, "MALFORMED_CHAR_LITERAL:" + stringBuffer.toString());
}

<CHARLIT> [\r\n] {
    yybegin(YYINITIAL);
    return token(TokenType.ERROR, "Unclosed character literal");
}

<CHARLIT> <<EOF>> {
    yybegin(YYINITIAL);
    return token(TokenType.ERROR, "Unclosed character literal");
}

<CHARLIT> [^] {
    stringBuffer.append(yytext());
}

/* SINGLE-CHAR OPERATORS  */
<YYINITIAL> {OP_SINGLE} { return token(TokenType.OPERATOR, yytext()); }

/* PUNCTUATORS  */
<YYINITIAL> {PUNCT}     { return token(TokenType.PUNCTUATOR, yytext()); }

/*  WHITESPACE  */
<YYINITIAL> {WS}        { /* skip */ }

/* INVALID IDENTIFIERS  */
<YYINITIAL> {INVALID_ID} { return token(TokenType.ERROR, yytext()); }

/*  ERROR  */
<YYINITIAL> .           { return token(TokenType.ERROR, yytext()); }