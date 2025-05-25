package com.warnomodmaker.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes an NDF file into a stream of tokens.
 * This class handles the lexical analysis of the NDF file format.
 */
public class NDFTokenizer {
    private final Reader reader;
    private StringBuilder buffer;
    private StringBuilder whitespaceBuffer;
    private int currentChar;
    private int line;
    private int column;
    private boolean reachedEOF;
    private boolean preserveFormatting;

    /**
     * Creates a new tokenizer for the given reader
     *
     * @param reader The reader to tokenize
     */
    public NDFTokenizer(Reader reader) {
        this(reader, true);
    }

    /**
     * Creates a new tokenizer for the given reader
     *
     * @param reader The reader to tokenize
     * @param preserveFormatting Whether to preserve formatting information
     */
    public NDFTokenizer(Reader reader, boolean preserveFormatting) {
        this.reader = reader;
        this.buffer = new StringBuilder();
        this.whitespaceBuffer = new StringBuilder();
        this.line = 1;
        this.column = 0;
        this.reachedEOF = false;
        this.preserveFormatting = preserveFormatting;

        // Read the first character
        advance();
    }

    /**
     * Tokenizes the entire input and returns a list of tokens
     *
     * @return A list of tokens
     * @throws IOException If an I/O error occurs
     */
    public List<NDFToken> tokenize() throws IOException {
        List<NDFToken> tokens = new ArrayList<>();
        NDFToken token;

        do {
            token = nextToken();
            if (token.getType() != NDFToken.TokenType.COMMENT) {
                tokens.add(token);
            }
        } while (token.getType() != NDFToken.TokenType.EOF);

        return tokens;
    }

    /**
     * Gets the next token from the input
     *
     * @return The next token
     * @throws IOException If an I/O error occurs
     */
    public NDFToken nextToken() throws IOException {
        // Collect leading whitespace
        String leadingWhitespace = collectWhitespace();

        // Check for EOF
        if (reachedEOF) {
            return new NDFToken(NDFToken.TokenType.EOF, "", line, column, leadingWhitespace, "", "");
        }

        // Save the current position
        int tokenLine = line;
        int tokenColumn = column;

        // Save the start position for capturing the original text
        int startPos = buffer.length();

        // Check for comments
        if (currentChar == '/' && peek() == '/') {
            return scanComment(tokenLine, tokenColumn);
        }

        // Check for single-character tokens
        switch (currentChar) {
            case '(':
                String originalText = "(";
                advance();
                String trailingWhitespace = preserveFormatting ? collectWhitespace() : "";
                return new NDFToken(NDFToken.TokenType.OPEN_PAREN, "(", tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case ')':
                originalText = ")";
                advance();
                trailingWhitespace = preserveFormatting ? collectWhitespace() : "";
                return new NDFToken(NDFToken.TokenType.CLOSE_PAREN, ")", tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case '[':
                originalText = "[";
                advance();
                trailingWhitespace = preserveFormatting ? collectWhitespace() : "";
                return new NDFToken(NDFToken.TokenType.OPEN_BRACKET, "[", tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case ']':
                originalText = "]";
                advance();
                trailingWhitespace = preserveFormatting ? collectWhitespace() : "";
                return new NDFToken(NDFToken.TokenType.CLOSE_BRACKET, "]", tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case ',':
                originalText = ",";
                advance();
                trailingWhitespace = preserveFormatting ? collectWhitespace() : "";
                return new NDFToken(NDFToken.TokenType.COMMA, ",", tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case '=':
                originalText = "=";
                advance();
                trailingWhitespace = preserveFormatting ? collectWhitespace() : "";
                return new NDFToken(NDFToken.TokenType.EQUALS, "=", tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case '|':
                originalText = "|";
                advance();
                trailingWhitespace = preserveFormatting ? collectWhitespace() : "";
                return new NDFToken(NDFToken.TokenType.PIPE, "|", tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case '\'':
                return scanSingleQuoteString(tokenLine, tokenColumn, leadingWhitespace);
            case '"':
                return scanDoubleQuoteString(tokenLine, tokenColumn, leadingWhitespace);
        }

        // Check for template references (~/...)
        if (currentChar == '~' && peek() == '/') {
            return scanTemplateRef(tokenLine, tokenColumn, leadingWhitespace);
        }

        // Check for resource references ($/...)
        if (currentChar == '$' && peek() == '/') {
            return scanResourceRef(tokenLine, tokenColumn, leadingWhitespace);
        }

        // Check for numbers
        if (Character.isDigit(currentChar) || (currentChar == '-' && Character.isDigit(peek()))) {
            return scanNumber(tokenLine, tokenColumn, leadingWhitespace);
        }

        // Check for identifiers and keywords
        if (Character.isLetter(currentChar) || currentChar == '_') {
            return scanIdentifier(tokenLine, tokenColumn, leadingWhitespace);
        }

        // Unknown character
        String unknownChar = String.valueOf((char) currentChar);
        String originalText = unknownChar;
        advance();
        String trailingWhitespace = preserveFormatting ? collectWhitespace() : "";
        return new NDFToken(NDFToken.TokenType.UNKNOWN, unknownChar, tokenLine, tokenColumn,
                           leadingWhitespace, trailingWhitespace, originalText);
    }

    /**
     * Scans a comment token
     */
    private NDFToken scanComment(int tokenLine, int tokenColumn) throws IOException {
        buffer.setLength(0);
        buffer.append("//");
        StringBuilder originalTextBuffer = new StringBuilder("//");

        // Consume the '//'
        advance();
        advance();

        // Read until end of line
        while (!reachedEOF && currentChar != '\n' && currentChar != '\r') {
            char c = (char) currentChar;
            buffer.append(c);
            originalTextBuffer.append(c);
            advance();
        }

        String originalText = originalTextBuffer.toString();
        String leadingWhitespace = ""; // Comments don't have leading whitespace in our model
        String trailingWhitespace = ""; // Comments don't have trailing whitespace in our model

        return new NDFToken(NDFToken.TokenType.COMMENT, buffer.toString(), tokenLine, tokenColumn,
                           leadingWhitespace, trailingWhitespace, originalText);
    }

    /**
     * Scans a single-quoted string literal token
     */
    private NDFToken scanSingleQuoteString(int tokenLine, int tokenColumn, String leadingWhitespace) throws IOException {
        buffer.setLength(0);
        StringBuilder originalTextBuffer = new StringBuilder("'");

        // Consume the opening quote
        advance();

        // Read until closing quote
        while (!reachedEOF && currentChar != '\'') {
            // Handle escape sequences
            if (currentChar == '\\') {
                originalTextBuffer.append('\\');
                advance();
                if (!reachedEOF) {
                    // Add the escaped character
                    char escapedChar = (char) currentChar;
                    buffer.append(escapedChar);
                    originalTextBuffer.append(escapedChar);
                    advance();
                }
            } else {
                char c = (char) currentChar;
                buffer.append(c);
                originalTextBuffer.append(c);
                advance();
            }
        }

        // Consume the closing quote
        if (currentChar == '\'') {
            originalTextBuffer.append('\'');
            advance();
        }

        String originalText = originalTextBuffer.toString();
        String trailingWhitespace = preserveFormatting ? collectWhitespace() : "";

        return new NDFToken(NDFToken.TokenType.STRING_LITERAL, buffer.toString(), tokenLine, tokenColumn,
                           leadingWhitespace, trailingWhitespace, originalText);
    }

    /**
     * Scans a double-quoted string literal token
     */
    private NDFToken scanDoubleQuoteString(int tokenLine, int tokenColumn, String leadingWhitespace) throws IOException {
        buffer.setLength(0);
        StringBuilder originalTextBuffer = new StringBuilder("\"");

        // Consume the opening quote
        advance();

        // Read until closing quote
        while (!reachedEOF && currentChar != '"') {
            // Handle escape sequences
            if (currentChar == '\\') {
                originalTextBuffer.append('\\');
                advance();
                if (!reachedEOF) {
                    // Add the escaped character
                    char escapedChar = (char) currentChar;
                    buffer.append(escapedChar);
                    originalTextBuffer.append(escapedChar);
                    advance();
                }
            } else {
                char c = (char) currentChar;
                buffer.append(c);
                originalTextBuffer.append(c);
                advance();
            }
        }

        // Consume the closing quote
        if (currentChar == '"') {
            originalTextBuffer.append('"');
            advance();
        }

        String originalText = originalTextBuffer.toString();
        String trailingWhitespace = preserveFormatting ? collectWhitespace() : "";

        return new NDFToken(NDFToken.TokenType.STRING_LITERAL, buffer.toString(), tokenLine, tokenColumn,
                           leadingWhitespace, trailingWhitespace, originalText);
    }

    /**
     * Scans a template reference token (~/...)
     */
    private NDFToken scanTemplateRef(int tokenLine, int tokenColumn, String leadingWhitespace) throws IOException {
        buffer.setLength(0);
        buffer.append("~/");
        StringBuilder originalTextBuffer = new StringBuilder("~/");

        // Consume the '~/'
        advance();
        advance();

        // Read the reference path
        while (!reachedEOF && (Character.isLetterOrDigit(currentChar) || currentChar == '_' ||
                currentChar == '/' || currentChar == '.')) {
            char c = (char) currentChar;
            buffer.append(c);
            originalTextBuffer.append(c);
            advance();
        }

        String originalText = originalTextBuffer.toString();
        String trailingWhitespace = preserveFormatting ? collectWhitespace() : "";

        return new NDFToken(NDFToken.TokenType.TEMPLATE_REF, buffer.toString(), tokenLine, tokenColumn,
                           leadingWhitespace, trailingWhitespace, originalText);
    }

    /**
     * Scans a resource reference token ($/...)
     */
    private NDFToken scanResourceRef(int tokenLine, int tokenColumn, String leadingWhitespace) throws IOException {
        buffer.setLength(0);
        buffer.append("$/");
        StringBuilder originalTextBuffer = new StringBuilder("$/");

        // Consume the '$/'
        advance();
        advance();

        // Read the reference path
        while (!reachedEOF && (Character.isLetterOrDigit(currentChar) || currentChar == '_' ||
                currentChar == '/' || currentChar == '.')) {
            char c = (char) currentChar;
            buffer.append(c);
            originalTextBuffer.append(c);
            advance();
        }

        String originalText = originalTextBuffer.toString();
        String trailingWhitespace = preserveFormatting ? collectWhitespace() : "";

        return new NDFToken(NDFToken.TokenType.RESOURCE_REF, buffer.toString(), tokenLine, tokenColumn,
                           leadingWhitespace, trailingWhitespace, originalText);
    }

    /**
     * Scans a number literal token
     */
    private NDFToken scanNumber(int tokenLine, int tokenColumn, String leadingWhitespace) throws IOException {
        buffer.setLength(0);
        StringBuilder originalTextBuffer = new StringBuilder();

        // Handle negative numbers
        if (currentChar == '-') {
            buffer.append('-');
            originalTextBuffer.append('-');
            advance();
        }

        // Read digits before decimal point
        while (!reachedEOF && Character.isDigit(currentChar)) {
            char c = (char) currentChar;
            buffer.append(c);
            originalTextBuffer.append(c);
            advance();
        }

        // Handle decimal point
        if (currentChar == '.') {
            buffer.append('.');
            originalTextBuffer.append('.');
            advance();

            // Read digits after decimal point
            while (!reachedEOF && Character.isDigit(currentChar)) {
                char c = (char) currentChar;
                buffer.append(c);
                originalTextBuffer.append(c);
                advance();
            }
        }

        String originalText = originalTextBuffer.toString();
        String trailingWhitespace = preserveFormatting ? collectWhitespace() : "";

        return new NDFToken(NDFToken.TokenType.NUMBER_LITERAL, buffer.toString(), tokenLine, tokenColumn,
                           leadingWhitespace, trailingWhitespace, originalText);
    }

    /**
     * Scans an identifier or keyword token
     */
    private NDFToken scanIdentifier(int tokenLine, int tokenColumn, String leadingWhitespace) throws IOException {
        buffer.setLength(0);
        StringBuilder originalTextBuffer = new StringBuilder();

        // Read the identifier
        while (!reachedEOF && (Character.isLetterOrDigit(currentChar) || currentChar == '_')) {
            char c = (char) currentChar;
            buffer.append(c);
            originalTextBuffer.append(c);
            advance();
        }

        String identifier = buffer.toString();
        String originalText = originalTextBuffer.toString();

        // Collect whitespace after identifier
        String trailingWhitespace = preserveFormatting ? collectWhitespace() : "";

        // Check for keywords
        switch (identifier.toLowerCase()) {
            case "export":
                return new NDFToken(NDFToken.TokenType.EXPORT, identifier, tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case "is":
                return new NDFToken(NDFToken.TokenType.IS, identifier, tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case "map":
                return new NDFToken(NDFToken.TokenType.MAP, identifier, tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            case "true":
            case "false":
                return new NDFToken(NDFToken.TokenType.BOOLEAN_LITERAL, identifier, tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
            default:
                // Check for enum values (Type/Value)
                if (currentChar == '/') {
                    buffer.append('/');
                    originalTextBuffer.append('/');
                    advance();

                    // Read the enum value
                    while (!reachedEOF && (Character.isLetterOrDigit(currentChar) || currentChar == '_')) {
                        char c = (char) currentChar;
                        buffer.append(c);
                        originalTextBuffer.append(c);
                        advance();
                    }

                    originalText = originalTextBuffer.toString();
                    trailingWhitespace = preserveFormatting ? collectWhitespace() : "";

                    return new NDFToken(NDFToken.TokenType.ENUM_VALUE, buffer.toString(), tokenLine, tokenColumn,
                                       leadingWhitespace, trailingWhitespace, originalText);
                }

                // Check for GUID
                if (identifier.equals("GUID") && currentChar == ':') {
                    buffer.append(':');
                    originalTextBuffer.append(':');
                    advance();

                    // Read the GUID value
                    if (currentChar == '{') {
                        buffer.append('{');
                        originalTextBuffer.append('{');
                        advance();

                        // Read until closing brace
                        while (!reachedEOF && currentChar != '}') {
                            char c = (char) currentChar;
                            buffer.append(c);
                            originalTextBuffer.append(c);
                            advance();
                        }

                        if (currentChar == '}') {
                            buffer.append('}');
                            originalTextBuffer.append('}');
                            advance();
                        }

                        originalText = originalTextBuffer.toString();
                        trailingWhitespace = preserveFormatting ? collectWhitespace() : "";

                        return new NDFToken(NDFToken.TokenType.GUID, buffer.toString(), tokenLine, tokenColumn,
                                           leadingWhitespace, trailingWhitespace, originalText);
                    }
                }

                // Return just the identifier - don't consume 'is' or '(' here
                // The parser will handle these tokens separately
                return new NDFToken(NDFToken.TokenType.IDENTIFIER, identifier, tokenLine, tokenColumn,
                                   leadingWhitespace, trailingWhitespace, originalText);
        }
    }

    /**
     * Advances to the next character in the input
     */
    private void advance() {
        try {
            currentChar = reader.read();

            if (currentChar == -1) {
                reachedEOF = true;
                return;
            }

            column++;

            if (currentChar == '\n') {
                line++;
                column = 0;
            }
        } catch (IOException e) {
            reachedEOF = true;
            currentChar = -1;
        }
    }

    /**
     * Peeks at the next character without advancing
     */
    private int peek() throws IOException {
        reader.mark(1);
        int nextChar = reader.read();
        reader.reset();
        return nextChar;
    }

    /**
     * Peeks at multiple characters ahead without advancing
     */
    private String peekMultiple(int count) throws IOException {
        reader.mark(count);
        char[] buffer = new char[count];
        int charsRead = reader.read(buffer, 0, count);
        reader.reset();

        if (charsRead < count) {
            return new String(buffer, 0, charsRead);
        } else {
            return new String(buffer);
        }
    }

    /**
     * Collects whitespace characters
     *
     * @return The collected whitespace
     */
    private String collectWhitespace() {
        if (!preserveFormatting) {
            // If not preserving formatting, just skip whitespace
            while (!reachedEOF && Character.isWhitespace(currentChar)) {
                advance();
            }
            return "";
        }

        // Clear the whitespace buffer
        whitespaceBuffer.setLength(0);

        // Collect whitespace
        while (!reachedEOF && Character.isWhitespace(currentChar)) {
            whitespaceBuffer.append((char) currentChar);
            advance();
        }

        return whitespaceBuffer.toString();
    }
}
