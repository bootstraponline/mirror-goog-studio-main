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
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.debugging.sendDdmsExit
import com.android.adblib.withErrorTimeout
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.IDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Implementation of the ddmlib [Client] interface based on a [JdwpProcess] instance.
 */
internal class AdblibClientWrapper(
    private val deviceClientManager: AdbLibDeviceClientManager,
    private val iDevice: IDevice,
    val jdwpProcess: JdwpProcess
) : Client {

    private val clientDataWrapper = ClientData(this, jdwpProcess.pid)

    override fun getDevice(): IDevice {
        return iDevice
    }

    override fun isDdmAware(): Boolean {
        // Note: This should return `true` when there has been DDMS packet seen
        //  on the JDWP connection to the process. This is a signal the process
        //  is a process running on an Android VM.
        // We use vmIdentifier as a proxy for checking a DDM HELO packet has
        // been received.
        return jdwpProcess.properties.vmIdentifier != null
    }

    override fun getClientData(): ClientData {
        return clientDataWrapper
    }

    override fun kill() {
        // Sends a DDMS EXIT packet to the VM
        deviceClientManager.session.runBlockingLegacy {
            jdwpProcess.withJdwpSession {
                sendDdmsExit(1)
            }
        }
    }

    /**
     * In ddmlib case, this method would return `true` when ddmlib had an active JDWP socket
     * connection with the process on the device.
     * Since we use "on-demand" JDWP connection, we return `true` when 1) the process is still
     * active and 2) we were able to retrieve all process properties during the initial JDWP
     * connection.
     */
    override fun isValid(): Boolean {
        return jdwpProcess.scope.isActive &&
                jdwpProcess.properties.vmIdentifier != null
    }

    /**
     * Returns the TCP port (on "localhost") that an "external" debugger (i.e. IntelliJ or
     * Android Studio) can connect to open a JDWP session with the process.
     */
    override fun getDebuggerListenPort(): Int {
        return jdwpProcess.properties.jdwpSessionProxyStatus.socketAddress?.port ?: -1
    }

    /**
     * Returns `true' if there is an "external" debugger (i.e. IntelliJ or Android Studio)
     * currently attached to the process via a JDWP session.
     */
    override fun isDebuggerAttached(): Boolean {
        return jdwpProcess.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached
    }

    override fun executeGarbageCollector() {
        TODO("Not yet implemented")
    }

    override fun startMethodTracer() {
        TODO("Not yet implemented")
    }

    override fun stopMethodTracer() {
        TODO("Not yet implemented")
    }

    override fun startSamplingProfiler(samplingInterval: Int, timeUnit: TimeUnit?) {
        TODO("Not yet implemented")
    }

    override fun stopSamplingProfiler() {
        TODO("Not yet implemented")
    }

    override fun requestAllocationDetails() {
        TODO("Not yet implemented")
    }

    override fun enableAllocationTracker(enabled: Boolean) {
        TODO("Not yet implemented")
    }

    override fun notifyVmMirrorExited() {
        TODO("Not yet implemented")
    }

    override fun listViewRoots(replyHandler: DebugViewDumpHandler?) {
        TODO("Not yet implemented")
    }

    override fun captureView(
        viewRoot: String,
        view: String,
        handler: DebugViewDumpHandler
    ) {
        TODO("Not yet implemented")
    }

    override fun dumpViewHierarchy(
        viewRoot: String,
        skipChildren: Boolean,
        includeProperties: Boolean,
        useV2: Boolean,
        handler: DebugViewDumpHandler
    ) {
        TODO("Not yet implemented")
    }

    override fun dumpDisplayList(viewRoot: String, view: String) {
        TODO("Not yet implemented")
    }

    /**
     * Similar to [runBlocking] but with a custom [timeout]
     *
     * @throws TimeoutException if [block] take more than [timeout] to execute
     */
    private fun <T> AdbSession.runBlockingLegacy(
        timeout: Duration = RUN_BLOCKING_LEGACY_DEFAULT_TIMEOUT,
        block: suspend CoroutineScope.() -> T
    ): T {
        return runBlocking {
            withErrorTimeout(timeout) {
                block()
            }
        }
    }
}

private val RUN_BLOCKING_LEGACY_DEFAULT_TIMEOUT: Duration = Duration.ofMillis(5_000)
