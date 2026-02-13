import java.util.*;

public class ManualScanner {
    private final String sourceCode;
    private int index;
    private int line;
    private int column;
    private SymbolTable symbolTable;
    
    // Statistics tracking
    private int totalTokens;
    private int totalCommentsRemoved;
    private int totalLinesProcessed;
    private Map<String, Integer> tokenTypeCount;

    // DFA States
    private enum State {
        START, DIGIT, DOT, FLOAT, LETTER, IDENTIFIER, OPERATOR, PUNCTUATOR
    }

    public ManualScanner(String sourceCode) {
        this.sourceCode = sourceCode;
        this.index = 0;
        this.line = 1;
        this.column = 1;
        this.symbolTable = new SymbolTable();
        this.totalTokens = 0;
        this.totalCommentsRemoved = 0;
        this.totalLinesProcessed = countLines(sourceCode);
        this.tokenTypeCount = new HashMap<>();
        initializeTokenTypeCount();
    }

    private void initializeTokenTypeCount() {
        for (TokenType type : TokenType.values()) {
            tokenTypeCount.put(type.toString(), 0);
        }
    }

    // Helper: Count total lines in source code
    private int countLines(String code) {
        return (int) code.chars().filter(ch -> ch == '\n').count() + 1;
    }

    public List<Token> scan() {
        List<Token> tokens = new ArrayList<>();

        while (index < sourceCode.length()) {
            char currentChar = sourceCode.charAt(index);

            // Skip whitespace but track line and column
            if (Character.isWhitespace(currentChar)) {
                if (currentChar == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
                index++;
                continue;
            }

            Token token;

            // Priority order: check each token type (Longest Match Principle)
            if ((token = matchSingleLineComment()) != null) {
                totalCommentsRemoved++;
                // Comments are filtered out
            } else if ((token = matchBooleanLiteral()) != null) {
                tokens.add(token);
                recordToken(token);
            } else if ((token = matchNumericLiteral()) != null) {
                tokens.add(token);
                recordToken(token);
            } else if ((token = matchIdentifier()) != null) {
                symbolTable.insert(token.getLexeme(), "IDENTIFIER", line);
                tokens.add(token);
                recordToken(token);
            } else if ((token = matchMultiCharOperator()) != null) {
                tokens.add(token);
                recordToken(token);
            } else if ((token = matchSingleCharOperator()) != null) {
                tokens.add(token);
                recordToken(token);
            } else if ((token = matchPunctuator()) != null) {
                tokens.add(token);
                recordToken(token);
            } else {
                // No match → error
                System.err.println("Invalid Token at line " + line + ", column " + column + ": '" + currentChar + "'");
                index++;
                column++;
            }
        }

        return tokens;
    }

    // Record token statistics
    private void recordToken(Token token) {
        totalTokens++;
        String tokenType = token.getType().toString();
        tokenTypeCount.put(tokenType, tokenTypeCount.getOrDefault(tokenType, 0) + 1);
    }

    // Display statistics
    public void displayStatistics() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SCANNER STATISTICS");
        System.out.println("=".repeat(70));
        
        // Total tokens
        System.out.println("\nTotal Tokens:               " + totalTokens);
        
        // Comments removed
        System.out.println("Total Comments Removed:     " + totalCommentsRemoved);
        
        // Lines processed
        System.out.println("Lines Processed:            " + totalLinesProcessed);
        
        // Count per token type
        System.out.println("\nCount Per Token Type:");
        System.out.println("-".repeat(70));
        
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(tokenTypeCount.entrySet());
        sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        for (Map.Entry<String, Integer> entry : sortedEntries) {
            if (entry.getValue() > 0) {
                System.out.printf("  %-30s: %d\n", entry.getKey(), entry.getValue());
            }
        }
        
        System.out.println("=".repeat(70) + "\n");
    }

    // ----------------------
    // DFA-based Match functions
    // ----------------------

    // DFA for single-line comments
    // Rules: ##[ ^\n]* (## followed by any characters except newline)
    private Token matchSingleLineComment() {
        int startColumn = column;
        int startIndex = index;
        
        // State: START → check for ## (comment delimiter)
        if (index + 1 < sourceCode.length() && sourceCode.charAt(index) == '#' && sourceCode.charAt(index + 1) == '#') {
            index += 2;
            column += 2;
            
            // State: COMMENT → consume all characters until newline
            while (index < sourceCode.length() && sourceCode.charAt(index) != '\n') {
                index++;
                column++;
            }
            
            String lexeme = sourceCode.substring(startIndex, index);
            return new Token(TokenType.SINGLE_LINE_COMMENT, lexeme, line, startColumn);
        }
        return null;
    }

    // DFA for boolean literals
    // Rules: (true|false) - case-sensitive, must be word boundary
    private Token matchBooleanLiteral() {
        int startColumn = column;
        int startIndex = index;
        State state = State.START;
        
        // State: START → check for "true" (case-sensitive)
        if (index + 4 <= sourceCode.length() && sourceCode.substring(index, index + 4).equals("true")) {
            // Validate word boundary: next char must not be letter, digit, or underscore
            if (index + 4 >= sourceCode.length() || 
                (!Character.isLetterOrDigit(sourceCode.charAt(index + 4)) && sourceCode.charAt(index + 4) != '_')) {
                state = State.IDENTIFIER;
                index += 4;
                column += 4;
                return new Token(TokenType.BOOLEAN_LITERAL, "true", line, startColumn);
            }
        }
        
        // State: START → check for "false" (case-sensitive)
        if (index + 5 <= sourceCode.length() && sourceCode.substring(index, index + 5).equals("false")) {
            // Validate word boundary: next char must not be letter, digit, or underscore
            if (index + 5 >= sourceCode.length() || 
                (!Character.isLetterOrDigit(sourceCode.charAt(index + 5)) && sourceCode.charAt(index + 5) != '_')) {
                state = State.IDENTIFIER;
                index += 5;
                column += 5;
                return new Token(TokenType.BOOLEAN_LITERAL, "false", line, startColumn);
            }
        }
        
        return null;
    }

    // DFA for numeric literals (INTEGER or FLOAT)
    // Integer: [+-]?[0-9]+
    // Float: [+-]?[0-9]+\.[0-9]{1,6}([eE][+-]?[0-9]+)?
    private Token matchNumericLiteral() {
        int startColumn = column;
        int startIndex = index;
        State state = State.START;

        // State: START → check for optional sign [+|-]
        if (index < sourceCode.length() && (sourceCode.charAt(index) == '+' || sourceCode.charAt(index) == '-')) {
            index++;
            column++;
        }

        // State: START/SIGN → DIGIT (must have at least one digit)
        if (index >= sourceCode.length() || !Character.isDigit(sourceCode.charAt(index))) {
            // No digit found after optional sign, reset and return null
            index = startIndex;
            column = startColumn;
            return null;
        }

        state = State.DIGIT;
        while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
            index++;
            column++;
        }

        // State: DIGIT → DOT (check for decimal point)
        if (index < sourceCode.length() && sourceCode.charAt(index) == '.') {
            state = State.DOT;
            index++;
            column++;
            
            // State: DOT → FLOAT (must have 1-6 digits after dot)
            int decimalCount = 0;
            
            while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index)) && decimalCount < 6) {
                state = State.FLOAT;
                index++;
                column++;
                decimalCount++;
            }
            
            // Invalid: no decimal digits or more than 6 decimal places
            if (decimalCount == 0 || (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index)))) {
                index = startIndex;
                column = startColumn;
                return null;
            }
            
            // State: FLOAT → EXPONENT (check for [eE][+-]?[0-9]+)
            if (index < sourceCode.length() && (sourceCode.charAt(index) == 'e' || sourceCode.charAt(index) == 'E')) {
                index++;
                column++;
                
                // Optional sign after exponent
                if (index < sourceCode.length() && (sourceCode.charAt(index) == '+' || sourceCode.charAt(index) == '-')) {
                    index++;
                    column++;
                }
                
                // Must have at least one digit in exponent
                if (index >= sourceCode.length() || !Character.isDigit(sourceCode.charAt(index))) {
                    index = startIndex;
                    column = startColumn;
                    return null;
                }
                
                while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
                    index++;
                    column++;
                }
            }
            
            String lexeme = sourceCode.substring(startIndex, index);
            return new Token(TokenType.FLOAT_LITERAL, lexeme, line, startColumn);
        }

        // State: DIGIT (no dot) → INTEGER
        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.INTEGER_LITERAL, lexeme, line, startColumn);
    }

    // DFA for identifiers
    // Rules: Start with uppercase [A-Z], followed by lowercase, digits, underscores. Max 31 chars.
    private Token matchIdentifier() {
        int startColumn = column;
        int startIndex = index;
        State state = State.START;

        // State: START → LETTER (must be UPPERCASE)
        if (Character.isUpperCase(sourceCode.charAt(index))) {
            state = State.LETTER;
            index++;
            column++;
            int identifierLength = 1;
            
            // State: LETTER → IDENTIFIER (consume lowercase, digits, underscores)
            while (index < sourceCode.length() && identifierLength < 31 && 
                   (Character.isLowerCase(sourceCode.charAt(index)) || 
                    Character.isDigit(sourceCode.charAt(index)) || 
                    sourceCode.charAt(index) == '_')) {
                state = State.IDENTIFIER;
                index++;
                column++;
                identifierLength++;
            }
            
            String lexeme = sourceCode.substring(startIndex, index);
            return new Token(TokenType.IDENTIFIER, lexeme, line, startColumn);
        }
        return null;
    }

    // DFA for multi-character operators
    // Arithmetic: **, +, -, *, /, %
    // Relational: ==, !=, <=, >=, <, >
    // Logical: &&, ||, !
    // Assignment: +=, -=, *=, /=, =
    // Inc/Dec: ++, --
    private Token matchMultiCharOperator() {
        int startColumn = column;
        State state = State.START;
        
        if (index + 1 < sourceCode.length()) {
            String twoChar = sourceCode.substring(index, index + 2);
            
            // State: START → multi-char operators
            if (twoChar.equals("**") || twoChar.equals("==") || twoChar.equals("!=") || 
                twoChar.equals("<=") || twoChar.equals(">=") || twoChar.equals("&&") || 
                twoChar.equals("||") || twoChar.equals("++") || twoChar.equals("--") || 
                twoChar.equals("+=") || twoChar.equals("-=") || twoChar.equals("*=") || 
                twoChar.equals("/=")) {
                state = State.OPERATOR;
                index += 2;
                column += 2;
                return new Token(TokenType.OPERATOR, twoChar, line, startColumn);
            }
        }
        return null;
    }

    // DFA for single-character operators
    // Arithmetic: +, -, *, /, %
    // Relational: <, >
    // Logical: !
    // Assignment: =
    private Token matchSingleCharOperator() {
        int startColumn = column;
        char ch = sourceCode.charAt(index);
        State state = State.START;
        
        // State: START → single-char operators
        if ("+-*/%<>=!".indexOf(ch) != -1) {
            state = State.OPERATOR;
            index++;
            column++;
            return new Token(TokenType.OPERATOR, String.valueOf(ch), line, startColumn);
        }
        return null;
    }

    // DFA for punctuators
    // Rules: [(){}[\],;:] - parentheses, braces, brackets, comma, semicolon, colon
    private Token matchPunctuator() {
        int startColumn = column;
        char ch = sourceCode.charAt(index);
        State state = State.START;
        
        // State: START → validate punctuator character
        if ("(){}[],:;".indexOf(ch) != -1) {
            state = State.PUNCTUATOR;
            index++;
            column++;
            return new Token(TokenType.PUNCTUATOR, String.valueOf(ch), line, startColumn);
        }
        return null;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public static void main(String[] args) {
        String code = "## This is a comment\ntrue false Count + 3.14 42 ++ ; Count MyVar";
        ManualScanner scanner = new ManualScanner(code);
        List<Token> tokens = scanner.scan();
        
        System.out.println("TOKENS:");
        for (Token t : tokens) {
            System.out.println(t);
        }
        
        scanner.getSymbolTable().display();
        scanner.displayStatistics();
    }
}

