package com.warnomodmaker.parser;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.*;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class NDFParser {
    private final NDFTokenizer tokenizer;
    private List<NDFToken> tokens;
    private int currentTokenIndex;
    private NDFToken currentToken;
    private List<NDFToken> originalTokens;
    private NDFFileType fileType;
    public NDFParser(Reader reader) {
        this.tokenizer = new NDFTokenizer(reader);
        this.currentTokenIndex = 0;
        this.originalTokens = new ArrayList<>();
        this.fileType = NDFFileType.UNKNOWN;
    }

    public void setFileType(NDFFileType fileType) {
        this.fileType = fileType;
    }

    public List<NDFToken> getOriginalTokens() {
        return originalTokens;
    }

    public List<ObjectValue> parse() throws IOException, NDFParseException {
        tokens = tokenizer.tokenize();
        originalTokens = new ArrayList<>(tokens);
        currentTokenIndex = 0;
        currentToken = tokens.get(currentTokenIndex);
        List<ObjectValue> ndfObjects = new ArrayList<>();
        while (currentToken.getType() != NDFToken.TokenType.EOF) {
            try {

                if (currentToken.getType() == NDFToken.TokenType.COMMENT) {
                    advance();
                    continue;
                }

                if (currentToken.getType() == NDFToken.TokenType.EXPORT) {
                    ndfObjects.add(parseExportedDescriptor());
                } else if (currentToken.getType() == NDFToken.TokenType.RESOURCE_REF) {

                    ndfObjects.add(parseStandaloneObjectDefinition());
                } else if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {

                    if (isDescriptorDefinition()) {
                        ndfObjects.add(parseDescriptorDefinition());
                    } else if (isSimpleAssignment()) {
                        ndfObjects.add(parseNonExportedDefinition());
                    } else {
                        ndfObjects.add(parseNonExportedDefinition());
                    }
                } else {
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

        return ndfObjects;
    }


    private ObjectValue parseExportedDescriptor() throws NDFParseException {
        // Capture export token index before advancing
        int exportTokenIndex = currentTokenIndex;

        // Expect 'export DescriptorName is TypeName'
        expect(NDFToken.TokenType.EXPORT);
        String descriptorName;
        if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
            descriptorName = currentToken.getValue();
            advance();
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
        NDFToken typeNameToken = expect(NDFToken.TokenType.IDENTIFIER);
        String typeName = typeNameToken.getValue();
        int typeNameTokenIndex = currentTokenIndex - 1; // -1 because we just advanced past it
        ObjectValue descriptor = NDFValue.createObject(typeName);
        descriptor.setInstanceName(descriptorName);
        descriptor.setExported(true); // Mark as exported
        // Use the export token index to include the entire export declaration
        if (exportTokenIndex >= 0) {
            descriptor.setOriginalTokenStartIndex(exportTokenIndex);
        }
        NDFToken openParenToken = currentToken;
        expect(NDFToken.TokenType.OPEN_PAREN);
        descriptor.setOriginalOpeningParen(openParenToken.getExactText());

        parseObjectProperties(descriptor);

        NDFToken closeParenToken = currentToken;
        expect(NDFToken.TokenType.CLOSE_PAREN);
        descriptor.setOriginalClosingParen(closeParenToken.getExactText());

        // Store token range end
        int closeParenTokenIndex = currentTokenIndex - 1; // -1 because we already advanced past it
        descriptor.setOriginalTokenEndIndex(closeParenTokenIndex);

        return descriptor;
    }


    private boolean isDescriptorDefinition() {
        // Look ahead to check if we have "IDENTIFIER is IDENTIFIER" pattern
        if (currentTokenIndex + 2 < tokens.size()) {
            NDFToken nextToken = tokens.get(currentTokenIndex + 1);
            NDFToken thirdToken = tokens.get(currentTokenIndex + 2);
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


    private boolean isSimpleAssignment() {
        // Look ahead to check if we have "IDENTIFIER is VALUE" pattern
        if (currentTokenIndex + 2 < tokens.size()) {
            NDFToken nextToken = tokens.get(currentTokenIndex + 1);
            NDFToken thirdToken = tokens.get(currentTokenIndex + 2);

            if (nextToken.getType() == NDFToken.TokenType.IS) {
                return thirdToken.getType() == NDFToken.TokenType.NUMBER_LITERAL ||
                       thirdToken.getType() == NDFToken.TokenType.STRING_LITERAL ||
                       thirdToken.getType() == NDFToken.TokenType.OPEN_BRACKET ||
                       (thirdToken.getType() == NDFToken.TokenType.IDENTIFIER &&
                        !isDescriptorDefinition()); // Not a descriptor type
            }
        }
        return false;
    }


    private ObjectValue parseDescriptorDefinition() throws NDFParseException {
        // TOKEN RANGE PRESERVATION - CAPTURE START TOKEN INDEX
        int startTokenIndex = currentTokenIndex;
        String descriptorName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        expect(NDFToken.TokenType.IS);
        String typeName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        ObjectValue descriptor = NDFValue.createObject(typeName);
        descriptor.setInstanceName(descriptorName);

        // TOKEN RANGE PRESERVATION - SET START INDEX
        descriptor.setOriginalTokenStartIndex(startTokenIndex);
        expect(NDFToken.TokenType.OPEN_PAREN);
        parseObjectProperties(descriptor);
        expect(NDFToken.TokenType.CLOSE_PAREN);

        // TOKEN RANGE PRESERVATION - SET END INDEX
        int endTokenIndex = currentTokenIndex - 1; // -1 because we already advanced past the closing paren
        descriptor.setOriginalTokenEndIndex(endTokenIndex);

        return descriptor;
    }


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


    private ObjectValue parseNonExportedDefinition() throws NDFParseException {
        // Capture start token index
        int startTokenIndex = currentTokenIndex;

        // Expect 'IdentifierName is Value' or other patterns
        String identifierName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        if (currentToken.getType() == NDFToken.TokenType.IS) {
            advance(); // consume 'is'
            NDFValue value = parseValue();
            ObjectValue definition = NDFValue.createObject("ConstantDefinition");
            definition.setInstanceName(identifierName);
            definition.setProperty("Value", value);

            definition.setOriginalTokenStartIndex(startTokenIndex);
            int endTokenIndex = currentTokenIndex - 1;
            definition.setOriginalTokenEndIndex(endTokenIndex);

            return definition;
        } else if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
            // This could be:
            // 1. "unnamed TypeName(...)" - object definition with unnamed instance
            // 2. "IdentifierName TypeName" - type declaration
            String secondIdentifier = currentToken.getValue();
            advance();
            if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                // This is an object definition: "instanceName TypeName(...)"
                // For "unnamed TypeName(...)", instanceName will be "unnamed"
                ObjectValue definition = parseObject(secondIdentifier, startTokenIndex);
                definition.setInstanceName(identifierName);

                return definition;
            } else {
                // This is a type declaration or other construct
                ObjectValue definition = NDFValue.createObject("TypeDeclaration");
                definition.setInstanceName(identifierName);
                definition.setProperty("Type", NDFValue.createString(secondIdentifier));

                // Skip any remaining tokens until we find a reasonable stopping point
                while (currentToken.getType() != NDFToken.TokenType.EOF &&
                       currentToken.getType() != NDFToken.TokenType.EXPORT &&
                       currentToken.getType() != NDFToken.TokenType.IDENTIFIER) {
                    advance();
                }

                definition.setOriginalTokenStartIndex(startTokenIndex);
                int endTokenIndex = currentTokenIndex - 1;
                definition.setOriginalTokenEndIndex(endTokenIndex);

                return definition;
            }
        } else {
            // Unknown pattern, create a placeholder and skip CONSERVATIVELY
            ObjectValue definition = NDFValue.createObject("UnknownDefinition");
            definition.setInstanceName(identifierName);

            // Skip tokens conservatively to avoid consuming entire file
            int parenDepth = 0;
            boolean foundOpenParen = false;
            int tokensSkipped = 0;
            int maxTokensToSkip = 10000;

            while (currentToken.getType() != NDFToken.TokenType.EOF &&
                   tokensSkipped < maxTokensToSkip) {

                if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                    parenDepth++;
                    foundOpenParen = true;
                } else if (currentToken.getType() == NDFToken.TokenType.CLOSE_PAREN) {
                    parenDepth--;
                    if (foundOpenParen && parenDepth == 0) {
                        // Found the end of this object
                        advance(); // Consume the closing paren
                        break;
                    }
                } else if (currentToken.getType() == NDFToken.TokenType.EXPORT && !foundOpenParen) {
                    // If we haven't found an opening paren yet and we hit an export, stop
                    break;
                }

                advance();
                tokensSkipped++;
            }

            definition.setOriginalTokenStartIndex(startTokenIndex);
            int endTokenIndex = currentTokenIndex - 1;
            definition.setOriginalTokenEndIndex(endTokenIndex);

            return definition;
        }
    }


    private ObjectValue parseStandaloneObjectDefinition() throws NDFParseException {
        // Expect '$/Path/To/Object is TypeName'
        String resourcePath = expect(NDFToken.TokenType.RESOURCE_REF).getValue();
        expect(NDFToken.TokenType.IS);
        String typeName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        ObjectValue object = NDFValue.createObject(typeName);
        object.setInstanceName(resourcePath);
        expect(NDFToken.TokenType.OPEN_PAREN);
        parseObjectProperties(object);
        expect(NDFToken.TokenType.CLOSE_PAREN);

        return object;
    }


    private void parseObjectProperties(ObjectValue object) throws NDFParseException {
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN) {
            String propertyName;
            if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                propertyName = currentToken.getValue();
                advance();
            } else if (currentToken.getType() == NDFToken.TokenType.UNKNOWN) {
                propertyName = currentToken.getValue();
                advance();
            } else {
                // Skip unexpected tokens and try to continue
                advance();
                continue;
            }

            // CAPTURE ORIGINAL WHITESPACE/INDENTATION BEFORE PROPERTY
            String propertyPrefix = currentToken.getLeadingWhitespace();

            // CAPTURE ORIGINAL EQUALS SIGN WITH EXACT FORMATTING
            NDFToken equalsToken = currentToken;
            expect(NDFToken.TokenType.EQUALS);
            String originalEquals = equalsToken.getExactText();
            NDFValue propertyValue;
            try {
                propertyValue = parseValue();
            } catch (NDFParseException e) {
                // Log the error and skip this property
                System.err.println("Warning: Failed to parse property '" + propertyName + "': " + e.getMessage());
                System.err.println("  Skipping property and continuing...");

                // Skip tokens until we find a comma or closing parenthesis
                while (currentToken.getType() != NDFToken.TokenType.COMMA &&
                       currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN &&
                       currentToken.getType() != NDFToken.TokenType.EOF) {
                    advance();
                }

                // If we found a comma, consume it and continue
                if (currentToken.getType() == NDFToken.TokenType.COMMA) {
                    advance();
                }

                continue; // Skip this property and continue with the next one
            }
            if (currentToken.getType() == NDFToken.TokenType.PIPE) {
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
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            String propertySuffix = "";

            if (hasComma) {
                // CAPTURE COMMA AND ANY TRAILING WHITESPACE/NEWLINES
                NDFToken commaToken = currentToken;
                advance(); // Consume the comma
                propertySuffix = commaToken.getExactText();
            } else {
                // CAPTURE ANY TRAILING WHITESPACE/NEWLINES AFTER PROPERTY
                propertySuffix = currentToken.getLeadingWhitespace();
            }

            // Store original formatting for this property
            object.setOriginalPropertyPrefix(propertyName, propertyPrefix);
            object.setOriginalPropertyEquals(propertyName, originalEquals);
            object.setOriginalPropertySuffix(propertyName, propertySuffix);
            object.setProperty(propertyName, propertyValue, hasComma);

            // Debug logging for BaseHitValueModifiers
            if ("BaseHitValueModifiers".equals(propertyName)) {
                System.err.println("DEBUG: Successfully stored BaseHitValueModifiers property");
                System.err.println("  Object: " + object.getInstanceName());
                System.err.println("  Value type: " + propertyValue.getClass().getSimpleName());
                System.err.println("  Properties count after storage: " + object.getProperties().size());
            }
        }
    }


    private NDFValue parseValue() throws NDFParseException {
        switch (currentToken.getType()) {
            case STRING_LITERAL:
                String stringValue = currentToken.getValue();
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
                // TOKEN RANGE PRESERVATION - CAPTURE IDENTIFIER TOKEN INDEX BEFORE ADVANCING
                int identifierTokenIndex = currentTokenIndex;
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
                    if (currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF) {
                        String refPath = currentToken.getValue();
                        advance();
                        TemplateRefValue templateRef = (TemplateRefValue) NDFValue.createTemplateRef(refPath);
                        templateRef.setInstanceName(identifier);
                        return templateRef;
                    } else {
                        NDFToken typeNameToken = expect(NDFToken.TokenType.IDENTIFIER);
                        String typeName = typeNameToken.getValue();
                        // PERFECT FIX: Use identifierTokenIndex (instance name) as start of token range
                        // This ensures the token range includes "instanceName is typeName(...)"
                        ObjectValue object = parseObject(typeName, identifierTokenIndex);
                        object.setInstanceName(identifier);
                        return object;
                    }
                } else if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                    // This could be either:
                    // 1. Anonymous object: 'Type(...)'
                    // 2. Function call with parameters: 'Function(param=value)'
                    int savedIndex = currentTokenIndex;
                    NDFToken savedToken = currentToken;

                    try {
                        // Try parsing as a function call with parameters
                        advance(); // Consume the '('
                        if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                            // Look ahead to see if there's an '=' after the identifier
                            int lookAheadIndex = currentTokenIndex + 1;
                            if (lookAheadIndex < tokens.size() &&
                                tokens.get(lookAheadIndex).getType() == NDFToken.TokenType.EQUALS) {

                                // This is a function call with named parameters
                                ObjectValue functionCall = NDFValue.createObject(identifier);

                                // TOKEN RANGE PRESERVATION - SIMPLER AND MORE RELIABLE APPROACH
                                // Store the start token index (the identifier token we captured earlier)
                                functionCall.setOriginalTokenStartIndex(identifierTokenIndex);

                                // CAPTURE ORIGINAL OPENING PARENTHESIS FORMATTING
                                NDFToken openParenToken = tokens.get(savedIndex);
                                functionCall.setOriginalOpeningParen(openParenToken.getExactText());
                                while (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                                    // CAPTURE ORIGINAL WHITESPACE/SPACING BEFORE PARAMETER
                                    String paramPrefix = currentToken.getLeadingWhitespace();

                                    String paramName = currentToken.getValue();
                                    NDFToken paramNameToken = currentToken;
                                    advance();

                                    // CAPTURE ORIGINAL EQUALS SIGN WITH EXACT FORMATTING
                                    NDFToken equalsToken = currentToken;
                                    expect(NDFToken.TokenType.EQUALS);
                                    String originalEquals = equalsToken.getExactText();

                                    // CAPTURE THE TOKEN BEFORE PARSING THE VALUE (to get trailing whitespace)
                                    int valueStartIndex = currentTokenIndex;
                                    NDFValue paramValue = parseValue();

                                    // CAPTURE ORIGINAL SPACING AFTER PARAMETER VALUE
                                    // The space is in the trailing whitespace of the last token of the parameter value
                                    String paramSuffix = "";
                                    if (valueStartIndex < tokens.size()) {
                                        // Look at the token just before the current position
                                        int lastValueTokenIndex = currentTokenIndex - 1;
                                        if (lastValueTokenIndex >= 0 && lastValueTokenIndex < tokens.size()) {
                                            NDFToken lastValueToken = tokens.get(lastValueTokenIndex);
                                            paramSuffix = lastValueToken.getTrailingWhitespace();
                                        }
                                    }

                                    // Store original formatting for this parameter
                                    functionCall.setOriginalPropertyPrefix(paramName, paramPrefix);
                                    functionCall.setOriginalPropertyEquals(paramName, originalEquals);
                                    functionCall.setOriginalPropertySuffix(paramName, paramSuffix);

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

                                // CAPTURE ORIGINAL CLOSING PARENTHESIS FORMATTING
                                NDFToken closeParenToken = currentToken;
                                expect(NDFToken.TokenType.CLOSE_PAREN);
                                functionCall.setOriginalClosingParen(closeParenToken.getExactText());

                                // Store the end token index (the closing parenthesis token)
                                int functionEndIndex = currentTokenIndex - 1; // -1 because we already advanced past the closing paren
                                functionCall.setOriginalTokenEndIndex(functionEndIndex);

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
                    return parseObject(identifier, identifierTokenIndex);

                } else if (currentToken.getType() == NDFToken.TokenType.OPEN_BRACKET) {
                    ArrayValue array = parseArray();
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


    private TupleValue parseTuple() throws NDFParseException {
        expect(NDFToken.TokenType.OPEN_PAREN);
        TupleValue tuple = NDFValue.createTuple();
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN) {
            NDFValue element = parseValue();
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            if (hasComma) {
                advance(); // Consume the comma
            }
            tuple.add(element, hasComma);
        }

        expect(NDFToken.TokenType.CLOSE_PAREN);
        return tuple;
    }


    private ArrayValue parseArray() throws NDFParseException {
        // CAPTURE ORIGINAL OPENING BRACKET WITH EXACT FORMATTING
        NDFToken openBracketToken = currentToken;
        expect(NDFToken.TokenType.OPEN_BRACKET);

        ArrayValue array = NDFValue.createArray();

        // Store original opening bracket formatting (including any trailing whitespace/newlines)
        array.setOriginalOpeningBracket(openBracketToken.getExactText());

        // Determine if this is originally multi-line by checking if opening bracket has newlines
        boolean isMultiLine = openBracketToken.getTrailingWhitespace().contains("\n");
        array.setOriginallyMultiLine(isMultiLine);

        int elementIndex = 0;
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET) {
            // CAPTURE WHITESPACE/INDENTATION BEFORE ELEMENT
            String elementPrefix = currentToken.getLeadingWhitespace();
            array.setOriginalElementPrefix(elementIndex, elementPrefix);

            NDFValue element = parseValue();
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            String elementSuffix = "";

            if (hasComma) {
                // CAPTURE COMMA AND ANY TRAILING WHITESPACE/NEWLINES
                NDFToken commaToken = currentToken;
                advance(); // Consume the comma
                elementSuffix = commaToken.getExactText();
            } else {
                // CAPTURE ANY TRAILING WHITESPACE/NEWLINES AFTER ELEMENT
                elementSuffix = currentToken.getLeadingWhitespace();
            }

            array.setOriginalElementSuffix(elementIndex, elementSuffix);
            array.add(element, hasComma);
            elementIndex++;
        }

        // CAPTURE ORIGINAL CLOSING BRACKET WITH EXACT FORMATTING
        NDFToken closeBracketToken = currentToken;
        expect(NDFToken.TokenType.CLOSE_BRACKET);

        // Store original closing bracket formatting (including any leading whitespace/indentation)
        array.setOriginalClosingBracket(closeBracketToken.getExactText());

        return array;
    }


    private MapValue parseMap() throws NDFParseException {
        expect(NDFToken.TokenType.MAP);
        expect(NDFToken.TokenType.OPEN_BRACKET);
        MapValue map = NDFValue.createMap();
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET) {
            expect(NDFToken.TokenType.OPEN_PAREN);
            NDFValue key = parseValue();
            expect(NDFToken.TokenType.COMMA);
            NDFValue value = parseValue();
            expect(NDFToken.TokenType.CLOSE_PAREN);
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            if (hasComma) {
                advance(); // Consume the comma
            }
            map.add(key, value, hasComma);
        }

        expect(NDFToken.TokenType.CLOSE_BRACKET);
        return map;
    }


    private ObjectValue parseObject(String typeName, int typeNameTokenIndex) throws NDFParseException {
        // CAPTURE ORIGINAL OPENING PARENTHESIS WITH EXACT FORMATTING
        NDFToken openParenToken = currentToken;
        expect(NDFToken.TokenType.OPEN_PAREN);

        ObjectValue object = NDFValue.createObject(typeName);

        // Store token range start (type name token)
        if (typeNameTokenIndex >= 0) {
            object.setOriginalTokenStartIndex(typeNameTokenIndex);
        }

        // Store original opening parenthesis formatting
        object.setOriginalOpeningParen(openParenToken.getExactText());
        parseObjectProperties(object);

        // CAPTURE ORIGINAL CLOSING PARENTHESIS WITH EXACT FORMATTING
        NDFToken closeParenToken = currentToken;
        expect(NDFToken.TokenType.CLOSE_PAREN);

        // Store original closing parenthesis formatting
        object.setOriginalClosingParen(closeParenToken.getExactText());

        // Store token range end (closing parenthesis token)
        int closeParenTokenIndex = currentTokenIndex - 1; // -1 because we already advanced past it
        object.setOriginalTokenEndIndex(closeParenTokenIndex);

        return object;
    }

    // REMOVED: findTypeNameTokenIndex() method - no longer needed
    // Now passing token index directly to parseObject()


    private void advance() {
        currentTokenIndex++;
        if (currentTokenIndex < tokens.size()) {
            currentToken = tokens.get(currentTokenIndex);
        }
    }


    private void skipComments() {
        while (currentTokenIndex < tokens.size() &&
               currentToken.getType() == NDFToken.TokenType.COMMENT) {
            currentTokenIndex++;
            if (currentTokenIndex < tokens.size()) {
                currentToken = tokens.get(currentTokenIndex);
            }
        }
    }


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
