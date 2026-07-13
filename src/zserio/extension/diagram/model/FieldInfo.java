package zserio.extension.diagram.model;

/**
 * Represents a field within a zserio compound type.
 */
public class FieldInfo
{
    private final String name;
    private final String typeName;
    private final String fullTypeName;
    private final boolean isOptional;
    private final boolean isArray;
    private final String arrayLength;
    private final boolean isPrimitiveType;

    /**
     * Creates a new field info.
     *
     * @param name The field name.
     * @param typeName The simple type name.
     * @param fullTypeName The fully qualified type name.
     * @param isOptional Whether the field is optional.
     * @param isArray Whether the field is an array.
     * @param arrayLength The array length expression (null for dynamic arrays).
     * @param isPrimitiveType Whether the field type is a primitive type.
     */
    public FieldInfo(String name, String typeName, String fullTypeName, boolean isOptional,
            boolean isArray, String arrayLength, boolean isPrimitiveType)
    {
        this.name = name;
        this.typeName = typeName;
        this.fullTypeName = fullTypeName;
        this.isOptional = isOptional;
        this.isArray = isArray;
        this.arrayLength = arrayLength;
        this.isPrimitiveType = isPrimitiveType;
    }

    public String getName()
    {
        return name;
    }

    public String getTypeName()
    {
        return typeName;
    }

    public String getFullTypeName()
    {
        return fullTypeName;
    }

    public boolean isOptional()
    {
        return isOptional;
    }

    public boolean isArray()
    {
        return isArray;
    }

    public String getArrayLength()
    {
        return arrayLength;
    }

    public boolean isPrimitiveType()
    {
        return isPrimitiveType;
    }

    /**
     * Gets the cardinality string for relationship diagrams.
     *
     * @return The cardinality string (e.g., "1", "0..1", "*", "1..*").
     */
    public String getCardinality()
    {
        if (isArray)
        {
            if (isOptional)
            {
                return "*";
            }
            else if (arrayLength != null && !arrayLength.isEmpty())
            {
                // Fixed size array
                return arrayLength;
            }
            else
            {
                return "*";
            }
        }
        else
        {
            return isOptional ? "0..1" : "1";
        }
    }

    /**
     * Gets the display type name with array notation if applicable.
     *
     * @return The display type name.
     */
    public String getDisplayTypeName()
    {
        if (isArray)
        {
            return typeName + "[]";
        }
        return typeName;
    }
}
