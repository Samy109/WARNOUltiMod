package com.warnomodmaker.parser;

import com.warnomodmaker.model.NDFValue;
import com.warnomodmaker.model.NDFValue.*;
import com.warnomodmaker.model.ModificationTracker;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NDFWriter {
    private final Writer writer;
    private boolean preserveFormatting;
    private String originalSourceContent;
    private ModificationTracker modificationTracker;


    public NDFWriter(Writer writer) {
        this(writer, true);
    }


    public NDFWriter(Writer writer, boolean preserveFormatting) {
        this.writer = writer;
        this.preserveFormatting = preserveFormatting;
    }


    public void setOriginalTokens(List<NDFToken> tokens) {
        // Keep for compatibility with MainWindow, but not used in line-based approach
    }

    public void markObjectAsModified(ObjectValue object) {
        // Keep for compatibility with MainWindow, but not used in line-based approach
    }

    public void setOriginalSourceContent(String content) {
        this.originalSourceContent = content;
    }

    public void setModificationTracker(ModificationTracker tracker) {
        this.modificationTracker = tracker;
    }


    public void write(List<ObjectValue> ndfObjects) throws IOException {
        // ONLY line-based writing - NO FALLBACKS ALLOWED
        if (originalSourceContent == null || modificationTracker == null) {
            throw new IllegalStateException("Line-based writing requires originalSourceContent and modificationTracker - NO FALLBACKS ALLOWED");
        }

        writeWithLineBasedReplacement(ndfObjects);
    }









    private void writeWithLineBasedReplacement(List<ObjectValue> ndfObjects) throws IOException {
        // NO FALLBACKS - line-based writing ONLY
        LineBasedWriter lineWriter = new LineBasedWriter(writer, originalSourceContent, modificationTracker);
        lineWriter.write(ndfObjects);
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
            writer.write("export ");
            writer.write(instanceName);
            writer.write(" is ");
            writer.write(ndfObject.getTypeName());
            writer.write("\n");
            writeCleanObjectFromMemoryModel(ndfObject);
        } else {
            writer.write(instanceName);
            writer.write(" is ");
            writer.write(ndfObject.getTypeName());
            writer.write("\n");
            writeCleanObjectFromMemoryModel(ndfObject);
        }
    }


    private void writeCleanObjectFromMemoryModel(NDFValue.ObjectValue object) throws IOException {
        String openingParen = object.getOriginalOpeningParen();
        if (openingParen.isEmpty()) {
            writer.write("(\n");
        } else {
            writer.write(openingParen);
        }

        Map<String, NDFValue> properties = object.getProperties();

        for (Map.Entry<String, NDFValue> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            NDFValue value = entry.getValue();

            String propertyPrefix = object.getOriginalPropertyPrefix(propertyName);
            String propertyEquals = object.getOriginalPropertyEquals(propertyName);
            String propertySuffix = object.getOriginalPropertySuffix(propertyName);
            if (!propertyPrefix.isEmpty() || !propertyEquals.isEmpty() || !propertySuffix.isEmpty()) {
                writer.write(propertyPrefix);
                writer.write(propertyName);
                writer.write(propertyEquals);
            } else {
                writer.write("\n    ");
                writer.write(propertyName);
                writer.write("=");
            }

            if ("ModulesDescriptors".equals(propertyName) && value instanceof NDFValue.ArrayValue && isUniteDescriptorFile()) {
                writeUniteDescriptorModulesArray((NDFValue.ArrayValue) value);
            } else {
                writeValueWithFormatting(value);
            }

            if (!propertySuffix.isEmpty()) {
                writer.write(propertySuffix);
            } else {
                writer.write("\n");
            }
        }

        String closingParen = object.getOriginalClosingParen();
        if (closingParen.isEmpty()) {
            writer.write(")");
        } else {
            writer.write(closingParen);
        }
    }


    private void writeValueWithFormatting(NDFValue value) throws IOException {
        if (value.hasOriginalFormatting()) {
            writer.write(value.getOriginalPrefix());
            writeValueContent(value);
            writer.write(value.getOriginalSuffix());
        } else {
            writeCleanValue(value);
        }
    }


    private void writeValueContent(NDFValue value) throws IOException {
        writeValue(value, false); // Use formatting-aware writing
    }

    private void writeCleanValue(NDFValue value) throws IOException {
        writeValue(value, true); // Use clean writing
    }

    private void writeValue(NDFValue value, boolean useCleanFormatting) throws IOException {
        switch (value.getType()) {
            case STRING:
                StringValue stringValue = (StringValue) value;
                if (stringValue.useDoubleQuotes()) {
                    writer.write("\"");
                    writer.write(escapeStringValue(stringValue.getValue(), true));
                    writer.write("\"");
                } else {
                    writer.write("'");
                    writer.write(escapeStringValue(stringValue.getValue(), false));
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
                if (templateRefValue.getInstanceName() != null && !templateRefValue.getInstanceName().trim().isEmpty()) {
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
                writer.write(enumValue.toString());
                break;

            case RAW_EXPRESSION:
                RawExpressionValue rawValue = (RawExpressionValue) value;
                writer.write(rawValue.getExpression());
                break;

            case ARRAY:
                ArrayValue arrayValue = (ArrayValue) value;
                if (useCleanFormatting) {
                    writeCleanArray(arrayValue);
                } else {
                    writeFormattingAwareArray(arrayValue);
                }
                break;

            case TUPLE:
                TupleValue tupleValue = (TupleValue) value;
                if (useCleanFormatting) {
                    writeCleanTuple(tupleValue);
                } else {
                    writeFormattingAwareTuple(tupleValue);
                }
                break;

            case MAP:
                MapValue mapValue = (MapValue) value;
                if (useCleanFormatting) {
                    writeCleanMap(mapValue);
                } else {
                    writeFormattingAwareMap(mapValue);
                }
                break;

            case OBJECT:
                ObjectValue objectValue = (ObjectValue) value;
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
    // Specialized writing for UniteDescriptorOLD.ndf without affecting other files

    /**
     * Check if we're writing a UniteDescriptor file
     */
    private boolean isUniteDescriptorFile() {
        // Simple heuristic: always return false since we're using line-based approach
        // The line-based writer handles all formatting correctly
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
                writeCleanObjectFromMemoryModelInline(objectValue); // Use inline formatting for array elements
            } else {
                // Simple object: "TTagsModuleDescriptor(...)"
                writer.write(objectValue.getTypeName());
                writeCleanObjectFromMemoryModelInline(objectValue); // Use inline formatting for array elements
            }
        } else {
            writeCleanValue(element);
        }
    }

    /**
     * Write object content inline (without newline before opening parenthesis)
     * Used for array elements where the type name and content should be on the same line
     */
    private void writeCleanObjectFromMemoryModelInline(ObjectValue object) throws IOException {
        Map<String, NDFValue> properties = object.getProperties();

        if (properties.isEmpty()) {
            writer.write("()");
            return;
        }

        writer.write("(\n");

        boolean first = true;
        for (Map.Entry<String, NDFValue> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            NDFValue propertyValue = entry.getValue();

            if (!first) {
                writer.write("\n");
            }
            first = false;

            // Write property with proper indentation for array context
            writer.write("      "); // Extra indentation for array element properties
            writer.write(propertyName);
            writer.write(" = ");
            writeCleanValue(propertyValue);
        }

        writer.write("\n    )");
    }

    /**
     * Escape string values to prevent parsing issues
     */
    private String escapeStringValue(String value, boolean useDoubleQuotes) {
        if (value == null) {
            return "";
        }

        if (useDoubleQuotes) {
            // Escape double quotes and backslashes
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        } else {
            // Escape single quotes and backslashes
            return value.replace("\\", "\\\\").replace("'", "\\'");
        }
    }

    private void writeConstantDefinition(ObjectValue constantDef) throws IOException {
        String instanceName = constantDef.getInstanceName();
        String typeName = constantDef.getTypeName();
        NDFValue value = constantDef.getProperties().get("Value");

        if (instanceName != null && "ConstantDefinition".equals(typeName) && value != null) {
            writer.write(instanceName);
            writer.write(" is ");
            writeCleanValue(value);
            writer.write("\n");
        } else if (instanceName != null && typeName != null && !"ConstantDefinition".equals(typeName)) {
            writer.write(instanceName);
            writer.write(" is ");
            writer.write(typeName);
            writeCleanObjectFromMemoryModel(constantDef);
        } else {
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





















}
