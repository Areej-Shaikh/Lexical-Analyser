import java.io.*;
import java.util.*;

public class ManualScanner {
    private final String sourceCode;
    private int index;
    private int line;
    private int column;
    private SymbolTable symbolTable;
    private ErrorHandler errorHandler;
//DFA states for different token types
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

            // Skip whitespace and track line numbers
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

            // Try matching tokens using longest match principle
            if ((token = matchMultiLineComment()) != null) {
                tokens.add(token);
            } else if ((token = matchSingleLineComment()) != null) {
                tokens.add(token);
            } else if ((token = matchMultiCharOperator()) != null) {
                tokens.add(token);
            } else if ((token = matchBooleanLiteral()) != null) {
                tokens.add(token);
            } else if ((token = matchNumericLiteral()) != null) {
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
                // Report invalid character and skip it
                errorHandler.report(ErrorHandler.ErrorType.INVALID_CHARACTER, line, column, 
                                   String.valueOf(currentChar), 
                                   "Unrecognized character '" + currentChar + "'");
                index++;
                column++;
            }
        }

        return tokens;
    }

  // DFA: Match single-line comments starting with ##
    private Token matchSingleLineComment() {
        int startColumn = column;
        int startIndex = index;
        CommentState state = CommentState.START;

        if (index >= sourceCode.length() || sourceCode.charAt(index) != '#') {
            return null;
        }
        state = CommentState.HASH1;
        index++;
        column++;

        if (index >= sourceCode.length() || sourceCode.charAt(index) != '#') {
            index = startIndex;
            column = startColumn;
            return null;
        }
        state = CommentState.IN_COMMENT;
        index++;
        column++;

        while (index < sourceCode.length() && sourceCode.charAt(index) != '\n') {
            index++;
            column++;
        }

        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.SINGLE_LINE_COMMENT, lexeme, line, startColumn);
    }

    // DFA: Match nested multi-line comments using #* *#
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
        
        // Track nesting depth for nested comments
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

    // DFA: Match string literals with escape sequences
    private Token matchStringLiteral() {
        int startColumn = column;
        int startIndex = index;
        int startLine = line;

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
                    state = StringState.ESCAPE;
                    index++;
                    column++;
                } else if (ch == '"') {
                    state = StringState.END;
                    index++;
                    column++;
                } else if (ch == '\n') {
                    line++;
                    column = 1;
                    index++;
                } else {
                    index++;
                    column++;
                }
            } else if (state == StringState.ESCAPE) {
                if ("\"\\ntr".indexOf(ch) != -1) {
                    index++;
                    column++;
                    state = StringState.IN_STRING;
                } else if (ch == 'u') {
               
                    index++;
                    column++;
                   
                    if (index + 4 > sourceCode.length()) {
                        String lexeme = sourceCode.substring(startIndex, Math.min(index + 4, sourceCode.length()));
                        errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, startLine, startColumn, 
                                            lexeme, "Incomplete Unicode escape sequence — expected \\uXXXX with 4 hex digits");
                        return null;
                    }
                    
                    for (int i = 0; i < 4; i++) {
                        char hexChar = sourceCode.charAt(index);
                        if (!isHexDigit(hexChar)) {
                            String lexeme = sourceCode.substring(startIndex, Math.min(index + 1, sourceCode.length()));
                            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, startLine, startColumn, 
                                                lexeme, "Invalid Unicode escape sequence — expected hex digit but found '" + hexChar + "'");
                            return null;
                        }
                        index++;
                        column++;
                    }
                    
                    state = StringState.IN_STRING;
                } else {
                    String lexeme = sourceCode.substring(startIndex, Math.min(index + 1, sourceCode.length()));
                    errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, startLine, startColumn, 
                                        lexeme, "Invalid escape sequence '\\" + ch + "' in string literal");
                    return null;
                }
            }
        }

        if (state != StringState.END) {
            String lexeme = sourceCode.substring(startIndex, index);
            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, startLine, startColumn, 
                                lexeme, "Unterminated string literal at end of file");
            return null;
        }

        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.STRING_LITERAL, lexeme, startLine, startColumn);
    }

    // Check if character is valid hex
    private boolean isHexDigit(char ch) {
        return (ch >= '0' && ch <= '9') || 
               (ch >= 'a' && ch <= 'f') || 
               (ch >= 'A' && ch <= 'F');
    }

    // DFA: Match character literals with escape sequences
    private Token matchCharacterLiteral() {
        int startColumn = column;
        int startIndex = index;
        CharState state = CharState.START;

        if (index >= sourceCode.length() || sourceCode.charAt(index) != '\'') {
            return null;
        }

        state = CharState.IN_CHAR;
        index++;
        column++;

        if (index >= sourceCode.length()) {
            errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                    "'", "Unterminated character literal");
            return null;
        }

        char ch = sourceCode.charAt(index);

        if (ch == '\\') {
            state = CharState.ESCAPE;
            index++;
            column++;

            if (index >= sourceCode.length()) {
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                        sourceCode.substring(startIndex, index),
                        "Unterminated escape in character literal");
                return null;
            }

            char esc = sourceCode.charAt(index);
            
            if (esc == 'u') {
                // Unicode escape sequence requires four hex digits
                index++;
                column++;
                
                if (index + 4 > sourceCode.length()) {
                    String lexeme = sourceCode.substring(startIndex, Math.min(index + 4, sourceCode.length()));
                    errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn, 
                                        lexeme, "Incomplete Unicode escape sequence in character literal — expected \\uXXXX with 4 hex digits");
                    return null;
                }
                
                for (int i = 0; i < 4; i++) {
                    char hexChar = sourceCode.charAt(index);
                    if (!isHexDigit(hexChar)) {
                        String lexeme = sourceCode.substring(startIndex, Math.min(index + 1, sourceCode.length()));
                        errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn, 
                                            lexeme, "Invalid Unicode escape sequence in character literal — expected hex digit but found '" + hexChar + "'");
                        return null;
                    }
                    index++;
                    column++;
                }
            } else if ("'\\ntr".indexOf(esc) == -1) {
                String lexeme = sourceCode.substring(startIndex, index + 1);
                errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                        lexeme, "Invalid escape sequence \\" + esc);
                return null;
            } else {
                index++;
                column++;
            }
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

        state = CharState.END;
        index++;
        column++;

        String lexeme = sourceCode.substring(startIndex, index);
        return new Token(TokenType.CHAR_LITERAL, lexeme, line, startColumn);
    }

    // DFA: Match boolean literals true and false
    private Token matchBooleanLiteral() {
        int startColumn = column;
        int startIndex = index;
        BooleanState state = BooleanState.START;

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

    // DFA: Match identifiers starting with uppercase letter
    private Token matchIdentifier() {
        int startColumn = column;
        int startIndex = index;
        IdentifierState state = IdentifierState.START;

        if (index >= sourceCode.length()) return null;

        while (index < sourceCode.length()) {
            char ch = sourceCode.charAt(index);

            switch (state) {
                case START -> {
                    if (ch >= 'A' && ch <= 'Z') {
                        state = IdentifierState.LETTER;
                        index++;
                        column++;
                    } else {
                        return null;
                    }
                }
                case LETTER -> {
                    if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                        index++;
                        column++;
                    } else {
                        String lexeme = sourceCode.substring(startIndex, index);
                        return new Token(TokenType.IDENTIFIER, lexeme, line, startColumn);
                    }
                }
                default -> throw new IllegalStateException("Unexpected IdentifierState: " + state);
            }
        }

        if (state == IdentifierState.LETTER) {
            String lexeme = sourceCode.substring(startIndex, index);
            return new Token(TokenType.IDENTIFIER, lexeme, line, startColumn);
        }

        return null;
    }

    // DFA: Match integer and float literals with sign
    private Token matchNumericLiteral() {
        int startColumn = column;
        int startIndex = index;
        NumericState state = NumericState.START;

        if (index < sourceCode.length() && (sourceCode.charAt(index) == '+' || sourceCode.charAt(index) == '-')) {
            state = NumericState.SIGN;
            index++;
            column++;
        }

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

        if (index < sourceCode.length() && sourceCode.charAt(index) == '.') {
            int dotIndex = index;
            int dotColumn = column;
            state = NumericState.DOT;
            index++;
            column++;

            if (index >= sourceCode.length() || !Character.isDigit(sourceCode.charAt(index))) {
                if (index >= sourceCode.length() || 
                    (!Character.isLetterOrDigit(sourceCode.charAt(index)) && sourceCode.charAt(index) != '.')) {
                    String malformedLexeme = sourceCode.substring(startIndex, index);
                    errorHandler.report(ErrorHandler.ErrorType.MALFORMED_LITERAL, line, startColumn,
                                       malformedLexeme, "Number '" + malformedLexeme + 
                                       "' ends with decimal point — must have digits after '.'");
                    return null;
                }
                
                index = dotIndex;
                column = dotColumn;
                String lexeme = sourceCode.substring(startIndex, index);
                
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
            
            while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
                decimalCount++;
                index++;
                column++;
            }

            if (decimalCount > 6) {
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

            if (index < sourceCode.length() && sourceCode.charAt(index) == '.') {
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

            if (index < sourceCode.length() && (sourceCode.charAt(index) == 'e' || sourceCode.charAt(index) == 'E')) {
                state = NumericState.EXPONENT;
                index++;
                column++;

                if (index < sourceCode.length() && (sourceCode.charAt(index) == '+' || sourceCode.charAt(index) == '-')) {
                    state = NumericState.EXP_SIGN;
                    index++;
                    column++;
                }

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

        String lexeme = sourceCode.substring(startIndex, index);
        
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

    //Detect invalid characters after number literals
    private boolean isInvalidNumberSuffix(char ch) {
        if (ch == '$' || ch == '@' || ch == '#') {
            return true;
        }
        
        if (ch >= 'a' && ch <= 'z' && ch != 'e') {
            return true;
        }
        
        if (ch >= 'A' && ch <= 'Z' && ch != 'E') {
            return true;
        }
        
        return false;
    }

    // DFA: Match muli operators
    private Token matchMultiCharOperator() {
        if (index >= sourceCode.length()) {
            return null;
        }

        int startColumn = column;
        int startIndex = index;
        OperatorState state = OperatorState.START;
        char firstChar = sourceCode.charAt(index);

        if ("*=!<>&|+-/".indexOf(firstChar) == -1) {
            return null;
        }

        state = OperatorState.SINGLE;
        index++;
        column++;

        if (index >= sourceCode.length()) {
            index = startIndex;
            column = startColumn;
            return null;
        }

        char secondChar = sourceCode.charAt(index);

        boolean isValidMultiChar = false;
        
        switch (firstChar) {
            case '*':
                if (secondChar == '*' || secondChar == '=') isValidMultiChar = true;
                break;
            case '=':
                if (secondChar == '=') isValidMultiChar = true;
                break;
            case '!':
                if (secondChar == '=') isValidMultiChar = true;
                break;
            case '<':
                if (secondChar == '=') isValidMultiChar = true;
                break;
            case '>':
                if (secondChar == '=') isValidMultiChar = true;
                break;
            case '&':
                if (secondChar == '&') isValidMultiChar = true;
                break;
            case '|':
                if (secondChar == '|') isValidMultiChar = true;
                break;
            case '+':
                if (secondChar == '+' || secondChar == '=') isValidMultiChar = true;
                break;
            case '-':
                if (secondChar == '-' || secondChar == '=') isValidMultiChar = true;
                break;
            case '/':
                if (secondChar == '=') isValidMultiChar = true;
                break;
        }

        if (isValidMultiChar) {
            state = OperatorState.MULTI;
            String twoChar = "" + firstChar + secondChar;
            index++;
            column++;
            return new Token(TokenType.OPERATOR, twoChar, line, startColumn);
        }

        index = startIndex;
        column = startColumn;
        return null;
    }

    // DFA: Match single-character operators like + -
    private Token matchSingleCharOperator() {
        if (index >= sourceCode.length()) {
            return null;
        }
        
        int startColumn = column;
        OperatorState state = OperatorState.START;
        char ch = sourceCode.charAt(index);

        if ("+-*/%<>=!".indexOf(ch) == -1) {
            return null;
        }

        state = OperatorState.SINGLE;
        index++;
        column++;
        return new Token(TokenType.OPERATOR, String.valueOf(ch), line, startColumn);
    }

    // DFA: Match punctuators like semicolons and brackets
    private Token matchPunctuator() {
        if (index >= sourceCode.length()) {
            return null;
        }
        
        int startColumn = column;
        PunctuatorState state = PunctuatorState.START;
        char ch = sourceCode.charAt(index);

        if (";,(){}[]:.".indexOf(ch) == -1) {
            return null;
        }

        state = PunctuatorState.PUNCTUATOR;
        index++;
        column++;
        return new Token(TokenType.PUNCTUATOR, String.valueOf(ch), line, startColumn);
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
                System.out.println("Processing: " + fileName);
                
                writer.println("\n" + "=".repeat(90));
                writer.println("READING FILE: " + fileName);
                writer.println("=".repeat(90));

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

                try {
                    System.out.println("Scanning: " + fileName);
                    ManualScanner scanner = new ManualScanner(code.toString());
                    List<Token> tokens = scanner.scan();
                    System.out.println("Scanned " + tokens.size() + " tokens from " + fileName);

                    // Calculate and display token statistics
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

                    writer.println("SCANNED TOKENS:");
                    if (tokens.isEmpty()) {
                        writer.println("[INFO] No valid tokens found in " + fileName);
                    }
                    for (Token t : tokens) {
                        writer.println(t);
                    }

                    writer.println("\nSYMBOL TABLE:");
                    scanner.getSymbolTable().write(writer);

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
                writer.flush();
            }

            writer.println("\nAll files processed.");
        } catch (IOException e) {
            System.err.println("Error writing TestResults.txt: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
