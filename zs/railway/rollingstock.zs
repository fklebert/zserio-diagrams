/**
 * Rolling stock: locomotives, cars and train compositions.
 */
package railway.rollingstock;

import railway.common.*;

/** On-board amenities of a passenger car (combinable flags). */
bitmask uint8 Amenities
{
    WIFI,
    POWER_SOCKETS,
    AIR_CONDITIONING,
    BISTRO,
    WHEELCHAIR_ACCESS
};

/** Primary traction of a locomotive. */
enum uint8 TractionType
{
    DIESEL,
    ELECTRIC,
    HYBRID,
    STEAM
};

/** A locomotive or power car. */
struct Locomotive
{
    /** Class designation, e.g. "BR 101". */
    string designation;

    TractionType traction;

    uint16 powerKw;
};

/** A car for passengers. */
struct PassengerCar
{
    uint16 seatCount;

    Amenities amenities;
};

/** A car for goods. */
struct FreightCar
{
    uint32 maxLoadKg;

    bool hazardousGoods;
};

/** The payload-specific part of a car. */
union CarBody
{
    PassengerCar passenger;

    FreightCar freight;
};

/** One car of a train. */
struct Car
{
    uint32 tareWeightKg;

    CarBody body;
};

/**
 * An ordered sequence of cars. The length is supplied by the containing
 * type, demonstrating a parameterized type.
 */
struct TrainComposition(uint8 carCount)
{
    Car cars[carCount];
};

/** A complete train: locomotive plus composition. */
struct Train
{
    TrainId id;

    /** Marketing name, if any, e.g. "Night Owl Express". */
    optional string name;

    Locomotive locomotive;

    uint8 carCount;

    TrainComposition(carCount) composition;
};
