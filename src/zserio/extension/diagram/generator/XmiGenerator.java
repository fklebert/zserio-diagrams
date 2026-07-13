package zserio.extension.diagram.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import zserio.extension.common.ZserioExtensionException;
import zserio.extension.diagram.DiagramConfig;
import zserio.extension.diagram.model.DiagramModel;
import zserio.extension.diagram.model.EnumItemInfo;
import zserio.extension.diagram.model.FieldInfo;
import zserio.extension.diagram.model.RelationshipInfo;
import zserio.extension.diagram.model.TypeInfo;
import zserio.extension.diagram.model.TypeKind;

/**
 * Generates XMI 2.1 files from the diagram model for import into UML tools
 * such as Sparx Enterprise Architect.
 */
public class XmiGenerator implements DiagramGenerator
{
    private static final String XMI_NS = "http://schema.omg.org/spec/XMI/2.1";
    private static final String UML_NS = "http://schema.omg.org/spec/UML/2.1";
    private static final String XMI_VERSION = "2.1";
    static final String ROOT_PACKAGE_ID = "pkg_root";

    private List<String> diagramRoots = new ArrayList<>();
    private int diagramDepth = Integer.MAX_VALUE;

    @Override
    public void setConfig(DiagramConfig config)
    {
        this.config = config;
    }

    @Override
    public String getFileExtension()
    {
        return ".xmi";
    }

    @Override
    public String getFormatName()
    {
        return "XMI";
    }

    public void setDiagramRoots(String roots)
    {
        diagramRoots = new ArrayList<>();
        if (roots != null && !roots.isEmpty())
        {
            for (String root : roots.split(","))
            {
                String trimmed = root.trim();
                if (!trimmed.isEmpty())
                {
                    diagramRoots.add(trimmed);
                }
            }
        }
    }

    public void setDiagramDepth(int depth)
    {
        this.diagramDepth = depth;
    }

    @Override
    public void generate(DiagramModel model, File outputDir, String baseName) throws ZserioExtensionException
    {
        File outputFile = new File(outputDir, baseName + getFileExtension());
        try
        {
            writeXmi(outputFile, model, model.getAllTypes(), model.getAllRelationships(), baseName);
            System.out.println("Generated " + getFormatName() + " file: " + outputFile.getAbsolutePath());
        }
        catch (XMLStreamException | IOException e)
        {
            throw new ZserioExtensionException("Failed to write XMI file: " + e.getMessage());
        }
    }

    @Override
    public void generatePerPackage(DiagramModel model, File outputDir) throws ZserioExtensionException
    {
        for (String packageName : model.getPackageNames())
        {
            String fileName = packageName.isEmpty() ? "default" : packageName.replace('.', '_');
            File outputFile = new File(outputDir, fileName + getFileExtension());

            List<TypeInfo> packageTypes = model.getTypesInPackage(packageName);
            Set<RelationshipInfo> packageRelationships = model.getRelationshipsInPackage(packageName);

            try
            {
                writeXmi(outputFile, model, packageTypes, packageRelationships, fileName);
                System.out.println("Generated " + getFormatName() + " file: " + outputFile.getAbsolutePath());
            }
            catch (XMLStreamException | IOException e)
            {
                throw new ZserioExtensionException("Failed to write XMI file: " + e.getMessage());
            }
        }
    }

    private void writeXmi(File outputFile, DiagramModel model, List<TypeInfo> types,
            Set<RelationshipInfo> relationships, String modelName)
            throws XMLStreamException, IOException
    {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try (FileOutputStream fos = new FileOutputStream(outputFile))
        {
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");
            try
            {
                writer.writeStartDocument("UTF-8", "1.0");
                writeNewline(writer);

                // <xmi:XMI>
                writer.writeStartElement("xmi", "XMI", XMI_NS);
                writer.writeNamespace("xmi", XMI_NS);
                writer.writeNamespace("uml", UML_NS);
                writer.writeAttribute(XMI_NS, "version", XMI_VERSION);
                writeNewline(writer);

                // EA identification — tells EA to process the Extension section
                writer.writeEmptyElement(XMI_NS, "Documentation");
                writer.writeAttribute("exporter", "Enterprise Architect");
                writer.writeAttribute("exporterVersion", "6.5");
                writer.writeAttribute("exporterID", "1710");
                writeNewline(writer);

                // <uml:Model>
                writer.writeStartElement(UML_NS, "Model");
                writer.writeAttribute(XMI_NS, "type", "uml:Model");
                writer.writeAttribute(XMI_NS, "id", "model_root");
                writer.writeAttribute("name", modelName);
                writer.writeAttribute("visibility", "public");
                writeNewline(writer);

                // Root package — EA requires a top-level uml:Package inside the model
                writer.writeStartElement("packagedElement");
                writer.writeAttribute(XMI_NS, "type", "uml:Package");
                writer.writeAttribute(XMI_NS, "id", ROOT_PACKAGE_ID);
                writer.writeAttribute("name", modelName);
                writer.writeAttribute("visibility", "public");
                writeNewline(writer);

                // Collect primitive types referenced by fields and subtype generalizations
                this.primitiveTypes = collectPrimitiveTypes(types);
                writePrimitiveTypes(writer, primitiveTypes);

                // Group types by package and write packages
                writeTypesAndRelationships(writer, model, types, relationships);

                writer.writeEndElement(); // root packagedElement
                writeNewline(writer);

                writer.writeEndElement(); // uml:Model
                writeNewline(writer);

                // EA Extension section: element/connector metadata + optional diagrams
                XmiDiagramWriter dw = new XmiDiagramWriter(model, diagramDepth);
                dw.writeExtension(writer, types, relationships, diagramRoots, modelName);

                writer.writeEndElement(); // xmi:XMI
                writer.writeEndDocument();
            }
            finally
            {
                writer.close();
            }
        }
    }

    private void writeTypesAndRelationships(XMLStreamWriter writer, DiagramModel model,
            List<TypeInfo> types, Set<RelationshipInfo> relationships) throws XMLStreamException
    {
        // Group types by package
        String currentPackage = null;
        boolean inPackage = false;

        for (TypeInfo type : types)
        {
            String packageName = type.getPackageName();
            if (packageName == null)
            {
                packageName = "";
            }

            if (!packageName.equals(currentPackage))
            {
                if (inPackage)
                {
                    writer.writeEndElement(); // close previous package
                    writeNewline(writer);
                }

                currentPackage = packageName;
                if (!packageName.isEmpty())
                {
                    // <packagedElement xmi:type="uml:Package">
                    writer.writeStartElement("packagedElement");
                    writer.writeAttribute(XMI_NS, "type", "uml:Package");
                    writer.writeAttribute(XMI_NS, "id", generateId("pkg", packageName));
                    writer.writeAttribute("name", packageName);
                    writeNewline(writer);
                    inPackage = true;
                }
                else
                {
                    inPackage = false;
                }
            }

            writeType(writer, type, model);
        }

        if (inPackage)
        {
            writer.writeEndElement(); // close last package
            writeNewline(writer);
        }

        // Write associations at model level (outside packages)
        for (RelationshipInfo rel : relationships)
        {
            if (rel.getRelationshipType() == RelationshipInfo.RelationshipType.INHERITANCE)
            {
                continue; // generalizations are written inside the class element
            }
            if (!model.hasType(rel.getTargetType()))
            {
                continue; // skip relationships to primitives
            }
            writeAssociation(writer, rel);
        }
    }

    private void writeType(XMLStreamWriter writer, TypeInfo type, DiagramModel model) throws XMLStreamException
    {
        switch (type.getKind())
        {
        case ENUM:
        case BITMASK:
            writeEnumeration(writer, type);
            break;
        default:
            writeClass(writer, type, model);
            break;
        }
    }

    private void writeClass(XMLStreamWriter writer, TypeInfo type, DiagramModel model) throws XMLStreamException
    {
        String typeId = generateId("type", type.getFullName());

        // <packagedElement xmi:type="uml:Class">
        writer.writeStartElement("packagedElement");
        writer.writeAttribute(XMI_NS, "type", "uml:Class");
        writer.writeAttribute(XMI_NS, "id", typeId);
        writer.writeAttribute("name", type.getName());
        writer.writeAttribute("visibility", "public");
        writeNewline(writer);

        // For subtypes, write generalization to aliased type
        if (type.getKind() == TypeKind.SUBTYPE && type.getAliasedTypeFullName() != null)
        {
            writeGeneralization(writer, type);
        }

        // For constants, write a single attribute with default value
        if (type.getKind() == TypeKind.CONSTANT)
        {
            writeConstantAttribute(writer, type);
        }
        else
        {
            // Write fields as ownedAttribute
            for (FieldInfo field : type.getFields())
            {
                writeOwnedAttribute(writer, type, field);
            }
        }

        writer.writeEndElement(); // packagedElement (class)
        writeNewline(writer);
    }

    private void writeEnumeration(XMLStreamWriter writer, TypeInfo type) throws XMLStreamException
    {
        String typeId = generateId("type", type.getFullName());

        // <packagedElement xmi:type="uml:Enumeration">
        writer.writeStartElement("packagedElement");
        writer.writeAttribute(XMI_NS, "type", "uml:Enumeration");
        writer.writeAttribute(XMI_NS, "id", typeId);
        writer.writeAttribute("name", type.getName());
        writer.writeAttribute("visibility", "public");
        writeNewline(writer);

        // Write enum literals
        for (EnumItemInfo item : type.getEnumItems())
        {
            String literalId = generateId("lit", type.getFullName(), item.getName());
            writer.writeStartElement("ownedLiteral");
            writer.writeAttribute(XMI_NS, "type", "uml:EnumerationLiteral");
            writer.writeAttribute(XMI_NS, "id", literalId);
            writer.writeAttribute("name", item.getName());

            if (item.getValue() != null)
            {
                // Write specification for the value
                writer.writeStartElement("specification");
                writer.writeAttribute(XMI_NS, "type", "uml:LiteralString");
                writer.writeAttribute(XMI_NS, "id", generateId("spec", type.getFullName(), item.getName()));
                writer.writeAttribute("value", item.getValue());
                writer.writeEndElement(); // specification
            }

            writer.writeEndElement(); // ownedLiteral
            writeNewline(writer);
        }

        writer.writeEndElement(); // packagedElement (enumeration)
        writeNewline(writer);
    }

    private void writeOwnedAttribute(XMLStreamWriter writer, TypeInfo ownerType, FieldInfo field)
            throws XMLStreamException
    {
        String attrId = generateId("attr", ownerType.getFullName(), field.getName());

        writer.writeStartElement("ownedAttribute");
        writer.writeAttribute(XMI_NS, "type", "uml:Property");
        writer.writeAttribute(XMI_NS, "id", attrId);
        writer.writeAttribute("name", field.getName());
        writer.writeAttribute("visibility", "public");
        writer.writeAttribute("isStatic", "false");
        writer.writeAttribute("isReadOnly", "false");
        writer.writeAttribute("isDerived", "false");
        writer.writeAttribute("isOrdered", "false");
        writer.writeAttribute("isUnique", "true");
        writer.writeAttribute("isDerivedUnion", "false");
        writeNewline(writer);

        // Multiplicity
        writeMultiplicity(writer, field, attrId);

        // Type reference as child element (EA convention)
        String typeRef;
        if (field.isPrimitiveType())
        {
            typeRef = generateId("prim", field.getTypeName());
        }
        else
        {
            typeRef = generateId("type", field.getFullTypeName());
        }
        writer.writeStartElement("type");
        writer.writeAttribute(XMI_NS, "idref", typeRef);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEndElement(); // ownedAttribute
        writeNewline(writer);
    }

    private void writeConstantAttribute(XMLStreamWriter writer, TypeInfo type) throws XMLStreamException
    {
        String attrId = generateId("attr", type.getFullName(), "value");

        writer.writeStartElement("ownedAttribute");
        writer.writeAttribute(XMI_NS, "type", "uml:Property");
        writer.writeAttribute(XMI_NS, "id", attrId);
        writer.writeAttribute("name", "value");
        writer.writeAttribute("visibility", "public");
        writer.writeAttribute("isStatic", "true");
        writer.writeAttribute("isReadOnly", "true");
        writer.writeAttribute("isDerived", "false");
        writer.writeAttribute("isOrdered", "false");
        writer.writeAttribute("isUnique", "true");
        writer.writeAttribute("isDerivedUnion", "false");
        writeNewline(writer);

        // Multiplicity (1..1)
        writer.writeStartElement("lowerValue");
        writer.writeAttribute(XMI_NS, "type", "uml:LiteralInteger");
        writer.writeAttribute(XMI_NS, "id", generateId("lower", attrId));
        writer.writeAttribute("value", "1");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("upperValue");
        writer.writeAttribute(XMI_NS, "type", "uml:LiteralInteger");
        writer.writeAttribute(XMI_NS, "id", generateId("upper", attrId));
        writer.writeAttribute("value", "1");
        writer.writeEndElement();
        writeNewline(writer);

        // Default value
        if (type.getConstantValue() != null)
        {
            writer.writeStartElement("defaultValue");
            writer.writeAttribute(XMI_NS, "type", "uml:LiteralString");
            writer.writeAttribute(XMI_NS, "id", generateId("dval", type.getFullName()));
            writer.writeAttribute("value", type.getConstantValue());
            writer.writeEndElement(); // defaultValue
            writeNewline(writer);
        }

        writer.writeEndElement(); // ownedAttribute
        writeNewline(writer);
    }

    private void writeMultiplicity(XMLStreamWriter writer, FieldInfo field, String parentId)
            throws XMLStreamException
    {
        String lower;
        String upper;

        if (field.isArray())
        {
            lower = "0";
            if (field.getArrayLength() != null && !field.getArrayLength().isEmpty())
            {
                upper = field.getArrayLength();
            }
            else
            {
                upper = "-1"; // EA convention for unlimited (*)
            }
        }
        else if (field.isOptional())
        {
            lower = "0";
            upper = "1";
        }
        else
        {
            lower = "1";
            upper = "1";
        }

        writer.writeStartElement("lowerValue");
        writer.writeAttribute(XMI_NS, "type", "uml:LiteralInteger");
        writer.writeAttribute(XMI_NS, "id", generateId("lower", parentId));
        writer.writeAttribute("value", lower);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("upperValue");
        writer.writeAttribute(XMI_NS, "type", "uml:LiteralInteger");
        writer.writeAttribute(XMI_NS, "id", generateId("upper", parentId));
        writer.writeAttribute("value", upper);
        writer.writeEndElement();
        writeNewline(writer);
    }

    private void writeGeneralization(XMLStreamWriter writer, TypeInfo subtype) throws XMLStreamException
    {
        String genId = generateId("gen", subtype.getFullName(), subtype.getAliasedTypeFullName());
        String targetId;
        if (primitiveTypes.contains(subtype.getAliasedTypeFullName()))
        {
            targetId = generateId("prim", subtype.getAliasedTypeFullName());
        }
        else
        {
            targetId = generateId("type", subtype.getAliasedTypeFullName());
        }

        writer.writeStartElement("generalization");
        writer.writeAttribute(XMI_NS, "type", "uml:Generalization");
        writer.writeAttribute(XMI_NS, "id", genId);
        writer.writeAttribute("general", targetId);
        writer.writeEndElement();
        writeNewline(writer);
    }

    private void writeAssociation(XMLStreamWriter writer, RelationshipInfo rel) throws XMLStreamException
    {
        String assocId = generateId("assoc", rel.getSourceType(), rel.getTargetType(), rel.getFieldName());
        String sourceEndId = generateId("end_src", assocId);
        String targetEndId = generateId("end_tgt", assocId);
        String sourceTypeId = generateId("type", rel.getSourceType());
        String targetTypeId = generateId("type", rel.getTargetType());

        boolean isComposition =
                rel.getRelationshipType() == RelationshipInfo.RelationshipType.COMPOSITION;

        // <packagedElement xmi:type="uml:Association">
        writer.writeStartElement("packagedElement");
        writer.writeAttribute(XMI_NS, "type", "uml:Association");
        writer.writeAttribute(XMI_NS, "id", assocId);
        if (!rel.getFieldName().isEmpty())
        {
            writer.writeAttribute("name", rel.getFieldName());
        }
        writeNewline(writer);

        // Member ends
        writer.writeStartElement("memberEnd");
        writer.writeAttribute(XMI_NS, "idref", sourceEndId);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("memberEnd");
        writer.writeAttribute(XMI_NS, "idref", targetEndId);
        writer.writeEndElement();
        writeNewline(writer);

        // Source end (navigable from source)
        writer.writeStartElement("ownedEnd");
        writer.writeAttribute(XMI_NS, "type", "uml:Property");
        writer.writeAttribute(XMI_NS, "id", sourceEndId);
        writer.writeAttribute("association", assocId);
        writeNewline(writer);
        writer.writeStartElement("type");
        writer.writeAttribute(XMI_NS, "idref", sourceTypeId);
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeEndElement(); // ownedEnd (source)
        writeNewline(writer);

        // Target end (with cardinality and optional composition)
        writer.writeStartElement("ownedEnd");
        writer.writeAttribute(XMI_NS, "type", "uml:Property");
        writer.writeAttribute(XMI_NS, "id", targetEndId);
        writer.writeAttribute("association", assocId);
        if (isComposition)
        {
            writer.writeAttribute("aggregation", "composite");
        }
        writeNewline(writer);

        // Write cardinality on the target end
        writeAssociationMultiplicity(writer, rel.getCardinality(), targetEndId);

        writer.writeStartElement("type");
        writer.writeAttribute(XMI_NS, "idref", targetTypeId);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEndElement(); // ownedEnd (target)
        writeNewline(writer);

        writer.writeEndElement(); // packagedElement (association)
        writeNewline(writer);
    }

    private void writeAssociationMultiplicity(XMLStreamWriter writer, String cardinality, String endId)
            throws XMLStreamException
    {
        String lower;
        String upper;

        switch (cardinality)
        {
        case "0..1":
            lower = "0";
            upper = "1";
            break;
        case "*":
            lower = "0";
            upper = "-1"; // EA convention for unlimited
            break;
        case "1":
            lower = "1";
            upper = "1";
            break;
        default:
            // Fixed array size or other
            lower = "0";
            upper = cardinality;
            break;
        }

        writer.writeStartElement("lowerValue");
        writer.writeAttribute(XMI_NS, "type", "uml:LiteralInteger");
        writer.writeAttribute(XMI_NS, "id", generateId("lower", endId));
        writer.writeAttribute("value", lower);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("upperValue");
        writer.writeAttribute(XMI_NS, "type", "uml:LiteralInteger");
        writer.writeAttribute(XMI_NS, "id", generateId("upper", endId));
        writer.writeAttribute("value", upper);
        writer.writeEndElement();
        writeNewline(writer);
    }

    private Set<String> collectPrimitiveTypes(List<TypeInfo> types)
    {
        Set<String> primitives = new LinkedHashSet<>();
        for (TypeInfo type : types)
        {
            for (FieldInfo field : type.getFields())
            {
                if (field.isPrimitiveType())
                {
                    primitives.add(field.getTypeName());
                }
            }
            if (type.getUnderlyingType() != null)
            {
                primitives.add(type.getUnderlyingType());
            }
            // Collect primitive types referenced by subtype generalizations
            if (type.getKind() == TypeKind.SUBTYPE && type.getAliasedTypeFullName() != null
                    && !type.getAliasedTypeFullName().contains("."))
            {
                primitives.add(type.getAliasedTypeFullName());
            }
        }
        return primitives;
    }

    private void writePrimitiveTypes(XMLStreamWriter writer, Set<String> primitiveTypes)
            throws XMLStreamException
    {
        if (primitiveTypes.isEmpty())
        {
            return;
        }

        // Write primitive types in a dedicated package
        writer.writeStartElement("packagedElement");
        writer.writeAttribute(XMI_NS, "type", "uml:Package");
        writer.writeAttribute(XMI_NS, "id", generateId("pkg", "PrimitiveTypes"));
        writer.writeAttribute("name", "PrimitiveTypes");
        writeNewline(writer);

        for (String primName : primitiveTypes)
        {
            writer.writeStartElement("packagedElement");
            writer.writeAttribute(XMI_NS, "type", "uml:PrimitiveType");
            writer.writeAttribute(XMI_NS, "id", generateId("prim", primName));
            writer.writeAttribute("name", primName);
            writer.writeEndElement();
            writeNewline(writer);
        }

        writer.writeEndElement(); // PrimitiveTypes package
        writeNewline(writer);
    }

    /**
     * Generates a deterministic, XML-safe ID from the given parts.
     * Uses a stable encoding so the same model always produces the same IDs.
     */
    static String generateId(String... parts)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
            {
                sb.append("_");
            }
            sb.append(parts[i]);
        }
        // Sanitize for XML ID: replace dots, spaces, and special chars with underscores
        return sb.toString()
                .replace('.', '_')
                .replace(' ', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace(',', '_')
                .replace('(', '_')
                .replace(')', '_')
                .replace('[', '_')
                .replace(']', '_')
                .replace('"', '_');
    }

    private void writeNewline(XMLStreamWriter writer) throws XMLStreamException
    {
        writer.writeCharacters("\n");
    }

    private DiagramConfig config;
    private Set<String> primitiveTypes;
}
