import java.util.*;

public class SymbolTable {
    private static class SymbolEntry {
        String name;
        String type;
        int firstOccurrence;
        int frequency;

        SymbolEntry(String name, String type, int line) {
            this.name = name;
            this.type = type;
            this.firstOccurrence = line;
            this.frequency = 1;
        }

        @Override
        public String toString() {
            return String.format("Name: %-15s Type: %-10s First Occurrence: %-5d Frequency: %d", 
                name, type, firstOccurrence, frequency);
        }
    }

    private Map<String, SymbolEntry> symbols;

    public SymbolTable() {
        this.symbols = new LinkedHashMap<>();
    }

    // Insert or update symbol
    public void insert(String name, String type, int line) {
        if (symbols.containsKey(name)) {
            symbols.get(name).frequency++;
        } else {
            symbols.put(name, new SymbolEntry(name, type, line));
        }
    }

    // Lookup symbol
    public SymbolEntry lookup(String name) {
        return symbols.get(name);
    }

    // Check if symbol exists
    public boolean contains(String name) {
        return symbols.containsKey(name);
    }

    // Display symbol table
    public void display() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SYMBOL TABLE");
        System.out.println("=".repeat(70));
        
        if (symbols.isEmpty()) {
            System.out.println("(Empty - No identifiers found)");
        } else {
            System.out.printf("%-20s %-15s %-15s %-10s\n", "Name", "Type", "First Line", "Frequency");
            System.out.println("-".repeat(70));
            
            for (SymbolEntry entry : symbols.values()) {
                System.out.printf("%-20s %-15s %-15d %-10d\n", 
                    entry.name, entry.type, entry.firstOccurrence, entry.frequency);
            }
        }
        System.out.println("=".repeat(70) + "\n");
    }

    // Get symbol count
    public int getSize() {
        return symbols.size();
    }
}
