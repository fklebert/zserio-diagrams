package zserio.extension.diagram.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central model holding all type information and relationships for diagram generation.
 */
public class DiagramModel
{
    private final Map<String, TypeInfo> types;
    private final Set<RelationshipInfo> relationships;
    private final Map<String, List<TypeInfo>> typesByPackage;

    /**
     * Creates a new diagram model.
     */
    public DiagramModel()
    {
        this.types = new LinkedHashMap<>();
        this.relationships = new LinkedHashSet<>();
        this.typesByPackage = new LinkedHashMap<>();
    }

    /**
     * Adds a type to the model.
     *
     * @param typeInfo The type info to add.
     */
    public void addType(TypeInfo typeInfo)
    {
        types.put(typeInfo.getFullName(), typeInfo);

        String packageName = typeInfo.getPackageName();
        if (packageName == null)
        {
            packageName = "";
        }

        typesByPackage.computeIfAbsent(packageName, k -> new ArrayList<>()).add(typeInfo);
    }

    /**
     * Adds a relationship to the model.
     *
     * @param relationship The relationship to add.
     */
    public void addRelationship(RelationshipInfo relationship)
    {
        relationships.add(relationship);
    }

    /**
     * Gets a type by its full name.
     *
     * @param fullName The fully qualified type name.
     * @return The type info, or null if not found.
     */
    public TypeInfo getType(String fullName)
    {
        return types.get(fullName);
    }

    /**
     * Gets all types in the model.
     *
     * @return An unmodifiable collection of all types.
     */
    public List<TypeInfo> getAllTypes()
    {
        return Collections.unmodifiableList(new ArrayList<>(types.values()));
    }

    /**
     * Gets all relationships in the model.
     *
     * @return An unmodifiable set of all relationships.
     */
    public Set<RelationshipInfo> getAllRelationships()
    {
        return Collections.unmodifiableSet(relationships);
    }

    /**
     * Gets all package names in the model.
     *
     * @return An unmodifiable set of package names.
     */
    public Set<String> getPackageNames()
    {
        return Collections.unmodifiableSet(typesByPackage.keySet());
    }

    /**
     * Gets all types in a specific package.
     *
     * @param packageName The package name.
     * @return An unmodifiable list of types in the package.
     */
    public List<TypeInfo> getTypesInPackage(String packageName)
    {
        List<TypeInfo> packageTypes = typesByPackage.get(packageName);
        if (packageTypes == null)
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(packageTypes);
    }

    /**
     * Gets relationships for a specific source type.
     *
     * @param sourceFullName The full name of the source type.
     * @return A list of relationships from this source type.
     */
    public List<RelationshipInfo> getRelationshipsFrom(String sourceFullName)
    {
        List<RelationshipInfo> result = new ArrayList<>();
        for (RelationshipInfo rel : relationships)
        {
            if (rel.getSourceType().equals(sourceFullName))
            {
                result.add(rel);
            }
        }
        return result;
    }

    /**
     * Gets relationships involving types in a specific package.
     *
     * @param packageName The package name.
     * @return A set of relationships involving types in this package.
     */
    public Set<RelationshipInfo> getRelationshipsInPackage(String packageName)
    {
        Set<RelationshipInfo> result = new LinkedHashSet<>();
        List<TypeInfo> packageTypes = typesByPackage.get(packageName);
        if (packageTypes == null)
        {
            return result;
        }

        Set<String> typeNames = new LinkedHashSet<>();
        for (TypeInfo type : packageTypes)
        {
            typeNames.add(type.getFullName());
        }

        for (RelationshipInfo rel : relationships)
        {
            if (typeNames.contains(rel.getSourceType()) || typeNames.contains(rel.getTargetType()))
            {
                result.add(rel);
            }
        }
        return result;
    }

    /**
     * Checks if a type exists in the model.
     *
     * @param fullName The fully qualified type name.
     * @return True if the type exists.
     */
    public boolean hasType(String fullName)
    {
        return types.containsKey(fullName);
    }

    /**
     * Gets the number of types in the model.
     *
     * @return The type count.
     */
    public int getTypeCount()
    {
        return types.size();
    }

    /**
     * Gets the number of relationships in the model.
     *
     * @return The relationship count.
     */
    public int getRelationshipCount()
    {
        return relationships.size();
    }
}
