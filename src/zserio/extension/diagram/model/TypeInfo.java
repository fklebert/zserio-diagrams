package zserio.extension.diagram.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a zserio type for diagram generation.
 */
public class TypeInfo
{
    private final String name;
    private final String packageName;
    private final TypeKind kind;
    private final String documentation;
    private final List<FieldInfo> fields;
    private final List<EnumItemInfo> enumItems;
    private final List<String> parameters;
    private String selectorExpression;
    private String aliasedTypeName;
    private String aliasedTypeFullName;
    private String constantValue;
    private String underlyingType;

    /**
     * Creates a new type info.
     *
     * @param name The type name.
     * @param packageName The package name.
     * @param kind The type kind.
     * @param documentation The documentation string.
     */
    public TypeInfo(String name, String packageName, TypeKind kind, String documentation)
    {
        this.name = name;
        this.packageName = packageName;
        this.kind = kind;
        this.documentation = documentation;
        this.fields = new ArrayList<>();
        this.enumItems = new ArrayList<>();
        this.parameters = new ArrayList<>();
    }

    public String getName()
    {
        return name;
    }

    public String getPackageName()
    {
        return packageName;
    }

    /**
     * Gets the fully qualified name of this type.
     *
     * @return The fully qualified name.
     */
    public String getFullName()
    {
        if (packageName == null || packageName.isEmpty())
        {
            return name;
        }
        return packageName + "." + name;
    }

    public TypeKind getKind()
    {
        return kind;
    }

    public String getDocumentation()
    {
        return documentation;
    }

    public List<FieldInfo> getFields()
    {
        return Collections.unmodifiableList(fields);
    }

    public void addField(FieldInfo field)
    {
        fields.add(field);
    }

    public List<EnumItemInfo> getEnumItems()
    {
        return Collections.unmodifiableList(enumItems);
    }

    public void addEnumItem(EnumItemInfo item)
    {
        enumItems.add(item);
    }

    public List<String> getParameters()
    {
        return Collections.unmodifiableList(parameters);
    }

    public void addParameter(String parameter)
    {
        parameters.add(parameter);
    }

    public String getSelectorExpression()
    {
        return selectorExpression;
    }

    public void setSelectorExpression(String selectorExpression)
    {
        this.selectorExpression = selectorExpression;
    }

    public String getAliasedTypeName()
    {
        return aliasedTypeName;
    }

    public void setAliasedTypeName(String aliasedTypeName)
    {
        this.aliasedTypeName = aliasedTypeName;
    }

    public String getAliasedTypeFullName()
    {
        return aliasedTypeFullName;
    }

    public void setAliasedTypeFullName(String aliasedTypeFullName)
    {
        this.aliasedTypeFullName = aliasedTypeFullName;
    }

    public String getConstantValue()
    {
        return constantValue;
    }

    public void setConstantValue(String constantValue)
    {
        this.constantValue = constantValue;
    }

    public String getUnderlyingType()
    {
        return underlyingType;
    }

    public void setUnderlyingType(String underlyingType)
    {
        this.underlyingType = underlyingType;
    }
}
