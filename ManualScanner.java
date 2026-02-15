import java.io.*;
import java.util.*;

public class ManualScanner {
    private final String sourceCode;
    private int index;
    private int line;
    private int column;
    private SymbolTable symbolTable;
    private ErrorHandler errorHandler;

    // DFA States for different token types
    private enum CommentState { START, HASH1, IN_COMMENT }
    private enum MultiLineCommentState { START, HASH, STAR, IN_COMMENT, STAR_FOUND }
    private enum BooleanState { START, T, TR, TRU, R, U, E, F, FA, FAL, FALS }
    private enum IdentifierState { START, LETTER, IN_IDENTIFIER }
    private enum NumericState { START, SIGN, DIGIT, DOT, DECIMAL, EXPONENT, EXP_SIGN, EXP_DIGIT }
    private enum OperatorState { START, SINGLE, MULTI }
    private enum PunctuatorState { START, PUNCTUATOR }
    private enum StringState { START, IN_STRING, ESCAPE, END }
    private enum CharState { START, IN_CHAR, ESCAPE, END }

    public ManualScanner(String sourceCode) {
        this.sourceCode = sourceCode;
        this.index = 0;
        this.line = 1;
        this.column = 1;
        this.symbolTable = new SymbolTable();
        this.errorHandler = new ErrorHandler();
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
            if ((token = matchMultiLineComment()) != null) {
                tokens.add(token);
            } else if ((token = matchSingleLineComment()) != null) {
                tokens.add(token);
            } else if ((token = matchMultiCharOperator()) != null) {
                tokens.add(token);
            } else if ((token = matchBooleanLiteral()) != null) {
                tokens.add(token);
            } else if ((token = matchNumericLiteral()) != null) {
                // Numeric literal already validated, only add if not null
                if (token != null) {
                    tokens.add(token);
                }
            } else if ((token = matchStringLiteral()) != null) {
                tokens.add(token);
            } else if ((token = matchCharacterLiteral()) != null) {
                tokens.add(token);
            } else if ((token = matchIdentifier()) != null) {
                String lexeme = token.getLexeme();
                if (errorHandler.isInvalidIdentifier(lexeme)) {
                    errorHandler.reportFromToken(token);
                } else {
                    symbolTable.insert(lexeme, "IDENTIFIER", line);
                    tokens.add(token);
                }
            } else if ((token = matchSingleCharOperator()) != null) {
                tokens.add(token);
            } else if ((token = matchPunctuator()) != null) {
                tokens.add(token);
            } else {
                // Invalid character - report error and skip
                errorHandler.report(ErrorHandler.ErrorType.INVALID_CHARACTER, line, column, 
                                   String.valueOf(currentChar), 
                                   "Unrecognized character '" + currentChar + "'");
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

    // DFA for multi-line comments with nesting support
    private Token matchMultiLineComment() {
        int startColumn = column;
        int startIndex = index;
        int startLine = line;
        MultiLineCommentState state = MultiLineCommentState.START;

        if (index >= sourceCode.length() || sourceCode.charAt(index) != '#') {
            return null;
        }
        state = MultiLineCommentState.HASH;
        index++;
        column++;

        if (index >= sourceCode.length() || sourceCode.charAt(index) != '*') {
            index = startIndex;
            column = startColumn;
            return null;
        }
        state = MultiLineCommentState.STAR;
        index++;
        column++;

        int nestingDepth = 1;
        state = MultiLineCommentState.IN_COMMENT;
        
        while (index < sourceCode.length() && nestingDepth > 0) {
            char currentChar = sourceCode.charAt(index);

            if (currentChar == '\n') {
                line++;
                column = 1;
                index++;
            } else if (currentChar == '#' && index + 1 < sourceCode.length() && sourceCode.charAt(index + 1) == '*') {
                nestingDepth++;
                index += 2;
                column += 2;
            } else if (currentChar == '*') {
                state = MultiLineCommentState.STAR_FOUND;
                int tempIndex = index;
                int tempColumn = column;

                while (tempIndex < sourceCode.length() && sourceCode.charAt(tempIndex) == '*') {
                    tempIndex++;
                    tempColumn++;
                }

                if (tempIndex < sourceCode.length() && sourceCode.charAt(tempIndex) == '#') {
                    nestingDepth--;
                    
                    if (nestingDepth == 0) {
                        index = tempIndex + 1;
                        column = tempColumn + 1;
                        String lexeme = sourceCode.substring(startIndex, index);
                        return new Token(TokenType.MULTI_LINE_COMMENT, lexeme, startLine, startColumn);
                    } else {
                        index = tempIndex + 1;
                        column = tempColumn + 1;
                    }
                } else if (tempIndex < sourceCode.length()) {
                    index = tempIndex + 1;
                    column = tempColumn + 1;
                } else {
                    index = tempIndex;
                    column = tempColumn;
                    String lexeme = sourceCode.substring(startIndex, index);
                    errorHandler.reportUnclosedComment(startLine, startColumn, lexeme);
                    return new Token(TokenType.MULTI_LINE_COMMENT, lexeme, startLine, startColumn);
                }
            } else {
                index++;
                column++;
            }
        }

        String lexeme = sourceCode.substring(startIndex, index);
        errorHandler.reportUnclosedComment(startLine, startColumn, lexeme);
        return new Token(TokenType.MULTI_LINE_COMMENT, lexeme, startLine, startColumn);
    }

  private Token matchStringLiteral() {
    int startColumn = column;
    int startIndex = index;
    int startLine = line;  // Track starting line for error reporting

    if (index >= sourceCode.length() || sourceCode.charAt(index) != '"') {
        return null;
    }

    StringState state = StringState.IN_STRING;
    index++;
    column++;

    while (index < sourceCode.length() && state != StringState.END) {
        char ch = sourceCode.charAt(index);

        if (state == StringState.IN_STRING) {
            if (ch == '\\') {
                // Start of escape sequence
                state = StringState.ESCAPE;
                index++;
                column++;
            } else if (ch == '"') {
                // Found closing quote - string is complete
                state = StringState.END;
                index++;
                column++;
            } else if (ch == '\n') {
                // STOP at newline - unterminated string
                String lexeme = sourceCode.substring(startIndex, index);
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, startLine, startColumn, 
                                    lexeme, "Unterminated string literal — missing closing '\"' before newline");
                // Don't consume the newline, let main loop handle it
                return null;
            } else {
                // Regular character in string
                index++;
                column++;
            }
        } else if (state == StringState.ESCAPE) {
            // Validate escape sequence
            if ("\"\\ntr".indexOf(ch) != -1) {
                // Valid escape: \", \\, \n, \t, \r
                index++;
                column++;
                state = StringState.IN_STRING;
            } else {
                // Invalid escape sequence
                String lexeme = sourceCode.substring(startIndex, Math.min(index + 1, sourceCode.length()));
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, startLine, startColumn, 
                                    lexeme, "Invalid escape sequence '\\" + ch + "' in string literal");
                return null;
            }
        }
    }

    // Check if we reached EOF without closing the string
    if (state != StringState.END) {
        String lexeme = sourceCode.substring(startIndex, index);
        errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, startLine, startColumn, 
                            lexeme, "Unterminated string literal at end of file");
        return null;
    }

    // Successfully matched a complete string
    String lexeme = sourceCode.substring(startIndex, index);
    return new Token(TokenType.STRING_LITERAL, lexeme, startLine, startColumn);
}

    private Token matchCharacterLiteral() {
        int startColumn = column;
        int startIndex = index;

        if (index >= sourceCode.length() || sourceCode.charAt(index) != '\'') {
            return null;
        }

        index++;
        column++;

        if (index >= sourceCode.length()) {
            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                    "'", "Unterminated character literal");
            return null;
        }

        char ch = sourceCode.charAt(index);

        if (ch == '\\') {
            index++;
            column++;

            if (index >= sourceCode.length()) {
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                        sourceCode.substring(startIndex, index),
                        "Unterminated escape in character literal");
                return null;
            }

            char esc = sourceCode.charAt(index);
            if ("'\\ntr".indexOf(esc) == -1) {
                String lexeme = sourceCode.substring(startIndex, index + 1);
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                        lexeme, "Invalid escape sequence \\" + esc);
                return null;
            }

            index++;
            column++;
        } else if (ch == '\'' || ch == '\n') {
            String lexeme = sourceCode.substring(startIndex, index + 1);
            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                    lexeme, "Invalid character literal");
            return null;
        } else {
            index++;
            column++;
        }

        if (index >= sourceCode.length() || sourceCode.charAt(index) != '\'') {
            String lexeme = sourceCode.substring(startIndex, index);
            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                    lexeme, "Missing closing ' in character literal");
            return null;
        }

        index++;
        column++;

        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.CHAR_LITERAL, lexeme, line, startColumn);
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
                        state = BooleanState.R;
                        index++;
                        column++;

                        if (index >= sourceCode.length() || 
                            (!Character.isLetterOrDigit(sourceCode.charAt(index)) && sourceCode.charAt(index) != '_')) {
                            return new Token(TokenType.BOOLEAN_LITERAL, "true", line, startColumn);
                        }
                    }
                }
            }

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
                            state = BooleanState.E;
                            index++;
                            column++;

                            if (index >= sourceCode.length() || 
                                (!Character.isLetterOrDigit(sourceCode.charAt(index)) && sourceCode.charAt(index) != '_')) {
                                return new Token(TokenType.BOOLEAN_LITERAL, "false", line, startColumn);
                            }
                        }
                    }
                }
            }

            index = tempIndex;
            column = tempColumn;
        }

        index = startIndex;
        column = startColumn;
        return null;
    }

    // DFA for identifiers
    private Token matchIdentifier() {
        int startColumn = column;
        int startIndex = index;

        if (index >= sourceCode.length()) return null;

        char firstChar = sourceCode.charAt(index);
        
        if (!Character.isLetter(firstChar)) {
            return null;
        }

        index++;
        column++;
        int length = 1;

        while (index < sourceCode.length() && length < 100) {
            char ch = sourceCode.charAt(index);

            if (Character.isLetter(ch) || (ch >= '0' && ch <= '9') || ch == '_') {
                index++;
                column++;
                length++;
            } else {
                break;
            }
        }

        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.IDENTIFIER, lexeme, line, startColumn);
    }

    // DFA for numeric literals with sign support
   private Token matchNumericLiteral() {
    int startColumn = column;
    int startIndex = index;
    NumericState state = NumericState.START;

    // Optional sign
    if (index < sourceCode.length() && (sourceCode.charAt(index) == '+' || sourceCode.charAt(index) == '-')) {
        state = NumericState.SIGN;
        index++;
        column++;
    }

    // Must have at least one digit
    if (index >= sourceCode.length() || !Character.isDigit(sourceCode.charAt(index))) {
        index = startIndex;
        column = startColumn;
        return null;
    }

    state = NumericState.DIGIT;
    index++;
    column++;

    while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
        index++;
        column++;
    }

    // Check for decimal point
    if (index < sourceCode.length() && sourceCode.charAt(index) == '.') {
        int dotIndex = index;
        int dotColumn = column;
        state = NumericState.DOT;
        index++;
        column++;

        // Check if there's a digit after the decimal point
        if (index >= sourceCode.length() || !Character.isDigit(sourceCode.charAt(index))) {
            // No digit after decimal point
            // Check if this is "42." (invalid) or "42" followed by "." punctuator
            if (index >= sourceCode.length() || 
                (!Character.isLetterOrDigit(sourceCode.charAt(index)) && sourceCode.charAt(index) != '.')) {
                // This is a number ending with "." like "42." - INVALID
                String malformedLexeme = sourceCode.substring(startIndex, index);
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                                   malformedLexeme, "Number '" + malformedLexeme + 
                                   "' ends with decimal point — must have digits after '.'");
                return null;
            }
            
            // Otherwise backtrack (the dot is separate punctuation)
            index = dotIndex;
            column = dotColumn;
            String lexeme = sourceCode.substring(startIndex, index);
            
            // Check for invalid characters attached to integer like "123$"
            if (index < sourceCode.length() && isInvalidNumberSuffix(sourceCode.charAt(index))) {
                char invalidChar = sourceCode.charAt(index);
                index++;
                column++;
                String malformed = sourceCode.substring(startIndex, index);
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                                   malformed, "Invalid character '" + invalidChar + 
                                   "' attached to number literal");
                return null;
            }
            
            return new Token(TokenType.INTEGER_LITERAL, lexeme, line, startColumn);
        }

        state = NumericState.DECIMAL;
        int decimalCount = 0;
        
        // Consume decimal digits
        while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
            decimalCount++;
            index++;
            column++;
        }

        // Check for too many decimal places
        if (decimalCount > 6) {
            // Continue consuming to get the full malformed number
            while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
                index++;
                column++;
            }
            
            String lexeme = sourceCode.substring(startIndex, index);
            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                               lexeme, "Float literal '" + lexeme + "' has " + decimalCount + 
                               " decimal digits — maximum 6 allowed");
            return null;
        }

        // Check for MULTIPLE decimal points (like 12.34.56)
        if (index < sourceCode.length() && sourceCode.charAt(index) == '.') {
            // Found another decimal point - consume the entire malformed number
            while (index < sourceCode.length() && 
                   (Character.isDigit(sourceCode.charAt(index)) || sourceCode.charAt(index) == '.')) {
                index++;
                column++;
            }
            
            String malformedLexeme = sourceCode.substring(startIndex, index);
            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                               malformedLexeme, "Float literal '" + malformedLexeme + 
                               "' contains multiple decimal points");
            return null;
        }

        // Check for exponent
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
                String lexeme = sourceCode.substring(startIndex, index);
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                                   lexeme, "Float literal '" + lexeme + "' has incomplete exponent");
                return null;
            }

            state = NumericState.EXP_DIGIT;
            while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
                index++;
                column++;
            }
        }

        String lexeme = sourceCode.substring(startIndex, index);
        
        // Check for invalid characters attached to float like "3.14$"
        if (index < sourceCode.length() && isInvalidNumberSuffix(sourceCode.charAt(index))) {
            char invalidChar = sourceCode.charAt(index);
            index++;
            column++;
            String malformed = sourceCode.substring(startIndex, index);
            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                               malformed, "Invalid character '" + invalidChar + 
                               "' attached to float literal");
            return null;
        }
        
        return new Token(TokenType.FLOAT_LITERAL, lexeme, line, startColumn);
    }

    // No decimal point - return as integer
    String lexeme = sourceCode.substring(startIndex, index);
    
    // Check for invalid characters attached to integer like "123$"
    if (index < sourceCode.length() && isInvalidNumberSuffix(sourceCode.charAt(index))) {
        char invalidChar = sourceCode.charAt(index);
        index++;
        column++;
        String malformed = sourceCode.substring(startIndex, index);
        errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                           malformed, "Invalid character '" + invalidChar + 
                           "' attached to number literal");
        return null;
    }
    
    return new Token(TokenType.INTEGER_LITERAL, lexeme, line, startColumn);
}

// Helper method to detect invalid characters attached to numbers
private boolean isInvalidNumberSuffix(char ch) {
    // Invalid if it's a letter (except 'e' or 'E' for exponent) or special char like $
    if (ch == '$' || ch == '@' || ch == '#') {
        return true;
    }
    
    // Invalid if lowercase letter (except 'e' for exponent, but that's handled separately)
    if (ch >= 'a' && ch <= 'z' && ch != 'e') {
        return true;
    }
    
    // Invalid if uppercase letter (except 'E' for exponent, but that's handled separately)
    if (ch >= 'A' && ch <= 'Z' && ch != 'E') {
        return true;
    }
    
    return false;
}
    // DFA for multi-character operators
    private Token matchMultiCharOperator() {
        if (index + 1 >= sourceCode.length()) {
            return null;
        }

        int startColumn = column;
        String twoChar = sourceCode.substring(index, index + 2);

        if (twoChar.equals("**") || twoChar.equals("==") || twoChar.equals("!=") || 
            twoChar.equals("<=") || twoChar.equals(">=") || twoChar.equals("&&") || 
            twoChar.equals("||") || twoChar.equals("++") || twoChar.equals("--") || 
            twoChar.equals("+=") || twoChar.equals("-=") || twoChar.equals("*=") || 
            twoChar.equals("/=")) {
            index += 2;
            column += 2;
            return new Token(TokenType.OPERATOR, twoChar, line, startColumn);
        }
        
        return null;
    }

    // DFA for single-character operators
    private Token matchSingleCharOperator() {
        // ADD BOUNDS CHECK
        if (index >= sourceCode.length()) {
            return null;
        }
        
        int startColumn = column;
        char ch = sourceCode.charAt(index);

        if ("+-*/%<>=!".indexOf(ch) != -1) {
            index++;
            column++;
            return new Token(TokenType.OPERATOR, String.valueOf(ch), line, startColumn);
        }
        return null;
    }

    // DFA for punctuators
    private Token matchPunctuator() {
        // ADD BOUNDS CHECK
        if (index >= sourceCode.length()) {
            return null;
        }
        
        int startColumn = column;
        char ch = sourceCode.charAt(index);

        if (";,(){}[]:.".indexOf(ch) != -1) {
            index++;
            column++;
            return new Token(TokenType.PUNCTUATOR, String.valueOf(ch), line, startColumn);
        }
        return null;
    }

    public SymbolTable getSymbolTable() {
        return this.symbolTable;
    }

    public ErrorHandler getErrorHandler() {
        return this.errorHandler;
    }

    public int getLineCount() {
        return line;
    }



  public static void main(String[] args) {
    String[] files = { "test1.lang", "test2.lang", "test3.lang", "test4.lang", "test5.lang" };

    try (PrintWriter writer = new PrintWriter(new FileWriter("TestResults.txt"))) {
        for (String fileName : files) {
            System.out.println("Processing: " + fileName); // Console output for debugging
            
            writer.println("\n" + "=".repeat(90));
            writer.println("READING FILE: " + fileName);
            writer.println("=".repeat(90));

            // Check if file exists
            File file = new File(fileName);
            if (!file.exists()) {
                writer.println("ERROR: File not found - " + fileName);
                System.err.println("ERROR: File not found - " + fileName);
                writer.println("-".repeat(90));
                continue;
            }

            StringBuilder code = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    code.append(line).append("\n");
                }
            } catch (IOException e) {
                writer.println("Error reading file: " + e.getMessage());
                System.err.println("Error reading file " + fileName + ": " + e.getMessage());
                writer.println("-".repeat(90));
                continue;
            }

            // Add try-catch around scanning to catch any runtime errors
            try {
                System.out.println("Scanning: " + fileName);
                ManualScanner scanner = new ManualScanner(code.toString());
                List<Token> tokens = scanner.scan();
                System.out.println("Scanned " + tokens.size() + " tokens from " + fileName);

                // ------------------- STATISTICS -------------------
                int totalTokens = tokens.size();
                int identifiers = 0, keywords = 0, operators = 0, punctuators = 0, booleans = 0;
                int singleLineComments = 0, multiLineComments = 0;

                for (Token t : tokens) {
                    switch (t.getType()) {
                        case IDENTIFIER -> identifiers++;
                        case BOOLEAN_LITERAL -> booleans++;
                        case OPERATOR -> operators++;
                        case PUNCTUATOR -> punctuators++;
                        case SINGLE_LINE_COMMENT -> singleLineComments++;
                        case MULTI_LINE_COMMENT -> multiLineComments++;
                    }
                }

                writer.println("\n========== STATISTICS ==========");
                writer.println("Total tokens: " + totalTokens);
                writer.println("Identifiers: " + identifiers);
                writer.println("Keywords: " + keywords);
                writer.println("Boolean literals: " + booleans);
                writer.println("Operators: " + operators);
                writer.println("Punctuators: " + punctuators);
                writer.println("Single-line comments removed: " + singleLineComments);
                writer.println("Multi-line comments removed: " + multiLineComments);
                writer.println("Lines processed: " + scanner.getLineCount());
                writer.println("================================\n");

                // ------------------- SCANNED TOKENS -------------------
                writer.println("SCANNED TOKENS:");
                if (tokens.isEmpty()) {
                    writer.println("[INFO] No valid tokens found in " + fileName);
                }
                for (Token t : tokens) {
                    writer.println(t);
                }

                // ------------------- SYMBOL TABLE -------------------
                writer.println("\nSYMBOL TABLE:");
                scanner.getSymbolTable().write(writer);

                // ------------------- LEXICAL ERRORS -------------------
                writer.println("\nLEXICAL ERRORS:");
                scanner.getErrorHandler().write(writer);

            } catch (Exception e) {
                writer.println("\nERROR: Exception occurred while scanning " + fileName);
                writer.println("Exception: " + e.getClass().getName());
                writer.println("Message: " + e.getMessage());
                writer.println("Stack trace:");
                e.printStackTrace(writer);
                
                System.err.println("ERROR scanning " + fileName + ": " + e.getMessage());
                e.printStackTrace();
            }

            writer.println("\n" + "-".repeat(90));
            writer.flush(); // Flush after each file
        }

        writer.println("\nAll files processed.");
    } catch (IOException e) {
        System.err.println("Error writing TestResults.txt: " + e.getMessage());
        e.printStackTrace();
    }
}
}




