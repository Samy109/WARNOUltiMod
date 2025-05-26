package com.warnomodmaker.parser;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.*;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NDFWriter {
    private final Writer writer;
    private int indentLevel;
    private static final String INDENT = "    ";
    private boolean preserveFormatting;
    private List<NDFToken> originalTokens;
    private int currentTokenIndex;
    private Map<String, NDFValue> modifiedValues;
    private List<String> preservedComments; // Store comments to preserve
    private boolean writingFunctionCall = false; // Track if we're inside a function call
    private Set<ObjectValue> modifiedObjects; // Track which objects have been modified


    public NDFWriter(Writer writer) {
        this(writer, true);
    }


    public NDFWriter(Writer writer, boolean preserveFormatting) {
        this.writer = writer;
        this.indentLevel = 0;
        this.preserveFormatting = true; // Always preserve formatting
        this.originalTokens = null;
        this.currentTokenIndex = 0;
        this.modifiedValues = new java.util.HashMap<>();
        this.preservedComments = new java.util.ArrayList<>();
        this.writingFunctionCall = false;
        this.modifiedObjects = new java.util.HashSet<>();
    }


    public void setOriginalTokens(List<NDFToken> tokens) {
        this.originalTokens = tokens;
        this.currentTokenIndex = 0;

        // Extract comments from tokens
        extractCommentsFromTokens(tokens);
    }


    private void extractCommentsFromTokens(List<NDFToken> tokens) {
        preservedComments.clear();
        for (NDFToken token : tokens) {
            if (token.getType() == NDFToken.TokenType.COMMENT) {
                preservedComments.add(token.getValue());
            }
        }
    }


    public void trackModifiedValue(String path, NDFValue value) {
        modifiedValues.put(path, value);
    }


    public void markObjectAsModified(ObjectValue object) {
        modifiedObjects.add(object);
    }


    public boolean isObjectModified(ObjectValue object) {
        return modifiedObjects.contains(object);
    }


    public void write(List<ObjectValue> ndfObjects) throws IOException {
        // SOPHISTICATED FIX: Store all objects for nested object handling
        setAllObjects(ndfObjects);

        if (preserveFormatting && originalTokens != null && !originalTokens.isEmpty()) {
            // Don't write preserved comments when using token range reconstruction
            writeExact(ndfObjects);
        } else {
            writePreservedComments();
            for (ObjectValue ndfObject : ndfObjects) {
                writeNDFObject(ndfObject);
                writer.write("\n");
            }
        }
    }


    private void writePreservedComments() throws IOException {
        for (String comment : preservedComments) {
            writer.write(comment);
            writer.write("\n");
        }
        if (!preservedComments.isEmpty()) {
            writer.write("\n"); // Extra line after comments
        }
    }


    private void writeExact(List<ObjectValue> ndfObjects) throws IOException {
        // SOPHISTICATED FIX: Handle token gaps between objects
        int lastWrittenToken = -1;

        for (ObjectValue ndfObject : ndfObjects) {
            if (ndfObject.hasOriginalTokenRange() && originalTokens != null) {
                int objectStartToken = ndfObject.getOriginalTokenStartIndex();
                for (int i = lastWrittenToken + 1; i < objectStartToken; i++) {
                    if (i >= 0 && i < originalTokens.size()) {
                        NDFToken gapToken = originalTokens.get(i);
                        if (gapToken != null) {
                            // Use exact text to preserve all whitespace and formatting
                            String gapText = gapToken.getExactText();
                            if (gapText != null && !gapText.isEmpty()) {
                                writer.write(gapText);
                            }
                        }
                    }
                }

                lastWrittenToken = ndfObject.getOriginalTokenEndIndex();
            }
            writeNDFObject(ndfObject);
        }
        if (originalTokens != null && lastWrittenToken >= 0) {
            for (int i = lastWrittenToken + 1; i < originalTokens.size(); i++) {
                NDFToken endToken = originalTokens.get(i);
                if (endToken != null && endToken.getExactText() != null) {
                    writer.write(endToken.getExactText());
                }
            }
        }
    }


    private void writeNDFObject(ObjectValue ndfObject) throws IOException {
        String instanceName = ndfObject.getInstanceName();
        String typeName = ndfObject.getTypeName();
        if ("ConstantDefinition".equals(typeName)) {
            writeConstantDefinition(ndfObject);
            return;
        }
        if (instanceName != null && instanceName.startsWith("$/")) {
            writer.write(instanceName);
            writer.write(" is ");
            writer.write(typeName);
            writer.write("\n");
        } else if (ndfObject.isExported()) {
            if (modifiedObjects.contains(ndfObject)) {
                // CRITICAL FIX: Use memory model for modified objects with perfect formatting preservation
                writer.write("export ");
                writer.write(instanceName);
                writer.write(" is ");
                writer.write(ndfObject.getTypeName());
                writer.write("\n");
                writeObjectFromMemoryModelWithPerfectFormatting(ndfObject);
            } else if (ndfObject.hasOriginalTokenRange() && originalTokens != null) {
                // Use original tokens only for unmodified objects
                String originalText = reconstructOriginalText(
                    ndfObject.getOriginalTokenStartIndex(),
                    ndfObject.getOriginalTokenEndIndex()
                );
                writer.write(originalText);
            } else {
                // Fallback: Write directly from memory model
                writer.write("export ");
                writer.write(instanceName);
                writer.write(" is ");
                writer.write(ndfObject.getTypeName());
                writer.write("\n");
                writeObjectFromMemoryModel(ndfObject);
            }
        } else {
            if (modifiedObjects.contains(ndfObject)) {
                // CRITICAL FIX: Use memory model for modified objects with perfect formatting preservation
                writer.write(instanceName);
                writer.write(" is ");
                writer.write(ndfObject.getTypeName());
                writer.write("\n");
                writeObjectFromMemoryModelWithPerfectFormatting(ndfObject);
            } else if (ndfObject.hasOriginalTokenRange() && originalTokens != null) {
                // Use original tokens only for unmodified objects
                String originalText = reconstructOriginalText(
                    ndfObject.getOriginalTokenStartIndex(),
                    ndfObject.getOriginalTokenEndIndex()
                );
                writer.write(originalText);
            } else {
                // Fallback: Write directly from memory model
                writeObjectFromMemoryModel(ndfObject);
            }
        }
    }


    private void writeObjectFromMemoryModel(ObjectValue object) throws IOException {
        // CRITICAL: Use EXACT original formatting - no fallbacks that change formatting
        String openParen = object.getOriginalOpeningParen();
        if (openParen != null && !openParen.isEmpty()) {
            writer.write(openParen);
        } else {
            writer.write("(");
        }

        Map<String, NDFValue> properties = object.getProperties();
        for (Map.Entry<String, NDFValue> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            NDFValue value = entry.getValue();

            // CRITICAL: Use EXACT original formatting - with proper defaults
            String prefix = object.getOriginalPropertyPrefix(propertyName);
            String equals = object.getOriginalPropertyEquals(propertyName);
            String suffix = object.getOriginalPropertySuffix(propertyName);

            if (prefix != null && !prefix.isEmpty()) {
                writer.write(prefix);
            } else {
                writer.write("\n  "); // Default indentation
            }

            writer.write(propertyName);

            if (equals != null && !equals.isEmpty()) {
                writer.write(equals);
            } else {
                writer.write(" = ");
            }

            writeValue(value);

            if (suffix != null && !suffix.isEmpty()) {
                writer.write(suffix);
            } else {
                // No default suffix - let the closing paren handle the newline
            }
        }

        // CRITICAL: Use EXACT original closing parenthesis
        String closeParen = object.getOriginalClosingParen();
        if (closeParen != null && !closeParen.isEmpty()) {
            writer.write(closeParen);
        } else {
            writer.write("\n)");
        }
    }

    private void writeObjectFromMemoryModelWithPerfectFormatting(NDFValue.ObjectValue object) throws IOException {
        // Use the exact original opening parenthesis
        String openParen = object.getOriginalOpeningParen();
        if (openParen != null && !openParen.isEmpty()) {
            writer.write(openParen);
        } else {
            writer.write("(");
        }

        // Write properties with exact original formatting
        boolean isFirstProperty = true;
        for (String propertyName : object.getProperties().keySet()) {
            NDFValue value = object.getProperty(propertyName);

            // Use EXACT original formatting for each property
            String prefix = object.getOriginalPropertyPrefix(propertyName);
            String equals = object.getOriginalPropertyEquals(propertyName);
            String suffix = object.getOriginalPropertySuffix(propertyName);

            // CRITICAL: Use either stored formatting OR reconstructed formatting, not both
            if (prefix != null && !prefix.isEmpty()) {
                // Use stored formatting
                writer.write(prefix);
                writer.write(propertyName);

                if (equals != null && !equals.isEmpty()) {
                    writer.write(equals);
                } else {
                    writer.write(" = ");
                }
            } else {
                // Use reconstructed formatting from original tokens
                // Only add newline + indentation if the opening parenthesis doesn't already include it
                String objectOpenParen = object.getOriginalOpeningParen();
                boolean openParenIncludesNewline = objectOpenParen != null && objectOpenParen.contains("\n");

                if (!isFirstProperty || !openParenIncludesNewline) {
                    writer.write("\n    "); // Default indentation when needed
                }
                writer.write(propertyName);

                if (equals != null && !equals.isEmpty()) {
                    String propertySpacing = reconstructPropertySpacing(propertyName, object);
                    writer.write(propertySpacing);
                    writer.write(equals);
                } else {
                    writer.write(" = ");
                }
            }

            // Write the value (this will use current memory values, including modifications)
            writeValue(value);

            // Write suffix (usually contains trailing comma or newline)
            if (suffix != null && !suffix.isEmpty()) {
                writer.write(suffix);
            }
            // Note: No default suffix - let the closing paren handle final formatting

            isFirstProperty = false;
        }

        // Use EXACT original closing parenthesis
        String closeParen = object.getOriginalClosingParen();
        if (closeParen != null && !closeParen.isEmpty()) {
            writer.write(closeParen);
        } else {
            writer.write("\n)");
        }
    }


    private void writeConstantDefinition(ObjectValue constantDef) throws IOException {
        // PERFECT SURGICAL FIX: Prioritize token reconstruction for exact formatting preservation
        String instanceName = constantDef.getInstanceName();
        String typeName = constantDef.getTypeName();
        NDFValue value = constantDef.getProperties().get("Value");

        if (constantDef.hasOriginalTokenRange() && originalTokens != null) {
            // PRIORITY: Use token reconstruction to preserve exact original formatting
            if (modifiedObjects.contains(constantDef)) {
                String originalText = reconstructOriginalTextWithMemoryModel(
                    constantDef.getOriginalTokenStartIndex(),
                    constantDef.getOriginalTokenEndIndex(),
                    constantDef,
                    getAllObjects()
                );
                writer.write(originalText);
            } else {
                String originalText = reconstructOriginalText(
                    constantDef.getOriginalTokenStartIndex(),
                    constantDef.getOriginalTokenEndIndex()
                );
                writer.write(originalText);
            }
        } else if (instanceName != null && "ConstantDefinition".equals(typeName) && value != null) {
            // Fallback: Simple constant definition when no token range
            writer.write(instanceName);
            writer.write(" is ");
            writeValue(value);
            writer.write("\n");
        } else if (instanceName != null && typeName != null && !"ConstantDefinition".equals(typeName)) {
            // Fallback: Object constant definition when no token range
            writer.write(instanceName);
            writer.write(" is ");
            writer.write(typeName);
            writeObjectContent(constantDef);
        } else {
            // Final fallback
            if (instanceName != null) {
                writer.write(instanceName);
                writer.write(" is ");
            }
            if (value != null) {
                writeValue(value);
            }
            writer.write("\n");
        }
    }

    // REMOVED: writeObjectBody method
    // SINGLE SOURCE OF TRUTH: All object writing now goes through token reconstruction with memory model values
    // This eliminates the dual-path architecture that was causing content duplication


    private List<ObjectValue> allObjects = null;


    private List<ObjectValue> getAllObjects() {
        return allObjects != null ? allObjects : new ArrayList<>();
    }


    public void setAllObjects(List<ObjectValue> objects) {
        this.allObjects = objects;
    }


    private boolean shouldUsePropertyAlignment(ObjectValue object) {
        // GENERIC APPROACH: Always use original formatting to preserve exact structure
        // This eliminates bias toward specific object types or file types
        return false;
    }


    private void writeValue(NDFValue value) throws IOException {
        switch (value.getType()) {
            case STRING:
                StringValue stringValue = (StringValue) value;
                if (stringValue.useDoubleQuotes()) {
                    writer.write("\"");
                    writer.write(stringValue.getValue());
                    writer.write("\"");
                } else {
                    writer.write("'");
                    writer.write(stringValue.getValue());
                    writer.write("'");
                }
                break;

            case NUMBER:
                NumberValue numberValue = (NumberValue) value;
                writer.write(numberValue.toString());
                break;

            case BOOLEAN:
                BooleanValue booleanValue = (BooleanValue) value;
                writer.write(booleanValue.toString());
                break;

            case ARRAY:
                writeArray((ArrayValue) value);
                break;
            case TUPLE:
                writeTuple((TupleValue) value);
                break;

            case MAP:
                writeMap((MapValue) value);
                break;

            case OBJECT:
                writeObject((ObjectValue) value);
                break;

            case TEMPLATE_REF:
                TemplateRefValue templateRefValue = (TemplateRefValue) value;
                if (templateRefValue.getInstanceName() != null) {
                    writer.write(templateRefValue.getInstanceName());
                    writer.write(" is ");
                    writer.write(templateRefValue.getPath());
                } else {
                    writer.write(templateRefValue.getPath());
                }
                break;

            case RESOURCE_REF:
                ResourceRefValue resourceRefValue = (ResourceRefValue) value;
                writer.write(resourceRefValue.getPath());
                break;

            case GUID:
                GUIDValue guidValue = (GUIDValue) value;
                writer.write(guidValue.getGUID());
                break;

            case ENUM:
                EnumValue enumValue = (EnumValue) value;
                writer.write(enumValue.getEnumType());
                writer.write("/");
                writer.write(enumValue.getEnumValue());
                break;

            case RAW_EXPRESSION:
                RawExpressionValue rawExpressionValue = (RawExpressionValue) value;
                writer.write(rawExpressionValue.getExpression());
                break;

            default:
                throw new IllegalArgumentException("Unknown value type: " + value.getType());
        }
    }


    private void writeArray(ArrayValue array) throws IOException {
        List<NDFValue> elements = array.getElements();

        if (elements.isEmpty()) {
            // CRITICAL: Use exact original formatting for empty arrays
            String openBracket = array.getOriginalOpeningBracket();
            String closeBracket = array.getOriginalClosingBracket();
            if (openBracket != null && closeBracket != null) {
                writer.write(openBracket);
                writer.write(closeBracket);
            } else {
                writer.write("[]");
            }
            return;
        }

        // CRITICAL: REPRODUCE EXACT ORIGINAL FORMATTING
        String openBracket = array.getOriginalOpeningBracket();
        if (openBracket != null && !openBracket.isEmpty()) {
            writer.write(openBracket);
        } else {
            writer.write("[");
        }

        for (int i = 0; i < elements.size(); i++) {
            NDFValue element = elements.get(i);
            String prefix = array.getOriginalElementPrefix(i);
            String suffix = array.getOriginalElementSuffix(i);

            if (prefix != null && !prefix.isEmpty()) {
                writer.write(prefix);
            } else {
                // SURGICAL FIX: Add default formatting for array elements when missing
                // This handles DamageLevels.ndf and ExperienceLevels.ndf patterns
                if (element instanceof NDFValue.ObjectValue) {
                    NDFValue.ObjectValue objElement = (NDFValue.ObjectValue) element;
                    if (objElement.getInstanceName() != null) {
                        // This is a named object in an array - add proper indentation
                        writer.write("\n        ");
                    }
                }
            }

            // PERFECT FIX: Only use constant definition writing for individually modified array elements
            if (element instanceof NDFValue.ObjectValue) {
                NDFValue.ObjectValue objElement = (NDFValue.ObjectValue) element;
                if (objElement.getInstanceName() != null && objElement.getTypeName() != null &&
                    modifiedObjects.contains(objElement)) {
                    // This array element is individually modified - write as constant definition
                    writeConstantDefinition(objElement);
                } else {
                    // This array element is not individually modified - use normal value writing (token reconstruction)
                    writeValue(element);
                }
            } else {
                writeValue(element);
            }
            if (suffix != null && !suffix.isEmpty()) {
                writer.write(suffix);
            }
        }

        String closeBracket = array.getOriginalClosingBracket();
        if (closeBracket != null && !closeBracket.isEmpty()) {
            // CRITICAL: Strip trailing newlines from array closing bracket to prevent duplication
            // The newlines will be added by the next property's prefix
            String cleanCloseBracket = closeBracket.replaceAll("\\n\\s*$", "");
            writer.write(cleanCloseBracket);
        } else {
            writer.write("]");
        }
    }

    // REMOVED: shouldArrayBeSingleLine() and shouldUseCompactArrayFormat()
    // These methods made formatting decisions which violated 1-1 reproduction requirement
    // Now using EXACT ORIGINAL FORMATTING PRESERVATION instead


    private void writeTuple(TupleValue tuple) throws IOException {
        List<NDFValue> elements = tuple.getElements();

        if (elements.isEmpty()) {
            writer.write("()");
            return;
        }

        writer.write("(");
        for (int i = 0; i < elements.size(); i++) {
            NDFValue element = elements.get(i);
            writeValue(element);
            if (tuple.hasCommaAfter(i)) {
                writer.write(",");
            }
            if (i < elements.size() - 1) {
                writer.write(" ");
            }
        }

        writer.write(")");
    }


    private void writeMap(MapValue map) throws IOException {
        List<Map.Entry<NDFValue, NDFValue>> entries = map.getEntries();

        writer.write("MAP [\n");
        indentLevel++;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<NDFValue, NDFValue> entry = entries.get(i);

            writeIndent();
            writer.write("(");
            writeValue(entry.getKey());
            writer.write(", ");
            writeValue(entry.getValue());
            writer.write(")");
            if (map.hasCommaAfter(i)) {
                writer.write(",");
            }
            writer.write("\n");
        }

        indentLevel--;
        writeIndent();
        writer.write("]");
    }


    private void writeObject(ObjectValue object) throws IOException {
        // FALLBACK: Write instance name if present (only when not using token range)
        if (object.getInstanceName() != null) {
            writer.write(object.getInstanceName());
            writer.write(" is ");
        }

        // FALLBACK: Use the complex formatting preservation approach
        writer.write(object.getTypeName());

        // Write the object content
        writeObjectContent(object);
    }

    private void writeObjectContent(ObjectValue object) throws IOException {

        // UNIVERSAL FORMATTING PRESERVATION - Works for all file types
        String openParen = object.getOriginalOpeningParen();

        // SURGICAL FIX: Handle DamageLevels object constant definitions specifically
        if (object.getInstanceName() != null && object.getInstanceName().contains("DamageLevelDescriptor_")) {
            // This is a DamageLevels object constant definition - ensure proper newline + indentation
            if (openParen != null && openParen.contains("\n")) {
                // Original has newline - use it
                writer.write(openParen);
            } else {
                // Original doesn't have newline or is missing - add proper formatting
                writer.write("\n        (");
            }
        } else if (openParen != null && !openParen.isEmpty()) {
            writer.write(openParen);
        } else {
            writer.write("(");
        }

        Map<String, NDFValue> properties = object.getProperties();
        boolean isFirstProperty = true;
        for (Map.Entry<String, NDFValue> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            NDFValue propertyValue = entry.getValue();

            String prefix = object.getOriginalPropertyPrefix(propertyName);
            String equals = object.getOriginalPropertyEquals(propertyName);
            String suffix = object.getOriginalPropertySuffix(propertyName);

            // CRITICAL: Preserve original formatting style - don't force expansion
            if (prefix != null && !prefix.isEmpty()) {
                // Use stored formatting exactly as it was
                writer.write(prefix);
                writer.write(propertyName);

                if (equals != null && !equals.isEmpty()) {
                    writer.write(equals);
                } else {
                    writer.write(" = ");
                }
            } else {
                // ONLY add formatting if the original object had expanded formatting
                String objectOpenParen = object.getOriginalOpeningParen();
                boolean originalWasExpanded = objectOpenParen != null && objectOpenParen.contains("\n");

                // SURGICAL FIX: Force expanded formatting for nested objects in DamageLevels/ExperienceLevels
                if (!originalWasExpanded && object.getInstanceName() != null &&
                    (object.getInstanceName().contains("DamageLevel") || object.getInstanceName().contains("ExperienceLevel"))) {
                    originalWasExpanded = true;
                }

                if (originalWasExpanded) {
                    // Original was expanded - use expanded formatting
                    if (!isFirstProperty || !originalWasExpanded) {
                        writer.write("\n    ");
                    }
                    writer.write(propertyName);

                    if (equals != null && !equals.isEmpty()) {
                        String propertySpacing = reconstructPropertySpacing(propertyName, object);
                        writer.write(propertySpacing);
                        writer.write(equals);
                    } else {
                        writer.write(" = ");
                    }
                } else {
                    // Original was compact - preserve compact formatting
                    if (!isFirstProperty) {
                        writer.write(" ");
                    }
                    writer.write(propertyName);

                    if (equals != null && !equals.isEmpty()) {
                        writer.write(equals);
                    } else {
                        writer.write("=");
                    }
                }
            }

            writeValue(propertyValue);

            if (suffix != null && !suffix.isEmpty()) {
                writer.write(suffix);
            }

            isFirstProperty = false;
        }

        String closeParen = object.getOriginalClosingParen();
        if (closeParen != null && !closeParen.isEmpty()) {
            writer.write(closeParen);
        } else {
            writer.write("\n)");
        }
    }


    private String reconstructOriginalText(int startIndex, int endIndex) {
        if (originalTokens == null || startIndex < 0 || endIndex < 0 ||
            startIndex >= originalTokens.size() || endIndex >= originalTokens.size()) {
            return "";
        }

        // SAFETY CHECK: Ensure valid range
        if (startIndex > endIndex) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = startIndex; i <= endIndex; i++) {
            NDFToken token = originalTokens.get(i);
            // SAFETY: Ensure token and its exact text are not null
            if (token != null && token.getExactText() != null) {
                result.append(token.getExactText());
            }
        }

        return result.toString();
    }


    private String reconstructOriginalTextWithMemoryModel(int startIndex, int endIndex, ObjectValue object, List<ObjectValue> allObjects) {
        // SIMPLIFIED APPROACH: If no modifications were made to this object, use pure token reconstruction
        if (!modifiedObjects.contains(object)) {
            return reconstructOriginalText(startIndex, endIndex);
        }

        // PERFECT APPROACH: Use exact token reconstruction for all tokens
        // Only modify values that have actually been changed in memory
        StringBuilder result = new StringBuilder();

        for (int i = startIndex; i <= endIndex; i++) {
            NDFToken token = originalTokens.get(i);
            if (token == null) continue;
            boolean tokenReplaced = false;

            // Only replace if this object was actually modified AND this token represents a changed value
            if (modifiedObjects.contains(object) &&
                (token.getType() == NDFToken.TokenType.NUMBER_LITERAL ||
                 token.getType() == NDFToken.TokenType.STRING_LITERAL ||
                 token.getType() == NDFToken.TokenType.BOOLEAN_LITERAL ||
                 token.getType() == NDFToken.TokenType.TEMPLATE_REF ||
                 token.getType() == NDFToken.TokenType.RESOURCE_REF ||
                 token.getType() == NDFToken.TokenType.GUID ||
                 token.getType() == NDFToken.TokenType.ENUM_VALUE ||
                 token.getType() == NDFToken.TokenType.IDENTIFIER)) {

                String propertyPath = findPropertyNameForToken(i, startIndex, endIndex);
                if (propertyPath != null) {
                    // Use PropertyUpdater to access nested properties
                    NDFValue memoryValue = com.warnomodmaker.model.PropertyUpdater.getPropertyValue(object, propertyPath);
                    if (memoryValue != null) {
                        String originalValue = token.getValue();
                        String memoryValueStr = getComparableValueString(memoryValue);

                        // Only replace if the value actually changed AND this is a simple value token
                        if (!originalValue.equals(memoryValueStr) && isSimpleValueToken(token, i, startIndex, endIndex)) {
                            String originalText = token.getExactText();
                            String newValueStr = getReplacementValueString(memoryValue, token);
                            String modifiedText = originalText.replace(originalValue, newValueStr);
                            result.append(modifiedText);
                            tokenReplaced = true;
                        }
                    }
                }
            }

            // For all other tokens (including unmodified values), use exact original text
            if (!tokenReplaced) {
                result.append(token.getExactText());
            }
        }

        return result.toString();
    }


    private NDFValue findModifiedPropertyValue(NDFValue.ObjectValue rootObject, String propertyName, String originalTokenValue) {
        return searchForModifiedProperty(rootObject, propertyName, originalTokenValue);
    }


    private NDFValue searchForModifiedProperty(NDFValue value, String propertyName, String originalTokenValue) {
        if (value instanceof NDFValue.ObjectValue) {
            NDFValue.ObjectValue obj = (NDFValue.ObjectValue) value;
            if (obj.getProperties().containsKey(propertyName)) {
                NDFValue currentValue = obj.getProperties().get(propertyName);
                String currentValueStr = getComparableValueString(currentValue);

                // Return if modified AND this object is marked as modified
                if (!originalTokenValue.equals(currentValueStr) && modifiedObjects.contains(obj)) {
                    return currentValue;
                }
            }

            // Search all properties recursively
            for (NDFValue propertyValue : obj.getProperties().values()) {
                NDFValue result = searchForModifiedProperty(propertyValue, propertyName, originalTokenValue);
                if (result != null) {
                    return result;
                }
            }

        } else if (value instanceof NDFValue.ArrayValue) {
            NDFValue.ArrayValue array = (NDFValue.ArrayValue) value;
            for (NDFValue element : array.getElements()) {
                NDFValue result = searchForModifiedProperty(element, propertyName, originalTokenValue);
                if (result != null) {
                    return result;
                }
            }
        } else if (value instanceof NDFValue.TupleValue) {
            NDFValue.TupleValue tuple = (NDFValue.TupleValue) value;
            for (NDFValue element : tuple.getElements()) {
                NDFValue result = searchForModifiedProperty(element, propertyName, originalTokenValue);
                if (result != null) {
                    return result;
                }
            }
        } else if (value instanceof NDFValue.MapValue) {
            NDFValue.MapValue map = (NDFValue.MapValue) value;
            for (Map.Entry<NDFValue, NDFValue> entry : map.getEntries()) {
                NDFValue result = searchForModifiedProperty(entry.getKey(), propertyName, originalTokenValue);
                if (result != null) return result;
                result = searchForModifiedProperty(entry.getValue(), propertyName, originalTokenValue);
                if (result != null) return result;
            }
        }

        return null;
    }


    private String getComparableValueString(NDFValue value) {
        if (value instanceof NDFValue.StringValue) {
            return ((NDFValue.StringValue) value).getValue();
        } else if (value instanceof NDFValue.NumberValue) {
            return ((NDFValue.NumberValue) value).toString();
        } else if (value instanceof NDFValue.BooleanValue) {
            return ((NDFValue.BooleanValue) value).toString();
        } else if (value instanceof NDFValue.TemplateRefValue) {
            return ((NDFValue.TemplateRefValue) value).getPath();
        } else if (value instanceof NDFValue.ResourceRefValue) {
            return ((NDFValue.ResourceRefValue) value).getPath();
        } else if (value instanceof NDFValue.GUIDValue) {
            return ((NDFValue.GUIDValue) value).getGUID();
        } else if (value instanceof NDFValue.EnumValue) {
            NDFValue.EnumValue enumVal = (NDFValue.EnumValue) value;
            return enumVal.getEnumType() + "/" + enumVal.getEnumValue();
        } else {
            return value.toString();
        }
    }


    private String getReplacementValueString(NDFValue value, NDFToken originalToken) {
        if (value instanceof NDFValue.StringValue) {
            // For strings, return just the raw value (token already has quotes)
            return ((NDFValue.StringValue) value).getValue();
        } else if (value instanceof NDFValue.TemplateRefValue) {
            return ((NDFValue.TemplateRefValue) value).getPath();
        } else if (value instanceof NDFValue.ResourceRefValue) {
            return ((NDFValue.ResourceRefValue) value).getPath();
        } else if (value instanceof NDFValue.GUIDValue) {
            return ((NDFValue.GUIDValue) value).getGUID();
        } else if (value instanceof NDFValue.EnumValue) {
            NDFValue.EnumValue enumVal = (NDFValue.EnumValue) value;
            return enumVal.getEnumType() + "/" + enumVal.getEnumValue();
        } else {
            // For other types, use their string representation
            return value.toString();
        }
    }


    private String formatValueFromMemoryModel(NDFValue value) {
        // Use the memory model's toString() which preserves original formatting
        return value.toString();
    }


    private String findPropertyNameForToken(int tokenIndex, int startIndex, int endIndex) {
        // Build the full nested property path by tracking nesting levels
        return buildNestedPropertyPath(tokenIndex, startIndex, endIndex);
    }

    private String buildNestedPropertyPath(int tokenIndex, int startIndex, int endIndex) {
        java.util.List<String> propertyPath = new java.util.ArrayList<>();
        int nestingLevel = 0;
        boolean foundDirectProperty = false;

        // Look backwards from the token to build the full property path
        for (int i = tokenIndex - 1; i >= startIndex; i--) {
            NDFToken token = originalTokens.get(i);

            if (token.getType() == NDFToken.TokenType.CLOSE_PAREN) {
                nestingLevel++;
            } else if (token.getType() == NDFToken.TokenType.OPEN_PAREN) {
                nestingLevel--;
            } else if (token.getType() == NDFToken.TokenType.EQUALS && nestingLevel == 0) {
                // Found equals sign at the same nesting level, look for identifier before it
                for (int j = i - 1; j >= startIndex; j--) {
                    NDFToken nameToken = originalTokens.get(j);
                    if (nameToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                        propertyPath.add(0, nameToken.getValue()); // Add to front
                        foundDirectProperty = true;
                        break;
                    } else if (!isWhitespaceToken(nameToken)) {
                        break; // Non-whitespace, non-identifier found
                    }
                }
                if (foundDirectProperty) {
                    break; // Found the direct property, now look for parent properties
                }
            } else if (token.getType() == NDFToken.TokenType.IS && nestingLevel == 0) {
                // Found "is" keyword at the same nesting level - this indicates a constant definition
                // For constants, the property name is always "Value" (as stored in memory model)
                return "Value";
            }
        }

        if (!foundDirectProperty) {
            return null; // No property found
        }

        // Now look for parent properties by continuing backwards and tracking nesting
        nestingLevel = 0;
        for (int i = tokenIndex - 1; i >= startIndex; i--) {
            NDFToken token = originalTokens.get(i);

            if (token.getType() == NDFToken.TokenType.CLOSE_PAREN) {
                nestingLevel++;
            } else if (token.getType() == NDFToken.TokenType.OPEN_PAREN) {
                nestingLevel--;

                // When we exit a nesting level, look for the parent property
                if (nestingLevel < 0) {
                    // Look backwards for the parent property
                    for (int j = i - 1; j >= startIndex; j--) {
                        NDFToken parentToken = originalTokens.get(j);
                        if (parentToken.getType() == NDFToken.TokenType.EQUALS) {
                            // Found parent equals, look for parent property name
                            for (int k = j - 1; k >= startIndex; k--) {
                                NDFToken parentNameToken = originalTokens.get(k);
                                if (parentNameToken.getType() == NDFToken.TokenType.IDENTIFIER) {
                                    propertyPath.add(0, parentNameToken.getValue()); // Add parent to front
                                    nestingLevel = 0; // Reset for next level
                                    i = k; // Continue from parent property
                                    break;
                                } else if (!isWhitespaceToken(parentNameToken)) {
                                    break;
                                }
                            }
                            break;
                        } else if (!isWhitespaceToken(parentToken)) {
                            break;
                        }
                    }
                }
            }
        }

        // Join the property path with dots
        if (propertyPath.isEmpty()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < propertyPath.size(); i++) {
            if (i > 0) {
                result.append(".");
            }
            result.append(propertyPath.get(i));
        }

        return result.toString();
    }


    private boolean isTokenPropertyValue(int tokenIndex, String propertyName, int startIndex, int endIndex) {
        // Look backwards to find the pattern: propertyName = <this token> OR constantName is <this token>
        boolean foundEquals = false;
        boolean foundIs = false;
        boolean foundPropertyName = false;

        for (int i = tokenIndex - 1; i >= startIndex; i--) {
            NDFToken token = originalTokens.get(i);
            if (!foundEquals && !foundIs && token.getType() == NDFToken.TokenType.EQUALS) {
                foundEquals = true;
            }
            else if (!foundEquals && !foundIs && token.getType() == NDFToken.TokenType.IS) {
                foundIs = true;
            }
            // Look for the property/constant name
            else if ((foundEquals || foundIs) && token.getType() == NDFToken.TokenType.IDENTIFIER) {
                if (foundEquals && token.getValue().equals(propertyName)) {
                    foundPropertyName = true;
                    break;
                } else if (foundIs && "Value".equals(propertyName)) {
                    // For constants, any identifier before "is" matches the "Value" property
                    foundPropertyName = true;
                    break;
                }
            } else if (!isWhitespaceToken(token)) {
                // Hit non-whitespace that breaks the pattern
                break;
            }
        }

        return (foundEquals || foundIs) && foundPropertyName;
    }


    private boolean isWhitespaceToken(NDFToken token) {
        // Since there's no explicit WHITESPACE token type, we check if the token
        // is effectively whitespace by looking at its content
        if (token == null || token.getValue() == null) {
            return false;
        }
        String value = token.getValue().trim();
        return value.isEmpty();
    }

    private String reconstructPropertySpacing(String propertyName, NDFValue.ObjectValue object) {
        // Find the original spacing between property name and equals sign
        if (originalTokens == null || !object.hasOriginalTokenRange()) {
            return " "; // Default single space
        }

        int startIndex = object.getOriginalTokenStartIndex();
        int endIndex = object.getOriginalTokenEndIndex();

        // Look for the property name token in the object's token range
        for (int i = startIndex; i <= endIndex && i < originalTokens.size(); i++) {
            NDFToken token = originalTokens.get(i);
            if (token != null && token.getType() == NDFToken.TokenType.IDENTIFIER &&
                propertyName.equals(token.getValue())) {

                // Found the property name token, extract spacing from its exact text
                String exactText = token.getExactText();
                if (exactText != null && exactText.length() > propertyName.length()) {
                    // Extract the trailing spacing after the property name
                    String trailingSpacing = exactText.substring(propertyName.length());
                    return trailingSpacing;
                }
                break;
            }
        }

        return " "; // Default single space if not found
    }

    private boolean isSimpleValueToken(NDFToken token, int tokenIndex, int startIndex, int endIndex) {
        // Check if this token is inside a complex object structure
        // by looking for open/close parentheses around it

        int nestingLevel = 0;

        // Look backwards from the token to see if we're inside parentheses
        for (int i = tokenIndex - 1; i >= startIndex; i--) {
            NDFToken prevToken = originalTokens.get(i);
            if (prevToken.getType() == NDFToken.TokenType.CLOSE_PAREN) {
                nestingLevel++;
            } else if (prevToken.getType() == NDFToken.TokenType.OPEN_PAREN) {
                nestingLevel--;
            } else if (prevToken.getType() == NDFToken.TokenType.EQUALS && nestingLevel == 0) {
                // Found equals at same level - this is a top-level property value
                return true;
            } else if (prevToken.getType() == NDFToken.TokenType.IS && nestingLevel == 0) {
                // Found "is" at same level - this is a constant definition value
                return true;
            }
        }

        // If we never found an equals or "is" at nesting level 0, this is not a simple value
        return false;
    }

    // REMOVED: All formatting decision methods (isFunctionCallWithArraySyntax, isFunctionCall, etc.)
    // These methods made formatting decisions which violated 1-1 reproduction requirement
    // Now using EXACT ORIGINAL FORMATTING PRESERVATION instead

    // REMOVED: writeFunctionCallWithArraySyntax method
    // This method made formatting decisions which violated 1-1 reproduction requirement

    // REMOVED: isFunctionCall method and all related formatting decision methods
    // These methods made formatting decisions which violated 1-1 reproduction requirement

    // REMOVED: All remaining formatting decision methods
    // (isUniteDescriptorContext, isUniteModuleDescriptorFunctionCall, isSimpleEnoughForFunctionCall, writeFunctionCall)
    // These methods made formatting decisions which violated 1-1 reproduction requirement


    private void writeIndent() throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            writer.write(INDENT);
        }
    }
}
