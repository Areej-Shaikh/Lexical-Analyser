import java.io.PrintWriter;
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

    // Check token and report error type
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
            addError(ErrorType.INVALID_CHARACTER, line, col, lexeme,
                "Unrecognised token '" + lexeme + "'");
        }
    }

    // Report unclosed multi-line comment error
    public void reportUnclosedComment(int line, int col, String lexeme) {
        addError(ErrorType.UNCLOSED_COMMENT, line, col, lexeme,
            "Multi-line comment opened with '#*' but never closed with '*#'");
    }

    // Directly report custom error
    public void report(ErrorType type, int line, int col, String lexeme, String reason) {
        addError(type, line, col, lexeme, reason);
    }

    // Check invalid single character token
    public boolean isInvalidCharacter(String s) {
        if (s.length() == 1) {
            char c = s.charAt(0);
            return !Character.isLetterOrDigit(c)
                && "+-*/%=!<>(){}[];:,.'\"#_".indexOf(c) == -1;
        }
        return false;
    }

    // Check malformed numeric literal format
    public boolean isMalformedNumber(String s) {
        long dots = s.chars().filter(c -> c == '.').count();
        if (dots > 1) return true;

        if (s.startsWith(".") || s.endsWith(".")) return true;
        return false;
    }

    // Check unterminated string literal
    private boolean isUnterminatedString(String s) {
        return s.startsWith("\"") && !s.endsWith("\"");
    }

    // Check unterminated char literal
    private boolean isUnterminatedChar(String s) {
        return s.startsWith("'") && !s.endsWith("'");
    }

    // Validate identifier rules
    public boolean isInvalidIdentifier(String s) {
        if (s == null || s.isEmpty()) return true;
        
        char first = s.charAt(0);
        if (first < 'A' || first > 'Z') return true;
        if (s.length() > 31) return true;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) return true;
        }
        return false;
    }

    // Build reason string for identifier error
    private String buildIdentifierReason(String s) {
        if (s == null || s.isEmpty()) return "Empty identifier";
        
        char first = s.charAt(0);
        if (Character.isDigit(first)) return "Identifier '" + s + "' starts with digit '" + first + "' — must start with uppercase letter A-Z";
        if (first >= 'a' && first <= 'z') return "Identifier '" + s + "' starts with lowercase '" + first + "' — must start with uppercase letter A-Z";
        if (first < 'A' || first > 'Z') return "Identifier '" + s + "' starts with invalid character '" + first + "' — must start with uppercase letter A-Z";
        if (s.length() > 31) return "Identifier '" + s + "' exceeds maximum length of 31 characters (found " + s.length() + ")";
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                if (c >= 'A' && c <= 'Z') return "Identifier '" + s + "' contains uppercase letter '" + c + "' at position " + (i + 1) + " — only lowercase letters, digits, and underscores allowed after first character";
                else return "Identifier '" + s + "' contains invalid character '" + c + "' at position " + (i + 1) + " — only lowercase letters, digits, and underscores allowed after first character";
            }
        }
        return "Invalid identifier '" + s + "'";
    }

    // Add new lexical error internally
    private void addError(ErrorType type, int line, int col, String lexeme, String reason) {
        errors.add(new LexicalError(type, line, col, lexeme, reason));
    }

    // Display all errors on console
    public void display() {
        if (errors.isEmpty()) {
            System.out.println("\n✓ No lexical errors found.");
            return;
        }
        System.out.println("\n========== LEXICAL ERRORS (" + errors.size() + ") ==========");
        for (LexicalError e : errors) System.out.println(e);
        System.out.println("==========================================\n");
    }

    public boolean hasErrors() { return !errors.isEmpty(); }
    public int errorCount() { return errors.size(); }
    public List<LexicalError> getErrors() { return Collections.unmodifiableList(errors); }

    // Write errors to file
    public void write(PrintWriter writer) {
        if (errors.isEmpty()) writer.println("No lexical errors found.");
        else for (LexicalError e : errors) writer.println(e.toString());
    }

}
