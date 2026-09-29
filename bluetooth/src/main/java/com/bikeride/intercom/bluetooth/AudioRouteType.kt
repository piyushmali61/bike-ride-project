package com.bikeride.intercom.bluetooth

/**
 * Audio output routes available for motorcycle intercoms.
 */
enum class AudioRouteType {
    HELMET_BLUETOOTH, // Bluetooth Headset / SCO (Cardo, Sena, Helmet systems, Earbuds)
    LOUDSPEAKER,      // Device speakerphone
    EARPIECE          // Device earpiece
}
