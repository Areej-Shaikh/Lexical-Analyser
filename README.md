# Lexical Analyzer

This project implements a lexical analyzer for a custom language with the `.lang` file extension using both a **manual DFA-based scanner in Java** and a **JFlex-generated scanner**. The analyzer tokenizes source code into identifiers, literals, operators, punctuators, and comments, and reports lexical errors such as invalid identifiers and malformed literals.

## Language Name and File Extension

**Language Name:** OurLang  
**File Extension:** `.lang`

All source programs for this language must be saved with the `.lang` extension.

### Example:
test1.lang,
test2.lang

## Keywords

The following reserved keywords are implemented in OurLang and are recognized by the lexical analyzer:

**Regex:** (true|false)

**Keyword List:**
- true  
- false  

These keywords represent boolean literals and are used for logical expressions in the language. They cannot be used as identifiers.

## Identifier Rules and Examples

Identifiers in OurLang are used to name variables and must follow these rules:

**Rules:**
- An identifier must start with an uppercase letter (A–Z).
- It may be followed by digits, lowercase letters, or underscores (0-9, a–z, _).
- Identifiers cannot exceed 31 characters in length.
- Reserved keywords (`true`, `false`) cannot be used as identifiers.

**Regex:** [A-Z][a-zA-Z]{0,30}

**Valid Identifiers:**
- Count
- Value
- Result
- Totalsum0

**Invalid Identifiers:**
- count (starts with lowercase letter)
- verylongidthatexceeds31characters (exceedslimit)
- COUNT (followed by uppercase letters)

## Literals

Literals represent fixed values in OurLang. The lexical analyzer supports the following types:

### 1. Integer Literals
- Whole numbers without a decimal point.
- Optional `+` or `-` sign.
- Example:
123,
-42,
+7

### 2. Floating-Point Literals
- Numbers with a decimal point and optional exponent.
- Up to 6 digits allowed after the decimal point.
- Optional `+` or `-` sign.
- Exponent format: `e` or `E` followed by optional sign and digits.
- Examples:
3.14,
-0.123456,
2.0E-3,
+5.67e+2

### 3. Boolean Literals
- Only two values: `true` or `false`.
- Examples:
true,
false

### 4. Character Literals
- Single characters enclosed in single quotes `' '`.
- Supports escape sequences: `\n`, `\t`, `\\`, `\'`.
- Examples:
'A',
'\n',
'\t'

### 5. String Literals
- Sequence of characters enclosed in double quotes `" "`.
- Supports escape sequences: `\n`, `\t`, `\\`, `\"`.
- Can span multiple lines.
- Examples:
"Hello, World!",
"Line1\nLine2\tTabbed",
"I said "Hello""

## Operators

OurLang supports both **single-character** and **multi-character operators**. These are used for arithmetic, relational, logical, and assignment operations.

### 1. Single-Character Operators
- `+`  Addition  
- `-`  Subtraction  
- `*`  Multiplication  
- `/`  Division  
- `%`  Modulus  
- `<`  Less than  
- `>`  Greater than  
- `=`  Assignment  
- `!`  Logical NOT  

### 2. Multi-Character Operators
- `**`  Exponentiation  
- `==`  Equal to  
- `!=`  Not equal to  
- `<=`  Less than or equal to  
- `>=`  Greater than or equal to  
- `&&`  Logical AND  
- `||`  Logical OR  
- `++`  Increment  
- `--`  Decrement  
- `+=`  Add and assign  
- `-=`  Subtract and assign  
- `*=`  Multiply and assign  
- `/=`  Divide and assign  

### Operator Precedence (from highest to lowest)
1. `**` (Exponentiation)  
2. `*`, `/`, `%` (Multiplication, Division, Modulus)  
3. `+`, `-` (Addition, Subtraction)  
4. `==`, `!=`, `<`, `<=`, `>`, `>=` (Comparison)  
5. `&&` (Logical AND)  
6. `||` (Logical OR)  
7. `=` (Assignment)  
8. `+=`, `-=`, `*=`, `/=(assignment)  
9. `++`, `--` (Increment/Decrement)  

## Comments

OurLang supports **single-line** and **multi-line** comments, including **nested multi-line comments**.

### 1. Single-line Comments
Start with `##` and continue until the end of the line.

Example: ## This is a single-line comment

### 2. Multi-Line Comments
Enclosed between #* and *#.

Example:
#* This is a multi-line comment
   in multiple lines
*#

### 3. Nested Multi-Line Comments
Multi-line comments can be nested within another multi-line comment.

Example:
#* Outer comment start
   #* Inner comment *#
   Outer comment end
*#

## Sample Programs
### Sample Program 1
```lang

# Variables and assignment
Count = 10;
Value = 3.14;
Total = Count + Value;

# Increment and Decrement
Count++;
Value--;

# Compound assignment
Total += 5;
Result *= 2;

# Boolean literals
Flag1 = true;
Flag2 = false;
```

### Sample Program 2
```
# Single-line comment
Result = 100;

# Multi-line comment
#* This is a multi-line comment
   It spans several lines
*#

# Nested multi-line comment
#* Outer comment
   #* Inner comment *#
   End of outer comment
*#
```
### Sample Program 3
```lang
# Strings and Characters
Name = "Hello, World!";
Message = "Line1\nLine2\tTabbed";
Quote = "He said \"Hello\"";

# Character literals
CharA = 'A';
Newline = '\n';
Tab = '\t';
Backslash = '\\';
SingleQuote = '\'';

```
## Compilation and Execution Instructions
- Ensure you have Java 17 or later installed.

### To compile the manual scanner:

javac ManualScanner.java Token.java SymbolTable.java ErrorHandler.java

### To run the scanner:

java ManualScanner


### To use the JFlex-generated scanner, generate the scanner first using:

-jflex Scanner.jflex

#### then

javac Scanner.java Token.java SymbolTable.java ErrorHandler.java
java Scanner

### Team Members
## Areej Shaikh: 23I-0620
## Abeerah Sohail: 23I-0660







