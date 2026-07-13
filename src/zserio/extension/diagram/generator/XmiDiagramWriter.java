package zserio.extension.diagram.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import zserio.extension.diagram.model.DiagramModel;
import zserio.extension.diagram.model.RelationshipInfo;
import zserio.extension.diagram.model.TypeInfo;
import zserio.extension.diagram.model.TypeKind;

/**
 * Writes EA-compatible xmi:Extension section including element metadata,
 * connector metadata, and optional diagram definitions.
 */
class XmiDiagramWriter
{
    private static final String XMI_NS = "http://schema.omg.org/spec/XMI/2.1";

    private static final int LAYER_GAP_Y = 80;
    private static final int ELEMENT_GAP_X = 40;
    private static final int MIN_BOX_WIDTH = 140;
    private static final int MIN_BOX_HEIGHT = 60;
    private static final int BOX_HEADER_HEIGHT = 40;
    private static final int FIELD_LINE_HEIGHT = 18;
    private static final int CHAR_WIDTH = 8;
    private static final int BOX_PADDING = 40;

    private static final String DEFAULT_STYLE1 =
            "ShowPrivate=1;ShowProtected=1;ShowPublic=1;HideRelationships=0;"
            + "Locked=0;Border=1;HighlightForeign=1;PackageContents=1;"
            + "SequenceNotes=0;ScalePrintImage=0;PPgs.cx=1;PPgs.cy=1;"
            + "DocSize.cx=850;DocSize.cy=1098;ShowDetails=0;Orientation=P;"
            + "Zoom=100;ShowTags=0;OpParams=1;VisibleAttributeDetail=0;"
            + "ShowOpRetType=1;ShowIcons=1;CollabNums=0;HideProps=0;"
            + "ShowReqs=0;ShowCons=0;PaperSize=1;HideParents=0;UseAlias=0;"
            + "HideAtts=0;HideOps=0;HideStereo=0;HideElemStereo=0;"
            + "ShowTests=0;ShowMaint=0;ConnectorNotation=UML 2.1;"
            + "ExplicitNavigability=0;ShowShape=1;AllDockable=0;"
            + "AdvancedElementProps=1;AdvancedFeatureProps=1;"
            + "AdvancedConnectorProps=1;m_bElementClassifier=1;SPT=1;"
            + "ShowNotes=0;SuppressBrackets=0;SuppConnectorLabels=0;"
            + "PrintPageHeadFoot=0;ShowAsList=0;";

    private static final String DEFAULT_STYLE2 =
            "ExcludeRTF=0;DocAll=0;HideQuals=0;AttPkg=1;ShowTests=0;"
            + "ShowMaint=0;SuppressFOC=1;MatrixActive=0;SwimlanesActive=1;"
            + "KanbanActive=0;MatrixLineWidth=1;MatrixLineClr=0;"
            + "MatrixLocked=0;TConnectorNotation=UML 2.1;"
            + "TExplicitNavigability=0;AdvancedElementProps=1;"
            + "AdvancedFeatureProps=1;AdvancedConnectorProps=1;"
            + "m_bElementClassifier=1;SPT=1;MDGDgm=;STBLDgm=;ShowNotes=0;"
            + "VisibleAttributeDetail=0;ShowOpRetType=1;SuppressBrackets=0;"
            + "SuppConnectorLabels=0;PrintPageHeadFoot=0;ShowAsList=0;"
            + "SuppressedCompartments=;Theme=;";

    private final DiagramModel model;
    private final int depth;

    XmiDiagramWriter(DiagramModel model, int depth)
    {
        this.model = model;
        this.depth = depth;
    }

    /**
     * Writes the full xmi:Extension section with EA element metadata,
     * connector metadata, and optional diagram definitions.
     */
    void writeExtension(XMLStreamWriter writer, List<TypeInfo> types,
            Set<RelationshipInfo> relationships, List<String> rootTypeNames,
            String modelName) throws XMLStreamException
    {
        String timestamp = "2026-03-25 00:00:00";

        writer.writeStartElement("xmi", "Extension", XMI_NS);
        writer.writeAttribute("extender", "Enterprise Architect");
        writer.writeAttribute("extenderID", "6.5");
        writeNewline(writer);

        // <elements> — EA extended properties for each model element
        writeEaElements(writer, types, modelName, timestamp);

        // <connectors> — EA extended properties for each association
        writeEaConnectors(writer, relationships, timestamp);

        // <primitivetypes> — empty but EA expects it
        writer.writeStartElement("primitivetypes");
        writer.writeEndElement();
        writeNewline(writer);

        // <profiles> — empty but EA expects it
        writer.writeStartElement("profiles");
        writer.writeEndElement();
        writeNewline(writer);

        // <diagrams> — optional diagram definitions
        List<TypeInfo> resolvedRoots = resolveRootTypes(rootTypeNames);
        if (!resolvedRoots.isEmpty())
        {
            writer.writeStartElement("diagrams");
            writeNewline(writer);

            int localId = 1;
            for (TypeInfo root : resolvedRoots)
            {
                writeDiagram(writer, root, localId++, timestamp);
            }

            writer.writeEndElement(); // diagrams
            writeNewline(writer);
        }

        writer.writeEndElement(); // xmi:Extension
        writeNewline(writer);
    }

    private void writeEaElements(XMLStreamWriter writer, List<TypeInfo> types,
            String modelName, String timestamp) throws XMLStreamException
    {
        writer.writeStartElement("elements");
        writeNewline(writer);

        int localId = 1;

        // Root package element — mirrors the root packagedElement in uml:Model
        writeEaRootPackageElement(writer, modelName, localId++, timestamp);

        // Track which packages we've already written
        Set<String> writtenPackages = new LinkedHashSet<>();

        for (TypeInfo type : types)
        {
            String packageName = type.getPackageName();
            if (packageName != null && !packageName.isEmpty() && !writtenPackages.contains(packageName))
            {
                writtenPackages.add(packageName);
                writeEaPackageElement(writer, packageName, localId++, timestamp);
            }
        }

        for (TypeInfo type : types)
        {
            writeEaTypeElement(writer, type, localId++, timestamp);
        }

        writer.writeEndElement(); // elements
        writeNewline(writer);
    }

    private void writeEaRootPackageElement(XMLStreamWriter writer, String modelName,
            int localId, String timestamp) throws XMLStreamException
    {
        String rootId = XmiGenerator.ROOT_PACKAGE_ID;
        String rootElemId = XmiGenerator.generateId("eid", "root");

        writer.writeStartElement("element");
        writer.writeAttribute(XMI_NS, "idref", rootId);
        writer.writeAttribute(XMI_NS, "type", "uml:Package");
        writer.writeAttribute("name", modelName);
        writer.writeAttribute("scope", "public");
        writeNewline(writer);

        writer.writeStartElement("model");
        writer.writeAttribute("package2", rootElemId);
        writer.writeAttribute("package", "model_root");
        writer.writeAttribute("tpos", "0");
        writer.writeAttribute("ea_localid", String.valueOf(localId));
        writer.writeAttribute("ea_eleType", "package");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("properties");
        writer.writeAttribute("isSpecification", "false");
        writer.writeAttribute("sType", "Package");
        writer.writeAttribute("nType", "0");
        writer.writeAttribute("scope", "public");
        writer.writeEndElement();
        writeNewline(writer);

        writeEaProjectElement(writer, timestamp);
        writeEaStyleElement(writer);

        writer.writeEmptyElement("tags");
        writeNewline(writer);
        writer.writeEmptyElement("xrefs");
        writeNewline(writer);

        writer.writeStartElement("extendedProperties");
        writer.writeAttribute("tagged", "0");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("packageproperties");
        writer.writeAttribute("version", "1.0");
        writer.writeAttribute("tpos", "0");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEmptyElement("paths");
        writeNewline(writer);

        writer.writeStartElement("times");
        writer.writeAttribute("created", timestamp);
        writer.writeAttribute("modified", timestamp);
        writer.writeAttribute("lastloaddate", timestamp);
        writer.writeAttribute("lastsavedate", timestamp);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("flags");
        writer.writeAttribute("iscontrolled", "0");
        writer.writeAttribute("isprotected", "0");
        writer.writeAttribute("batchsave", "0");
        writer.writeAttribute("batchload", "0");
        writer.writeAttribute("usedtd", "0");
        writer.writeAttribute("logxml", "0");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEndElement(); // element
        writeNewline(writer);
    }

    private void writeEaPackageElement(XMLStreamWriter writer, String packageName,
            int localId, String timestamp) throws XMLStreamException
    {
        String pkgId = XmiGenerator.generateId("pkg", packageName);
        String pkgElemId = XmiGenerator.generateId("eid", packageName);

        writer.writeStartElement("element");
        writer.writeAttribute(XMI_NS, "idref", pkgId);
        writer.writeAttribute(XMI_NS, "type", "uml:Package");
        writer.writeAttribute("name", packageName);
        writer.writeAttribute("scope", "public");
        writeNewline(writer);

        writer.writeStartElement("model");
        writer.writeAttribute("package2", pkgElemId);
        writer.writeAttribute("package", XmiGenerator.ROOT_PACKAGE_ID);
        writer.writeAttribute("tpos", "0");
        writer.writeAttribute("ea_localid", String.valueOf(localId));
        writer.writeAttribute("ea_eleType", "package");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("properties");
        writer.writeAttribute("isSpecification", "false");
        writer.writeAttribute("sType", "Package");
        writer.writeAttribute("nType", "0");
        writer.writeAttribute("scope", "public");
        writer.writeEndElement();
        writeNewline(writer);

        writeEaProjectElement(writer, timestamp);
        writeEaStyleElement(writer);

        writer.writeEmptyElement("tags");
        writeNewline(writer);
        writer.writeEmptyElement("xrefs");
        writeNewline(writer);

        writer.writeStartElement("extendedProperties");
        writer.writeAttribute("tagged", "0");
        writer.writeEndElement();
        writeNewline(writer);

        // Package-specific elements required by EA
        writer.writeStartElement("packageproperties");
        writer.writeAttribute("version", "1.0");
        writer.writeAttribute("tpos", "0");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEmptyElement("paths");
        writeNewline(writer);

        writer.writeStartElement("times");
        writer.writeAttribute("created", timestamp);
        writer.writeAttribute("modified", timestamp);
        writer.writeAttribute("lastloaddate", timestamp);
        writer.writeAttribute("lastsavedate", timestamp);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("flags");
        writer.writeAttribute("iscontrolled", "0");
        writer.writeAttribute("isprotected", "0");
        writer.writeAttribute("batchsave", "0");
        writer.writeAttribute("batchload", "0");
        writer.writeAttribute("usedtd", "0");
        writer.writeAttribute("logxml", "0");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEndElement(); // element
        writeNewline(writer);
    }

    private void writeEaTypeElement(XMLStreamWriter writer, TypeInfo type,
            int localId, String timestamp) throws XMLStreamException
    {
        String typeId = XmiGenerator.generateId("type", type.getFullName());
        String packageId = XmiGenerator.generateId("pkg",
                type.getPackageName() != null ? type.getPackageName() : "");

        boolean isEnum = type.getKind() == TypeKind.ENUM || type.getKind() == TypeKind.BITMASK;
        String umlType = isEnum ? "uml:Enumeration" : "uml:Class";
        String sType = isEnum ? "Enumeration" : "Class";

        writer.writeStartElement("element");
        writer.writeAttribute(XMI_NS, "idref", typeId);
        writer.writeAttribute(XMI_NS, "type", umlType);
        writer.writeAttribute("name", type.getName());
        writer.writeAttribute("scope", "public");
        writeNewline(writer);

        writer.writeStartElement("model");
        writer.writeAttribute("package", packageId);
        writer.writeAttribute("tpos", "0");
        writer.writeAttribute("ea_localid", String.valueOf(localId));
        writer.writeAttribute("ea_eleType", "element");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("properties");
        writer.writeAttribute("isSpecification", "false");
        writer.writeAttribute("sType", sType);
        writer.writeAttribute("nType", "0");
        writer.writeAttribute("scope", "public");
        writer.writeAttribute("isRoot", "false");
        writer.writeAttribute("isLeaf", "false");
        writer.writeAttribute("isAbstract", "false");
        writer.writeAttribute("isActive", "false");
        writer.writeEndElement();
        writeNewline(writer);

        writeEaProjectElement(writer, timestamp);
        writeEaStyleElement(writer);

        writer.writeEmptyElement("tags");
        writeNewline(writer);
        writer.writeEmptyElement("xrefs");
        writeNewline(writer);

        writer.writeStartElement("extendedProperties");
        writer.writeAttribute("tagged", "0");
        if (type.getPackageName() != null && !type.getPackageName().isEmpty())
        {
            writer.writeAttribute("package_name", type.getPackageName());
        }
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEndElement(); // element
        writeNewline(writer);
    }

    private void writeEaProjectElement(XMLStreamWriter writer, String timestamp) throws XMLStreamException
    {
        writer.writeStartElement("project");
        writer.writeAttribute("author", "zserio");
        writer.writeAttribute("version", "1.0");
        writer.writeAttribute("phase", "1.0");
        writer.writeAttribute("created", timestamp);
        writer.writeAttribute("modified", timestamp);
        writer.writeAttribute("complexity", "1");
        writer.writeAttribute("status", "Proposed");
        writer.writeEndElement();
        writeNewline(writer);
    }

    private void writeEaStyleElement(XMLStreamWriter writer) throws XMLStreamException
    {
        writer.writeStartElement("code");
        writer.writeAttribute("gentype", "Java");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("style");
        writer.writeAttribute("appearance",
                "BackColor=-1;BorderColor=-1;BorderWidth=-1;FontColor=-1;"
                + "VSwimLanes=1;HSwimLanes=1;BorderStyle=0;");
        writer.writeEndElement();
        writeNewline(writer);
    }

    private void writeEaConnectors(XMLStreamWriter writer, Set<RelationshipInfo> relationships,
            String timestamp) throws XMLStreamException
    {
        writer.writeStartElement("connectors");
        writeNewline(writer);

        int localId = 1;
        for (RelationshipInfo rel : relationships)
        {
            if (rel.getRelationshipType() == RelationshipInfo.RelationshipType.INHERITANCE)
            {
                continue; // generalizations handled within class elements
            }
            if (!model.hasType(rel.getTargetType()))
            {
                continue; // skip relationships to primitives
            }
            writeEaConnector(writer, rel, localId++);
        }

        writer.writeEndElement(); // connectors
        writeNewline(writer);
    }

    private void writeEaConnector(XMLStreamWriter writer, RelationshipInfo rel, int localId)
            throws XMLStreamException
    {
        String assocId = XmiGenerator.generateId("assoc", rel.getSourceType(),
                rel.getTargetType(), rel.getFieldName());
        String sourceTypeId = XmiGenerator.generateId("type", rel.getSourceType());
        String targetTypeId = XmiGenerator.generateId("type", rel.getTargetType());

        boolean isComposition =
                rel.getRelationshipType() == RelationshipInfo.RelationshipType.COMPOSITION;
        String eaType = isComposition ? "Association" : "Association";
        String aggregation = isComposition ? "composite" : "none";

        TypeInfo sourceType = model.getType(rel.getSourceType());
        TypeInfo targetType = model.getType(rel.getTargetType());
        String sourceName = sourceType != null ? sourceType.getName() : rel.getSourceType();
        String targetName = targetType != null ? targetType.getName() : rel.getTargetType();

        writer.writeStartElement("connector");
        writer.writeAttribute(XMI_NS, "idref", assocId);
        writeNewline(writer);

        // <source>
        writer.writeStartElement("source");
        writer.writeAttribute(XMI_NS, "idref", sourceTypeId);
        writeNewline(writer);
        writer.writeStartElement("model");
        writer.writeAttribute("ea_localid", String.valueOf(localId));
        writer.writeAttribute("type", "Class");
        writer.writeAttribute("name", sourceName);
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeStartElement("role");
        writer.writeAttribute("visibility", "Public");
        writer.writeAttribute("targetScope", "instance");
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeStartElement("type");
        writer.writeAttribute("aggregation", aggregation);
        writer.writeAttribute("containment", "Unspecified");
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeEmptyElement("constraints");
        writeNewline(writer);
        writer.writeStartElement("modifiers");
        writer.writeAttribute("isOrdered", "false");
        writer.writeAttribute("changeable", "none");
        writer.writeAttribute("isNavigable", "false");
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeStartElement("style");
        writer.writeAttribute("value", "Union=0;Derived=0;AllowDuplicates=0;");
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeEmptyElement("documentation");
        writeNewline(writer);
        writer.writeEmptyElement("xrefs");
        writeNewline(writer);
        writer.writeEmptyElement("tags");
        writeNewline(writer);
        writer.writeEndElement(); // source
        writeNewline(writer);

        // <target>
        writer.writeStartElement("target");
        writer.writeAttribute(XMI_NS, "idref", targetTypeId);
        writeNewline(writer);
        writer.writeStartElement("model");
        writer.writeAttribute("ea_localid", String.valueOf(localId));
        writer.writeAttribute("type", "Class");
        writer.writeAttribute("name", targetName);
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeStartElement("role");
        writer.writeAttribute("name", rel.getFieldName());
        writer.writeAttribute("visibility", "Public");
        writer.writeAttribute("targetScope", "instance");
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeStartElement("type");
        writer.writeAttribute("aggregation", "none");
        writer.writeAttribute("containment", "Unspecified");
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeEmptyElement("constraints");
        writeNewline(writer);
        writer.writeStartElement("modifiers");
        writer.writeAttribute("isOrdered", "false");
        writer.writeAttribute("changeable", "none");
        writer.writeAttribute("isNavigable", "true");
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeStartElement("style");
        writer.writeAttribute("value", "Union=0;Derived=0;AllowDuplicates=0;");
        writer.writeEndElement();
        writeNewline(writer);
        writer.writeEmptyElement("documentation");
        writeNewline(writer);
        writer.writeEmptyElement("xrefs");
        writeNewline(writer);
        writer.writeEmptyElement("tags");
        writeNewline(writer);
        writer.writeEndElement(); // target
        writeNewline(writer);

        // <model>
        writer.writeStartElement("model");
        writer.writeAttribute("ea_localid", String.valueOf(localId));
        writer.writeEndElement();
        writeNewline(writer);

        // <properties>
        writer.writeStartElement("properties");
        writer.writeAttribute("ea_type", eaType);
        writer.writeAttribute("direction", "Source -> Destination");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEmptyElement("documentation");
        writeNewline(writer);

        writer.writeStartElement("appearance");
        writer.writeAttribute("linemode", "3");
        writer.writeAttribute("linecolor", "-1");
        writer.writeAttribute("linewidth", "0");
        writer.writeAttribute("seqno", "0");
        writer.writeAttribute("headStyle", "0");
        writer.writeAttribute("lineStyle", "0");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEmptyElement("labels");
        writeNewline(writer);

        writer.writeStartElement("extendedProperties");
        writer.writeAttribute("virtualInheritance", "0");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEmptyElement("style");
        writeNewline(writer);
        writer.writeEmptyElement("xrefs");
        writeNewline(writer);
        writer.writeEmptyElement("tags");
        writeNewline(writer);

        writer.writeEndElement(); // connector
        writeNewline(writer);
    }

    // ── Root type resolution ──

    private List<TypeInfo> resolveRootTypes(List<String> names)
    {
        List<TypeInfo> result = new ArrayList<>();
        if (names == null)
        {
            return result;
        }

        // Collect already-added full names to avoid duplicates from overlapping patterns
        Set<String> added = new LinkedHashSet<>();

        for (String name : names)
        {
            String trimmed = name.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }

            if (trimmed.contains("*") || trimmed.contains("?"))
            {
                // Wildcard pattern — match against simple names and full names
                String regex = wildcardToRegex(trimmed);
                for (TypeInfo t : model.getAllTypes())
                {
                    if (!added.contains(t.getFullName())
                            && (t.getName().matches(regex) || t.getFullName().matches(regex)))
                    {
                        result.add(t);
                        added.add(t.getFullName());
                    }
                }
                continue;
            }

            // Try exact full-name match first
            TypeInfo type = model.getType(trimmed);
            if (type != null && !added.contains(type.getFullName()))
            {
                result.add(type);
                added.add(type.getFullName());
                continue;
            }

            // Scan for simple name match
            TypeInfo found = null;
            for (TypeInfo t : model.getAllTypes())
            {
                if (t.getName().equals(trimmed))
                {
                    found = t;
                    break;
                }
            }
            if (found != null && !added.contains(found.getFullName()))
            {
                result.add(found);
                added.add(found.getFullName());
            }
            else if (found == null)
            {
                System.out.println("Warning: diagram root type '" + trimmed + "' not found in model");
            }
        }
        return result;
    }

    private String wildcardToRegex(String pattern)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++)
        {
            char c = pattern.charAt(i);
            switch (c)
            {
            case '*':
                sb.append(".*");
                break;
            case '?':
                sb.append(".");
                break;
            case '.':
            case '(':
            case ')':
            case '[':
            case ']':
            case '{':
            case '}':
            case '\\':
            case '^':
            case '$':
            case '|':
            case '+':
                sb.append("\\");
                sb.append(c);
                break;
            default:
                sb.append(c);
                break;
            }
        }
        return sb.toString();
    }

    // ── Subgraph extraction ──

    private SubgraphResult extractSubgraph(TypeInfo root)
    {
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        Map<String, Integer> depthMap = new HashMap<>();

        String rootName = root.getFullName();
        queue.add(rootName);
        visited.add(rootName);
        depthMap.put(rootName, 0);

        while (!queue.isEmpty())
        {
            String current = queue.poll();
            int currentDepth = depthMap.get(current);

            if (currentDepth >= depth)
            {
                continue;
            }

            for (RelationshipInfo rel : model.getRelationshipsFrom(current))
            {
                String target = rel.getTargetType();
                if (!visited.contains(target) && model.hasType(target))
                {
                    visited.add(target);
                    depthMap.put(target, currentDepth + 1);
                    queue.add(target);
                }
            }
        }

        List<TypeInfo> types = new ArrayList<>();
        for (String fullName : visited)
        {
            TypeInfo t = model.getType(fullName);
            if (t != null)
            {
                types.add(t);
            }
        }

        Set<RelationshipInfo> rels = new LinkedHashSet<>();
        for (RelationshipInfo rel : model.getAllRelationships())
        {
            if (visited.contains(rel.getSourceType()) && visited.contains(rel.getTargetType()))
            {
                rels.add(rel);
            }
        }

        return new SubgraphResult(types, rels, depthMap);
    }

    // ── Diagram writing ──

    private void writeDiagram(XMLStreamWriter writer, TypeInfo root, int localId, String timestamp)
            throws XMLStreamException
    {
        SubgraphResult subgraph = extractSubgraph(root);
        List<ShapeInfo> shapes = layoutShapes(root, subgraph);
        Map<String, String> duidMap = buildDuidMap(shapes);

        String diagramId = "DIAG_" + XmiGenerator.generateId("type", root.getFullName());
        String packageId = XmiGenerator.generateId("pkg", root.getPackageName() != null
                ? root.getPackageName() : "");
        String diagramName = root.getName() + " Diagram";

        writer.writeStartElement("diagram");
        writer.writeAttribute(XMI_NS, "id", diagramId);
        writeNewline(writer);

        // <model>
        writer.writeStartElement("model");
        writer.writeAttribute("package", packageId);
        writer.writeAttribute("localID", String.valueOf(localId));
        writer.writeAttribute("owner", packageId);
        writer.writeAttribute("tpos", "0");
        writer.writeEndElement();
        writeNewline(writer);

        // <properties>
        writer.writeStartElement("properties");
        writer.writeAttribute("name", diagramName);
        writer.writeAttribute("type", "Logical");
        writer.writeEndElement();
        writeNewline(writer);

        // <project>
        writer.writeStartElement("project");
        writer.writeAttribute("author", "zserio");
        writer.writeAttribute("version", "1.0");
        writer.writeAttribute("created", timestamp);
        writer.writeAttribute("modified", timestamp);
        writer.writeEndElement();
        writeNewline(writer);

        // EA style defaults
        writer.writeStartElement("style1");
        writer.writeAttribute("value", DEFAULT_STYLE1);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("style2");
        writer.writeAttribute("value", DEFAULT_STYLE2);
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("swimlanes");
        writer.writeAttribute("value",
                "locked=false;orientation=0;width=0;inbar=false;names=false;"
                + "color=0;bold=false;fcol=0;tcol=-1;ofCol=-1;ufCol=-1;"
                + "hl=1;ufh=0;hh=0;cls=0;bw=0;hli=0;bro=0;");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeStartElement("matrixitems");
        writer.writeAttribute("value",
                "locked=false;matrixactive=false;swimlanesactive=true;"
                + "kanbanactive=false;width=1;clrLine=0;");
        writer.writeEndElement();
        writeNewline(writer);

        writer.writeEmptyElement("extendedProperties");
        writeNewline(writer);
        writer.writeEmptyElement("xrefs");
        writeNewline(writer);

        // <elements> — shapes and connectors within the diagram
        writer.writeStartElement("elements");
        writeNewline(writer);

        int seqno = 1;
        for (ShapeInfo shape : shapes)
        {
            writer.writeStartElement("element");
            writer.writeAttribute("geometry",
                    "Left=" + shape.left + ";Top=" + shape.top + ";Right=" + shape.right
                    + ";Bottom=" + shape.bottom + ";");
            writer.writeAttribute("subject", shape.subjectId);
            writer.writeAttribute("seqno", String.valueOf(seqno++));
            writer.writeAttribute("style", "DUID=" + shape.duid + ";");
            writer.writeEndElement();
            writeNewline(writer);
        }

        for (RelationshipInfo rel : subgraph.relationships)
        {
            if (rel.getRelationshipType() == RelationshipInfo.RelationshipType.INHERITANCE)
            {
                continue;
            }

            String assocId = XmiGenerator.generateId("assoc", rel.getSourceType(),
                    rel.getTargetType(), rel.getFieldName());

            String sourceDuid = duidMap.get(rel.getSourceType());
            String targetDuid = duidMap.get(rel.getTargetType());

            if (sourceDuid == null || targetDuid == null)
            {
                continue;
            }

            int edge = calculateEdge(findShape(shapes, rel.getSourceType()),
                    findShape(shapes, rel.getTargetType()));

            writer.writeStartElement("element");
            writer.writeAttribute("geometry",
                    "EDGE=" + edge + ";$LLB=;LLT=;LMT=;LMB=;LRT=;LRB=;IRHS=;ILHS=;Path=;");
            writer.writeAttribute("subject", assocId);
            writer.writeAttribute("style",
                    "Mode=3;EOID=" + targetDuid + ";SOID=" + sourceDuid
                    + ";Color=-1;LWidth=0;Hidden=0;");
            writer.writeEndElement();
            writeNewline(writer);
        }

        writer.writeEndElement(); // elements
        writeNewline(writer);

        writer.writeEndElement(); // diagram
        writeNewline(writer);
    }

    // ── Layout ──

    private List<ShapeInfo> layoutShapes(TypeInfo root, SubgraphResult subgraph)
    {
        Map<Integer, List<TypeInfo>> layers = new LinkedHashMap<>();
        for (TypeInfo type : subgraph.types)
        {
            int layer = subgraph.depthMap.getOrDefault(type.getFullName(), 0);
            layers.computeIfAbsent(layer, k -> new ArrayList<>()).add(type);
        }

        List<ShapeInfo> shapes = new ArrayList<>();
        int currentY = 20;

        int maxLayer = layers.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int layer = 0; layer <= maxLayer; layer++)
        {
            List<TypeInfo> layerTypes = layers.getOrDefault(layer, List.of());
            if (layerTypes.isEmpty())
            {
                continue;
            }

            List<int[]> sizes = new ArrayList<>();
            int totalWidth = 0;
            for (TypeInfo type : layerTypes)
            {
                int[] size = calculateBoxSize(type);
                sizes.add(size);
                totalWidth += size[0];
            }
            totalWidth += (layerTypes.size() - 1) * ELEMENT_GAP_X;

            int startX = Math.max(20, (800 - totalWidth) / 2);
            int currentX = startX;
            int maxHeight = 0;

            for (int i = 0; i < layerTypes.size(); i++)
            {
                TypeInfo type = layerTypes.get(i);
                int[] size = sizes.get(i);
                int w = size[0];
                int h = size[1];

                String subjectId = XmiGenerator.generateId("type", type.getFullName());
                shapes.add(new ShapeInfo(type.getFullName(), subjectId,
                        currentX, currentY, currentX + w, currentY + h));

                currentX += w + ELEMENT_GAP_X;
                maxHeight = Math.max(maxHeight, h);
            }

            currentY += maxHeight + LAYER_GAP_Y;
        }

        return shapes;
    }

    private int[] calculateBoxSize(TypeInfo type)
    {
        int fieldCount = type.getFields().size() + type.getEnumItems().size();

        int longestLine = type.getName().length();
        for (var field : type.getFields())
        {
            int lineLen = field.getName().length() + field.getTypeName().length() + 3;
            longestLine = Math.max(longestLine, lineLen);
        }
        for (var item : type.getEnumItems())
        {
            longestLine = Math.max(longestLine, item.getName().length());
        }

        int width = Math.max(MIN_BOX_WIDTH, longestLine * CHAR_WIDTH + BOX_PADDING);
        int height = Math.max(MIN_BOX_HEIGHT, BOX_HEADER_HEIGHT + fieldCount * FIELD_LINE_HEIGHT);
        return new int[]{width, height};
    }

    private Map<String, String> buildDuidMap(List<ShapeInfo> shapes)
    {
        Map<String, String> duidMap = new LinkedHashMap<>();
        Set<String> usedDuids = new LinkedHashSet<>();

        for (ShapeInfo shape : shapes)
        {
            String duid = generateDuid(shape.typeFullName);

            while (usedDuids.contains(duid))
            {
                duid = generateDuid(shape.typeFullName + "_" + usedDuids.size());
            }

            usedDuids.add(duid);
            shape.duid = duid;
            duidMap.put(shape.typeFullName, duid);
        }

        return duidMap;
    }

    private String generateDuid(String elementId)
    {
        return String.format("%08X", elementId.hashCode() & 0xFFFFFFFFL).substring(0, 8);
    }

    private int calculateEdge(ShapeInfo source, ShapeInfo target)
    {
        if (source == null || target == null)
        {
            return 3;
        }

        int sourceCx = (source.left + source.right) / 2;
        int sourceCy = (source.top + source.bottom) / 2;
        int targetCx = (target.left + target.right) / 2;
        int targetCy = (target.top + target.bottom) / 2;

        int dx = targetCx - sourceCx;
        int dy = targetCy - sourceCy;

        if (Math.abs(dy) >= Math.abs(dx))
        {
            return dy >= 0 ? 3 : 1;
        }
        else
        {
            return dx >= 0 ? 2 : 4;
        }
    }

    private ShapeInfo findShape(List<ShapeInfo> shapes, String typeFullName)
    {
        for (ShapeInfo shape : shapes)
        {
            if (shape.typeFullName.equals(typeFullName))
            {
                return shape;
            }
        }
        return null;
    }

    private void writeNewline(XMLStreamWriter writer) throws XMLStreamException
    {
        writer.writeCharacters("\n");
    }

    // ── Inner classes ──

    private static class SubgraphResult
    {
        final List<TypeInfo> types;
        final Set<RelationshipInfo> relationships;
        final Map<String, Integer> depthMap;

        SubgraphResult(List<TypeInfo> types, Set<RelationshipInfo> relationships,
                Map<String, Integer> depthMap)
        {
            this.types = types;
            this.relationships = relationships;
            this.depthMap = depthMap;
        }
    }

    private static class ShapeInfo
    {
        final String typeFullName;
        final String subjectId;
        final int left;
        final int top;
        final int right;
        final int bottom;
        String duid;

        ShapeInfo(String typeFullName, String subjectId, int left, int top, int right, int bottom)
        {
            this.typeFullName = typeFullName;
            this.subjectId = subjectId;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
