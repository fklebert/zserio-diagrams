package zserio.extension.diagram.model;

/**
 * Represents an enum item or bitmask value.
 */
public class EnumItemInfo
{
    private final String name;
    private final String value;

    /**
     * Creates a new enum item info.
     *
     * @param name The item name.
     * @param value The item value (may be null if not explicitly defined).
     */
    public EnumItemInfo(String name, String value)
    {
        this.name = name;
        this.value = value;
    }

    public String getName()
    {
        return name;
    }

    public String getValue()
    {
        return value;
    }
}
