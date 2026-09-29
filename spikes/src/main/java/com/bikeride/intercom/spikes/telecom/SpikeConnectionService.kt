package com.bikeride.intercom.spikes.telecom

import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Self-Managed ConnectionService for Spike H.
 *
 * Evaluates Android Telecom framework benefits:
 * - Native VoIP call routing
 * - Native Bluetooth SCO prioritization
 * - Clean interaction with incoming cellular calls
 * - High process priority (OOM adjustment)
 */
@RequiresApi(Build.VERSION_CODES.O)
class SpikeConnectionService : ConnectionService() {

    companion object {
        var activeConnection: SpikeConnection? = null
            private set
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Timber.i("SpikeConnectionService: onCreateOutgoingConnection")
        val connection = SpikeConnection()
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD
        connection.setInitializing()
        connection.setActive()
        activeConnection = connection
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Timber.i("SpikeConnectionService: onCreateIncomingConnection")
        val connection = SpikeConnection()
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD
        connection.setRinging()
        activeConnection = connection
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Timber.e("SpikeConnectionService: onCreateOutgoingConnectionFailed")
        activeConnection = null
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Timber.e("SpikeConnectionService: onCreateIncomingConnectionFailed")
        activeConnection = null
    }
}

class SpikeConnection : Connection() {
    init {
        audioModeIsVoip = true
    }

    override fun onShowIncomingCallUi() {
        Timber.i("SpikeConnection: onShowIncomingCallUi")
    }

    override fun onCallAudioStateChanged(state: android.telecom.CallAudioState?) {
        Timber.i("SpikeConnection: Audio route changed: ${state?.route}")
    }

    override fun onDisconnect() {
        Timber.i("SpikeConnection: onDisconnect")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onAbort() {
        Timber.i("SpikeConnection: onAbort")
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }
}
