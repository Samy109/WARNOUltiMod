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


    /**
     * Legacy method - tokens are no longer used for writing.
     * Line-based replacement is now used instead via setOriginalSourceContent().
     * Kept for API compatibility with existing callers.
     */
    public void setOriginalTokens(List<NDFToken> tokens) {
        // Intentional no-op: line-based writing uses originalSourceContent instead
    }

    /**
     * Legacy method - object modification tracking is now handled by ModificationTracker.
     * Kept for API compatibility with existing callers.
     */
    public void markObjectAsModified(ObjectValue object) {
        // Intentional no-op: modifications are tracked via ModificationTracker
    }

    public void setOriginalSourceContent(String content) {
        this.originalSourceContent = content;
    }

    public void setModificationTracker(ModificationTracker tracker) {
        this.modificationTracker = tracker;
    }


    public void write(List<ObjectValue> ndfObjects) throws IOException {
        if (originalSourceContent == null || modificationTracker == null) {
            throw new IllegalStateException("Line-based writing requires originalSourceContent and modificationTracker");
        }

        writeWithLineBasedReplacement(ndfObjects);
    }









    private void writeWithLineBasedReplacement(List<ObjectValue> ndfObjects) throws IOException {
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

            writeValueWithFormatting(value);

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
        writeValue(value, false);
    }

    private void writeCleanValue(NDFValue value) throws IOException {
        writeValue(value, true);
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

        String openingBracket = array.getOriginalOpeningBracket();
        if (openingBracket.isEmpty()) {
            openingBracket = "[";
        }
        writer.write(openingBracket);

        for (int i = 0; i < elements.size(); i++) {
            NDFValue element = elements.get(i);

            String elementPrefix = array.getOriginalElementPrefix(i);
            if (!elementPrefix.isEmpty()) {
                writer.write(elementPrefix);
            } else if (array.isOriginallyMultiLine()) {
                writer.write("\n  ");
            }

            writeValueContent(element);

            String elementSuffix = array.getOriginalElementSuffix(i);
            if (!elementSuffix.isEmpty()) {
                writer.write(elementSuffix);
            } else {
                if (i < elements.size() - 1) {
                    writer.write(",");
                }
                if (array.isOriginallyMultiLine()) {
                    writer.write("\n");
                }
            }
        }

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

            writer.write("  ");
            writeCleanValue(element);

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

    private void writeCleanRGBA(ObjectValue rgbaObject) throws IOException {
        writer.write("RGBA[");

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

    private void writeCleanObject(ObjectValue object) throws IOException {
        writer.write(object.getTypeName());
        writeCleanObjectFromMemoryModel(object);
    }

    /**
     * Writes a number value to the output, carefully preserving float values
     * @param numberValue The NumberValue to write
     * @throws IOException If an I/O error occurs
     */
    private void writeNumberValue(NumberValue numberValue) throws IOException {
        // If there's an original format, try to use it for exact reproduction
        if (numberValue.getOriginalFormat() != null) {
            writer.write(numberValue.getOriginalFormat());
            return;
        }

        double value = numberValue.getValue();

        // Only convert to integer if it was originally an integer
        if (numberValue.wasOriginallyInteger()) {
            writer.write(Integer.toString((int) Math.round(value)));
        } else {
            // For floating point values, preserve the decimal point
            String formattedValue = formatDecimalNumber(value);
            writer.write(formattedValue);
        }
    }

    /**
     * Formats a decimal number to preserve precision while avoiding scientific notation
     * when possible
     * @param value The double value to format
     * @return A formatted string representation
     */
    private String formatDecimalNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }

        // For reasonable ranges, avoid scientific notation
        if (Math.abs(value) >= 1e-15 && Math.abs(value) < 1e15) {
            java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value);
            String result = bd.stripTrailingZeros().toPlainString();

            // Make sure we always have a decimal point for float values
            if (!result.contains(".")) {
                result += ".0";
            }

            return result;
        }

        // For very large or very small numbers, use scientific notation
        return Double.toString(value);
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
