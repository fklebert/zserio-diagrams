package zserio.extension.diagram;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import zserio.ast.Root;
import zserio.extension.common.ZserioExtensionException;
import zserio.extension.diagram.generator.DiagramGenerator;
import zserio.extension.diagram.generator.MermaidGenerator;
import zserio.extension.diagram.generator.PlantUmlGenerator;
import zserio.extension.diagram.generator.XmiGenerator;
import zserio.extension.diagram.model.DiagramModel;
import zserio.tools.Extension;
import zserio.tools.ExtensionParameters;

/**
 * Zserio extension that generates PlantUML and Mermaid class diagrams from zserio schemas.
 */
public class DiagramExtension implements Extension
{
    @Override
    public String getName()
    {
        return "Diagram";
    }

    @Override
    public String getExtensionVersion()
    {
        return EXTENSION_VERSION_STRING;
    }

    @Override
    public String getZserioVersion()
    {
        return ZSERIO_VERSION_STRING;
    }

    @Override
    public void registerOptions(Options options)
    {
        Option diagramOption =
                new Option(OPTION_DIAGRAM, false, "generate PlantUML and Mermaid class diagrams");
        diagramOption.setRequired(false);
        options.addOption(diagramOption);

        Option formatOption = Option.builder()
                                       .longOpt(OPTION_DIAGRAM_FORMAT)
                                       .hasArg()
                                       .argName("format")
                                       .desc("diagram format: plantuml, mermaid, xmi, both, or all (default: both)")
                                       .build();
        options.addOption(formatOption);

        Option outputOption = Option.builder()
                                        .longOpt(OPTION_DIAGRAM_OUTPUT)
                                        .hasArg()
                                        .argName("directory")
                                        .desc("output directory for diagram files")
                                        .build();
        options.addOption(outputOption);

        Option packageOption = Option.builder()
                                         .longOpt(OPTION_DIAGRAM_PACKAGE)
                                         .hasArg()
                                         .argName("package")
                                         .desc("filter by package name (can be specified multiple times)")
                                         .build();
        options.addOption(packageOption);

        Option splitOption = new Option(null, OPTION_DIAGRAM_SPLIT_PACKAGES, false,
                "generate separate diagram per package");
        options.addOption(splitOption);

        Option includeSubtypesOption = new Option(null, OPTION_DIAGRAM_INCLUDE_SUBTYPES, false,
                "include subtypes in diagrams (excluded by default)");
        options.addOption(includeSubtypesOption);

        Option includeConstantsOption = new Option(null, OPTION_DIAGRAM_INCLUDE_CONSTANTS, false,
                "include constants in diagrams (excluded by default)");
        options.addOption(includeConstantsOption);

        Option layoutOption = Option.builder()
                                        .longOpt(OPTION_DIAGRAM_LAYOUT)
                                        .hasArg()
                                        .argName("engine")
                                        .desc("PlantUML layout engine: default, smetana, or elk (default: default)")
                                        .build();
        options.addOption(layoutOption);

        Option directionOption = Option.builder()
                                           .longOpt(OPTION_DIAGRAM_DIRECTION)
                                           .hasArg()
                                           .argName("direction")
                                           .desc("diagram direction: top-to-bottom or left-to-right (default: top-to-bottom)")
                                           .build();
        options.addOption(directionOption);

        Option orthoOption = new Option(null, OPTION_DIAGRAM_ORTHO, false,
                "use orthogonal lines in PlantUML diagrams (right-angle connectors)");
        options.addOption(orthoOption);

        Option xmiDiagramsOption = Option.builder()
                                              .longOpt(OPTION_DIAGRAM_XMI_DIAGRAMS)
                                              .hasArg()
                                              .argName("roots")
                                              .desc("comma-separated root type names for EA diagram definitions in XMI")
                                              .build();
        options.addOption(xmiDiagramsOption);

        Option xmiDepthOption = Option.builder()
                                           .longOpt(OPTION_DIAGRAM_XMI_DEPTH)
                                           .hasArg()
                                           .argName("depth")
                                           .desc("traversal depth for XMI diagram types (default: 0 = unlimited)")
                                           .build();
        options.addOption(xmiDepthOption);
    }

    @Override
    public boolean isEnabled(ExtensionParameters parameters)
    {
        return parameters.argumentExists(OPTION_DIAGRAM);
    }

    @Override
    public void check(Root rootNode, ExtensionParameters parameters) throws ZserioExtensionException
    {
        // No additional checks needed
    }

    @Override
    public void process(Root rootNode, ExtensionParameters parameters) throws ZserioExtensionException
    {
        // Parse options
        String format = getStringParameter(parameters, OPTION_DIAGRAM_FORMAT, "both");
        String outputDir = getStringParameter(parameters, OPTION_DIAGRAM_OUTPUT, ".");
        Set<String> packageFilters = getPackageFilters(parameters);
        boolean splitPackages = parameters.argumentExists(OPTION_DIAGRAM_SPLIT_PACKAGES);
        boolean includeSubtypes = parameters.argumentExists(OPTION_DIAGRAM_INCLUDE_SUBTYPES);
        boolean includeConstants = parameters.argumentExists(OPTION_DIAGRAM_INCLUDE_CONSTANTS);

        // Parse layout options
        DiagramConfig config = parseLayoutConfig(parameters);

        // Create output directory
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists())
        {
            outputDirectory.mkdirs();
        }

        // Walk the AST and build the diagram model
        DiagramEmitter emitter = new DiagramEmitter(packageFilters, includeSubtypes, includeConstants);
        rootNode.walk(emitter);
        DiagramModel model = emitter.getModel();

        System.out.println("Collected " + model.getTypeCount() + " types and " + model.getRelationshipCount() +
                " relationships");

        // Generate diagrams
        List<DiagramGenerator> generators = new ArrayList<>();
        if (format.equals("plantuml") || format.equals("both") || format.equals("all"))
        {
            generators.add(new PlantUmlGenerator());
        }
        if (format.equals("mermaid") || format.equals("both") || format.equals("all"))
        {
            generators.add(new MermaidGenerator());
        }
        if (format.equals("xmi") || format.equals("all"))
        {
            generators.add(new XmiGenerator());
        }

        for (DiagramGenerator generator : generators)
        {
            generator.setConfig(config);

            if (generator instanceof XmiGenerator)
            {
                String roots = getStringParameter(parameters, OPTION_DIAGRAM_XMI_DIAGRAMS, "");
                String depthStr = getStringParameter(parameters, OPTION_DIAGRAM_XMI_DEPTH, "0");
                int depth = Integer.parseInt(depthStr);
                ((XmiGenerator) generator).setDiagramRoots(roots);
                ((XmiGenerator) generator).setDiagramDepth(depth <= 0 ? Integer.MAX_VALUE : depth);
            }
        }

        for (DiagramGenerator generator : generators)
        {
            if (splitPackages)
            {
                generator.generatePerPackage(model, outputDirectory);
            }
            else
            {
                generator.generate(model, outputDirectory, "schema");
            }
        }

        System.out.println("Diagram generation complete. Output written to: " + outputDirectory.getAbsolutePath());
    }

    private String getStringParameter(ExtensionParameters parameters, String name, String defaultValue)
    {
        String value = parameters.getCommandLineArg(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private DiagramConfig parseLayoutConfig(ExtensionParameters parameters)
    {
        String layoutStr = getStringParameter(parameters, OPTION_DIAGRAM_LAYOUT, "default");
        DiagramConfig.LayoutEngine layout;
        switch (layoutStr.toLowerCase())
        {
        case "smetana":
            layout = DiagramConfig.LayoutEngine.SMETANA;
            break;
        case "elk":
            layout = DiagramConfig.LayoutEngine.ELK;
            break;
        default:
            layout = DiagramConfig.LayoutEngine.DEFAULT;
            break;
        }

        String dirStr = getStringParameter(parameters, OPTION_DIAGRAM_DIRECTION, "top-to-bottom");
        DiagramConfig.Direction direction;
        switch (dirStr.toLowerCase())
        {
        case "left-to-right":
        case "lr":
            direction = DiagramConfig.Direction.LEFT_TO_RIGHT;
            break;
        default:
            direction = DiagramConfig.Direction.TOP_TO_BOTTOM;
            break;
        }

        boolean orthoLines = parameters.argumentExists(OPTION_DIAGRAM_ORTHO);

        return new DiagramConfig(layout, direction, orthoLines);
    }

    private Set<String> getPackageFilters(ExtensionParameters parameters)
    {
        Set<String> filters = new HashSet<>();
        String packageArg = parameters.getCommandLineArg(OPTION_DIAGRAM_PACKAGE);
        if (packageArg != null && !packageArg.isEmpty())
        {
            // Handle multiple package specifications
            String[] packages = packageArg.split(",");
            for (String pkg : packages)
            {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty())
                {
                    filters.add(trimmed);
                }
            }
        }
        return filters;
    }

    private static final String EXTENSION_VERSION_STRING = "1.0.0";
    private static final String ZSERIO_VERSION_STRING = "2.18.0";

    private static final String OPTION_DIAGRAM = "diagram";
    private static final String OPTION_DIAGRAM_FORMAT = "diagram-format";
    private static final String OPTION_DIAGRAM_OUTPUT = "diagram-output";
    private static final String OPTION_DIAGRAM_PACKAGE = "diagram-package";
    private static final String OPTION_DIAGRAM_SPLIT_PACKAGES = "diagram-split-packages";
    private static final String OPTION_DIAGRAM_INCLUDE_SUBTYPES = "diagram-include-subtypes";
    private static final String OPTION_DIAGRAM_INCLUDE_CONSTANTS = "diagram-include-constants";
    private static final String OPTION_DIAGRAM_LAYOUT = "diagram-layout";
    private static final String OPTION_DIAGRAM_DIRECTION = "diagram-direction";
    private static final String OPTION_DIAGRAM_ORTHO = "diagram-ortho";
    private static final String OPTION_DIAGRAM_XMI_DIAGRAMS = "diagram-xmi-diagrams";
    private static final String OPTION_DIAGRAM_XMI_DEPTH = "diagram-xmi-depth";
}
