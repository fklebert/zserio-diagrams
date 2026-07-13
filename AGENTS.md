# AGENTS.md

This file provides guidance to agentic coding tools working with code in this repository.

## Build Commands

```bash
ant zserio_bundle.install  # Build bundled zserio.jar with diagram extension (recommended)
ant jar                    # Build only the extension jar
ant install                # Install extension jar to distr/
ant clean                  # Clean build artifacts
```

## Testing the Extension

```bash
# Golden-file regression tests (requires distr/zserio.jar from the build above)
python3 tests/run_tests.py

# After INTENTIONAL output changes: regenerate golden files, then review the diff
python3 tests/run_tests.py --update
git diff tests/expected

# Ad-hoc generation from the bundled example schema
java -jar distr/zserio.jar -src zs railway.zs -diagram -diagram-format all -diagram-output ./output

# Structurally validate generated XMI against EA expectations
python3 validate_xmi.py output/*.xmi
```

Run the golden-file tests after every change to the extension source. Never blanket-regenerate golden files to make a failing test pass — a diff you can't explain is a bug.

The example schema in `zs/` exercises every construct the diagrams visualize (structs, choice-on-selector, union, enums, bitmasks, subtypes, const, parameterized types, optional/fixed/dynamic arrays, bit fields) — treat it as the regression fixture. The full CLI option reference is in README.md.

## Architecture

This is a **zserio extension** that generates PlantUML, Mermaid class diagrams, and XMI files (for Sparx Enterprise Architect). It follows the zserio extension pattern:

1. **DiagramExtension.java** - Implements `zserio.tools.Extension` interface, registers CLI options (`-diagram`, `-diagram-format`, etc.), coordinates the workflow
2. **DiagramEmitter.java** - Extends `DefaultTreeWalker` to traverse the zserio AST and collect type information into the model
3. **DiagramConfig.java** - Configuration record for layout engine (`default`/`smetana`/`elk`), direction, and orthogonal lines
4. **Model classes** (`model/`) - Data structures holding collected types (`TypeInfo`), fields (`FieldInfo`), and relationships (`RelationshipInfo`) in a `DiagramModel`
5. **Generators** (`generator/`) - `PlantUmlGenerator`, `MermaidGenerator`, and `XmiGenerator` implement `DiagramGenerator` interface to produce output files
6. **XmiDiagramWriter.java** (`generator/`) - Writes the EA-specific `<xmi:Extension>` section: element metadata, connector metadata, and optional diagram definitions with layout

### XMI/EA Integration Details

The XMI export produces EA-compatible output with:
- `<xmi:Documentation exporter="Enterprise Architect">` header so EA processes the Extension section
- A root `<packagedElement xmi:type="uml:Package">` wrapping all content (EA requires this hierarchy)
- `<xmi:Extension>` containing `<elements>` (with `<packageproperties>` for packages), `<connectors>`, and `<diagrams>`
- `XmiGenerator.generateId()` is `static` package-private so `XmiDiagramWriter` can produce matching subject IDs
- Diagram root types support wildcard patterns (`*`, `?`) matched against simple and fully-qualified type names
- Diagram depth defaults to unlimited (0); set `-diagram-xmi-depth N` to limit traversal hops

**Service discovery**: The extension is registered via `metainf/services/zserio.tools.Extension` which contains the fully qualified class name.

**Version coupling**: The `ZSERIO_VERSION_STRING` in `DiagramExtension.java` must match the zserio version being used (currently 2.18.0). The Ant build parses both version strings out of `DiagramExtension.java` directly.

## Key zserio AST Notes

- `BuiltInType` and `FixedBitFieldType` don't support `getPackage()` - handle with try/catch or instanceof checks
- Use `ArrayInstantiation.getElementTypeInstantiation()` to get the element type for arrays
- `Expression.getIntegerValue()` returns fixed array sizes; dynamic expressions return null

## Key EA/XMI Notes

- EA ignores `<xmi:Extension>` unless `<xmi:Documentation exporter="Enterprise Architect" exporterVersion="6.5" exporterID="1710"/>` is present
- EA requires a root `<packagedElement xmi:type="uml:Package">` inside `<uml:Model>` — packages directly under the model are not recognized as valid diagram targets
- Package elements in the Extension need `<packageproperties>`, `<paths/>`, `<times>`, `<flags>` and `package2` attribute on `<model>` for EA to register them as proper packages
- Stereotypes written as `ownedComment` with `<<name>>` body show as visible decorators in EA — use the Extension `<properties stereotype="...">` attribute instead (or omit for clean display)
- When in doubt about a format detail, compare against a native EA XMI export of a small test model
