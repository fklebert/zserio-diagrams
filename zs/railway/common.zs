/**
 * Shared identifiers and basic types used across the railway schema.
 */
package railway.common;

/** Unique identifier of a station. */
subtype uint32 StationId;

/** Unique identifier of a track segment. */
subtype uint32 TrackId;

/** Unique identifier of a train. */
subtype uint16 TrainId;

/** Highest platform number a station may have. */
const uint8 MAX_PLATFORMS = 64;

/** Track gauge classification. */
enum uint8 TrackGauge
{
    /** 1435 mm. */
    STANDARD,

    /** Less than 1435 mm. */
    NARROW,

    /** More than 1435 mm. */
    BROAD
};

/** A WGS84 position with fixed-point coordinates. */
struct GeoPosition
{
    /** Latitude in 1e-7 degrees. */
    int32 latitudeE7;

    /** Longitude in 1e-7 degrees. */
    int32 longitudeE7;

    /** Elevation above sea level, if surveyed. */
    optional int16 altitudeMeters;
};

/** Wall-clock time packed into 11 bits. */
struct TimeOfDay
{
    /** Hour of day (0-23). */
    bit:5 hour;

    /** Minute of hour (0-59). */
    bit:6 minute;
};
