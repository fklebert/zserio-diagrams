package zserio.extension.diagram;

import java.math.BigInteger;
import java.util.Set;

import zserio.ast.ArrayInstantiation;
import zserio.ast.BitmaskType;
import zserio.ast.BitmaskValue;
import zserio.ast.BuiltInType;
import zserio.ast.ChoiceCase;
import zserio.ast.ChoiceCaseExpression;
import zserio.ast.ChoiceDefault;
import zserio.ast.ChoiceType;
import zserio.ast.CompoundType;
import zserio.ast.Constant;
import zserio.ast.DynamicBitFieldInstantiation;
import zserio.ast.EnumItem;
import zserio.ast.EnumType;
import zserio.ast.Expression;
import zserio.ast.Field;
import zserio.ast.FixedBitFieldType;
import zserio.ast.Package;
import zserio.ast.Parameter;
import zserio.ast.StructureType;
import zserio.ast.Subtype;
import zserio.ast.TypeInstantiation;
import zserio.ast.TypeReference;
import zserio.ast.UnionType;
import zserio.ast.ZserioType;
import zserio.extension.common.DefaultTreeWalker;
import zserio.extension.common.ZserioExtensionException;
import zserio.extension.diagram.model.DiagramModel;
import zserio.extension.diagram.model.EnumItemInfo;
import zserio.extension.diagram.model.FieldInfo;
import zserio.extension.diagram.model.RelationshipInfo;
import zserio.extension.diagram.model.TypeInfo;
import zserio.extension.diagram.model.TypeKind;

/**
 * AST walker that collects type information for diagram generation.
 */
public class DiagramEmitter extends DefaultTreeWalker
{
    private final DiagramModel model;
    private final Set<String> packageFilters;
    private final boolean includeSubtypes;
    private final boolean includeConstants;
    private String currentPackage;

    /**
     * Creates a new diagram emitter.
     *
     * @param packageFilters Set of package names to include (empty means all packages).
     * @param includeSubtypes Whether to include subtypes.
     * @param includeConstants Whether to include constants.
     */
    public DiagramEmitter(Set<String> packageFilters, boolean includeSubtypes, boolean includeConstants)
    {
        this.model = new DiagramModel();
        this.packageFilters = packageFilters;
        this.includeSubtypes = includeSubtypes;
        this.includeConstants = includeConstants;
        this.currentPackage = "";
    }

    /**
     * Gets the collected diagram model.
     *
     * @return The diagram model.
     */
    public DiagramModel getModel()
    {
        return model;
    }

    @Override
    public boolean traverseTemplateInstantiations()
    {
        return true;
    }

    @Override
    public void beginPackage(Package packageToken) throws ZserioExtensionException
    {
        currentPackage = packageToken.getPackageName().toString();
    }

    private boolean isPackageIncluded()
    {
        if (packageFilters.isEmpty())
        {
            return true;
        }
        for (String filter : packageFilters)
        {
            if (currentPackage.equals(filter) || currentPackage.startsWith(filter + "."))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public void beginStructure(StructureType structureType) throws ZserioExtensionException
    {
        if (!isPackageIncluded())
        {
            return;
        }

        TypeInfo typeInfo = createTypeInfo(structureType.getName(), TypeKind.STRUCT);
        addParameters(typeInfo, structureType);
        processCompoundFields(typeInfo, structureType);
        model.addType(typeInfo);
    }

    @Override
    public void beginChoice(ChoiceType choiceType) throws ZserioExtensionException
    {
        if (!isPackageIncluded())
        {
            return;
        }

        TypeInfo typeInfo = createTypeInfo(choiceType.getName(), TypeKind.CHOICE);
        addParameters(typeInfo, choiceType);

        // Set selector expression
        Expression selectorExpr = choiceType.getSelectorExpression();
        if (selectorExpr != null)
        {
            typeInfo.setSelectorExpression(selectorExpr.toString());
        }

        // Process choice cases
        for (ChoiceCase choiceCase : choiceType.getChoiceCases())
        {
            for (ChoiceCaseExpression caseExpr : choiceCase.getExpressions())
            {
                // Case expressions are part of the choice
            }
            Field field = choiceCase.getField();
            if (field != null)
            {
                processField(typeInfo, field);
            }
        }

        // Process default case
        ChoiceDefault defaultCase = choiceType.getChoiceDefault();
        if (defaultCase != null)
        {
            Field defaultField = defaultCase.getField();
            if (defaultField != null)
            {
                processField(typeInfo, defaultField);
            }
        }

        model.addType(typeInfo);
    }

    @Override
    public void beginUnion(UnionType unionType) throws ZserioExtensionException
    {
        if (!isPackageIncluded())
        {
            return;
        }

        TypeInfo typeInfo = createTypeInfo(unionType.getName(), TypeKind.UNION);
        addParameters(typeInfo, unionType);
        processCompoundFields(typeInfo, unionType);
        model.addType(typeInfo);
    }

    @Override
    public void beginEnumeration(EnumType enumType) throws ZserioExtensionException
    {
        if (!isPackageIncluded())
        {
            return;
        }

        TypeInfo typeInfo = createTypeInfo(enumType.getName(), TypeKind.ENUM);
        typeInfo.setUnderlyingType(getTypeDisplayName(enumType.getTypeInstantiation()));

        for (EnumItem item : enumType.getItems())
        {
            String value = null;
            if (item.getValueExpression() != null)
            {
                BigInteger bigValue = item.getValue();
                if (bigValue != null)
                {
                    value = bigValue.toString();
                }
            }
            typeInfo.addEnumItem(new EnumItemInfo(item.getName(), value));
        }

        model.addType(typeInfo);
    }

    @Override
    public void beginBitmask(BitmaskType bitmaskType) throws ZserioExtensionException
    {
        if (!isPackageIncluded())
        {
            return;
        }

        TypeInfo typeInfo = createTypeInfo(bitmaskType.getName(), TypeKind.BITMASK);
        typeInfo.setUnderlyingType(getTypeDisplayName(bitmaskType.getTypeInstantiation()));

        for (BitmaskValue value : bitmaskType.getValues())
        {
            String valueStr = null;
            if (value.getValueExpression() != null)
            {
                BigInteger bigValue = value.getValue();
                if (bigValue != null)
                {
                    valueStr = bigValue.toString();
                }
            }
            typeInfo.addEnumItem(new EnumItemInfo(value.getName(), valueStr));
        }

        model.addType(typeInfo);
    }

    @Override
    public void beginSubtype(Subtype subtype) throws ZserioExtensionException
    {
        if (!isPackageIncluded() || !includeSubtypes)
        {
            return;
        }

        TypeInfo typeInfo = createTypeInfo(subtype.getName(), TypeKind.SUBTYPE);

        TypeReference typeRef = subtype.getTypeReference();
        ZserioType baseType = typeRef.getType();
        typeInfo.setAliasedTypeName(baseType.getName());
        typeInfo.setAliasedTypeFullName(getFullTypeName(baseType));

        model.addType(typeInfo);

        // Add inheritance relationship
        String targetFullName = getFullTypeName(baseType);
        if (!isPrimitiveType(baseType))
        {
            model.addRelationship(new RelationshipInfo(typeInfo.getFullName(), targetFullName, "",
                    "", RelationshipInfo.RelationshipType.INHERITANCE));
        }
    }

    @Override
    public void beginConst(Constant constant) throws ZserioExtensionException
    {
        if (!isPackageIncluded() || !includeConstants)
        {
            return;
        }

        TypeInfo typeInfo = createTypeInfo(constant.getName(), TypeKind.CONSTANT);
        typeInfo.setUnderlyingType(getTypeDisplayName(constant.getTypeInstantiation()));

        Expression valueExpr = constant.getValueExpression();
        if (valueExpr != null)
        {
            typeInfo.setConstantValue(getExpressionDisplayValue(valueExpr));
        }

        model.addType(typeInfo);
    }

    private String getExpressionDisplayValue(Expression expression)
    {
        BigInteger integerValue = expression.getIntegerValue();
        if (integerValue != null)
        {
            return integerValue.toString();
        }

        String stringValue = expression.getStringValue();
        if (stringValue != null)
        {
            return "\"" + stringValue + "\"";
        }

        return expression.getText();
    }

    private TypeInfo createTypeInfo(String name, TypeKind kind)
    {
        return new TypeInfo(name, currentPackage, kind, "");
    }

    private void addParameters(TypeInfo typeInfo, CompoundType compoundType)
    {
        for (Parameter param : compoundType.getTypeParameters())
        {
            String paramStr = getTypeDisplayName(param.getTypeReference()) + " " + param.getName();
            typeInfo.addParameter(paramStr);
        }
    }

    private void processCompoundFields(TypeInfo typeInfo, CompoundType compoundType)
    {
        for (Field field : compoundType.getFields())
        {
            processField(typeInfo, field);
        }
    }

    private void processField(TypeInfo typeInfo, Field field)
    {
        TypeInstantiation typeInst = field.getTypeInstantiation();
        boolean isArray = typeInst instanceof ArrayInstantiation;
        boolean isOptional = field.isOptional();
        String arrayLength = null;

        TypeInstantiation elementTypeInst = typeInst;
        if (isArray)
        {
            ArrayInstantiation arrayInst = (ArrayInstantiation)typeInst;
            elementTypeInst = arrayInst.getElementTypeInstantiation();
            Expression lengthExpr = arrayInst.getLengthExpression();
            if (lengthExpr != null)
            {
                // Try to get a fixed value if the expression is a simple integer literal
                try
                {
                    java.math.BigInteger fixedSize = lengthExpr.getIntegerValue();
                    if (fixedSize != null)
                    {
                        arrayLength = fixedSize.toString();
                    }
                }
                catch (Exception e)
                {
                    // Expression is not a simple integer, leave as null for dynamic array
                }
            }
        }

        ZserioType fieldType = elementTypeInst.getType();
        String typeName = fieldType.getName();
        String fullTypeName = getFullTypeName(fieldType);
        boolean isPrimitive = isPrimitiveType(fieldType);

        // Handle dynamic bit field types (both bit<N> and int<N>)
        if (elementTypeInst instanceof DynamicBitFieldInstantiation)
        {
            DynamicBitFieldInstantiation dynBitField = (DynamicBitFieldInstantiation)elementTypeInst;
            Expression lengthExpr = dynBitField.getLengthExpression();
            String lengthText = formatExpression(lengthExpr);
            // Use "int" for signed, "bit" for unsigned dynamic bit fields
            String prefix = dynBitField.getBaseType().isSigned() ? "int" : "bit";
            typeName = prefix + ":" + lengthText;
            fullTypeName = typeName;
            isPrimitive = true;
        }

        FieldInfo fieldInfo =
                new FieldInfo(field.getName(), typeName, fullTypeName, isOptional, isArray, arrayLength, isPrimitive);
        typeInfo.addField(fieldInfo);

        // Create relationship if not primitive
        if (!isPrimitive)
        {
            RelationshipInfo.RelationshipType relType;
            if (fieldType instanceof EnumType || fieldType instanceof BitmaskType)
            {
                relType = RelationshipInfo.RelationshipType.ASSOCIATION;
            }
            else
            {
                relType = RelationshipInfo.RelationshipType.COMPOSITION;
            }

            model.addRelationship(new RelationshipInfo(typeInfo.getFullName(), fullTypeName, field.getName(),
                    fieldInfo.getCardinality(), relType));
        }
    }

    private String getFullTypeName(ZserioType type)
    {
        // BuiltInType and FixedBitFieldType don't have packages
        if (type instanceof BuiltInType || type instanceof FixedBitFieldType)
        {
            return type.getName();
        }

        try
        {
            Package pkg = type.getPackage();
            if (pkg != null)
            {
                String packageName = pkg.getPackageName().toString();
                if (packageName != null && !packageName.isEmpty())
                {
                    return packageName + "." + type.getName();
                }
            }
        }
        catch (InternalError e)
        {
            // Some types don't support getPackage()
            return type.getName();
        }
        return type.getName();
    }

    private String getTypeDisplayName(TypeInstantiation typeInst)
    {
        if (typeInst == null)
        {
            return "unknown";
        }
        ZserioType type = typeInst.getType();
        return type.getName();
    }

    private String getTypeDisplayName(TypeReference typeRef)
    {
        if (typeRef == null)
        {
            return "unknown";
        }
        return typeRef.getType().getName();
    }

    private boolean isPrimitiveType(ZserioType type)
    {
        if (type instanceof BuiltInType)
        {
            return true;
        }
        if (type instanceof FixedBitFieldType)
        {
            return true;
        }
        // Check for standard types
        String name = type.getName();
        return name.startsWith("int") || name.startsWith("uint") || name.startsWith("bit") ||
                name.equals("bool") || name.equals("string") || name.equals("float16") ||
                name.equals("float32") || name.equals("float64") || name.startsWith("varint") ||
                name.startsWith("varuint") || name.equals("varsize") || name.equals("extern") ||
                name.equals("bytes");
    }

    /**
     * Formats an Expression for display in diagrams.
     * Handles the case where Expression.toString() returns the Java object reference.
     *
     * @param expr The expression to format.
     * @return A clean string representation of the expression.
     */
    private String formatExpression(Expression expr)
    {
        if (expr == null)
        {
            return "N";
        }

        // Try to get a fixed integer value first (for constant expressions)
        try
        {
            java.math.BigInteger value = expr.getIntegerValue();
            if (value != null)
            {
                return value.toString();
            }
        }
        catch (Exception e)
        {
            // Not a simple integer expression
        }

        // Try to get the expression text
        String text = expr.getText();
        if (text != null && !text.isEmpty())
        {
            // Check if the text looks like a valid identifier or number
            // Reject Java object references (containing @) and operator-only text
            if (!text.contains("@") && isValidExpressionText(text))
            {
                return text;
            }
        }

        // Fallback: use "N" for complex or unresolvable expressions
        return "N";
    }

    /**
     * Checks if the expression text is a valid identifier or number that can be displayed.
     * Rejects operator-only text or other invalid fragments.
     *
     * @param text The expression text to validate.
     * @return true if the text is valid for display.
     */
    private boolean isValidExpressionText(String text)
    {
        if (text == null || text.isEmpty())
        {
            return false;
        }

        // Reject pure operators or symbols
        String trimmed = text.trim();
        if (trimmed.isEmpty())
        {
            return false;
        }

        // Check first character - should be letter, digit, or underscore for valid identifier/number
        char first = trimmed.charAt(0);
        if (Character.isLetterOrDigit(first) || first == '_')
        {
            return true;
        }

        // Could be a parenthesized expression like "(numBits)"
        if (first == '(' && trimmed.endsWith(")"))
        {
            return true;
        }

        return false;
    }
}
