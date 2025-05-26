package com.warnomodmaker.parser;

public class NDFToken {

    
    public enum TokenType {
        // Basic tokens
        IDENTIFIER,         // Variable names, type names
        STRING_LITERAL,     // 'string' or "string"
        NUMBER_LITERAL,     // 123, 123.45
        BOOLEAN_LITERAL,    // True, False, true, false

        // Operators and delimiters
        EQUALS,             // =
        IS,                 // is
        OPEN_PAREN,         // (
        CLOSE_PAREN,        // )
        OPEN_BRACKET,       // [
        CLOSE_BRACKET,      // ]
        COMMA,              // ,
        PIPE,               // |
        DOT,                // .

        // Special tokens
        TEMPLATE_REF,       // ~/TemplateName
        RESOURCE_REF,       // $/Path/To/Resource
        GUID,               // GUID:{...}
        ENUM_VALUE,         // EnumType/Value

        // Structure tokens
        MAP,                // MAP
        EXPORT,             // export
        MODULE,             // Module=
        FAMILY,             // Family=
        INDEX,              // Index=

        // Other
        COMMENT,            // // Comment
        EOF,                // End of file
        UNKNOWN             // Unknown token
    }

    private final TokenType type;
    private final String value;
    private final int line;
    private final int column;
    private String leadingWhitespace = "";  // Whitespace before the token
    private String trailingWhitespace = ""; // Whitespace after the token
    private String originalText = "";       // The exact original text of the token

    
    public NDFToken(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
        this.originalText = value;
    }

    
    public NDFToken(TokenType type, String value, int line, int column,
                   String leadingWhitespace, String trailingWhitespace, String originalText) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
        this.leadingWhitespace = leadingWhitespace;
        this.trailingWhitespace = trailingWhitespace;
        this.originalText = originalText;
    }

    
    public TokenType getType() {
        return type;
    }

    
    public String getValue() {
        return value;
    }

    
    public int getLine() {
        return line;
    }

    
    public int getColumn() {
        return column;
    }

    
    public String getLeadingWhitespace() {
        return leadingWhitespace;
    }

    
    public void setLeadingWhitespace(String leadingWhitespace) {
        this.leadingWhitespace = leadingWhitespace;
    }

    
    public String getTrailingWhitespace() {
        return trailingWhitespace;
    }

    
    public void setTrailingWhitespace(String trailingWhitespace) {
        this.trailingWhitespace = trailingWhitespace;
    }

    
    public String getOriginalText() {
        return originalText;
    }

    
    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    
    public String getExactText() {
        return leadingWhitespace + originalText + trailingWhitespace;
    }

    @Override
    public String toString() {
        return String.format("%s('%s') at %d:%d", type, value, line, column);
    }
}
