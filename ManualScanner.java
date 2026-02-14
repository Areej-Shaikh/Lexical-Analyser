import java.util.*;  

public class ManualScanner {
    private final String sourceCode;
    private int index;
    private int line;
    private int column;
    private SymbolTable symbolTable;

    // DFA States for different token types
    private enum CommentState { START, HASH1, IN_COMMENT }
    private enum BooleanState { START, T, TR, TRU, R, U, E, F, FA, FAL, FALS }
    private enum IdentifierState { START, LETTER, IN_IDENTIFIER }
    private enum NumericState { START, DIGIT, DOT, DECIMAL, EXPONENT, EXP_SIGN, EXP_DIGIT }
    private enum OperatorState { START, SINGLE, MULTI }
    private enum PunctuatorState { START, PUNCTUATOR }

    public ManualScanner(String sourceCode) {
        this.sourceCode = sourceCode;
        this.index = 0;
        this.line = 1;
        this.column = 1;
        this.symbolTable = new SymbolTable();
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
            // Multi-char operators checked before single-char (longest match)
            // Floats handled within numeric matching DFA
            if ((token = matchSingleLineComment()) != null) {
                tokens.add(token);
            } else if ((token = matchBooleanLiteral()) != null) {
                tokens.add(token);
            } else if ((token = matchNumericLiteral()) != null) {
                tokens.add(token);
            } else if ((token = matchIdentifier()) != null) {
                symbolTable.insert(token.getLexeme(), "IDENTIFIER", line);
                tokens.add(token);
            } else if ((token = matchMultiCharOperator()) != null) {
                tokens.add(token);
            } else if ((token = matchSingleCharOperator()) != null) {
                tokens.add(token);
            } else if ((token = matchPunctuator()) != null) {
                tokens.add(token);
            } else {
                // No match → error
                System.err.println("Invalid Token at line " + line + ", column " + column + ": '" + currentChar + "'");
                index++;
                column++;
            }
        }

        return tokens;
    }

    // ----------------------
    // DFA-based Match functions
    // ----------------------

    // DFA for single-line comments: ## followed by any chars until newline
    private Token matchSingleLineComment() {
        int startColumn = column;
        int startIndex = index;
        CommentState state = CommentState.START;

        // State: START - check for '#'
        if (index >= sourceCode.length() || sourceCode.charAt(index) != '#') {
            return null;
        }
        state = CommentState.HASH1;
        index++;
        column++;

        // State: HASH1 - check for second '#'
        if (index >= sourceCode.length() || sourceCode.charAt(index) != '#') {
            index = startIndex;
            column = startColumn;
            return null;
        }
        state = CommentState.IN_COMMENT;
        index++;
        column++;

        // State: IN_COMMENT - consume until newline (longest match)
        while (index < sourceCode.length() && sourceCode.charAt(index) != '\n') {
            index++;
            column++;
        }

        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.SINGLE_LINE_COMMENT, lexeme, line, startColumn);
    }

    // DFA for boolean literals: "true" or "false" with word boundary check
    private Token matchBooleanLiteral() {
        int startColumn = column;
        int startIndex = index;
        BooleanState state = BooleanState.START;

        // Try to match "true"
        if (index < sourceCode.length() && sourceCode.charAt(index) == 't') {
            state = BooleanState.T;
            int tempIndex = index;
            int tempColumn = column;
            index++;
            column++;

            if (index < sourceCode.length() && sourceCode.charAt(index) == 'r') {
                state = BooleanState.TR;
                index++;
                column++;

                if (index < sourceCode.length() && sourceCode.charAt(index) == 'u') {
                    state = BooleanState.TRU;
                    index++;
                    column++;

                    if (index < sourceCode.length() && sourceCode.charAt(index) == 'e') {
                        state = BooleanState.R; // final state for "true"
                        index++;
                        column++;

                        // Check word boundary: next char must not be alphanumeric or underscore
                        if (index >= sourceCode.length() || 
                            (!Character.isLetterOrDigit(sourceCode.charAt(index)) && sourceCode.charAt(index) != '_')) {
                            return new Token(TokenType.BOOLEAN_LITERAL, "true", line, startColumn);
                        }
                    }
                }
            }

            // Reset if "true" was incomplete
            index = tempIndex;
            column = tempColumn;
        }

        // Try to match "false"
        if (index < sourceCode.length() && sourceCode.charAt(index) == 'f') {
            state = BooleanState.F;
            int tempIndex = index;
            int tempColumn = column;
            index++;
            column++;

            if (index < sourceCode.length() && sourceCode.charAt(index) == 'a') {
                state = BooleanState.FA;
                index++;
                column++;

                if (index < sourceCode.length() && sourceCode.charAt(index) == 'l') {
                    state = BooleanState.FAL;
                    index++;
                    column++;

                    if (index < sourceCode.length() && sourceCode.charAt(index) == 's') {
                        state = BooleanState.FALS;
                        index++;
                        column++;

                        if (index < sourceCode.length() && sourceCode.charAt(index) == 'e') {
                            state = BooleanState.E; // final state for "false"
                            index++;
                            column++;

                            // Check word boundary
                            if (index >= sourceCode.length() || 
                                (!Character.isLetterOrDigit(sourceCode.charAt(index)) && sourceCode.charAt(index) != '_')) {
                                return new Token(TokenType.BOOLEAN_LITERAL, "false", line, startColumn);
                            }
                        }
                    }
                }
            }

            // Reset if "false" was incomplete
            index = tempIndex;
            column = tempColumn;
        }

        index = startIndex;
        column = startColumn;
        return null;
    }

    // DFA for identifiers: letter followed by letters, digits, underscores (max 31 chars)
    private Token matchIdentifier() {
        int startColumn = column;
        int startIndex = index;
        IdentifierState state = IdentifierState.START;

        // State: START - must begin with a letter
        if (index >= sourceCode.length() || !Character.isLetter(sourceCode.charAt(index))) {
            return null;
        }
        state = IdentifierState.LETTER;
        index++;
        column++;

        // State: IN_IDENTIFIER - consume letters, digits, underscores (longest match, max 31)
        int identifierLength = 1;
        while (index < sourceCode.length() && identifierLength < 31 && 
               (Character.isLetterOrDigit(sourceCode.charAt(index)) || sourceCode.charAt(index) == '_')) {
            state = IdentifierState.IN_IDENTIFIER;
            index++;
            column++;
            identifierLength++;
        }

        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.IDENTIFIER, lexeme, line, startColumn);
    }

    // DFA for numeric literals: integer or float with proper decimal/exponent handling
    private Token matchNumericLiteral() {
        int startColumn = column;
        int startIndex = index;
        NumericState state = NumericState.START;

        // State: START - first digit required
        if (index >= sourceCode.length() || !Character.isDigit(sourceCode.charAt(index))) {
            return null;
        }
        state = NumericState.DIGIT;
        index++;
        column++;

        // State: DIGIT - consume more digits (longest match)
        while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
            index++;
            column++;
        }

        // Check for decimal point (float)
        if (index < sourceCode.length() && sourceCode.charAt(index) == '.') {
            int dotIndex = index;
            int dotColumn = column;
            state = NumericState.DOT;
            index++;
            column++;

            // State: DECIMAL - must have at least one digit after dot
            if (index >= sourceCode.length() || !Character.isDigit(sourceCode.charAt(index))) {
                // No decimal digits, backtrack
                index = dotIndex;
                column = dotColumn;
                String lexeme = sourceCode.substring(startIndex, index);
                return new Token(TokenType.INTEGER_LITERAL, lexeme, line, startColumn);
            }

            state = NumericState.DECIMAL;
            int decimalCount = 0;
            while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index)) && decimalCount < 6) {
                index++;
                column++;
                decimalCount++;
            }

            // Check for more than 6 decimal places
            if (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
                // Invalid: more than 6 decimal digits
                index = startIndex;
                column = startColumn;
                return null;
            }

            // Check for exponent (e/E)
            if (index < sourceCode.length() && (sourceCode.charAt(index) == 'e' || sourceCode.charAt(index) == 'E')) {
                state = NumericState.EXPONENT;
                index++;
                column++;

                // Optional sign after exponent
                if (index < sourceCode.length() && (sourceCode.charAt(index) == '+' || sourceCode.charAt(index) == '-')) {
                    state = NumericState.EXP_SIGN;
                    index++;
                    column++;
                }

                // Must have at least one digit in exponent
                if (index >= sourceCode.length() || !Character.isDigit(sourceCode.charAt(index))) {
                    index = startIndex;
                    column = startColumn;
                    return null;
                }

                state = NumericState.EXP_DIGIT;
                while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
                    index++;
                    column++;
                }
            }

            String lexeme = sourceCode.substring(startIndex, index);
            return new Token(TokenType.FLOAT_LITERAL, lexeme, line, startColumn);
        }

        // State: DIGIT (no decimal point) - return as integer
        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.INTEGER_LITERAL, lexeme, line, startColumn);
    }

    // DFA for multi-character operators: check two-character combinations first (longest match)
    private Token matchMultiCharOperator() {
        int startColumn = column;
        OperatorState state = OperatorState.START;

        if (index + 1 < sourceCode.length()) {
            String twoChar = sourceCode.substring(index, index + 2);
            state = OperatorState.MULTI;

            // Multi-character operators (longest match principle)
            if (twoChar.equals("**") || twoChar.equals("==") || twoChar.equals("!=") || 
                twoChar.equals("<=") || twoChar.equals(">=") || twoChar.equals("&&") || 
                twoChar.equals("||") || twoChar.equals("++") || twoChar.equals("--") || 
                twoChar.equals("+=") || twoChar.equals("-=") || twoChar.equals("*=") || 
                twoChar.equals("/=")) {
                index += 2;
                column += 2;
                return new Token(TokenType.OPERATOR, twoChar, line, startColumn);
            }
        }
        return null;
    }

    // DFA for single-character operators: only if not part of multi-char operator
    private Token matchSingleCharOperator() {
        int startColumn = column;
        char ch = sourceCode.charAt(index);
        OperatorState state = OperatorState.START;

        // State: SINGLE - single-character operators
        if ("+-*/%<>=!".indexOf(ch) != -1) {
            state = OperatorState.SINGLE;
            index++;
            column++;
            return new Token(TokenType.OPERATOR, String.valueOf(ch), line, startColumn);
        }
        return null;
    }

    // DFA for punctuators: single-character punctuation marks
    private Token matchPunctuator() {
        int startColumn = column;
        char ch = sourceCode.charAt(index);
        PunctuatorState state = PunctuatorState.START;

        // State: PUNCTUATOR - validate punctuation character
        if (";,(){}[]:.".indexOf(ch) != -1) {
            state = PunctuatorState.PUNCTUATOR;
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
        for (Token t : tokens) {
            System.out.println(t);
        }
        scanner.getSymbolTable().display();
    }
}

