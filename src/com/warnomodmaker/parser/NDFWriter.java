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

        // CRITICAL FIX: Never use writeExact if any objects are modified
        // writeExact corrupts array element prefixes with gap tokens
        boolean hasModifiedObjects = ndfObjects.stream().anyMatch(modifiedObjects::contains);
        boolean isCompleteFile = isCompleteFileWrite(ndfObjects);

        if (preserveFormatting && originalTokens != null && !originalTokens.isEmpty() &&
            isCompleteFile && !hasModifiedObjects) {
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
            writer.write("\n");
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
                // CRITICAL FIX: Always use memory model for modified objects
                // Never use token reconstruction for modified objects as it can include unwanted content
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
                writeCleanObjectFromMemoryModel(ndfObject);
            }
        } else {
            if (modifiedObjects.contains(ndfObject)) {
                // CRITICAL FIX: Always use memory model for modified objects
                // Never use token reconstruction for modified objects as it can include unwanted content
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
                writer.write(instanceName);
                writer.write(" is ");
                writer.write(ndfObject.getTypeName());
                writeCleanObjectFromMemoryModel(ndfObject);
            }
        }
    }


    // REMOVED: writeObjectFromMemoryModel - replaced by writeCleanObjectFromMemoryModel

    private void writeObjectFromMemoryModelWithPerfectFormatting(NDFValue.ObjectValue object) throws IOException {
        // COMPLETE ARCHITECTURAL REWRITE: Clean memory model writing for modified objects
        // This completely bypasses all token reconstruction and array corruption issues
        writeCleanObjectFromMemoryModel(object);
    }

    /**
     * ENHANCED MEMORY MODEL WRITING SYSTEM for modified objects
     * Uses original formatting when available, falls back to clean formatting
     */
    private void writeCleanObjectFromMemoryModel(NDFValue.ObjectValue object) throws IOException {
        // Write opening parenthesis with original formatting if available
        String openingParen = object.getOriginalOpeningParen();
        writer.write(openingParen);

        Map<String, NDFValue> properties = object.getProperties();

        for (Map.Entry<String, NDFValue> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            NDFValue value = entry.getValue();

            // ENHANCED: Use original formatting if available, otherwise use clean formatting
            String propertyPrefix = object.getOriginalPropertyPrefix(propertyName);
            String propertyEquals = object.getOriginalPropertyEquals(propertyName);
            String propertySuffix = object.getOriginalPropertySuffix(propertyName);

            // Use original formatting if available, otherwise fall back to clean formatting
            if (!propertyPrefix.isEmpty() || !propertyEquals.isEmpty() || !propertySuffix.isEmpty()) {
                // Use captured original formatting
                writer.write(propertyPrefix);
                writer.write(propertyName);
                writer.write(propertyEquals);
            } else {
                // No original formatting captured, use clean formatting
                writer.write("\n  ");
                writer.write(propertyName);
                writer.write(" = ");
            }

            // STANDALONE UNITEDESCRIPTOR FUNCTIONALITY - Special handling for ModulesDescriptors
            if ("ModulesDescriptors".equals(propertyName) && value instanceof NDFValue.ArrayValue && isUniteDescriptorFile()) {
                writeUniteDescriptorModulesArray((NDFValue.ArrayValue) value);
            } else {
                // ENHANCED: Use formatting-aware value writing
                writeValueWithFormatting(value);
            }

            // Write property suffix (comma and/or whitespace)
            if (!propertySuffix.isEmpty()) {
                writer.write(propertySuffix);
            }
        }

        // Write closing parenthesis with original formatting if available
        String closingParen = object.getOriginalClosingParen();
        writer.write(closingParen);
    }

    /**
     * ENHANCED VALUE WRITING SYSTEM with formatting preservation
     * Uses original formatting when available, falls back to clean formatting
     */
    private void writeValueWithFormatting(NDFValue value) throws IOException {
        // Use original formatting if available
        if (value.hasOriginalFormatting()) {
            // Write prefix (whitespace before value)
            writer.write(value.getOriginalPrefix());
            writeValueContent(value);
            // Write suffix (whitespace after value)
            writer.write(value.getOriginalSuffix());
        } else {
            // Fall back to clean value writing
            writeCleanValue(value);
        }
    }

    /**
     * Write just the content of a value without any surrounding formatting
     */
    private void writeValueContent(NDFValue value) throws IOException {
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

            case TEMPLATE_REF:
                TemplateRefValue templateRefValue = (TemplateRefValue) value;
                if (templateRefValue.getInstanceName() != null) {
                    writer.write(templateRefValue.getInstanceName());
                    writer.write(" is ");
                }
                writer.write(templateRefValue.getPath());
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
                writer.write(enumValue.toString());
                break;

            case RAW_EXPRESSION:
                RawExpressionValue rawValue = (RawExpressionValue) value;
                writer.write(rawValue.getExpression());
                break;

            case ARRAY:
                ArrayValue arrayValue = (ArrayValue) value;
                writeFormattingAwareArray(arrayValue);
                break;

            case TUPLE:
                TupleValue tupleValue = (TupleValue) value;
                writeFormattingAwareTuple(tupleValue);
                break;

            case MAP:
                MapValue mapValue = (MapValue) value;
                writeFormattingAwareMap(mapValue);
                break;

            case OBJECT:
                ObjectValue objectValue = (ObjectValue) value;
                // Special handling for RGBA objects
                if ("RGBA".equals(objectValue.getTypeName())) {
                    writeCleanRGBA(objectValue);
                } else {
                    writeCleanObject(objectValue);
                }
                break;

            case NULL:
                writer.write("nil");
                break;

            default:
                writer.write(value.toString());
                break;
        }
    }

    /**
     * BRAND NEW CLEAN VALUE WRITING SYSTEM
     * This completely avoids all the array corruption issues in the existing writeValue method
     */
    private void writeCleanValue(NDFValue value) throws IOException {
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

            case TEMPLATE_REF:
                TemplateRefValue templateRefValue = (TemplateRefValue) value;
                if (templateRefValue.getInstanceName() != null) {
                    writer.write(templateRefValue.getInstanceName());
                    writer.write(" is ");
                }
                writer.write(templateRefValue.getPath());
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
                writer.write(enumValue.toString());
                break;

            case RAW_EXPRESSION:
                RawExpressionValue rawValue = (RawExpressionValue) value;
                writer.write(rawValue.getExpression());
                break;

            case ARRAY:
                ArrayValue arrayValue = (ArrayValue) value;
                writeCleanArray(arrayValue);
                break;

            case TUPLE:
                TupleValue tupleValue = (TupleValue) value;
                writeCleanTuple(tupleValue);
                break;

            case MAP:
                MapValue mapValue = (MapValue) value;
                writeCleanMap(mapValue);
                break;

            case OBJECT:
                ObjectValue objectValue = (ObjectValue) value;
                // Special handling for RGBA objects
                if ("RGBA".equals(objectValue.getTypeName())) {
                    writeCleanRGBA(objectValue);
                } else {
                    writeCleanObject(objectValue);
                }
                break;

            case NULL:
                writer.write("nil");
                break;

            default:
                writer.write(value.toString());
                break;
        }
    }

    /**
     * FORMATTING-AWARE ARRAY WRITING - Uses original formatting when available
     */
    private void writeFormattingAwareArray(ArrayValue array) throws IOException {
        List<NDFValue> elements = array.getElements();

        if (elements.isEmpty()) {
            writer.write("[]");
            return;
        }

        // Use original opening bracket formatting if available
        String openingBracket = array.getOriginalOpeningBracket();
        if (openingBracket.isEmpty()) {
            openingBracket = "[";
        }
        writer.write(openingBracket);

        for (int i = 0; i < elements.size(); i++) {
            NDFValue element = elements.get(i);

            // Use original element prefix formatting if available
            String elementPrefix = array.getOriginalElementPrefix(i);
            if (!elementPrefix.isEmpty()) {
                writer.write(elementPrefix);
            } else if (array.isOriginallyMultiLine()) {
                writer.write("\n  "); // Default multi-line formatting
            }

            // Write the element content
            writeValueContent(element);

            // Use original element suffix formatting if available
            String elementSuffix = array.getOriginalElementSuffix(i);
            if (!elementSuffix.isEmpty()) {
                writer.write(elementSuffix);
            } else {
                // Add comma for all but the last element
                if (i < elements.size() - 1) {
                    writer.write(",");
                }
                if (array.isOriginallyMultiLine()) {
                    writer.write("\n");
                }
            }
        }

        // Use original closing bracket formatting if available
        String closingBracket = array.getOriginalClosingBracket();
        if (closingBracket.isEmpty()) {
            if (array.isOriginallyMultiLine()) {
                writer.write("\n]");
            } else {
                writer.write("]");
            }
        } else {
            writer.write(closingBracket);
        }
    }

    /**
     * FORMATTING-AWARE TUPLE WRITING
     */
    private void writeFormattingAwareTuple(TupleValue tuple) throws IOException {
        List<NDFValue> elements = tuple.getElements();

        if (elements.isEmpty()) {
            writer.write("()");
            return;
        }

        writer.write("(");
        for (int i = 0; i < elements.size(); i++) {
            NDFValue element = elements.get(i);
            writeValueContent(element);
            if (i < elements.size() - 1) {
                writer.write(", ");
            }
        }
        writer.write(")");
    }

    /**
     * FORMATTING-AWARE MAP WRITING
     */
    private void writeFormattingAwareMap(MapValue map) throws IOException {
        List<Map.Entry<NDFValue, NDFValue>> entries = map.getEntries();

        writer.write("MAP [\n");
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<NDFValue, NDFValue> entry = entries.get(i);

            writer.write("  (");
            writeValueContent(entry.getKey());
            writer.write(", ");
            writeValueContent(entry.getValue());
            writer.write(")");
            if (i < entries.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write("]");
    }

    /**
     * CLEAN ARRAY WRITING - No corruption, no token reconstruction issues
     */
    private void writeCleanArray(ArrayValue array) throws IOException {
        List<NDFValue> elements = array.getElements();

        if (elements.isEmpty()) {
            writer.write("[]");
            return;
        }

        writer.write("[\n");

        for (int i = 0; i < elements.size(); i++) {
            NDFValue element = elements.get(i);

            // Clean indentation for each element
            writer.write("  ");
            writeCleanValue(element);

            // Add comma for all but the last element
            if (i < elements.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }

        writer.write("]");
    }

    /**
     * CLEAN TUPLE WRITING
     */
    private void writeCleanTuple(TupleValue tuple) throws IOException {
        List<NDFValue> elements = tuple.getElements();

        if (elements.isEmpty()) {
            writer.write("()");
            return;
        }

        writer.write("(");
        for (int i = 0; i < elements.size(); i++) {
            NDFValue element = elements.get(i);
            writeCleanValue(element);
            if (i < elements.size() - 1) {
                writer.write(", ");
            }
        }
        writer.write(")");
    }

    /**
     * CLEAN MAP WRITING
     */
    private void writeCleanMap(MapValue map) throws IOException {
        List<Map.Entry<NDFValue, NDFValue>> entries = map.getEntries();

        writer.write("MAP [\n");
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<NDFValue, NDFValue> entry = entries.get(i);

            writer.write("  (");
            writeCleanValue(entry.getKey());
            writer.write(", ");
            writeCleanValue(entry.getValue());
            writer.write(")");
            if (i < entries.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write("]");
    }

    /**
     * CLEAN RGBA WRITING - Special compact format for RGBA objects
     */
    private void writeCleanRGBA(ObjectValue rgbaObject) throws IOException {
        writer.write("RGBA[");

        // Find the array property (usually "values" or similar)
        NDFValue arrayValue = null;
        for (NDFValue value : rgbaObject.getProperties().values()) {
            if (value instanceof ArrayValue) {
                arrayValue = value;
                break;
            }
        }

        if (arrayValue instanceof ArrayValue) {
            ArrayValue array = (ArrayValue) arrayValue;
            List<NDFValue> elements = array.getElements();

            for (int i = 0; i < elements.size(); i++) {
                NDFValue element = elements.get(i);
                if (element instanceof NumberValue) {
                    NumberValue numberValue = (NumberValue) element;
                    // Write as integer if it was originally an integer
                    if (numberValue.wasOriginallyInteger()) {
                        writer.write(String.valueOf(numberValue.getIntValue()));
                    } else {
                        writer.write(numberValue.toString());
                    }
                } else {
                    writer.write(element.toString());
                }

                if (i < elements.size() - 1) {
                    writer.write(",");
                }
            }
        }

        writer.write("]");
    }

    /**
     * CLEAN OBJECT WRITING
     */
    private void writeCleanObject(ObjectValue object) throws IOException {
        // Write type name
        writer.write(object.getTypeName());

        // Write object content using clean method
        writeCleanObjectFromMemoryModel(object);
    }

    // ===== STANDALONE UNITEDESCRIPTOR FUNCTIONALITY =====
    // Specialized writing for UniteDescriptor.ndf without affecting other files

    /**
     * Check if we're writing a UniteDescriptor file
     */
    private boolean isUniteDescriptorFile() {
        // Simple heuristic: check if any object has TEntityDescriptor type
        // This is safe and additive - won't affect other files
        List<ObjectValue> objects = getAllObjects();
        for (NDFValue.ObjectValue obj : objects) {
            if ("TEntityDescriptor".equals(obj.getTypeName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * STANDALONE UniteDescriptor ModulesDescriptors array writing
     * Handles the unique patterns: named assignments, template refs, objects
     */
    private void writeUniteDescriptorModulesArray(NDFValue.ArrayValue array) throws IOException {
        List<NDFValue> elements = array.getElements();

        if (elements.isEmpty()) {
            writer.write("[]");
            return;
        }

        writer.write("[\n");

        for (int i = 0; i < elements.size(); i++) {
            NDFValue element = elements.get(i);

            // Clean indentation for each element
            writer.write("    ");
            writeUniteDescriptorModuleElement(element);

            // Add comma for all but the last element
            if (i < elements.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }

        writer.write("  ]");
    }

    /**
     * Write individual module elements with UniteDescriptor-specific formatting
     */
    private void writeUniteDescriptorModuleElement(NDFValue element) throws IOException {
        if (element instanceof NDFValue.TemplateRefValue) {
            NDFValue.TemplateRefValue templateRef = (NDFValue.TemplateRefValue) element;
            if (templateRef.getInstanceName() != null) {
                // Named assignment: "FacingInfos is ~/FacingInfosModuleDescriptor"
                writer.write(templateRef.getInstanceName());
                writer.write(" is ");
                writer.write(templateRef.getPath());
            } else {
                // Simple template ref: "~/TargetManagerModuleSelector"
                writer.write(templateRef.getPath());
            }
        } else if (element instanceof NDFValue.ObjectValue) {
            NDFValue.ObjectValue objectValue = (NDFValue.ObjectValue) element;
            if (objectValue.getInstanceName() != null) {
                // Named assignment: "ApparenceModel is VehicleApparenceModuleDescriptor(...)"
                writer.write(objectValue.getInstanceName());
                writer.write(" is ");
                writer.write(objectValue.getTypeName());
                writeCleanObjectFromMemoryModel(objectValue);
            } else {
                // Simple object: "TTagsModuleDescriptor(...)"
                writer.write(objectValue.getTypeName());
                writeCleanObjectFromMemoryModel(objectValue);
            }
        } else {
            // Fallback to standard clean value writing
            writeCleanValue(element);
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
                // CRITICAL FIX: Use CLEAN writing system for modified constant definitions
                // This completely avoids all array corruption issues
                writer.write(instanceName);
                writer.write(" is ");

                if (value != null) {
                    writeCleanValue(value);
                }
                writer.write("\n");
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
            writeCleanValue(value);
            writer.write("\n");
        } else if (instanceName != null && typeName != null && !"ConstantDefinition".equals(typeName)) {
            // Fallback: Object constant definition when no token range
            writer.write(instanceName);
            writer.write(" is ");
            writer.write(typeName);
            writeCleanObjectFromMemoryModel(constantDef);
        } else {
            // Final fallback
            if (instanceName != null) {
                writer.write(instanceName);
                writer.write(" is ");
            }
            if (value != null) {
                writeCleanValue(value);
            }
            writer.write("\n");
        }
    }

    // REMOVED: writeConstantDefinitionWithArrayPreservation - no longer needed with clean system

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




    // REMOVED: Old writeArrayElementContent method - was part of the corrupted system

    // REMOVED: Old writeObjectContent method - replaced by writeCleanObjectFromMemoryModel
    // This method was part of the corrupted system and is no longer used


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


    /**
     * Determines if we're writing a complete file or just individual objects
     * This is used to decide whether to use writeExact (which includes gap tokens) or memory model approach
     */
    private boolean isCompleteFileWrite(List<ObjectValue> ndfObjects) {
        // writeExact is designed for complete file writes and includes gap tokens from file start

        // If we're writing only a few objects, it's definitely individual object writing
        if (ndfObjects.size() <= 10) {
            return false;
        }

        // If we don't have original tokens, we can't use writeExact anyway
        if (originalTokens == null || originalTokens.isEmpty()) {
            return false;
        }

        // For larger writes, we need to be more careful
        // Only consider it a complete file write if we're writing a substantial portion
        // and the objects appear to be in file order

        // If we're writing more than 100 objects, it's likely a complete file
        if (ndfObjects.size() > 100) {
            return true;
        }

        // Otherwise, err on the side of caution and use memory model approach
        return false;
    }

    /**
     * Determines if an ObjectValue is an array element (should not have instance name written)
     * Array elements are objects that don't have instance names or have instance names that are null
     */
    private boolean isArrayElement(ObjectValue objectValue) {
        // Array elements typically don't have instance names
        // or have instance names that are just type descriptors
        String instanceName = objectValue.getInstanceName();

        // If no instance name, it's likely an array element
        if (instanceName == null) {
            return true;
        }

        // If instance name is just the type name, it's likely an array element
        String typeName = objectValue.getTypeName();
        if (instanceName.equals(typeName)) {
            return true;
        }

        // If instance name starts with the type name (like TDepictionDescriptor_1), it's likely an array element
        if (instanceName.startsWith(typeName)) {
            return true;
        }

        // Otherwise, it's a top-level object
        return false;
    }



    private void writeIndent() throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            writer.write(INDENT);
        }
    }
}
