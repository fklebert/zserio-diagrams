/**
 * The physical rail network: stations, platforms and track segments.
 */
package railway.network;

import railway.common.*;

/** The complete rail network of one operator. */
struct RailNetwork
{
    /** Display name of the network. */
    string name;

    /** Two opposite corners enclosing the whole network. */
    GeoPosition boundingBox[2];

    /** All stations, ordered by id. */
    Station stations[];

    /** All track segments connecting the stations. */
    TrackSegment segments[];
};

/** A passenger or freight station. */
struct Station
{
    StationId id;

    string name;

    GeoPosition position;

    /** Platforms available at this station. */
    Platform platforms[];
};

/** A single platform within a station. */
struct Platform
{
    /** Platform number as printed on signage (1..MAX_PLATFORMS). */
    uint8 number;

    /** Usable platform length. */
    uint16 lengthMeters;

    bool hasShelter;
};

/** A stretch of track between two stations. */
struct TrackSegment
{
    TrackId id;

    StationId fromStation;

    StationId toStation;

    TrackGauge gauge;

    Electrification electrification;

    uint32 lengthMeters;

    /** Present only where the line speed is reduced. */
    optional SpeedRestriction speedRestriction;
};

/** Overhead line / third rail electrification system. */
enum uint8 Electrification
{
    NONE,
    DC_1500V,
    DC_3000V,
    AC_15KV_16_7HZ,
    AC_25KV_50HZ
};

/** A temporary or permanent speed restriction on a segment. */
struct SpeedRestriction
{
    uint16 maxSpeedKmh;

    /** Human-readable justification, e.g. "bridge under repair". */
    string reason;
};
