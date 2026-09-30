package com.bikeride.intercom.mesh

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bluetooth LE radio for the convoy mesh.
 *
 * Every phone is at the same time:
 * - a **peripheral**: advertises [SERVICE_UUID] (no name, no personal data) and runs a GATT
 *   server whose single characteristic receives writes and sends notifications;
 * - a **central**: scans for [SERVICE_UUID] and connects to up to [MAX_CENTRAL_LINKS] phones.
 *
 * Each connection is a link. Bytes go out through a per-link queue because Android allows only
 * one outstanding GATT operation per connection.
 */
@SuppressLint("MissingPermission")
class BleMeshLink(
    private val context: Context,
    private val onReceive: (linkId: String, bytes: ByteArray) -> Unit,
    private val onLinkUp: (linkId: String) -> Unit
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private var scope: CoroutineScope? = null
    private var gattServer: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null

    private val links = ConcurrentHashMap<String, Link>()
    private val connecting = ConcurrentHashMap.newKeySet<String>()

    private val _linkCount = MutableStateFlow(0)
    val linkCount: StateFlow<Int> = _linkCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** One connected neighbour, reached either as a GATT client or through our GATT server. */
    private inner class Link(val id: String, val device: BluetoothDevice, val gatt: BluetoothGatt?) {
        val queue = Channel<ByteArray>(capacity = 64)
        var opDone: CompletableDeferred<Unit>? = null
        var mtu = 23
        var worker: Job? = null
        var characteristic: BluetoothGattCharacteristic? = null

        fun startWorker(scope: CoroutineScope) {
            worker = scope.launch(Dispatchers.IO) {
                for (bytes in queue) {
                    if (bytes.size > mtu - 3) {
                        Timber.d("Mesh: packet ${bytes.size}B too big for MTU $mtu on $id")
                        continue
                    }
                    val done = CompletableDeferred<Unit>()
                    opDone = done
                    if (!writeNow(bytes)) continue
                    withTimeoutOrNull(1500) { done.await() }
                }
            }
        }

        private fun writeNow(bytes: ByteArray): Boolean = try {
            if (gatt != null) {
                val ch = characteristic ?: return false
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    ch.value = bytes
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(ch)
                }
            } else {
                val server = gattServer ?: return false
                val ch = serverCharacteristic ?: return false
                if (Build.VERSION.SDK_INT >= 33) {
                    server.notifyCharacteristicChanged(device, ch, false, bytes) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    ch.value = bytes
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, ch, false)
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Mesh: write failed on $id")
            false
        }

        fun close() {
            worker?.cancel()
            queue.close()
            try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        }
    }

    fun start(parent: CoroutineScope) {
        if (_isRunning.value) return
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            Timber.w("Mesh: Bluetooth unavailable or off")
            return
        }
        scope = CoroutineScope(parent.coroutineContext + SupervisorJob())
        try {
            openServer()
            startAdvertising(bt)
            startScanning(bt)
            _isRunning.value = true
            Timber.i("Mesh: BLE started")
        } catch (e: SecurityException) {
            Timber.w(e, "Mesh: Bluetooth permission missing")
            stop()
        }
    }

    fun stop() {
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
        try { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        links.values.forEach { it.close() }
        links.clear()
        connecting.clear()
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
        scope?.cancel()
        scope = null
        _linkCount.value = 0
        _isRunning.value = false
    }

    /** Sends to every connected neighbour except [exceptLinkId] (the one it came from). */
    fun broadcast(bytes: ByteArray, exceptLinkId: String? = null) {
        links.values.forEach { if (it.id != exceptLinkId) it.queue.trySend(bytes) }
    }

    /** Sends to one neighbour (used to replay stored packets to a new link). */
    fun sendTo(linkId: String, bytes: ByteArray) {
        links[linkId]?.queue?.trySend(bytes)
    }

    // ── Peripheral side ─────────────────────────────────────────────

    private fun openServer() {
        val server = manager?.openGattServer(context, serverCallback) ?: return
        val characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        characteristic.addDescriptor(
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)
        server.addService(service)
        gattServer = server
        serverCharacteristic = characteristic
    }

    private fun startAdvertising(bt: BluetoothAdapter) {
        val advertiser = bt.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            guard("onStartFailure") {
                Timber.w("Mesh: advertising failed ($errorCode)")
            }
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            guard("onConnectionStateChange") {
                val id = "s:${device.address}"
                if (newState == BluetoothProfile.STATE_DISCONNECTED) removeLink(id)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            guard("onMtuChanged") {
                links["s:${device.address}"]?.mtu = mtu
                pendingServerMtu[device.address] = mtu
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            guard("onDescriptorWriteRequest") {
                if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                // The client subscribed to notifications: it is now a full link.
                if (descriptor.uuid == CCCD_UUID && value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true) {
                    val id = "s:${device.address}"
                    if (!links.containsKey(id)) {
                        val link = Link(id, device, gatt = null)
                        link.mtu = pendingServerMtu[device.address] ?: 185
                        addLink(link)
                    }
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            guard("onCharacteristicWriteRequest") {
                if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                if (value != null) onReceive("s:${device.address}", value)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            guard("onNotificationSent") {
                links["s:${device.address}"]?.opDone?.complete(Unit)
            }
        }
    }

    private val pendingServerMtu = ConcurrentHashMap<String, Int>()

    // ── Central side ────────────────────────────────────────────────

    private fun startScanning(bt: BluetoothAdapter) {
        val scanner = bt.bluetoothLeScanner ?: return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner.startScan(filters, settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            guard("onScanResult") {
                val device = result.device
                val id = "c:${device.address}"
                val centralLinks = links.keys.count { it.startsWith("c:") }
                if (links.containsKey(id) || !connecting.add(id)) return
                if (centralLinks >= MAX_CENTRAL_LINKS) {
                    connecting.remove(id)
                    return
                }
                try {
                    device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
                } catch (e: Exception) {
                    connecting.remove(id)
                    Timber.d(e, "Mesh: connect failed")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            guard("onScanFailed") {
                Timber.w("Mesh: scan failed ($errorCode)")
            }
        }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            guard("onConnectionStateChange") {
                val id = "c:${gatt.device.address}"
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.requestMtu(517)
                } else {
                    connecting.remove(id)
                    removeLink(id)
                    try { gatt.close() } catch (_: Exception) {}
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            guard("onMtuChanged") {
                pendingClientMtu[gatt.device.address] = mtu
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            guard("onServicesDiscovered") {
                val ch = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
                if (ch == null) {
                    gatt.disconnect()
                    return
                }
                gatt.setCharacteristicNotification(ch, true)
                val cccd = ch.getDescriptor(CCCD_UUID) ?: return
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            guard("onDescriptorWrite") {
                val id = "c:${gatt.device.address}"
                connecting.remove(id)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gatt.disconnect()
                    return
                }
                val link = Link(id, gatt.device, gatt)
                link.mtu = pendingClientMtu[gatt.device.address] ?: 23
                link.characteristic = descriptor.characteristic
                addLink(link)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            guard("onCharacteristicWrite") {
                links["c:${gatt.device.address}"]?.opDone?.complete(Unit)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            guard("onCharacteristicChanged") {
                onReceive("c:${gatt.device.address}", value)
            }
        }

        @Deprecated("Used below API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            guard("onCharacteristicChanged") {
                if (Build.VERSION.SDK_INT < 33) {
                    @Suppress("DEPRECATION")
                    characteristic.value?.let { onReceive("c:${gatt.device.address}", it) }
                }
            }
        }
    }

    private val pendingClientMtu = ConcurrentHashMap<String, Int>()

    /** Bluetooth can be switched off or its permission revoked mid-ride; never let that crash the app. */
    private inline fun guard(where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "Mesh: Bluetooth error in $where")
        }
    }

    private fun addLink(link: Link) {
        val s = scope ?: return
        links.put(link.id, link)?.close()
        link.startWorker(s)
        _linkCount.value = links.size
        Timber.i("Mesh: link up ${link.id} (mtu ${link.mtu}), ${links.size} total")
        onLinkUp(link.id)
    }

    private fun removeLink(id: String) {
        links.remove(id)?.let {
            it.close()
            _linkCount.value = links.size
            Timber.i("Mesh: link down $id, ${links.size} left")
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("7a3f0c52-8d1e-4b6a-9c2f-a57121de0001")
        val CHAR_UUID: UUID = UUID.fromString("7a3f0c52-8d1e-4b6a-9c2f-a57121de0002")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val MAX_CENTRAL_LINKS = 6
    }
}
