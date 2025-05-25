package com.warnomodmaker.parser;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.*;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for NDF files.
 * This class builds an object model from the tokens produced by the tokenizer.
 */
public class NDFParser {
    private final NDFTokenizer tokenizer;
    private List<NDFToken> tokens;
    private int currentTokenIndex;
    private NDFToken currentToken;
    private List<NDFToken> originalTokens;
    private NDFFileType fileType; // Current file type being parsed

    /**
     * Creates a new parser for the given reader
     *
     * @param reader The reader to parse
     */
    public NDFParser(Reader reader) {
        this.tokenizer = new NDFTokenizer(reader);
        this.currentTokenIndex = 0;
        this.originalTokens = new ArrayList<>();
        this.fileType = NDFFileType.UNKNOWN; // Default to unknown
    }

    /**
     * Sets the file type for this parser
     *
     * @param fileType The file type to set
     */
    public void setFileType(NDFFileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Gets the original tokens from the parser
     *
     * @return The original tokens
     */
    public List<NDFToken> getOriginalTokens() {
        return originalTokens;
    }

    /**
     * Parses the input and returns a list of unit descriptors
     *
     * @return A list of unit descriptors
     * @throws IOException If an I/O error occurs
     * @throws NDFParseException If a parsing error occurs
     */
    public List<ObjectValue> parse() throws IOException, NDFParseException {
        // Tokenize the input
        tokens = tokenizer.tokenize();

        // Store a copy of the original tokens
        originalTokens = new ArrayList<>(tokens);

        currentTokenIndex = 0;
        currentToken = tokens.get(currentTokenIndex);

        List<ObjectValue> unitDescriptors = new ArrayList<>();

        // Parse descriptors based on file type
        while (currentToken.getType() != NDFToken.TokenType.EOF) {
            try {
                if (currentToken.getType() == NDFToken.TokenType.EXPORT) {
                    unitDescriptors.add(parseExportedDescriptor());
                } else if (currentToken.getType() == NDFToken.TokenType.RESOURCE_REF) {
                    // Handle standalone object definitions like $/GFX/Unit/Descriptor_Unit_A109BA_BEL/HeliApparence is THeliApparence(...)
                    unitDescriptors.add(parseStandaloneObjectDefinition());
                } else if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                    // Check what type of definition this is
                    if (isDescriptorDefinition()) {
                        // Descriptor definition like "Name is TypeName(...)"
                        unitDescriptors.add(parseDescriptorDefinition());
                    } else if (isSimpleAssignment()) {
                        // Simple assignment like "Name is Value" - parse these as constant definitions
                        // These are constants/aliases like "eAAM is 1"
                        unitDescriptors.add(parseNonExportedDefinition());
                    } else {
                        // Handle other non-exported definitions (fallback)
                        unitDescriptors.add(parseNonExportedDefinition());
                    }
                } else {
                    // Skip unknown tokens
                    advance();
                }
            } catch (NDFParseException e) {
                // If we encounter a parsing error, try to recover by skipping to the next likely start point
                System.err.println("Warning: Parsing error at line " + currentToken.getLine() + ": " + e.getMessage());

                // Skip tokens until we find a likely recovery point
                while (currentToken.getType() != NDFToken.TokenType.EOF &&
                       currentToken.getType() != NDFToken.TokenType.EXPORT &&
                       currentToken.getType() != NDFToken.TokenType.RESOURCE_REF) {
                    advance();
                }
            }
        }

        return unitDescriptors;
    }

    /**
     * Parses an exported descriptor (works for all file types)
     *
     * @return The parsed descriptor
     * @throws NDFParseException If a parsing error occurs
     */
    private ObjectValue parseExportedDescriptor() throws NDFParseException {
        // Expect 'export DescriptorName is TypeName'
        expect(NDFToken.TokenType.EXPORT);

        // Handle case where descriptor name might be followed by brackets (like export Name[0])
        String descriptorName;
        if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
            descriptorName = currentToken.getValue();
            advance();

            // Check for array syntax like Name[0]
            if (currentToken.getType() == NDFToken.TokenType.OPEN_BRACKET) {
                advance(); // consume [
                // Skip array index content
                while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET &&
                       currentToken.getType() != NDFToken.TokenType.EOF) {
                    advance();
                }
                if (currentToken.getType() == NDFToken.TokenType.CLOSE_BRACKET) {
                    advance(); // consume ]
                }
            }
        } else {
            throw new NDFParseException("Expected descriptor name after export", currentToken);
        }

        expect(NDFToken.TokenType.IS);
        String typeName = expect(NDFToken.TokenType.IDENTIFIER).getValue();

        // Create the descriptor object
        ObjectValue descriptor = NDFValue.createObject(typeName);
        descriptor.setInstanceName(descriptorName);
        descriptor.setExported(true); // Mark as exported

        // Parse the descriptor body
        expect(NDFToken.TokenType.OPEN_PAREN);
        parseObjectProperties(descriptor);
        expect(NDFToken.TokenType.CLOSE_PAREN);

        return descriptor;
    }

    /**
     * Checks if the current token sequence represents a descriptor definition
     * (like "Name is TypeName" pattern)
     *
     * @return true if this is a descriptor definition
     */
    private boolean isDescriptorDefinition() {
        // Look ahead to check if we have "IDENTIFIER is IDENTIFIER" pattern
        if (currentTokenIndex + 2 < tokens.size()) {
            NDFToken nextToken = tokens.get(currentTokenIndex + 1);
            NDFToken thirdToken = tokens.get(currentTokenIndex + 2);

            // Check for descriptor pattern: "Name is TypeName("
            if (nextToken.getType() == NDFToken.TokenType.IS &&
                thirdToken.getType() == NDFToken.TokenType.IDENTIFIER) {

                // Look ahead for opening parenthesis to confirm it's a descriptor
                if (currentTokenIndex + 3 < tokens.size()) {
                    NDFToken fourthToken = tokens.get(currentTokenIndex + 3);
                    return fourthToken.getType() == NDFToken.TokenType.OPEN_PAREN;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the current token sequence represents a simple assignment
     * (like "Name is Value" or "Name is [...]")
     *
     * @return true if this is a simple assignment
     */
    private boolean isSimpleAssignment() {
        // Look ahead to check if we have "IDENTIFIER is VALUE" pattern
        if (currentTokenIndex + 2 < tokens.size()) {
            NDFToken nextToken = tokens.get(currentTokenIndex + 1);
            NDFToken thirdToken = tokens.get(currentTokenIndex + 2);

            if (nextToken.getType() == NDFToken.TokenType.IS) {
                // Check if third token is a simple value (number, string, identifier, or array start)
                return thirdToken.getType() == NDFToken.TokenType.NUMBER_LITERAL ||
                       thirdToken.getType() == NDFToken.TokenType.STRING_LITERAL ||
                       thirdToken.getType() == NDFToken.TokenType.OPEN_BRACKET ||
                       (thirdToken.getType() == NDFToken.TokenType.IDENTIFIER &&
                        !isDescriptorDefinition()); // Not a descriptor type
            }
        }
        return false;
    }

    /**
     * Parses a descriptor definition (like in Ammunition.ndf)
     *
     * @return The parsed descriptor
     * @throws NDFParseException If a parsing error occurs
     */
    private ObjectValue parseDescriptorDefinition() throws NDFParseException {
        // Parse "Name is TypeName(...)"
        String descriptorName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        expect(NDFToken.TokenType.IS);
        String typeName = expect(NDFToken.TokenType.IDENTIFIER).getValue();

        // Create the descriptor object
        ObjectValue descriptor = NDFValue.createObject(typeName);
        descriptor.setInstanceName(descriptorName);

        // Parse the descriptor body
        expect(NDFToken.TokenType.OPEN_PAREN);
        parseObjectProperties(descriptor);
        expect(NDFToken.TokenType.CLOSE_PAREN);

        return descriptor;
    }

    /**
     * Skips a simple assignment (like constants or aliases)
     * These are not unit descriptors and should be ignored
     */
    private void skipSimpleAssignment() throws NDFParseException {
        // Skip "IDENTIFIER is VALUE"
        advance(); // Skip identifier
        expect(NDFToken.TokenType.IS); // Skip "is"

        // Skip the value (could be number, string, identifier, or array)
        if (currentToken.getType() == NDFToken.TokenType.OPEN_BRACKET) {
            // Skip array
            skipArray();
        } else if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
            // Skip object instantiation
            skipObjectInstantiation();
        } else {
            // Skip simple value
            advance();
        }
    }

    /**
     * Skips an array structure
     */
    private void skipArray() throws NDFParseException {
        expect(NDFToken.TokenType.OPEN_BRACKET);
        int depth = 1;

        while (depth > 0 && currentToken.getType() != NDFToken.TokenType.EOF) {
            if (currentToken.getType() == NDFToken.TokenType.OPEN_BRACKET) {
                depth++;
            } else if (currentToken.getType() == NDFToken.TokenType.CLOSE_BRACKET) {
                depth--;
            }
            advance();
        }
    }

    /**
     * Skips an object instantiation
     */
    private void skipObjectInstantiation() throws NDFParseException {
        expect(NDFToken.TokenType.OPEN_PAREN);
        int depth = 1;

        while (depth > 0 && currentToken.getType() != NDFToken.TokenType.EOF) {
            if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                depth++;
            } else if (currentToken.getType() == NDFToken.TokenType.CLOSE_PAREN) {
                depth--;
            }
            advance();
        }
    }

    /**
     * Parses a non-exported definition (like enum constants)
     *
     * @return The parsed definition
     * @throws NDFParseException If a parsing error occurs
     */
    private ObjectValue parseNonExportedDefinition() throws NDFParseException {
        // Expect 'IdentifierName is Value' or other patterns
        String identifierName = expect(NDFToken.TokenType.IDENTIFIER).getValue();

        // Handle different patterns after identifier
        if (currentToken.getType() == NDFToken.TokenType.IS) {
            advance(); // consume 'is'
            NDFValue value = parseValue();

            // Create a simple object to hold this definition
            ObjectValue definition = NDFValue.createObject("ConstantDefinition");
            definition.setInstanceName(identifierName);
            definition.setProperty("Value", value);

            return definition;
        } else if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
            // Handle pattern like "IdentifierName AnotherIdentifier ..."
            // This might be a type declaration or other construct
            String secondIdentifier = currentToken.getValue();
            advance();

            // Try to parse as a type declaration
            ObjectValue definition = NDFValue.createObject("TypeDeclaration");
            definition.setInstanceName(identifierName);
            definition.setProperty("Type", NDFValue.createString(secondIdentifier));

            // Skip any remaining tokens until we find a reasonable stopping point
            while (currentToken.getType() != NDFToken.TokenType.EOF &&
                   currentToken.getType() != NDFToken.TokenType.EXPORT &&
                   currentToken.getType() != NDFToken.TokenType.IDENTIFIER) {
                advance();
            }

            return definition;
        } else {
            // Unknown pattern, create a placeholder and skip
            ObjectValue definition = NDFValue.createObject("UnknownDefinition");
            definition.setInstanceName(identifierName);

            // Skip tokens until we find a safe stopping point
            while (currentToken.getType() != NDFToken.TokenType.EOF &&
                   currentToken.getType() != NDFToken.TokenType.EXPORT) {
                advance();
            }

            return definition;
        }
    }

    /**
     * Parses a standalone object definition like $/GFX/Unit/Descriptor_Unit_A109BA_BEL/HeliApparence is THeliApparence(...)
     *
     * @return The parsed object definition
     * @throws NDFParseException If a parsing error occurs
     */
    private ObjectValue parseStandaloneObjectDefinition() throws NDFParseException {
        // Expect '$/Path/To/Object is TypeName'
        String resourcePath = expect(NDFToken.TokenType.RESOURCE_REF).getValue();
        expect(NDFToken.TokenType.IS);
        String typeName = expect(NDFToken.TokenType.IDENTIFIER).getValue();

        // Create the object
        ObjectValue object = NDFValue.createObject(typeName);
        object.setInstanceName(resourcePath);

        // Parse the object body
        expect(NDFToken.TokenType.OPEN_PAREN);
        parseObjectProperties(object);
        expect(NDFToken.TokenType.CLOSE_PAREN);

        return object;
    }

    /**
     * Parses the properties of an object
     *
     * @param object The object to add properties to
     * @throws NDFParseException If a parsing error occurs
     */
    private void parseObjectProperties(ObjectValue object) throws NDFParseException {
        // Parse properties until closing parenthesis
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN) {
            // Parse property name - handle different token types
            String propertyName;
            if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                propertyName = currentToken.getValue();
                advance();
            } else if (currentToken.getType() == NDFToken.TokenType.UNKNOWN) {
                // Handle unknown tokens as property names (might be special characters or keywords)
                propertyName = currentToken.getValue();
                advance();
            } else {
                // Skip unexpected tokens and try to continue
                advance();
                continue;
            }

            // Parse property value
            expect(NDFToken.TokenType.EQUALS);
            NDFValue propertyValue = parseValue();

            // Handle bitwise operations - preserve original syntax
            if (currentToken.getType() == NDFToken.TokenType.PIPE) {
                // For bitwise operations, we need to preserve the original syntax
                // Instead of creating artificial objects, store as a special string value
                StringBuilder bitwiseExpr = new StringBuilder();
                bitwiseExpr.append(propertyValue.toString());

                while (currentToken.getType() == NDFToken.TokenType.PIPE) {
                    bitwiseExpr.append(" | ");
                    advance(); // Consume the '|'

                    NDFValue rightOperand = parseValue();
                    bitwiseExpr.append(rightOperand.toString());
                }

                // Store as a special raw expression value
                propertyValue = NDFValue.createRawExpression(bitwiseExpr.toString());
            }

            // Check for comma after this property
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            if (hasComma) {
                advance(); // Consume the comma
            }

            // Add property to object with comma information
            object.setProperty(propertyName, propertyValue, hasComma);
        }
    }

    /**
     * Parses a value
     *
     * @return The parsed value
     * @throws NDFParseException If a parsing error occurs
     */
    private NDFValue parseValue() throws NDFParseException {
        switch (currentToken.getType()) {
            case STRING_LITERAL:
                String stringValue = currentToken.getValue();
                // Check if original text used double quotes
                boolean useDoubleQuotes = currentToken.getOriginalText().startsWith("\"");
                advance();
                return NDFValue.createString(stringValue, useDoubleQuotes);

            case NUMBER_LITERAL:
                double numberValue = Double.parseDouble(currentToken.getValue());
                String originalFormat = currentToken.getOriginalText();
                advance();
                return NDFValue.createNumber(numberValue, originalFormat);

            case BOOLEAN_LITERAL:
                boolean booleanValue = currentToken.getValue().equals("True");
                advance();
                return NDFValue.createBoolean(booleanValue);

            case OPEN_BRACKET:
                return parseArray();

            case OPEN_PAREN:
                // Handle tuple syntax like (key, value)
                return parseTuple();

            case MAP:
                return parseMap();

            case GUID:
                String guidValue = currentToken.getValue();
                advance();
                return NDFValue.createGUID(guidValue);

            case ENUM_VALUE:
                String enumValue = currentToken.getValue();
                String[] parts = enumValue.split("/");
                advance();
                return NDFValue.createEnum(parts[0], parts[1]);

            case TEMPLATE_REF:
                String templatePath = currentToken.getValue();
                advance();
                return NDFValue.createTemplateRef(templatePath);

            case RESOURCE_REF:
                String resourcePath = currentToken.getValue();
                advance();
                return NDFValue.createResourceRef(resourcePath);

            case IDENTIFIER:
                // This could be an object type or a named instance
                String identifier = currentToken.getValue();
                advance();

                // Skip any whitespace
                while (currentToken.getType() == NDFToken.TokenType.UNKNOWN &&
                       Character.isWhitespace(currentToken.getValue().charAt(0))) {
                    advance();
                }

                if (currentToken.getType() == NDFToken.TokenType.IS) {
                    // Named instance: 'name is Type(...)'
                    advance();

                    // Skip any whitespace after 'is'
                    while (currentToken.getType() == NDFToken.TokenType.UNKNOWN &&
                           Character.isWhitespace(currentToken.getValue().charAt(0))) {
                        advance();
                    }

                    // Check if the next token is a template reference
                    if (currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF) {
                        // Handle 'name is ~/TemplateName' - preserve original syntax
                        String refPath = currentToken.getValue();
                        advance();

                        // Create a template reference directly, don't wrap in artificial object
                        TemplateRefValue templateRef = (TemplateRefValue) NDFValue.createTemplateRef(refPath);
                        templateRef.setInstanceName(identifier);
                        return templateRef;
                    } else {
                        // Handle 'name is Type(...)'
                        String typeName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
                        ObjectValue object = parseObject(typeName);
                        object.setInstanceName(identifier);
                        return object;
                    }
                } else if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                    // This could be either:
                    // 1. Anonymous object: 'Type(...)'
                    // 2. Function call with parameters: 'Function(param=value)'

                    // Save the current position
                    int savedIndex = currentTokenIndex;
                    NDFToken savedToken = currentToken;

                    try {
                        // Try parsing as a function call with parameters
                        advance(); // Consume the '('

                        // Check if the next token is an identifier followed by '='
                        if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                            // Look ahead to see if there's an '=' after the identifier
                            int lookAheadIndex = currentTokenIndex + 1;
                            if (lookAheadIndex < tokens.size() &&
                                tokens.get(lookAheadIndex).getType() == NDFToken.TokenType.EQUALS) {

                                // This is a function call with named parameters
                                ObjectValue functionCall = NDFValue.createObject(identifier);

                                // Parse all named parameters
                                while (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                                    String paramName = currentToken.getValue();
                                    advance();
                                    expect(NDFToken.TokenType.EQUALS);
                                    NDFValue paramValue = parseValue();

                                    functionCall.setProperty(paramName, paramValue);

                                    // Skip whitespace tokens
                                    while (currentToken.getType() == NDFToken.TokenType.UNKNOWN &&
                                           Character.isWhitespace(currentToken.getValue().charAt(0))) {
                                        advance();
                                    }

                                    // If we hit the closing paren, we're done
                                    if (currentToken.getType() == NDFToken.TokenType.CLOSE_PAREN) {
                                        break;
                                    }
                                }

                                expect(NDFToken.TokenType.CLOSE_PAREN);
                                return functionCall;
                            }
                        }

                        // If we get here, it's not a function call with parameters
                        // Restore the position and try parsing as an anonymous object
                        currentTokenIndex = savedIndex;
                        currentToken = savedToken;
                    } catch (NDFParseException e) {
                        // Restore the position and try parsing as an anonymous object
                        currentTokenIndex = savedIndex;
                        currentToken = savedToken;
                    }

                    // Parse as an anonymous object
                    return parseObject(identifier);

                } else if (currentToken.getType() == NDFToken.TokenType.OPEN_BRACKET) {
                    // Handle function calls with array syntax like RGBA[0,0,0,0]
                    ArrayValue array = parseArray();

                    // Create a function call object to represent this
                    ObjectValue functionCall = NDFValue.createObject(identifier);
                    functionCall.setProperty("values", array);
                    return functionCall;

                } else {
                    // Just return the identifier as a template reference
                    return NDFValue.createTemplateRef(identifier);
                }

            default:
                throw new NDFParseException("Unexpected token: " + currentToken, currentToken);
        }
    }

    /**
     * Parses a tuple like (key, value)
     *
     * @return The parsed tuple as a TupleValue
     * @throws NDFParseException If a parsing error occurs
     */
    private TupleValue parseTuple() throws NDFParseException {
        expect(NDFToken.TokenType.OPEN_PAREN);
        TupleValue tuple = NDFValue.createTuple();

        // Parse tuple elements (typically 2 elements separated by comma)
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN) {
            NDFValue element = parseValue();

            // Check for comma after this element
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            if (hasComma) {
                advance(); // Consume the comma
            }

            // Add element with comma information
            tuple.add(element, hasComma);
        }

        expect(NDFToken.TokenType.CLOSE_PAREN);
        return tuple;
    }

    /**
     * Parses an array
     *
     * @return The parsed array
     * @throws NDFParseException If a parsing error occurs
     */
    private ArrayValue parseArray() throws NDFParseException {
        expect(NDFToken.TokenType.OPEN_BRACKET);
        ArrayValue array = NDFValue.createArray();

        // Parse array elements
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET) {
            NDFValue element = parseValue();

            // Check for comma after this element
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            if (hasComma) {
                advance(); // Consume the comma
            }

            // Add element with comma information
            array.add(element, hasComma);
        }

        expect(NDFToken.TokenType.CLOSE_BRACKET);
        return array;
    }

    /**
     * Parses a map
     *
     * @return The parsed map
     * @throws NDFParseException If a parsing error occurs
     */
    private MapValue parseMap() throws NDFParseException {
        expect(NDFToken.TokenType.MAP);
        expect(NDFToken.TokenType.OPEN_BRACKET);
        MapValue map = NDFValue.createMap();

        // Parse map entries
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET) {
            // Parse key-value pair: (key, value)
            expect(NDFToken.TokenType.OPEN_PAREN);
            NDFValue key = parseValue();
            expect(NDFToken.TokenType.COMMA); // This comma is required inside the parentheses
            NDFValue value = parseValue();
            expect(NDFToken.TokenType.CLOSE_PAREN);

            // Check for comma after this entry
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            if (hasComma) {
                advance(); // Consume the comma
            }

            // Add entry with comma information
            map.add(key, value, hasComma);
        }

        expect(NDFToken.TokenType.CLOSE_BRACKET);
        return map;
    }

    /**
     * Parses an object
     *
     * @param typeName The type name of the object
     * @return The parsed object
     * @throws NDFParseException If a parsing error occurs
     */
    private ObjectValue parseObject(String typeName) throws NDFParseException {
        expect(NDFToken.TokenType.OPEN_PAREN);
        ObjectValue object = NDFValue.createObject(typeName);

        // Parse object properties
        parseObjectProperties(object);

        expect(NDFToken.TokenType.CLOSE_PAREN);
        return object;
    }



    /**
     * Advances to the next token
     */
    private void advance() {
        currentTokenIndex++;
        if (currentTokenIndex < tokens.size()) {
            currentToken = tokens.get(currentTokenIndex);
        }
    }

    /**
     * Expects a token of the given type and advances to the next token
     *
     * @param type The expected token type
     * @return The expected token
     * @throws NDFParseException If the current token is not of the expected type
     */
    private NDFToken expect(NDFToken.TokenType type) throws NDFParseException {
        if (currentToken.getType() != type) {
            throw new NDFParseException(
                "Expected " + type + " but got " + currentToken.getType(),
                currentToken
            );
        }

        NDFToken token = currentToken;
        advance();
        return token;
    }

    /**
     * Exception thrown when a parsing error occurs
     */
    public static class NDFParseException extends Exception {
        private final NDFToken token;

        public NDFParseException(String message, NDFToken token) {
            super(message + " at line " + token.getLine() + ", column " + token.getColumn());
            this.token = token;
        }

        public NDFToken getToken() {
            return token;
        }
    }
}
