import java.io.*;

public class Main {
    public static void main(String[] args) {
        try {
            FileReader reader = new FileReader("src/test.txt");
            Yylex lexer = new Yylex(reader);
            SymbolTable  symbolTable  = new SymbolTable();
            ErrorHandler errorHandler = new ErrorHandler();

            Token token;
            while ((token = lexer.yylex()) != null) {
                System.out.println(token);

                if (token.getType() == TokenType.IDENTIFIER) {
                    symbolTable.insert(token.getLexeme(), "IDENTIFIER", token.getLine());

                } else if (token.getType() == TokenType.INTEGER_LITERAL) {
                    symbolTable.insert(token.getLexeme(), "INTEGER", token.getLine());

                } else if (token.getType() == TokenType.FLOAT_LITERAL) {
                    symbolTable.insert(token.getLexeme(), "FLOAT", token.getLine());

                } else if (token.getType() == TokenType.BOOLEAN_EXPRESSION) {
                    symbolTable.insert(token.getLexeme(), "BOOLEAN", token.getLine());

                } else if (token.getType() == TokenType.ERROR) {
                    errorHandler.reportFromToken(token);
                }
            }

            symbolTable.display();
            errorHandler.display();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}