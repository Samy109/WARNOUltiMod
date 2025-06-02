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

        // STANDALONE UNITEDESCRIPTOR FUNCTIONALITY - Route to specialized parser
        if (fileType == NDFFileType.UNITE_DESCRIPTOR) {
            return parseUniteDescriptor();
        }

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
            // CAPTURE ORIGINAL WHITESPACE/INDENTATION BEFORE PROPERTY
            String propertyPrefix = currentToken.getLeadingWhitespace();

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

                // CRITICAL FIX: Include comma + whitespace that follows the comma
                String commaText = commaToken.getExactText();
                String followingWhitespace = currentToken.getLeadingWhitespace();
                propertySuffix = commaText + followingWhitespace;
            } else {
                // CAPTURE ANY TRAILING WHITESPACE/NEWLINES AFTER PROPERTY
                propertySuffix = currentToken.getLeadingWhitespace();
            }

            // Store original formatting for this property
            object.setOriginalPropertyPrefix(propertyName, propertyPrefix);
            object.setOriginalPropertyEquals(propertyName, originalEquals);
            object.setOriginalPropertySuffix(propertyName, propertySuffix);
            object.setProperty(propertyName, propertyValue, hasComma);


        }
    }


    private NDFValue parseValue() throws NDFParseException {
        // ENHANCED MEMORY MODEL: Capture formatting information for all values
        String valuePrefix = currentToken.getLeadingWhitespace();

        switch (currentToken.getType()) {
            case STRING_LITERAL:
                String stringValue = currentToken.getValue();
                boolean useDoubleQuotes = currentToken.getOriginalText().startsWith("\"");
                String stringTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue stringVal = NDFValue.createString(stringValue, useDoubleQuotes);
                stringVal.setOriginalFormatting(valuePrefix, stringTrailingWhitespace);
                return stringVal;

            case NUMBER_LITERAL:
                double numberValue = Double.parseDouble(currentToken.getValue());
                String originalFormat = currentToken.getOriginalText();
                String numberTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue numberVal = NDFValue.createNumber(numberValue, originalFormat);
                numberVal.setOriginalFormatting(valuePrefix, numberTrailingWhitespace);
                return numberVal;

            case BOOLEAN_LITERAL:
                boolean booleanValue = currentToken.getValue().equals("True");
                String booleanTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue booleanVal = NDFValue.createBoolean(booleanValue);
                booleanVal.setOriginalFormatting(valuePrefix, booleanTrailingWhitespace);
                return booleanVal;

            case OPEN_BRACKET:
                NDFValue arrayVal = parseArray();
                arrayVal.setOriginalFormatting(valuePrefix, "");
                return arrayVal;

            case OPEN_PAREN:
                NDFValue tupleVal = parseTuple();
                tupleVal.setOriginalFormatting(valuePrefix, "");
                return tupleVal;

            case MAP:
                NDFValue mapVal = parseMap();
                mapVal.setOriginalFormatting(valuePrefix, "");
                return mapVal;

            case GUID:
                String guidValue = currentToken.getValue();
                String guidTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue guidVal = NDFValue.createGUID(guidValue);
                guidVal.setOriginalFormatting(valuePrefix, guidTrailingWhitespace);
                return guidVal;

            case ENUM_VALUE:
                String enumValue = currentToken.getValue();
                String[] parts = enumValue.split("/");
                String enumTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue enumVal = NDFValue.createEnum(parts[0], parts[1]);
                enumVal.setOriginalFormatting(valuePrefix, enumTrailingWhitespace);
                return enumVal;

            case TEMPLATE_REF:
                String templatePath = currentToken.getValue();
                String templateTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue templateVal = NDFValue.createTemplateRef(templatePath);
                templateVal.setOriginalFormatting(valuePrefix, templateTrailingWhitespace);
                return templateVal;

            case RESOURCE_REF:
                String resourcePath = currentToken.getValue();
                String resourceTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue resourceVal = NDFValue.createResourceRef(resourcePath);
                resourceVal.setOriginalFormatting(valuePrefix, resourceTrailingWhitespace);
                return resourceVal;

            case IDENTIFIER:
                // This could be an object type or a named instance
                String identifier = currentToken.getValue();
                String identifierTrailingWhitespace = currentToken.getTrailingWhitespace();
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
                        String refTrailingWhitespace = currentToken.getTrailingWhitespace();
                        advance();
                        TemplateRefValue templateRef = (TemplateRefValue) NDFValue.createTemplateRef(refPath);
                        templateRef.setInstanceName(identifier);
                        templateRef.setOriginalFormatting(valuePrefix, refTrailingWhitespace);
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
                    NDFValue templateRef = NDFValue.createTemplateRef(identifier);
                    templateRef.setOriginalFormatting(valuePrefix, identifierTrailingWhitespace);
                    return templateRef;
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

                // CRITICAL FIX: Include comma + whitespace that follows the comma
                String commaText = commaToken.getExactText();
                String followingWhitespace = currentToken.getLeadingWhitespace();
                elementSuffix = commaText + followingWhitespace;
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
        skipWhitespaceAndComments();

        if (currentToken.getType() != type) {
            // Special handling for common formatting issues
            if (type == NDFToken.TokenType.EQUALS && currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                // Look ahead to see if there's an EQUALS token nearby
                int lookAheadIndex = currentTokenIndex + 1;
                while (lookAheadIndex < tokens.size()) {
                    NDFToken lookAheadToken = tokens.get(lookAheadIndex);
                    if (lookAheadToken.getType() == NDFToken.TokenType.EQUALS) {
                        // Found EQUALS token, skip the intervening tokens
                        while (currentTokenIndex < lookAheadIndex) {
                            advance();
                        }
                        break;
                    } else if (lookAheadToken.getType() == NDFToken.TokenType.IDENTIFIER ||
                               isWhitespaceToken(lookAheadToken) ||
                               lookAheadToken.getType() == NDFToken.TokenType.COMMENT) {
                        // Continue looking
                        lookAheadIndex++;
                    } else {
                        // Hit something else, stop looking
                        break;
                    }
                }
            } else if (type == NDFToken.TokenType.IS && currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                // Look ahead to see if there's an IS token nearby
                int lookAheadIndex = currentTokenIndex + 1;
                while (lookAheadIndex < tokens.size()) {
                    NDFToken lookAheadToken = tokens.get(lookAheadIndex);
                    if (lookAheadToken.getType() == NDFToken.TokenType.IS) {
                        // Found IS token, skip the intervening tokens
                        while (currentTokenIndex < lookAheadIndex) {
                            advance();
                        }
                        break;
                    } else if (lookAheadToken.getType() == NDFToken.TokenType.IDENTIFIER ||
                               isWhitespaceToken(lookAheadToken) ||
                               lookAheadToken.getType() == NDFToken.TokenType.COMMENT) {
                        // Continue looking
                        lookAheadIndex++;
                    } else {
                        // Hit something else, stop looking
                        break;
                    }
                }
            }

            // Check again after potential recovery
            if (currentToken.getType() != type) {
                throw new NDFParseException(
                    "Expected " + type + " but got " + currentToken.getType(),
                    currentToken
                );
            }
        }

        NDFToken token = currentToken;
        advance();
        return token;
    }

    private void skipWhitespaceAndComments() {
        while (currentTokenIndex < tokens.size() &&
               (isWhitespaceToken(currentToken) || currentToken.getType() == NDFToken.TokenType.COMMENT)) {
            advance();
        }
    }

    private boolean isWhitespaceToken(NDFToken token) {
        return token.getType() == NDFToken.TokenType.UNKNOWN &&
               (token.getValue().trim().isEmpty() || token.getValue().matches("\\s+"));
    }


    private List<ObjectValue> parseUniteDescriptor() throws IOException, NDFParseException {
        List<ObjectValue> ndfObjects = new ArrayList<>();

        while (currentToken.getType() != NDFToken.TokenType.EOF) {
            try {
                if (currentToken.getType() == NDFToken.TokenType.COMMENT) {
                    advance();
                    continue;
                }

                if (currentToken.getType() == NDFToken.TokenType.EXPORT) {
                    ndfObjects.add(parseUniteDescriptorExportedObject());
                } else {
                    advance(); // Skip any other tokens
                }
            } catch (NDFParseException e) {
                System.err.println("Warning: UniteDescriptor parsing error at line " + currentToken.getLine() + ": " + e.getMessage());

                // Skip to next export
                while (currentToken.getType() != NDFToken.TokenType.EOF &&
                       currentToken.getType() != NDFToken.TokenType.EXPORT) {
                    advance();
                }
            }
        }

        return ndfObjects;
    }

    /**
     * Parse exported objects in UniteDescriptor.ndf (TEntityDescriptor objects) with ENHANCED MEMORY MODEL
     */
    private ObjectValue parseUniteDescriptorExportedObject() throws NDFParseException {
        int exportTokenIndex = currentTokenIndex;

        expect(NDFToken.TokenType.EXPORT);
        String descriptorName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        expect(NDFToken.TokenType.IS);
        String typeName = expect(NDFToken.TokenType.IDENTIFIER).getValue();

        ObjectValue descriptor = NDFValue.createObject(typeName);
        descriptor.setInstanceName(descriptorName);
        descriptor.setExported(true);
        descriptor.setOriginalTokenStartIndex(exportTokenIndex);

        // ENHANCED MEMORY MODEL: CAPTURE ORIGINAL OPENING PARENTHESIS WITH EXACT FORMATTING
        NDFToken openParenToken = currentToken;
        expect(NDFToken.TokenType.OPEN_PAREN);
        descriptor.setOriginalOpeningParen(openParenToken.getExactText());

        parseUniteDescriptorObjectProperties(descriptor);

        // ENHANCED MEMORY MODEL: CAPTURE ORIGINAL CLOSING PARENTHESIS WITH EXACT FORMATTING
        NDFToken closeParenToken = currentToken;
        expect(NDFToken.TokenType.CLOSE_PAREN);
        descriptor.setOriginalClosingParen(closeParenToken.getExactText());

        int endTokenIndex = currentTokenIndex - 1;
        descriptor.setOriginalTokenEndIndex(endTokenIndex);

        return descriptor;
    }

    /**
     * Parse object properties in UniteDescriptor.ndf with ENHANCED MEMORY MODEL formatting capture
     */
    private void parseUniteDescriptorObjectProperties(ObjectValue object) throws NDFParseException {
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN) {
            if (currentToken.getType() == NDFToken.TokenType.COMMENT) {
                advance();
                continue;
            }

            // ENHANCED MEMORY MODEL: CAPTURE ORIGINAL WHITESPACE/INDENTATION BEFORE PROPERTY
            String propertyPrefix = currentToken.getLeadingWhitespace();

            // UNITEDESCRIPTOR NORMALIZATION: Ensure consistent 4-space indentation
            if (!propertyPrefix.isEmpty()) {
                // Normalize to consistent 4-space indentation for UniteDescriptor files
                propertyPrefix = "    ";
            }

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

            // ENHANCED MEMORY MODEL: CAPTURE ORIGINAL EQUALS SIGN WITH EXACT FORMATTING
            NDFToken equalsToken = currentToken;
            expect(NDFToken.TokenType.EQUALS);
            String originalEquals = equalsToken.getExactText();

            NDFValue propertyValue;
            try {
                if ("ModulesDescriptors".equals(propertyName)) {
                    // Special handling for ModulesDescriptors array
                    propertyValue = parseUniteDescriptorModulesArray();
                } else {
                    // Use completely standalone UniteDescriptor value parsing
                    propertyValue = parseUniteDescriptorValueStandalone();
                }

                // ENHANCED MEMORY MODEL: CAPTURE PROPERTY SUFFIX WITH COMMA + WHITESPACE
                boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
                String propertySuffix = "";

                if (hasComma) {
                    // CAPTURE COMMA AND ANY TRAILING WHITESPACE/NEWLINES
                    NDFToken commaToken = currentToken;
                    advance(); // Consume the comma

                    // CRITICAL FIX: Include comma + whitespace that follows the comma
                    String commaText = commaToken.getExactText();
                    String followingWhitespace = currentToken.getLeadingWhitespace();
                    propertySuffix = commaText + followingWhitespace;
                } else {
                    // UNITEDESCRIPTOR FIX: In UniteDescriptor files, properties don't have commas
                    // Each property should end with a newline + indentation for the next property
                    // The suffix should be the newline that separates this property from the next

                    // Look ahead to see if there's another property coming
                    if (currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN) {
                        // There's another property coming, so we need a newline + indentation
                        String nextPropertyWhitespace = currentToken.getLeadingWhitespace();
                        if (!nextPropertyWhitespace.isEmpty() && nextPropertyWhitespace.contains("\n")) {
                            // NORMALIZE: Use consistent single newline + 4-space indentation
                            // Don't preserve excessive whitespace that causes formatting issues
                            propertySuffix = "\n    ";
                        } else {
                            // Default: newline + 4-space indentation
                            propertySuffix = "\n    ";
                        }
                    } else {
                        // This is the last property, normalize to single newline before closing paren
                        propertySuffix = "\n";
                    }
                }

                // ENHANCED MEMORY MODEL: Store original formatting for this property
                object.setOriginalPropertyPrefix(propertyName, propertyPrefix);
                object.setOriginalPropertyEquals(propertyName, originalEquals);
                object.setOriginalPropertySuffix(propertyName, propertySuffix);
                object.setProperty(propertyName, propertyValue, hasComma);

            } catch (NDFParseException e) {
                // Log the error and skip this property (same as standard parser)
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
            }
        }
    }

    /**
     * Parse ModulesDescriptors array with special handling for named object assignments
     */
    private ArrayValue parseUniteDescriptorModulesArray() throws NDFParseException {
        NDFToken openBracketToken = currentToken;
        expect(NDFToken.TokenType.OPEN_BRACKET);

        ArrayValue array = NDFValue.createArray();
        array.setOriginalOpeningBracket(openBracketToken.getExactText());

        boolean isMultiLine = openBracketToken.getTrailingWhitespace().contains("\n");
        array.setOriginallyMultiLine(isMultiLine);

        int elementIndex = 0;
        while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET) {
            String elementPrefix = currentToken.getLeadingWhitespace();
            array.setOriginalElementPrefix(elementIndex, elementPrefix);

            NDFValue element = parseUniteDescriptorModuleElement();
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            String elementSuffix = "";

            if (hasComma) {
                NDFToken commaToken = currentToken;
                advance();

                // CRITICAL FIX: Include comma + whitespace that follows the comma
                String commaText = commaToken.getExactText();
                String followingWhitespace = currentToken.getLeadingWhitespace();
                elementSuffix = commaText + followingWhitespace;
            } else {
                elementSuffix = currentToken.getLeadingWhitespace();
            }

            array.setOriginalElementSuffix(elementIndex, elementSuffix);
            array.add(element, hasComma);
            elementIndex++;
        }

        NDFToken closeBracketToken = currentToken;
        expect(NDFToken.TokenType.CLOSE_BRACKET);
        array.setOriginalClosingBracket(closeBracketToken.getExactText());

        return array;
    }

    /**
     * Parse individual module elements - handles the unique UniteDescriptor patterns:
     * 1. Simple template refs: ~/TargetManagerModuleSelector
     * 2. Simple objects: TTagsModuleDescriptor(...)
     * 3. Named assignments with objects: ApparenceModel is VehicleApparenceModuleDescriptor(...)
     * 4. Named assignments with template refs: FacingInfos is ~/FacingInfosModuleDescriptor
     */
    private NDFValue parseUniteDescriptorModuleElement() throws NDFParseException {
        if (currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF) {
            // Simple template reference
            String templatePath = currentToken.getValue();
            advance();
            return NDFValue.createTemplateRef(templatePath);
        } else if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
            String firstIdentifier = currentToken.getValue();
            int identifierTokenIndex = currentTokenIndex;
            advance();

            if (currentToken.getType() == NDFToken.TokenType.IS) {
                // Named assignment: "Name is Type(...)" or "Name is ~/TemplateRef"
                advance(); // consume 'is'

                if (currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF) {
                    // Named assignment with template ref: "FacingInfos is ~/FacingInfosModuleDescriptor"
                    String templatePath = currentToken.getValue();
                    advance();
                    NDFValue.TemplateRefValue templateRef = (NDFValue.TemplateRefValue) NDFValue.createTemplateRef(templatePath);
                    templateRef.setInstanceName(firstIdentifier);
                    return templateRef;
                } else if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                    // Named assignment with object: "ApparenceModel is VehicleApparenceModuleDescriptor(...)"
                    String typeName = currentToken.getValue();
                    advance();

                    ObjectValue namedObject = parseUniteDescriptorObject(typeName, identifierTokenIndex);
                    namedObject.setInstanceName(firstIdentifier);
                    return namedObject;
                } else {
                    throw new NDFParseException("Expected IDENTIFIER or TEMPLATE_REF after 'is' but got " + currentToken.getType() + " at line " + currentToken.getLine() + ", column " + currentToken.getColumn(), currentToken);
                }
            } else if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                // Simple object: "Type(...)"
                return parseUniteDescriptorObject(firstIdentifier, identifierTokenIndex);
            } else {
                // Just an identifier (shouldn't happen in ModulesDescriptors, but handle gracefully)
                return NDFValue.createTemplateRef(firstIdentifier);
            }
        } else {
            // Fallback to standard value parsing
            return parseValue();
        }
    }

    /**
     * Parse objects within UniteDescriptor.ndf using ENHANCED MEMORY MODEL
     */
    private ObjectValue parseUniteDescriptorObject(String typeName, int startTokenIndex) throws NDFParseException {
        ObjectValue object = NDFValue.createObject(typeName);
        object.setOriginalTokenStartIndex(startTokenIndex);

        // ENHANCED MEMORY MODEL: CAPTURE ORIGINAL OPENING PARENTHESIS WITH EXACT FORMATTING
        NDFToken openParenToken = currentToken;
        expect(NDFToken.TokenType.OPEN_PAREN);
        object.setOriginalOpeningParen(openParenToken.getExactText());

        parseUniteDescriptorObjectProperties(object);

        // ENHANCED MEMORY MODEL: CAPTURE ORIGINAL CLOSING PARENTHESIS WITH EXACT FORMATTING
        NDFToken closeParenToken = currentToken;
        expect(NDFToken.TokenType.CLOSE_PAREN);
        object.setOriginalClosingParen(closeParenToken.getExactText());

        int endTokenIndex = currentTokenIndex - 1;
        object.setOriginalTokenEndIndex(endTokenIndex);

        return object;
    }

    /**
     * COMPLETELY STANDALONE UniteDescriptor value parsing
     * Handles all the unique patterns in UniteDescriptor.ndf without using standard parsing
     */
    private NDFValue parseUniteDescriptorValueStandalone() throws NDFParseException {
        // Handle different value types with UniteDescriptor-specific logic

        // 1. Handle pipe-separated values (like GameplayBehavior=EGameplayBehavior/Nothing | EGameplayBehavior/TacticalAttackNearCover)
        if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER || currentToken.getType() == NDFToken.TokenType.ENUM_VALUE) {
            // Look ahead to see if there's a pipe after this value
            int lookAheadIndex = currentTokenIndex + 1;
            while (lookAheadIndex < tokens.size()) {
                NDFToken lookAheadToken = tokens.get(lookAheadIndex);
                if (lookAheadToken.getType() == NDFToken.TokenType.PIPE) {
                    // This is a pipe-separated value, parse it specially
                    return parseUniteDescriptorPipeSeparatedEnum();
                } else if (lookAheadToken.getType() == NDFToken.TokenType.COMMA ||
                          lookAheadToken.getType() == NDFToken.TokenType.CLOSE_PAREN ||
                          lookAheadToken.getType() == NDFToken.TokenType.CLOSE_BRACKET) {
                    // End of value, no pipe found
                    break;
                } else if (!isWhitespaceOrComment(lookAheadToken)) {
                    // Non-whitespace, non-comment token that's not a pipe
                    break;
                }
                lookAheadIndex++;
            }
        }

        // 2. Handle template references with pipes (like TerrainListMask = ~/ETerrainType/None | ~/ETerrainType/ForetLegere)
        if (currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF) {
            // Look ahead to see if there's a pipe after this template ref
            int lookAheadIndex = currentTokenIndex + 1;
            while (lookAheadIndex < tokens.size()) {
                NDFToken lookAheadToken = tokens.get(lookAheadIndex);
                if (lookAheadToken.getType() == NDFToken.TokenType.PIPE) {
                    // This is a pipe-separated template ref, parse it specially
                    return parseUniteDescriptorPipeSeparatedTemplateRefs();
                } else if (lookAheadToken.getType() == NDFToken.TokenType.COMMA ||
                          lookAheadToken.getType() == NDFToken.TokenType.CLOSE_PAREN ||
                          lookAheadToken.getType() == NDFToken.TokenType.CLOSE_BRACKET) {
                    // End of value, no pipe found - treat as simple template ref
                    break;
                } else if (!isWhitespaceOrComment(lookAheadToken)) {
                    // Non-whitespace, non-comment token that's not a pipe
                    break;
                }
                lookAheadIndex++;
            }

            // Not a pipe-separated template ref, treat as simple template ref
            String templateValue = currentToken.getValue();
            advance();
            return NDFValue.createTemplateRef(templateValue);
        }

        // 3. Handle objects (like TBlindageProperties(...))
        if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
            // Look ahead to see if this is an object
            int lookAheadIndex = currentTokenIndex + 1;
            while (lookAheadIndex < tokens.size()) {
                NDFToken lookAheadToken = tokens.get(lookAheadIndex);
                if (lookAheadToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                    // This is an object, parse it with UniteDescriptor logic
                    String typeName = currentToken.getValue();
                    int startTokenIndex = currentTokenIndex;
                    advance();
                    return parseUniteDescriptorObjectStandalone(typeName, startTokenIndex);
                } else if (!isWhitespaceOrComment(lookAheadToken)) {
                    // Not an object, treat as enum or identifier
                    break;
                }
                lookAheadIndex++;
            }

            // Not an object, check if it's a simple identifier or enum
            String identifierValue = currentToken.getValue();
            advance();
            // For UniteDescriptor, treat as raw expression since we don't know the enum type
            return NDFValue.createRawExpression(identifierValue);
        }

        // 4. Handle arrays (like [value1, value2])
        if (currentToken.getType() == NDFToken.TokenType.OPEN_BRACKET) {
            return parseUniteDescriptorArrayStandalone();
        }

        // 5. Handle MAP constructs
        if (currentToken.getType() == NDFToken.TokenType.MAP) {
            return parseUniteDescriptorMapStandalone(); // Parse as proper map, not array
        }

        // 6. Handle basic values (numbers, strings, booleans, etc.)
        return parseUniteDescriptorBasicValue();
    }

    /**
     * Parse pipe-separated enum values like: EGameplayBehavior/Nothing | EGameplayBehavior/TacticalAttackNearCover
     */
    private NDFValue parseUniteDescriptorPipeSeparatedEnum() throws NDFParseException {
        StringBuilder pipeSeparatedValue = new StringBuilder();

        // Parse the first enum value
        pipeSeparatedValue.append(currentToken.getValue());
        advance();

        // Parse additional pipe-separated enum values
        while (currentToken.getType() == NDFToken.TokenType.PIPE) {
            pipeSeparatedValue.append(" | ");
            advance(); // consume pipe

            // Skip whitespace
            while (currentToken.getType() == NDFToken.TokenType.UNKNOWN && isWhitespaceToken(currentToken)) {
                advance();
            }

            if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER ||
                currentToken.getType() == NDFToken.TokenType.ENUM_VALUE ||
                currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF) {
                pipeSeparatedValue.append(currentToken.getValue());
                advance();
            } else {
                throw new NDFParseException("Expected IDENTIFIER, ENUM_VALUE, or TEMPLATE_REF after pipe in enum", currentToken);
            }
        }

        return NDFValue.createRawExpression(pipeSeparatedValue.toString());
    }

    /**
     * Parse pipe-separated template refs like: ~/ETerrainType/None | ~/ETerrainType/ForetLegere | ~/ETerrainType/ForetDense
     */
    private NDFValue parseUniteDescriptorPipeSeparatedTemplateRefs() throws NDFParseException {
        StringBuilder pipeSeparatedValue = new StringBuilder();

        // Parse the first template ref
        pipeSeparatedValue.append(currentToken.getValue());
        advance();

        // Parse additional pipe-separated template refs
        while (currentToken.getType() == NDFToken.TokenType.PIPE) {
            pipeSeparatedValue.append(" | ");
            advance(); // consume pipe

            // Skip whitespace
            while (currentToken.getType() == NDFToken.TokenType.UNKNOWN && isWhitespaceToken(currentToken)) {
                advance();
            }

            if (currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF ||
                currentToken.getType() == NDFToken.TokenType.IDENTIFIER ||
                currentToken.getType() == NDFToken.TokenType.ENUM_VALUE) {
                pipeSeparatedValue.append(currentToken.getValue());
                advance();
            } else {
                throw new NDFParseException("Expected TEMPLATE_REF, IDENTIFIER, or ENUM_VALUE after pipe", currentToken);
            }
        }

        return NDFValue.createRawExpression(pipeSeparatedValue.toString());
    }

    /**
     * Parse objects in UniteDescriptor.ndf with ENHANCED MEMORY MODEL
     */
    private NDFValue.ObjectValue parseUniteDescriptorObjectStandalone(String typeName, int startTokenIndex) throws NDFParseException {
        NDFValue.ObjectValue object = NDFValue.createObject(typeName);
        object.setOriginalTokenStartIndex(startTokenIndex);

        // ENHANCED MEMORY MODEL: CAPTURE ORIGINAL OPENING PARENTHESIS WITH EXACT FORMATTING
        NDFToken openParenToken = currentToken;
        expect(NDFToken.TokenType.OPEN_PAREN);
        object.setOriginalOpeningParen(openParenToken.getExactText());

        parseUniteDescriptorObjectProperties(object);

        // ENHANCED MEMORY MODEL: CAPTURE ORIGINAL CLOSING PARENTHESIS WITH EXACT FORMATTING
        NDFToken closeParenToken = currentToken;
        expect(NDFToken.TokenType.CLOSE_PAREN);
        object.setOriginalClosingParen(closeParenToken.getExactText());

        int endTokenIndex = currentTokenIndex - 1;
        object.setOriginalTokenEndIndex(endTokenIndex);

        return object;
    }

    /**
     * Parse arrays in UniteDescriptor.ndf with standalone logic
     */
    private NDFValue parseUniteDescriptorArrayStandalone() throws NDFParseException {
        expect(NDFToken.TokenType.OPEN_BRACKET);

        List<NDFValue> elements = new ArrayList<>();

        while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET && currentToken.getType() != NDFToken.TokenType.EOF) {
            // Skip whitespace and comments
            if (currentToken.getType() == NDFToken.TokenType.UNKNOWN && isWhitespaceToken(currentToken)) {
                advance();
                continue;
            }
            if (currentToken.getType() == NDFToken.TokenType.COMMENT) {
                advance();
                continue;
            }

            // Parse array element using standalone logic
            // Handle tuples like (EVisionUnitType/Standard, 3500.0)
            if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                NDFValue element = parseUniteDescriptorTupleStandalone();
                elements.add(element);
            } else {
                NDFValue element = parseUniteDescriptorValueStandalone();
                elements.add(element);
            }

            // Skip optional comma
            if (currentToken.getType() == NDFToken.TokenType.COMMA) {
                advance();
            }
        }

        expect(NDFToken.TokenType.CLOSE_BRACKET);

        // Create array and add elements
        NDFValue.ArrayValue array = NDFValue.createArray();
        for (NDFValue element : elements) {
            array.add(element);
        }
        return array;
    }

    /**
     * Parse tuples in UniteDescriptor.ndf like (EVisionUnitType/Standard, 3500.0)
     */
    private NDFValue parseUniteDescriptorTupleStandalone() throws NDFParseException {
        expect(NDFToken.TokenType.OPEN_PAREN);

        List<NDFValue> elements = new ArrayList<>();

        while (currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN && currentToken.getType() != NDFToken.TokenType.EOF) {
            // Skip whitespace and comments
            if (currentToken.getType() == NDFToken.TokenType.UNKNOWN && isWhitespaceToken(currentToken)) {
                advance();
                continue;
            }
            if (currentToken.getType() == NDFToken.TokenType.COMMENT) {
                advance();
                continue;
            }

            // Parse tuple element using standalone logic
            NDFValue element = parseUniteDescriptorValueStandalone();
            elements.add(element);

            // Skip optional comma
            if (currentToken.getType() == NDFToken.TokenType.COMMA) {
                advance();
            }
        }

        expect(NDFToken.TokenType.CLOSE_PAREN);

        // Create tuple and add elements
        NDFValue.TupleValue tuple = NDFValue.createTuple();
        for (NDFValue element : elements) {
            tuple.add(element);
        }
        return tuple;
    }

    /**
     * Parse MAP constructs in UniteDescriptor.ndf like MAP [(key1, value1), (key2, value2)]
     * This creates proper MapValue objects instead of arrays to fix type mismatch errors
     */
    private NDFValue.MapValue parseUniteDescriptorMapStandalone() throws NDFParseException {
        expect(NDFToken.TokenType.MAP);
        expect(NDFToken.TokenType.OPEN_BRACKET);

        NDFValue.MapValue map = NDFValue.createMap();

        while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET && currentToken.getType() != NDFToken.TokenType.EOF) {
            // Skip whitespace and comments
            if (currentToken.getType() == NDFToken.TokenType.UNKNOWN && isWhitespaceToken(currentToken)) {
                advance();
                continue;
            }
            if (currentToken.getType() == NDFToken.TokenType.COMMENT) {
                advance();
                continue;
            }

            // Parse map entry as tuple (key, value)
            expect(NDFToken.TokenType.OPEN_PAREN);
            NDFValue key = parseUniteDescriptorValueStandalone();
            expect(NDFToken.TokenType.COMMA);
            NDFValue value = parseUniteDescriptorValueStandalone();
            expect(NDFToken.TokenType.CLOSE_PAREN);

            // Check for comma after the entry
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            if (hasComma) {
                advance(); // Consume the comma
            }

            map.add(key, value, hasComma);
        }

        expect(NDFToken.TokenType.CLOSE_BRACKET);
        return map;
    }

    /**
     * Parse basic values (numbers, strings, booleans, etc.) in UniteDescriptor.ndf
     */
    private NDFValue parseUniteDescriptorBasicValue() throws NDFParseException {
        switch (currentToken.getType()) {
            case NUMBER_LITERAL:
                String numberValue = currentToken.getValue();
                advance();
                try {
                    double numValue = Double.parseDouble(numberValue);
                    return NDFValue.createNumber(numValue, numberValue);
                } catch (NumberFormatException e) {
                    // Fallback to raw expression if parsing fails
                    return NDFValue.createRawExpression(numberValue);
                }

            case STRING_LITERAL:
                String stringValue = currentToken.getValue();
                boolean useDoubleQuotes = currentToken.getOriginalText().startsWith("\"");
                advance();
                return NDFValue.createString(stringValue, useDoubleQuotes);

            case BOOLEAN_LITERAL:
                String boolValue = currentToken.getValue();
                advance();
                return NDFValue.createBoolean(Boolean.parseBoolean(boolValue));

            case IDENTIFIER:
                String identifierValue = currentToken.getValue();
                advance();
                // Treat as raw expression for UniteDescriptor
                return NDFValue.createRawExpression(identifierValue);

            case ENUM_VALUE:
                String enumValue = currentToken.getValue();
                advance();
                return NDFValue.createRawExpression(enumValue);

            case TEMPLATE_REF:
                String templateValue = currentToken.getValue();
                advance();
                return NDFValue.createTemplateRef(templateValue);

            case RESOURCE_REF:
                String resourceValue = currentToken.getValue();
                advance();
                return NDFValue.createResourceRef(resourceValue);

            case GUID:
                String guidValue = currentToken.getValue();
                advance();
                return NDFValue.createGUID(guidValue);

            case MAP:
                return parseUniteDescriptorMapStandalone(); // Parse as proper map, not array

            default:
                throw new NDFParseException("Unexpected token type in UniteDescriptor basic value: " + currentToken.getType(), currentToken);
        }
    }

    /**
     * Check if a token is whitespace or comment
     */
    private boolean isWhitespaceOrComment(NDFToken token) {
        return token.getType() == NDFToken.TokenType.COMMENT || isWhitespaceToken(token);
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
