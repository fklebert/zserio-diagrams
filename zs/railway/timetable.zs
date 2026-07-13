/**
 * Timetable: services and their scheduled stops.
 */
package railway.timetable;

import railway.common.*;

/** Days on which a service operates (combinable flags). */
bitmask uint8 OperatingDays
{
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
};

/** Commercial category of a service. */
enum uint8 ServiceCategory
{
    REGIONAL,
    INTERCITY,
    HIGH_SPEED,
    NIGHT,
    FREIGHT
};

/** The complete timetable for one schedule period. */
struct Timetable
{
    /** Human-readable validity, e.g. "2026 summer schedule". */
    string validityPeriod;

    Service services[];
};

/** One scheduled train run. */
struct Service
{
    TrainId trainId;

    ServiceCategory category;

    OperatingDays operatingDays;

    /** Stops in order of travel. */
    TimetableStop stops[];
};

/** A scheduled stop at a station. */
struct TimetableStop
{
    StationId station;

    TimeOfDay arrival;

    TimeOfDay departure;

    /** Assigned platform, if already known. */
    optional uint8 platformNumber;
};
