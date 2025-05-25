package com.warnomodmaker.parser;

/**
 * Represents a token in the NDF file format.
 * Tokens are the basic building blocks of the NDF syntax.
 */
public class NDFToken {

    /**
     * Types of tokens in the NDF file format
     */
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

    /**
     * Creates a new token
     *
     * @param type The type of the token
     * @param value The string value of the token
     * @param line The line number where the token appears
     * @param column The column number where the token appears
     */
    public NDFToken(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
        this.originalText = value;
    }

    /**
     * Creates a new token with formatting information
     *
     * @param type The type of the token
     * @param value The string value of the token
     * @param line The line number where the token appears
     * @param column The column number where the token appears
     * @param leadingWhitespace Whitespace before the token
     * @param trailingWhitespace Whitespace after the token
     * @param originalText The exact original text of the token
     */
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

    /**
     * Gets the type of the token
     *
     * @return The token type
     */
    public TokenType getType() {
        return type;
    }

    /**
     * Gets the string value of the token
     *
     * @return The token value
     */
    public String getValue() {
        return value;
    }

    /**
     * Gets the line number where the token appears
     *
     * @return The line number
     */
    public int getLine() {
        return line;
    }

    /**
     * Gets the column number where the token appears
     *
     * @return The column number
     */
    public int getColumn() {
        return column;
    }

    /**
     * Gets the whitespace before the token
     *
     * @return The leading whitespace
     */
    public String getLeadingWhitespace() {
        return leadingWhitespace;
    }

    /**
     * Sets the whitespace before the token
     *
     * @param leadingWhitespace The leading whitespace
     */
    public void setLeadingWhitespace(String leadingWhitespace) {
        this.leadingWhitespace = leadingWhitespace;
    }

    /**
     * Gets the whitespace after the token
     *
     * @return The trailing whitespace
     */
    public String getTrailingWhitespace() {
        return trailingWhitespace;
    }

    /**
     * Sets the whitespace after the token
     *
     * @param trailingWhitespace The trailing whitespace
     */
    public void setTrailingWhitespace(String trailingWhitespace) {
        this.trailingWhitespace = trailingWhitespace;
    }

    /**
     * Gets the exact original text of the token
     *
     * @return The original text
     */
    public String getOriginalText() {
        return originalText;
    }

    /**
     * Sets the exact original text of the token
     *
     * @param originalText The original text
     */
    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    /**
     * Gets the exact text representation of this token, including whitespace
     *
     * @return The exact text
     */
    public String getExactText() {
        return leadingWhitespace + originalText + trailingWhitespace;
    }

    @Override
    public String toString() {
        return String.format("%s('%s') at %d:%d", type, value, line, column);
    }
}
