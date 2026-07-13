package zserio.extension.diagram;

/**
 * Configuration for diagram layout and rendering options.
 */
public class DiagramConfig
{
    public enum LayoutEngine
    {
        DEFAULT,
        SMETANA,
        ELK
    }

    public enum Direction
    {
        TOP_TO_BOTTOM,
        LEFT_TO_RIGHT
    }

    public DiagramConfig(LayoutEngine layout, Direction direction, boolean orthoLines)
    {
        this.layout = layout;
        this.direction = direction;
        this.orthoLines = orthoLines;
    }

    public LayoutEngine getLayout()
    {
        return layout;
    }

    public Direction getDirection()
    {
        return direction;
    }

    public boolean isOrthoLines()
    {
        return orthoLines;
    }

    private final LayoutEngine layout;
    private final Direction direction;
    private final boolean orthoLines;
}
