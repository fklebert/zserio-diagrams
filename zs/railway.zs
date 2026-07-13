/**
 * Example zserio schema: a small fictional railway database.
 *
 * This schema exists to demonstrate the diagram extension. It deliberately
 * uses every zserio construct the diagrams can visualize: structs, choices,
 * unions, enums, bitmasks, subtypes, constants, parameterized types,
 * optional fields, fixed- and variable-size arrays, and bit fields.
 */
package railway;

import railway.network.*;
import railway.rollingstock.*;
import railway.signalling.*;
import railway.timetable.*;

/** Top-level container tying all sub-packages together. */
struct RailwayDatabase
{
    /** The physical rail network: stations and track segments. */
    RailNetwork network;

    /** All trains operating on the network. */
    Train fleet[];

    /** Trackside signals. */
    Signal signals[];

    /** The current timetable. */
    Timetable timetable;
};
