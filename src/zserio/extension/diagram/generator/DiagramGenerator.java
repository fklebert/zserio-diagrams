package zserio.extension.diagram.generator;

import java.io.File;

import zserio.extension.common.ZserioExtensionException;
import zserio.extension.diagram.DiagramConfig;
import zserio.extension.diagram.model.DiagramModel;

/**
 * Interface for diagram generators.
 */
public interface DiagramGenerator
{
    /**
     * Gets the file extension for this diagram format.
     *
     * @return The file extension (e.g., ".puml", ".mmd").
     */
    String getFileExtension();

    /**
     * Gets the name of this diagram format.
     *
     * @return The format name.
     */
    String getFormatName();

    /**
     * Sets the diagram configuration for layout options.
     *
     * @param config The diagram configuration.
     */
    void setConfig(DiagramConfig config);

    /**
     * Generates a single diagram file containing all types.
     *
     * @param model The diagram model.
     * @param outputDir The output directory.
     * @param baseName The base name for the output file.
     * @throws ZserioExtensionException If generation fails.
     */
    void generate(DiagramModel model, File outputDir, String baseName) throws ZserioExtensionException;

    /**
     * Generates separate diagram files for each package.
     *
     * @param model The diagram model.
     * @param outputDir The output directory.
     * @throws ZserioExtensionException If generation fails.
     */
    void generatePerPackage(DiagramModel model, File outputDir) throws ZserioExtensionException;
}
