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

    private String[] sourceLines;
    private String originalSourceContent;
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

        initializeSourceLines();

        if (fileType == NDFFileType.UNITE_DESCRIPTOR) {
            return parseUniteDescriptor();
        } else if (fileType == NDFFileType.FIRE_DESCRIPTOR || fileType == NDFFileType.SMOKE_DESCRIPTOR) {
            // Fire and Smoke descriptors have the same structure as Unite descriptors (exported objects)
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
                System.err.println("Parsing error at line " + currentToken.getLine() +
                                 ", token: " + currentToken.getValue() + " (" + currentToken.getType() + "): " + e.getMessage());

                if (currentToken.getType() != NDFToken.TokenType.EOF) {
                    advance();
                }
            }
        }

        return ndfObjects;
    }


    private ObjectValue parseExportedDescriptor() throws NDFParseException {
        int exportTokenIndex = currentTokenIndex;

        expect(NDFToken.TokenType.EXPORT);
        String descriptorName;
        if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
            descriptorName = currentToken.getValue();
            advance();
            if (currentToken.getType() == NDFToken.TokenType.OPEN_BRACKET) {
                advance();
                while (currentToken.getType() != NDFToken.TokenType.CLOSE_BRACKET &&
                       currentToken.getType() != NDFToken.TokenType.EOF) {
                    advance();
                }
                if (currentToken.getType() == NDFToken.TokenType.CLOSE_BRACKET) {
                    advance();
                }
            }
        } else {
            throw new NDFParseException("Expected descriptor name after export", currentToken);
        }

        expect(NDFToken.TokenType.IS);
        NDFToken typeNameToken = expect(NDFToken.TokenType.IDENTIFIER);
        String typeName = typeNameToken.getValue();
        int typeNameTokenIndex = currentTokenIndex - 1;
        ObjectValue descriptor = NDFValue.createObject(typeName);
        descriptor.setInstanceName(descriptorName);
        descriptor.setExported(true);
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

        int closeParenTokenIndex = currentTokenIndex - 1;
        descriptor.setOriginalTokenEndIndex(closeParenTokenIndex);

        return descriptor;
    }


    private boolean isDescriptorDefinition() {
        if (currentTokenIndex + 2 < tokens.size()) {
            NDFToken nextToken = tokens.get(currentTokenIndex + 1);
            NDFToken thirdToken = tokens.get(currentTokenIndex + 2);
            if (nextToken.getType() == NDFToken.TokenType.IS &&
                thirdToken.getType() == NDFToken.TokenType.IDENTIFIER) {

                if (currentTokenIndex + 3 < tokens.size()) {
                    NDFToken fourthToken = tokens.get(currentTokenIndex + 3);
                    return fourthToken.getType() == NDFToken.TokenType.OPEN_PAREN;
                }
            }
        }
        return false;
    }


    private boolean isSimpleAssignment() {
        if (currentTokenIndex + 2 < tokens.size()) {
            NDFToken nextToken = tokens.get(currentTokenIndex + 1);
            NDFToken thirdToken = tokens.get(currentTokenIndex + 2);

            if (nextToken.getType() == NDFToken.TokenType.IS) {
                return thirdToken.getType() == NDFToken.TokenType.NUMBER_LITERAL ||
                       thirdToken.getType() == NDFToken.TokenType.STRING_LITERAL ||
                       thirdToken.getType() == NDFToken.TokenType.OPEN_BRACKET ||
                       (thirdToken.getType() == NDFToken.TokenType.IDENTIFIER &&
                        !isDescriptorDefinition());
            }
        }
        return false;
    }


    private ObjectValue parseDescriptorDefinition() throws NDFParseException {
        int startTokenIndex = currentTokenIndex;
        String descriptorName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        expect(NDFToken.TokenType.IS);
        String typeName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        ObjectValue descriptor = NDFValue.createObject(typeName);
        descriptor.setInstanceName(descriptorName);

        descriptor.setOriginalTokenStartIndex(startTokenIndex);
        expect(NDFToken.TokenType.OPEN_PAREN);
        parseObjectProperties(descriptor);
        expect(NDFToken.TokenType.CLOSE_PAREN);

        int endTokenIndex = currentTokenIndex - 1;
        descriptor.setOriginalTokenEndIndex(endTokenIndex);

        return descriptor;
    }


    private void skipSimpleAssignment() throws NDFParseException {
        advance();
        expect(NDFToken.TokenType.IS);

        if (currentToken.getType() == NDFToken.TokenType.OPEN_BRACKET) {
            skipArray();
        } else if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
            skipObjectInstantiation();
        } else {
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
        int startTokenIndex = currentTokenIndex;

        String identifierName = expect(NDFToken.TokenType.IDENTIFIER).getValue();
        if (currentToken.getType() == NDFToken.TokenType.IS) {
            advance();
            NDFValue value = parseValue();
            ObjectValue definition = NDFValue.createObject("ConstantDefinition");
            definition.setInstanceName(identifierName);
            definition.setProperty("Value", value);

            definition.setOriginalTokenStartIndex(startTokenIndex);
            int endTokenIndex = currentTokenIndex - 1;
            definition.setOriginalTokenEndIndex(endTokenIndex);

            return definition;
        } else if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
            String secondIdentifier = currentToken.getValue();
            advance();
            if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                ObjectValue definition = parseObject(secondIdentifier, startTokenIndex);
                definition.setInstanceName(identifierName);

                return definition;
            } else {
                ObjectValue definition = NDFValue.createObject("TypeDeclaration");
                definition.setInstanceName(identifierName);
                definition.setProperty("Type", NDFValue.createString(secondIdentifier));

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
            ObjectValue definition = NDFValue.createObject("UnknownDefinition");
            definition.setInstanceName(identifierName);

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
                        advance();
                        break;
                    }
                } else if (currentToken.getType() == NDFToken.TokenType.EXPORT && !foundOpenParen) {
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
            String propertyPrefix = currentToken.getLeadingWhitespace();

            String propertyName;
            if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                propertyName = currentToken.getValue();
                advance();
            } else if (currentToken.getType() == NDFToken.TokenType.UNKNOWN) {
                propertyName = currentToken.getValue();
                advance();
            } else {
                advance();
                continue;
            }

            NDFToken equalsToken = currentToken;
            expect(NDFToken.TokenType.EQUALS);
            String originalEquals = equalsToken.getExactText();
            NDFValue propertyValue;
            try {
                propertyValue = parseValue();
            } catch (NDFParseException e) {
                while (currentToken.getType() != NDFToken.TokenType.COMMA &&
                       currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN &&
                       currentToken.getType() != NDFToken.TokenType.EOF) {
                    advance();
                }

                if (currentToken.getType() == NDFToken.TokenType.COMMA) {
                    advance();
                }
                continue;
            }
            if (currentToken.getType() == NDFToken.TokenType.PIPE) {
                StringBuilder bitwiseExpr = new StringBuilder();
                bitwiseExpr.append(propertyValue.toString());

                while (currentToken.getType() == NDFToken.TokenType.PIPE) {
                    bitwiseExpr.append(" | ");
                    advance(); // Consume the '|'

                    NDFValue rightOperand = parseValue();
                    bitwiseExpr.append(rightOperand.toString());
                }

                propertyValue = NDFValue.createRawExpression(bitwiseExpr.toString());
            }
            boolean hasComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            String propertySuffix = "";

            if (hasComma) {
                NDFToken commaToken = currentToken;
                advance();

                String commaText = commaToken.getExactText();
                String followingWhitespace = currentToken.getLeadingWhitespace();
                propertySuffix = commaText + followingWhitespace;
            } else {
                propertySuffix = currentToken.getLeadingWhitespace();
            }

            object.setOriginalPropertyPrefix(propertyName, propertyPrefix);
            object.setOriginalPropertyEquals(propertyName, originalEquals);
            object.setOriginalPropertySuffix(propertyName, propertySuffix);
            object.setProperty(propertyName, propertyValue, hasComma);


        }
    }


    private NDFValue parseValue() throws NDFParseException {
        String valuePrefix = currentToken.getLeadingWhitespace();
        int lineNumber = currentToken.getLine();

        switch (currentToken.getType()) {
            case STRING_LITERAL:
                String stringValue = currentToken.getValue();
                boolean useDoubleQuotes = currentToken.getOriginalText().startsWith("\"");
                String stringTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue stringVal = NDFValue.createString(stringValue, useDoubleQuotes);
                stringVal.setOriginalFormatting(valuePrefix, stringTrailingWhitespace);
                setLineInfo(stringVal, lineNumber);
                return stringVal;

            case NUMBER_LITERAL:
                double numberValue = Double.parseDouble(currentToken.getValue());
                String originalFormat = currentToken.getOriginalText();
                String numberTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue numberVal = NDFValue.createNumber(numberValue, originalFormat);
                numberVal.setOriginalFormatting(valuePrefix, numberTrailingWhitespace);
                setLineInfo(numberVal, lineNumber);
                return numberVal;

            case BOOLEAN_LITERAL:
                boolean booleanValue = currentToken.getValue().equals("True");
                String booleanTrailingWhitespace = currentToken.getTrailingWhitespace();
                advance();
                NDFValue booleanVal = NDFValue.createBoolean(booleanValue);
                booleanVal.setOriginalFormatting(valuePrefix, booleanTrailingWhitespace);
                setLineInfo(booleanVal, lineNumber);
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

                while (currentToken.getType() == NDFToken.TokenType.UNKNOWN &&
                       Character.isWhitespace(currentToken.getValue().charAt(0))) {
                    advance();
                }

                if (currentToken.getType() == NDFToken.TokenType.IS) {
                    advance();

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

                                functionCall.setOriginalTokenStartIndex(identifierTokenIndex);

                                NDFToken openParenToken = tokens.get(savedIndex);
                                functionCall.setOriginalOpeningParen(openParenToken.getExactText());
                                while (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                                    String paramPrefix = currentToken.getLeadingWhitespace();

                                    String paramName = currentToken.getValue();
                                    NDFToken paramNameToken = currentToken;
                                    advance();

                                    NDFToken equalsToken = currentToken;
                                    expect(NDFToken.TokenType.EQUALS);
                                    String originalEquals = equalsToken.getExactText();

                                    int valueStartIndex = currentTokenIndex;
                                    NDFValue paramValue = parseValue();

                                    String paramSuffix = "";
                                    if (valueStartIndex < tokens.size()) {
                                        int lastValueTokenIndex = currentTokenIndex - 1;
                                        if (lastValueTokenIndex >= 0 && lastValueTokenIndex < tokens.size()) {
                                            NDFToken lastValueToken = tokens.get(lastValueTokenIndex);
                                            paramSuffix = lastValueToken.getTrailingWhitespace();
                                        }
                                    }

                                    functionCall.setOriginalPropertyPrefix(paramName, paramPrefix);
                                    functionCall.setOriginalPropertyEquals(paramName, originalEquals);
                                    functionCall.setOriginalPropertySuffix(paramName, paramSuffix);

                                    functionCall.setProperty(paramName, paramValue);

                                    while (currentToken.getType() == NDFToken.TokenType.UNKNOWN &&
                                           Character.isWhitespace(currentToken.getValue().charAt(0))) {
                                        advance();
                                    }

                                    if (currentToken.getType() == NDFToken.TokenType.CLOSE_PAREN) {
                                        break;
                                    }
                                }

                                NDFToken closeParenToken = currentToken;
                                expect(NDFToken.TokenType.CLOSE_PAREN);
                                functionCall.setOriginalClosingParen(closeParenToken.getExactText());

                                int functionEndIndex = currentTokenIndex - 1;
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


    public List<ObjectValue> parseUniteDescriptor() throws IOException, NDFParseException {
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
                // CRITICAL: Log parsing errors to help debug missing objects
                System.err.println("WARNING: UniteDescriptor parsing error at line " + currentToken.getLine() +
                                 ", token: " + currentToken.getValue() + " (" + currentToken.getType() + "): " + e.getMessage());

                // Try to recover more gracefully - advance just one token instead of skipping to next export
                // This prevents losing entire objects due to minor parsing issues
                if (currentToken.getType() != NDFToken.TokenType.EOF) {
                    advance();
                }
            }
        }

        return ndfObjects;
    }

    /**
     * Parse exported objects in UniteDescriptorOLD.ndf (TEntityDescriptor objects) with ENHANCED MEMORY MODEL
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
     * Parse object properties in UniteDescriptorOLD.ndf with ENHANCED MEMORY MODEL formatting capture
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
                // Skip failed property parsing and continue - only log if debugging
                // System.err.println("Warning: Failed to parse property '" + propertyName + "': " + e.getMessage());
                while (currentToken.getType() != NDFToken.TokenType.COMMA &&
                       currentToken.getType() != NDFToken.TokenType.CLOSE_PAREN &&
                       currentToken.getType() != NDFToken.TokenType.EOF) {
                    advance();
                }
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
            // Skip whitespace and comments
            if (currentToken.getType() == NDFToken.TokenType.UNKNOWN && isWhitespaceToken(currentToken)) {
                advance();
                continue;
            }
            if (currentToken.getType() == NDFToken.TokenType.COMMENT) {
                advance();
                continue;
            }

            // Handle leading comma (NEW format: ",element")
            boolean hasLeadingComma = false;
            String leadingCommaText = "";
            if (currentToken.getType() == NDFToken.TokenType.COMMA) {
                hasLeadingComma = true;
                NDFToken commaToken = currentToken;
                leadingCommaText = commaToken.getExactText();
                advance();
            }

            String elementPrefix = currentToken.getLeadingWhitespace();
            // If we had a leading comma, include it in the prefix
            if (hasLeadingComma) {
                elementPrefix = leadingCommaText + elementPrefix;
            }
            array.setOriginalElementPrefix(elementIndex, elementPrefix);

            NDFValue element = parseUniteDescriptorModuleElement();

            // Handle trailing comma (OLD format: "element,")
            boolean hasTrailingComma = currentToken.getType() == NDFToken.TokenType.COMMA;
            String elementSuffix = "";

            if (hasTrailingComma) {
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
            // Element has comma if either leading or trailing comma was found
            array.add(element, hasLeadingComma || hasTrailingComma);
            elementIndex++;
        }

        NDFToken closeBracketToken = currentToken;
        expect(NDFToken.TokenType.CLOSE_BRACKET);
        array.setOriginalClosingBracket(closeBracketToken.getExactText());

        return array;
    }


    private NDFValue parseUniteDescriptorModuleElement() throws NDFParseException {
        if (currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF) {
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
                } else if (currentToken.getType() == NDFToken.TokenType.RESOURCE_REF) {
                    // NEW FORMAT: Named assignment with resource ref: "WeaponManager is $/GFX/Weapon/WeaponDescriptor_2K11_KRUG_DDR"
                    String resourcePath = currentToken.getValue();
                    advance();
                    NDFValue.ResourceRefValue resourceRef = (NDFValue.ResourceRefValue) NDFValue.createResourceRef(resourcePath);
                    resourceRef.setInstanceName(firstIdentifier);
                    return resourceRef;
                } else {
                    throw new NDFParseException("Expected IDENTIFIER, TEMPLATE_REF, or RESOURCE_REF after 'is' but got " + currentToken.getType() + " at line " + currentToken.getLine() + ", column " + currentToken.getColumn(), currentToken);
                }
            } else if (currentToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                // Simple object: "Type(...)"
                return parseUniteDescriptorObject(firstIdentifier, identifierTokenIndex);
            } else {
                // Just an identifier (shouldn't happen in ModulesDescriptors, but handle gracefully)
                return NDFValue.createTemplateRef(firstIdentifier);
            }
        } else {
            // Fallback to UniteDescriptor-specific value parsing
            return parseUniteDescriptorValueStandalone();
        }
    }


    private ObjectValue parseUniteDescriptorObject(String typeName, int startTokenIndex) throws NDFParseException {
        ObjectValue object = NDFValue.createObject(typeName);
        object.setOriginalTokenStartIndex(startTokenIndex);

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
     * Handles all the unique patterns in UniteDescriptorOLD.ndf without using standard parsing
     */
    private NDFValue parseUniteDescriptorValueStandalone() throws NDFParseException {

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

        if (currentToken.getType() == NDFToken.TokenType.TEMPLATE_REF) {
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
                    break;
                }
                lookAheadIndex++;
            }

            String templateValue = currentToken.getValue();
            advance();
            return NDFValue.createTemplateRef(templateValue);
        }

        if (currentToken.getType() == NDFToken.TokenType.IDENTIFIER) {
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
     * Parse objects in UniteDescriptorOLD.ndf with ENHANCED MEMORY MODEL
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
     * Parse arrays in UniteDescriptorOLD.ndf with standalone logic
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
     * Parse tuples in UniteDescriptorOLD.ndf like (EVisionUnitType/Standard, 3500.0)
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
     * Parse MAP constructs in UniteDescriptorOLD.ndf like MAP [(key1, value1), (key2, value2)]
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
     * Parse basic values (numbers, strings, booleans, etc.) in UniteDescriptorOLD.ndf
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

    private void initializeSourceLines() {
        if (originalSourceContent != null) {
            sourceLines = originalSourceContent.split("\n", -1);
        } else {
            sourceLines = new String[0];
        }
    }

    public void setOriginalSourceContent(String content) {
        this.originalSourceContent = content;
    }

    private void setLineInfo(NDFValue value, int lineNumber) {
        if (value != null && lineNumber >= 0 && sourceLines != null && lineNumber < sourceLines.length) {
            String lineContent = sourceLines[lineNumber];
            value.setSourceLineInfo(lineNumber, lineContent);
        }
    }

    public String[] getSourceLines() {
        return sourceLines;
    }
}
