import java.util.*;

public class ErrorHandler {

    public enum ErrorType {
        INVALID_CHARACTER,
        MALFORMED_LITERAL,
        INVALID_IDENTIFIER,
        UNCLOSED_COMMENT
    }

    public static class LexicalError {
        ErrorType errorType;
        int line;
        int column;
        String lexeme;
        String reason;

        LexicalError(ErrorType errorType, int line, int column, String lexeme, String reason) {
            this.errorType   = errorType;
            this.line        = line;
            this.column      = column;
            this.lexeme      = lexeme;
            this.reason      = reason;
        }

        @Override
        public String toString() {
            return String.format(
                "[LEXICAL ERROR] Type: %-22s | Line: %-4d | Column: %-4d | Lexeme: %-20s | Reason: %s",
                errorType, line, column, "'" + lexeme + "'", reason
            );
        }
    }

    private List<LexicalError> errors = new ArrayList<>();

    // ------------------------------------------------------------------ //
    //  Registration methods — called from Main.java during token loop     //
    // ------------------------------------------------------------------ //

    /** Any ERROR token from the scanner is classified here */
    public void reportFromToken(Token token) {
        String lexeme = token.getLexeme();
        int    line   = token.getLine();
        int    col    = token.getColumn();

        if (isInvalidCharacter(lexeme)) {
            addError(ErrorType.INVALID_CHARACTER, line, col, lexeme,
                "Character '" + lexeme + "' is not part of the language alphabet");

        } else if (isMalformedNumber(lexeme)) {
            addError(ErrorType.MALFORMED_LITERAL, line, col, lexeme,
                "Malformed numeric literal (e.g. multiple decimal points)");

        } else if (isUnterminatedString(lexeme)) {
            addError(ErrorType.MALFORMED_LITERAL, line, col, lexeme,
                "Unterminated string literal — missing closing '\"'");

        } else if (isUnterminatedChar(lexeme)) {
            addError(ErrorType.MALFORMED_LITERAL, line, col, lexeme,
                "Unterminated char literal — missing closing \"'\"");

        } else if (isInvalidIdentifier(lexeme)) {
            addError(ErrorType.INVALID_IDENTIFIER, line, col, lexeme,
                buildIdentifierReason(lexeme));

        } else {
            // Fallback — unknown bad token
            addError(ErrorType.INVALID_CHARACTER, line, col, lexeme,
                "Unrecognised token '" + lexeme + "'");
        }
    }

    /** Called explicitly when scanner detects an unclosed #* comment */
    public void reportUnclosedComment(int line, int col, String lexeme) {
        addError(ErrorType.UNCLOSED_COMMENT, line, col, lexeme,
            "Multi-line comment opened with '#*' but never closed with '*#'");
    }

    /** Generic manual report */
    public void report(ErrorType type, int line, int col, String lexeme, String reason) {
        addError(type, line, col, lexeme, reason);
    }

    // ------------------------------------------------------------------ //
    //  Classification helpers                                              //
    // ------------------------------------------------------------------ //

    private boolean isInvalidCharacter(String s) {
        // Single characters that are simply not in the alphabet
        if (s.length() == 1) {
            char c = s.charAt(0);
            return !Character.isLetterOrDigit(c)
                && "+-*/%=!<>(){}[];:,.'\"#_".indexOf(c) == -1;
        }
        return false;
    }

    private boolean isMalformedNumber(String s) {
        // More than one decimal point
        long dots = s.chars().filter(c -> c == '.').count();
        if (dots > 1) return true;
        // Starts/ends with a decimal point
        if (s.startsWith(".") || s.endsWith(".")) return true;
        // Digits only check fails (contains a letter that isn't 'e'/'E' exponent)
        return false;
    }

    private boolean isUnterminatedString(String s) {
        return s.startsWith("\"") && !s.endsWith("\"");
    }

    private boolean isUnterminatedChar(String s) {
        return s.startsWith("'") && !s.endsWith("'");
    }

    private boolean isInvalidIdentifier(String s) {
        if (s.isEmpty()) return false;
        char first = s.charAt(0);
        // Starts with lowercase or digit
        if (Character.isLowerCase(first) || Character.isDigit(first)) return true;
        // Exceeds 31 characters
        if (s.length() > 31) return true;
        // Contains invalid characters
        for (char c : s.toCharArray()) {
            if (!Character.isLowerCase(c) && !Character.isUpperCase(c)
                    && !Character.isDigit(c) && c != '_') return true;
        }
        return false;
    }

    private String buildIdentifierReason(String s) {
        if (s.isEmpty()) return "Empty identifier";
        char first = s.charAt(0);
        if (Character.isDigit(first))
            return "Identifier '" + s + "' starts with a digit — must start with uppercase A-Z";
        if (Character.isLowerCase(first))
            return "Identifier '" + s + "' starts with lowercase — must start with uppercase A-Z";
        if (s.length() > 31)
            return "Identifier '" + s + "' exceeds maximum length of 31 characters (found " + s.length() + ")";
        return "Identifier '" + s + "' contains invalid characters";
    }

    // ------------------------------------------------------------------ //
    //  Internal add + deduplication                                        //
    // ------------------------------------------------------------------ //

    private void addError(ErrorType type, int line, int col, String lexeme, String reason) {
        errors.add(new LexicalError(type, line, col, lexeme, reason));
    }

    // ------------------------------------------------------------------ //
    //  Output                                                              //
    // ------------------------------------------------------------------ //

    public void display() {
        if (errors.isEmpty()) {
            System.out.println("\n✓ No lexical errors found.");
            return;
        }
        System.out.println("\n========== LEXICAL ERRORS (" + errors.size() + ") ==========");
        for (LexicalError e : errors) {
            System.out.println(e);
        }
        System.out.println("==========================================\n");
    }

    public boolean hasErrors()        { return !errors.isEmpty(); }
    public int     errorCount()       { return errors.size(); }
    public List<LexicalError> getErrors() { return Collections.unmodifiableList(errors); }
}