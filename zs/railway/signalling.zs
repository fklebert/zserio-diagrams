/**
 * Trackside signalling. Demonstrates a choice type with an enum selector.
 */
package railway.signalling;

import railway.common.*;

/** Kind of trackside signal. */
enum uint8 SignalType
{
    /** Governs entry into the section ahead. */
    MAIN,

    /** Announces the aspect of the next main signal. */
    DISTANT,

    /** Governs shunting movements only. */
    SHUNTING
};

/** Aspects a main signal can show. */
enum uint8 MainAspect
{
    STOP,
    CLEAR,
    CAUTION
};

/** Aspects a distant signal can show. */
enum uint8 DistantAspect
{
    EXPECT_STOP,
    EXPECT_CLEAR
};

/** Aspects a shunting signal can show. */
enum uint8 ShuntingAspect
{
    HALT,
    PROCEED
};

/** The current aspect, depending on the signal type. */
choice SignalState(SignalType type) on type
{
    case MAIN:
        MainAspect mainAspect;

    case DISTANT:
        DistantAspect distantAspect;

    case SHUNTING:
        ShuntingAspect shuntingAspect;
};

/** A signal installed along a track segment. */
struct Signal
{
    TrackId track;

    /** Distance from the start of the track segment. */
    uint32 positionMeters;

    SignalType type;

    SignalState(type) state;
};
