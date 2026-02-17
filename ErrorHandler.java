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
            this.errorType = errorType;
            this.line      = line;
            this.column    = column;
            this.lexeme    = lexeme;
            this.reason    = reason;
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

    public void reportFromToken(Token token) {
        String lexeme = token.getLexeme();
        int    line   = token.getLine();
        int    col    = token.getColumn();

        // Check unclosed/unterminated comments first
        if (lexeme.startsWith("Unclosed comment:") || lexeme.equals("Unterminated multi-line comment")) {
            addError(ErrorType.UNCLOSED_COMMENT, line, col, "comment",
                "Multi-line comment was never closed with '*#'");
            return;
        }

        // Check for unterminated string
        if (lexeme.equals("Unterminated string literal")) {
            addError(ErrorType.MALFORMED_LITERAL, line, col, lexeme,
                "Unterminated string literal - missing closing '\"'");
            return;
        }

        // Check malformed character literal
        if (lexeme.startsWith("MALFORMED_CHAR_LITERAL:")) {
            String content = lexeme.substring("MALFORMED_CHAR_LITERAL:".length());
            addError(ErrorType.MALFORMED_LITERAL, line, col, "'" + content + "'",
                "Malformed character literal - must contain exactly one character");
            return;
        }

        // Check for uppercase keywords
        if (isUppercaseKeyword(lexeme)) {
            addError(ErrorType.INVALID_IDENTIFIER, line, col, lexeme,
                "Keyword '" + lexeme + "' must be lowercase");
            return;
        }

        // Check for camelCase identifiers
        if (isCamelCase(lexeme)) {
            addError(ErrorType.INVALID_IDENTIFIER, line, col, lexeme,
                "Identifier '" + lexeme + "' uses camelCase - identifiers must use only lowercase letters, digits, and underscores after the first uppercase letter");
            return;
        }

        if (isInvalidCharacter(lexeme)) {
            addError(ErrorType.INVALID_CHARACTER, line, col, lexeme,
                "Character '" + lexeme + "' is not part of the language alphabet");

        } else if (isMalformedNumber(lexeme)) {
            addError(ErrorType.MALFORMED_LITERAL, line, col, lexeme,
                "Malformed numeric literal (e.g. multiple decimal points or too many decimals)");

        } else if (isUnterminatedString(lexeme)) {
            addError(ErrorType.MALFORMED_LITERAL, line, col, lexeme,
                "Unterminated string literal - missing closing '\"'");

        } else if (isUnterminatedChar(lexeme)) {
            addError(ErrorType.MALFORMED_LITERAL, line, col, lexeme,
                "Unterminated char literal - missing closing \"'\"");

        } else if (isInvalidIdentifier(lexeme)) {
            addError(ErrorType.INVALID_IDENTIFIER, line, col, lexeme,
                buildIdentifierReason(lexeme));

        } else {
            addError(ErrorType.INVALID_CHARACTER, line, col, lexeme,
                "Unrecognised token '" + lexeme + "'");
        }
    }

    public void reportUnclosedComment(int line, int col, String lexeme) {
        addError(ErrorType.UNCLOSED_COMMENT, line, col, lexeme,
            "Multi-line comment opened with '#*' but never closed with '*#'");
    }

    public void report(ErrorType type, int line, int col, String lexeme, String reason) {
        addError(type, line, col, lexeme, reason);
    }

    private boolean isInvalidCharacter(String s) {
        if (s.length() == 1) {
            char c = s.charAt(0);
            // Check for unrecognized special characters
            if (c == '"' || c == '$') {
                return true;
            }
            return !Character.isLetterOrDigit(c)
                && "+-*/%=!<>(){}[];:,.'#_".indexOf(c) == -1;
        }
        boolean allSymbols = true;
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                allSymbols = false;
                break;
            }
        }
        return allSymbols;
    }

    private boolean isMalformedNumber(String s) {
        long dots = s.chars().filter(c -> c == '.').count();
        if (dots > 1) return true;
        if (s.startsWith(".") || s.endsWith(".")) return true;
        return false;
    }

    private boolean isUnterminatedString(String s) {
        return s.startsWith("\"") && !s.endsWith("\"");
    }

    private boolean isUnterminatedChar(String s) {
        return s.startsWith("'") && !s.endsWith("'");
    }

    private boolean isUppercaseKeyword(String s) {
        String[] keywords = {"START", "FINISH", "LOOP", "CONDITION", "DECLARE", 
                            "OUTPUT", "INPUT", "FUNCTION", "RETURN", "BREAK", 
                            "CONTINUE", "ELSE", "IF"};
        for (String kw : keywords) {
            if (s.equals(kw)) return true;
        }
        return false;
    }

    private boolean isCamelCase(String s) {
        // Matches pattern like: Uppercase followed by lowercase, then another Uppercase
        if (s.length() < 3) return false;
        if (!Character.isUpperCase(s.charAt(0))) return false;
        
        boolean hasLowerAfterFirst = false;
        for (int i = 1; i < s.length(); i++) {
            if (Character.isLowerCase(s.charAt(i))) {
                hasLowerAfterFirst = true;
            } else if (hasLowerAfterFirst && Character.isUpperCase(s.charAt(i))) {
                return true; // Found uppercase after lowercase - camelCase
            }
        }
        return false;
    }

    private boolean isInvalidIdentifier(String s) {
        if (s.isEmpty()) return false;
        char first = s.charAt(0);
        if (Character.isLowerCase(first) || Character.isDigit(first)) return true;
        if (s.length() > 31) return true;
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
            return "Identifier '" + s + "' starts with a digit - must start with uppercase A-Z";
        if (Character.isLowerCase(first))
            return "Identifier '" + s + "' starts with lowercase - must start with uppercase A-Z";
        if (s.length() > 31)
            return "Identifier '" + s + "' exceeds maximum length of 31 characters (found " + s.length() + ")";
        return "Identifier '" + s + "' contains invalid characters";
    }

    private void addError(ErrorType type, int line, int col, String lexeme, String reason) {
        errors.add(new LexicalError(type, line, col, lexeme, reason));
    }

    public void display() {
        if (errors.isEmpty()) {
            System.out.println("\nNo lexical errors found.");
            return;
        }
        System.out.println("\n========== LEXICAL ERRORS (" + errors.size() + ") ==========");
        for (LexicalError e : errors) {
            System.out.println(e);
        }
        System.out.println("==========================================\n");
    }

    public boolean hasErrors()            { return !errors.isEmpty(); }
    public int     errorCount()           { return errors.size(); }
    public List<LexicalError> getErrors() { return Collections.unmodifiableList(errors); }
}