package zserio.extension.diagram.model;

/**
 * Represents a relationship between two types in a diagram.
 */
public class RelationshipInfo
{
    /**
     * Types of relationships between types.
     */
    public enum RelationshipType
    {
        /** Composition relationship (struct contains another type). */
        COMPOSITION,
        /** Association relationship (reference to enum/bitmask). */
        ASSOCIATION,
        /** Inheritance/aliasing relationship (subtype). */
        INHERITANCE
    }

    private final String sourceType;
    private final String targetType;
    private final String fieldName;
    private final String cardinality;
    private final RelationshipType relationshipType;

    /**
     * Creates a new relationship info.
     *
     * @param sourceType The source type full name.
     * @param targetType The target type full name.
     * @param fieldName The field name that creates this relationship.
     * @param cardinality The cardinality string.
     * @param relationshipType The type of relationship.
     */
    public RelationshipInfo(String sourceType, String targetType, String fieldName,
            String cardinality, RelationshipType relationshipType)
    {
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.fieldName = fieldName;
        this.cardinality = cardinality;
        this.relationshipType = relationshipType;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public String getFieldName()
    {
        return fieldName;
    }

    public String getCardinality()
    {
        return cardinality;
    }

    public RelationshipType getRelationshipType()
    {
        return relationshipType;
    }

    @Override
    public int hashCode()
    {
        int result = sourceType.hashCode();
        result = 31 * result + targetType.hashCode();
        result = 31 * result + fieldName.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        RelationshipInfo other = (RelationshipInfo)obj;
        return sourceType.equals(other.sourceType) && targetType.equals(other.targetType) &&
                fieldName.equals(other.fieldName);
    }
}
