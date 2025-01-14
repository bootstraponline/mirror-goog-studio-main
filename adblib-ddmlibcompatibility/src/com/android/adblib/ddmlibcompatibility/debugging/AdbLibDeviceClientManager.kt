/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.createDeviceScope
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpProcessTracker
import com.android.adblib.trackDeviceInfo
import com.android.adblib.trackDevices
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.ClientData.DebuggerStatus
import com.android.ddmlib.IDevice
import com.android.ddmlib.clientmanager.DeviceClientManager
import com.android.ddmlib.clientmanager.DeviceClientManagerListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * Maximum amount of time to wait for a device to show up in [AdbSession.trackDevices]
 * after an [AdbLibDeviceClientManager] instance is created.
 */
private val DEVICE_TRACKER_WAIT_TIMEOUT = Duration.ofSeconds(2)

internal class AdbLibDeviceClientManager(
    private val clientManager: AdbLibClientManager,
    private val bridge: AndroidDebugBridge,
    private val iDevice: IDevice,
    private val listener: DeviceClientManagerListener
) : DeviceClientManager {

    private val logger = thisLogger(clientManager.session)

    private val deviceSelector = DeviceSelector.fromSerialNumber(iDevice.serialNumber)

    internal val session: AdbSession
        get() = clientManager.session

    private val retryDelay = Duration.ofSeconds(2)

    private val clientList = AtomicReference<List<Client>>(emptyList())

    override fun getDevice(): IDevice {
        return iDevice
    }

    override fun getClients(): MutableList<Client> {
        return clientList.get().toMutableList()
    }

    fun startDeviceTracking() {
        session.scope.launch {
            // Wait for the device to show in the list of tracked devices
            val deviceScope = withTimeoutOrNull(DEVICE_TRACKER_WAIT_TIMEOUT.toMillis()) {
                logger.debug { "Waiting for device ${iDevice.serialNumber} to show up in device tracker" }
                waitForDevice(iDevice.serialNumber)
                logger.debug { "Found device ${iDevice.serialNumber} in device tracker, creating device scope" }
                session.createDeviceScope(deviceSelector)
            } ?: run {
                val msg = "Could not find device ${iDevice.serialNumber} in list of tracked devices"
                logger.info { msg }
                throw CancellationException(msg)
            }

            // Track processes running on the device
            launchProcessTracking(deviceScope)
        }
    }

    private suspend fun waitForDevice(serialNumber: String) {
        // Collects the list of device tracking flow until the device shows up.
        session.trackDevices().takeWhile { list ->
            list.devices.entries.none { it.serialNumber == serialNumber }
        }.collect()
    }

    private fun launchProcessTracking(deviceScope: CoroutineScope) {
        deviceScope.launch(session.host.ioDispatcher) {
            logger.debug { "Starting process tracking for device $iDevice" }
            try {
                session.trackDeviceInfo(deviceSelector)
                    .filter {
                        // Wait until device is "ONLINE"
                        it.deviceState == DeviceState.ONLINE
                    }.map {
                        // Run the 'jdwp-track' service and collect PIDs
                        trackProcesses(deviceScope)
                    }.retryWhen { throwable, _ ->
                        logger.warn(
                            throwable,
                            "Device process tracking failed for device $deviceSelector " +
                                    "($throwable), retrying in ${retryDelay.seconds} sec"
                        )
                        // We retry as long as the device is valid
                        if (deviceScope.isActive) {
                            delay(retryDelay.toMillis())
                        }
                        deviceScope.isActive
                    }.collect()
            } finally {
                logger.debug { "Stop process tracking for device $iDevice (scope.isActive=${deviceScope.isActive})" }
            }
        }
    }

    private suspend fun trackProcesses(deviceScope: CoroutineScope) {
        val processEntryMap = mutableMapOf<Int, AdblibClientWrapper>()
        try {
            JdwpProcessTracker(clientManager.session, deviceSelector).createFlow()
                .collect { processList ->
                    updateProcessList(deviceScope, processEntryMap, processList)
                }
        } finally {
            updateProcessList(deviceScope, processEntryMap, emptyList())
        }
    }

    /**
     * Update our list of processes and invoke listeners.
     */
    private fun updateProcessList(
        deviceScope: CoroutineScope,
        currentProcessEntryMap: MutableMap<Int, AdblibClientWrapper>,
        newJdwpProcessList: List<JdwpProcess>
    ) {
        val knownPids = currentProcessEntryMap.keys.toHashSet()
        val effectivePids = newJdwpProcessList.map { it.pid }.toHashSet()
        val addedPids = effectivePids - knownPids
        val removePids = knownPids - effectivePids

        // Remove old pids
        removePids.forEach { pid ->
            logger.debug { "Removing PID $pid from list of Client processes" }
            currentProcessEntryMap.remove(pid)
        }

        // Add new pids
        addedPids.forEach { pid ->
            logger.debug { "Adding PID $pid to list of Client processes" }
            val jdwpProcess = newJdwpProcessList.first { it.pid == pid }
            val clientWrapper = AdblibClientWrapper(this, iDevice, jdwpProcess)
            currentProcessEntryMap[pid] = clientWrapper
            launchProcessInfoTracking(clientWrapper)
        }

        assert(currentProcessEntryMap.keys.size == newJdwpProcessList.size)
        clientList.set(currentProcessEntryMap.values.toList())
        invokeListener(deviceScope) {
            listener.processListUpdated(bridge, this)
        }
    }

    private fun launchProcessInfoTracking(clientWrapper: AdblibClientWrapper) {
        // Track process changes as long as process coroutine scope is active
        clientWrapper.jdwpProcess.scope.launch {
            trackProcessInfo(clientWrapper)
        }
    }

    private suspend fun trackProcessInfo(clientWrapper: AdblibClientWrapper) {
        var lastProcessInfo = clientWrapper.jdwpProcess.propertiesFlow.value
        clientWrapper.jdwpProcess.propertiesFlow.collect { processInfo ->
            try {
                updateProcessInfo(clientWrapper, lastProcessInfo, processInfo)
            } finally {
                lastProcessInfo = processInfo
            }
        }
    }

    private fun updateProcessInfo(
        clientWrapper: AdblibClientWrapper,
        previousProcessInfo: JdwpProcessProperties,
        newProcessInfo: JdwpProcessProperties
    ) {
        fun <T> hasChanged(x: T?, y: T?): Boolean {
            return x != y
        }

        // Always update "Client" wrapper data
        val previousDebuggerStatus = clientWrapper.clientData.debuggerConnectionStatus
        updateClientWrapper(clientWrapper, newProcessInfo)
        val newDebuggerStatus = clientWrapper.clientData.debuggerConnectionStatus

        // Check if anything related to process info has changed
        with(previousProcessInfo) {
            if (hasChanged(processName, newProcessInfo.processName) ||
                hasChanged(userId, newProcessInfo.userId) ||
                hasChanged(packageName, newProcessInfo.packageName) ||
                hasChanged(vmIdentifier, newProcessInfo.vmIdentifier) ||
                hasChanged(abi, newProcessInfo.abi) ||
                hasChanged(jvmFlags, newProcessInfo.jvmFlags) ||
                hasChanged(isWaitingForDebugger, newProcessInfo.isWaitingForDebugger) ||
                hasChanged(isNativeDebuggable, newProcessInfo.isNativeDebuggable)
            ) {
                invokeListener(clientWrapper.jdwpProcess.scope) {
                    // Note that "name" is really "any property"
                    listener.processNameUpdated(
                        bridge,
                        this@AdbLibDeviceClientManager,
                        clientWrapper
                    )
                }
            }
        }

        // Debugger status change is handled through its own callback
        if (hasChanged(previousDebuggerStatus, newDebuggerStatus)) {
            clientWrapper.clientData.debuggerConnectionStatus = newDebuggerStatus
            invokeListener(clientWrapper.jdwpProcess.scope) {
                listener.processDebuggerStatusUpdated(
                    bridge,
                    this@AdbLibDeviceClientManager,
                    clientWrapper
                )
            }
        }
    }

    private fun updateClientWrapper(
        clientWrapper: AdblibClientWrapper,
        newProperties: JdwpProcessProperties
    ) {
        val names = ClientData.Names(
            newProperties.processName ?: "",
            newProperties.userId,
            newProperties.packageName
        )
        clientWrapper.clientData.setNames(names)
        clientWrapper.clientData.vmIdentifier = newProperties.vmIdentifier
        clientWrapper.clientData.abi = newProperties.abi
        clientWrapper.clientData.jvmFlags = newProperties.jvmFlags
        clientWrapper.clientData.isNativeDebuggable = newProperties.isNativeDebuggable

        // "DebuggerStatus" is trickier: order is important
        clientWrapper.clientData.debuggerConnectionStatus = when {
            // This comes from the JDWP connection proxy, when a JDWP connection is started
            newProperties.jdwpSessionProxyStatus.isExternalDebuggerAttached -> DebuggerStatus.ATTACHED

            // This comes from seeing a DDMS_WAIT packet on the JDWP connection
            newProperties.isWaitingForDebugger -> DebuggerStatus.WAITING

            // This comes from any error during process properties polling
            newProperties.exception != null -> DebuggerStatus.ERROR

            // This happens when process properties have been collected and also
            // when there is no active jdwp debugger connection
            else -> DebuggerStatus.DEFAULT
        }
    }

    private fun invokeListener(scope: CoroutineScope, block: () -> Unit) {
        scope.launch(session.host.ioDispatcher) {
            kotlin.runCatching {
                block()
            }.onFailure { throwable ->
                logger.warn(throwable, "Invoking ddmlib listener threw an exception: $throwable")
            }
        }
    }
}
