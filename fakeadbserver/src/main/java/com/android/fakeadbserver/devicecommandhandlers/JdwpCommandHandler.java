/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.fakeadbserver.devicecommandhandlers;

import static com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpPacket.readFrom;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DDMPacketHandler;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DdmPacket;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.ExitHandler;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.HeloHandler;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpCommandId;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpPacket;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpPacketHandler;

import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpVmExitHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * jdwp:pid changes the connection to communicate with the pid's (required: Client) JDWP interface.
 */
public class JdwpCommandHandler extends DeviceCommandHandler {

    private static final String HANDSHAKE_STRING = "JDWP-Handshake";

    private final Map<Integer, DDMPacketHandler> ddmPacketHandlers = new HashMap<>();

    private final Map<JdwpCommandId, JdwpPacketHandler> jdwpPacketHandlers = new HashMap<>();

    public JdwpCommandHandler() {
        super("jdwp");
        addDdmPacketHandler(HeloHandler.CHUNK_TYPE, new HeloHandler());
        addDdmPacketHandler(ExitHandler.CHUNK_TYPE, new ExitHandler());

        addJdwpPacketHandler(JdwpVmExitHandler.Companion.getCommandId(), new JdwpVmExitHandler());
    }

    public void addDdmPacketHandler(int chunkType, @NonNull DDMPacketHandler packetHandler) {
        ddmPacketHandlers.put(chunkType, packetHandler);
    }

    public void addJdwpPacketHandler(@NonNull JdwpCommandId commandId, @NonNull JdwpPacketHandler packetHandler) {
        jdwpPacketHandlers.put(commandId, packetHandler);
    }

    @Override
    public void invoke(
            @NonNull FakeAdbServer server,
            @NonNull Socket socket,
            @NonNull DeviceState device,
            @NonNull String args) {
        OutputStream oStream;
        InputStream iStream;
        try {
            oStream = socket.getOutputStream();
            iStream = socket.getInputStream();
        } catch (IOException ignored) {
            return;
        }

        int pid;
        try {
            pid = Integer.parseInt(args);
        } catch (NumberFormatException ignored) {
            writeFailResponse(oStream, "Invalid pid specified: " + args);
            return;
        }

        ClientState client = device.getClient(pid);
        if (client == null) {
            writeFailResponse(oStream, "No client exists for pid: " + pid);
            return;
        }

        // Make sure there is only one JDWP session for this process
        while (!client.startJdwpSession(socket)) {
            // There is one active JDWP session.
            // On API < 28, we return EOF right away.
            // On API >= 28, we wait until the previous session is released
            if (device.getApiLevel() < 28) {
                writeFailResponse(oStream, "JDWP Session already opened for pid: " + pid);
                return;
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        try {
            writeOkay(oStream);
        } catch (IOException ignored) {
            return;
        }

        byte[] handshake = new byte[14];
        try {
            int readCount = iStream.read(handshake);
            if (handshake.length != readCount) {
                writeFailResponse(oStream, "Could not read full handshake.");
                return;
            }
        } catch (IOException ignored) {
            writeFailResponse(oStream, "Could not read handshake.");
            return;
        }

        if (!HANDSHAKE_STRING.equals(new String(handshake, US_ASCII))) {
            return;
        }

        try {
            writeString(oStream, HANDSHAKE_STRING);
        } catch (IOException ignored) {
            return;
        }

        // default - ignore the packet and keep listening
        DDMPacketHandler defaultDdmHandler = (unused, unused3, unused2, unused4) -> true;
        JdwpPacketHandler defaultJdwpHandler = (unused, unused3, unused2, unused4) -> true;

        boolean running = true;

        while (running) {
            try {
                JdwpPacket packet = readFrom(iStream);
                if (DdmPacket.isDdmPacket(packet)) {
                    DdmPacket ddmPacket = DdmPacket.fromJdwpPacket(packet);
                    running =
                            ddmPacketHandlers
                                    .getOrDefault(ddmPacket.getChunkType(), defaultDdmHandler)
                                    .handlePacket(device, client, ddmPacket, oStream);
                } else {
                    JdwpCommandId commandId = new JdwpCommandId(packet.getCmdSet(), packet.getCmd());
                    running =
                            jdwpPacketHandlers
                                    .getOrDefault(commandId, defaultJdwpHandler)
                                    .handlePacket(device, client, packet, oStream);
                }
            } catch (IOException e) {
                writeFailResponse(oStream, "Could not read packet.");
                return;
            }
        }
    }
}
