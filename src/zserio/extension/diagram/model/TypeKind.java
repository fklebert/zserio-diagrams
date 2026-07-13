package zserio.extension.diagram.model;

/**
 * Enumeration of zserio type kinds for diagram generation.
 */
public enum TypeKind
{
    STRUCT("struct"),
    CHOICE("choice"),
    UNION("union"),
    ENUM("enum"),
    BITMASK("bitmask"),
    SUBTYPE("subtype"),
    CONSTANT("const"),
    SQL_TABLE("sql_table"),
    SQL_DATABASE("sql_database"),
    SERVICE("service"),
    PUBSUB("pubsub");

    private final String stereotype;

    TypeKind(String stereotype)
    {
        this.stereotype = stereotype;
    }

    /**
     * Gets the stereotype string for diagram output.
     *
     * @return The stereotype string.
     */
    public String getStereotype()
    {
        return stereotype;
    }
}
