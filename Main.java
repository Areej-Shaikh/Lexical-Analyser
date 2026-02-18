import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            FileReader reader = new FileReader("test.txt");
            Yylex lexer = new Yylex(reader);
            SymbolTable  symbolTable  = new SymbolTable();
            ErrorHandler errorHandler = new ErrorHandler();

            // Collect all tokens
            List<Token> tokens = new ArrayList<>();
            Token token;
            while ((token = lexer.yylex()) != null) {
                tokens.add(token);
            }

            List<Token> merged = new ArrayList<>();
            int i = 0;
            while (i < tokens.size()) {
                Token t = tokens.get(i);
                if (t.getType() == TokenType.ERROR) {
                    StringBuilder sb = new StringBuilder(t.getLexeme());
                    int line = t.getLine();
                    int col  = t.getColumn();
                    int j = i + 1;
                    while (j < tokens.size()
                            && tokens.get(j).getType() == TokenType.ERROR
                            && tokens.get(j).getLine() == line
                            && tokens.get(j).getColumn() == tokens.get(j-1).getColumn() + 1) {
                        sb.append(tokens.get(j).getLexeme());
                        j++;
                    }
                    merged.add(new Token(TokenType.ERROR, sb.toString(), line, col));
                    i = j;
                } else {
                    merged.add(t);
                    i++;
                }
            }

            // Process all tokens 
            for (Token t : merged) {
                if (t.getType() == TokenType.IDENTIFIER) {
                    symbolTable.insert(t.getLexeme(), "IDENTIFIER", t.getLine());
                } else if (t.getType() == TokenType.ERROR) {
                    errorHandler.reportFromToken(t);
                }
            }

            StringBuilder output = new StringBuilder();

            output.append("TOKEN OUTPUT\n");
            for (Token t : merged) {
                if (t.getType() == TokenType.ERROR) {
                    continue;
                }
                if (t.getType() == TokenType.MULTI_LINE_COMMENT) {
                    String text = t.getLexeme();
                    if (text.length() > 50) {
                        text = text.substring(0, 47) + "...";
                    }
                    // Replace newlines with spaces 
                    text = text.replace("\n", " ").replace("\r", "");
                    output.append(String.format("<%s, Lines %d-%d, \"%s\">%n", 
                        t.getType(), t.getLine(), t.getEndLine(), text));
                } else {
                    output.append(t.toString()).append("\n");
                }
            }

            output.append("\n======================================================================\n");
            output.append("SYMBOL TABLE\n");
            output.append("======================================================================\n");
            output.append(String.format("%-20s %-15s %-15s %s\n",
                "Name", "Type", "First Line", "Frequency"));
            output.append("----------------------------------------------------------------------\n");
            for (SymbolTable.SymbolEntry entry : symbolTable.getAllSymbols()) {
                output.append(String.format("%-20s %-15s %-15d %-10d\n",
                    entry.name, entry.type, entry.firstOccurrence, entry.frequency));
            }
            output.append("======================================================================\n");

            output.append("\n========== LEXICAL ERRORS (")
                  .append(errorHandler.errorCount())
                  .append(") ==========\n");
            if (errorHandler.hasErrors()) {
                for (ErrorHandler.LexicalError e : errorHandler.getErrors()) {
                    output.append(e.toString()).append("\n");
                }
            } else {
                output.append("No lexical errors found.\n");
            }
            output.append("==========================================\n");

            // Print to console
            System.out.print(output);

            // Write to file
            PrintStream fileOut = new PrintStream(
                new FileOutputStream("output.txt"), true, "UTF-8");
            fileOut.print(output);
            fileOut.close();

            System.out.println("\nOutput written to output.txt");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}