// Token.java
public class Token {
    private TokenType type;
    private String lexeme;
    private int line;
    private int column;
    private int endLine;

    public Token(TokenType type, String lexeme, int line, int column) {
        this(type, lexeme, line, column, line);
    }

    public Token(TokenType type, String lexeme, int line, int column, int endLine) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
        this.endLine = endLine;
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    @Override
    public String toString() {
        if (endLine > line) {
            // Multi-line token
            return String.format("<%s, Lines %d-%d, Col: %d>", 
                type, line, endLine, column);
        }
        return String.format("<%s, \"%s\", Line: %d, Col: %d>", 
            type, lexeme, line, column);
    }
}
