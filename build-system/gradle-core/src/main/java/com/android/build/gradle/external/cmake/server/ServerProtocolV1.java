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

package com.android.build.gradle.external.cmake.server;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.external.cmake.server.receiver.InteractiveMessage;
import com.android.build.gradle.external.cmake.server.receiver.InteractiveProgress;
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.FormatFlagsConversionMismatchException;
import java.util.List;

/**
 * Implementation of version 1 of Cmake server for Cmake versions 3.7.1. Cmake server or a long
 * running mode which allows a client to configure and request buildsystem information generated by
 * Cmake. More info: https://cmake.org/cmake/help/v3.7/manual/cmake-server.7.html
 */
public class ServerProtocolV1 implements Server {
    // Messages sent to and from the Cmake server are wrapped in the header and footer strings.
    public static final String CMAKE_SERVER_HEADER_MSG = "[== \"CMake Server\" ==[";
    public static final String CMAKE_SERVER_FOOTER_MSG = "]== \"CMake Server\" ==]";

    // When configuring a given project, Cmake server reports progress and these could contain
    // compiler information. The compiler info is contained within these prefix/suffix messages.
    // Note: These progress messages will be used as a fallback to determine the compiler info only
    // when compile_commands.json file is not present.
    public static final String CMAKE_SERVER_C_COMPILER_PREFIX = "Check for working C compiler: ";
    public static final String CMAKE_SERVER_CXX_COMPILER_PREFIX =
            "Check for working CXX compiler: ";
    public static final String CMAKE_SERVER_C_COMPILER_SUFFIX = " -- works";

    // Reader and writers to communicate with Cmake server.
    private BufferedReader input;
    private BufferedWriter output;
    // Cmake's install path.
    private final File cmakeInstallPath;
    // Messages, signals etc received from Cmake server.
    private final ServerReceiver serverReceiver;
    // Cached hello result, used to get Cmake server versions.
    private HelloResult helloResult = null;
    // Indicates if we are connected to the Cmake server.
    private boolean connected = false;
    // Indicates if we have configured the given project.
    private boolean configured = false;
    // Indicates if we have computed the given project.
    private boolean computed = false;

    // Interactive messages received when configuring the project.
    private List<InteractiveMessage> configureMessages;
    // Process builder used primarily for testing.
    Process process = null;

    ServerProtocolV1(@NonNull File cmakeInstallPath, @NonNull ServerReceiver serverReceiver) {
        this.cmakeInstallPath = cmakeInstallPath;
        this.serverReceiver = serverReceiver;
    }

    /**
     * This constructor is used only for testing purpose, to pass mock process, buffered
     * input/output etc.
     */
    @VisibleForTesting
    ServerProtocolV1(
            @NonNull File cmakeInstallPath,
            @NonNull ServerReceiver serverReceiver,
            Process process,
            BufferedReader input,
            BufferedWriter output) {
        this.cmakeInstallPath = cmakeInstallPath;
        this.serverReceiver = serverReceiver;
        this.process = process;
        this.input = input;
        this.output = output;
    }

    @Override
    public void finalize() {
        try {
            disconnect();
        } catch (IOException e) {
            diagnostic("Error when disconnecting from Cmake server: %s", e.toString());
        }
    }

    @Override
    public boolean connect() throws IOException {
        init();
        helloResult = decodeResponse(HelloResult.class);
        connected = ServerUtils.isHelloResultValid(helloResult);
        return connected;
    }

    @Override
    public void disconnect() throws IOException {
        if (input != null) {
            input.close();
            input = null;
        }
        if (output != null) {
            output.close();
            output = null;
        }

        if (process != null) {
            process.destroy();
            process = null;
        }

        connected = false;
        configured = false;
        computed = false;
        configureMessages = null;
        helloResult = null;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Nullable
    @Override
    public List<ProtocolVersion> getSupportedVersion() {
        if (helloResult == null || helloResult.supportedProtocolVersions == null) {
            return null;
        }
        List<ProtocolVersion> result = new ArrayList<>();
        for (ProtocolVersion protocolVersion : helloResult.supportedProtocolVersions) {
            // We support 1.0+ (i.e., we support major version 1 and all minor versions).
            if (protocolVersion.major == 1) {
                result.add(protocolVersion);
                break;
            }
        }
        return result;
    }

    @NonNull
    @Override
    public HandshakeResult handshake(@NonNull HandshakeRequest handshakeRequest)
            throws IOException {
        throwIfNotConnected("handshake");
        writeMessage(new GsonBuilder().setPrettyPrinting().create().toJson(handshakeRequest));
        return decodeResponse(HandshakeResult.class);
    }

    @NonNull
    @Override
    public ConfigureCommandResult configure(@NonNull String... cacheArguments) throws IOException {
        throwIfNotConnected("configure");

        ConfigureRequest configureRequest = new ConfigureRequest();

        // Insert a blank element to work around a bug in Cmake 3.7.1 where the first element is
        // ignored.
        configureRequest.cacheArguments = new String[cacheArguments.length + 1];
        configureRequest.cacheArguments[0] = "";
        System.arraycopy(
                cacheArguments, 0, configureRequest.cacheArguments, 1, cacheArguments.length);

        writeMessage(new GsonBuilder().setPrettyPrinting().create().toJson(configureRequest));
        configureMessages = new ArrayList<>();
        ConfigureResult configureResult = decodeResponse(ConfigureResult.class, configureMessages);
        configured = ServerUtils.isConfigureResultValid(configureResult);

        return new ConfigureCommandResult(
                configureResult,
                !configureMessages.isEmpty()
                        ? getInteractiveMessagesAsString(configureMessages)
                        : "");
    }

    @NonNull
    @Override
    public ComputeResult compute() throws IOException {
        throwIfNotConnected("compute");
        throwIfNotConfigured("compute");

        writeMessage("{\"type\":\"compute\"}");
        ComputeResult computeResult = decodeResponse(ComputeResult.class);
        computed = ServerUtils.isComputedResultValid(computeResult);
        return computeResult;
    }

    @NonNull
    @Override
    public CodeModel codemodel() throws IOException {
        throwIfNotConnected("codemodel");
        throwIfNotComputed("codemodel");

        writeMessage("{\"type\":\"codemodel\"}");
        return decodeResponse(CodeModel.class);
    }

    @NonNull
    @Override
    public CacheResult cache() throws IOException {
        throwIfNotConnected("cache");

        CacheRequest request = new CacheRequest();
        writeMessage(new GsonBuilder().setPrettyPrinting().create().toJson(request));
        return decodeResponse(CacheResult.class);
    }

    @NonNull
    @Override
    public GlobalSettings globalSettings() throws IOException {
        throwIfNotConnected("globalSettings");

        writeMessage("{\"type\":\"globalSettings\"}");
        return decodeResponse(GlobalSettings.class);
    }

    @NonNull
    @Override
    public CmakeInputsResult cmakeInputs() throws IOException {
        throwIfNotConnected("cmakeInputs");
        throwIfNotConfigured("cmakeInputs");

        writeMessage("{\"type\":\"cmakeInputs\"}");
        return decodeResponse(CmakeInputsResult.class);
    }

    @NonNull
    @Override
    public String getCCompilerExecutable() {
        final String prefixMessage = "Check for working C compiler: ";
        final String suffixMessage = " -- works";

        return hackyGetLangExecutable(prefixMessage, suffixMessage);
    }

    @NonNull
    @Override
    public String getCppCompilerExecutable() {
        final String prefixMessage = "Check for working CXX compiler: ";
        final String suffixMessage = " -- works";

        return hackyGetLangExecutable(prefixMessage, suffixMessage);
    }

    @NonNull
    @Override
    public String getCmakePath() {
        return cmakeInstallPath.getAbsolutePath();
    }

    @NonNull
    public HelloResult getHelloResult() {
        return helloResult;
    }

    // Helper functions

    /** Helper functions to throw RuntimeException if not connected, configured or computed. */
    private void throwIfNotConnected(@NonNull String operation) {
        if (!connected) {
            throwError("Need to connect to CMake server before requesting for", operation);
        }
    }

    private void throwIfNotConfigured(@NonNull String operation) {
        if (!configured) {
            throwError("Need to configure before requesting for", operation);
        }
    }

    private void throwIfNotComputed(@NonNull String operation) {
        if (!computed) {
            throwError("Need to compute before requesting for", operation);
        }
    }

    private static void throwError(@NonNull String message, @NonNull String operation) {
        throw new RuntimeException(message + " " + operation + ".");
    }

    /**
     * Ideally, we should use compile_commands.json generated by Cmake to get C and Cxx compiler
     * information. If for whatever reason the file is not present (or not generated), we fall back
     * to check the progress messages generated Cmake server when configuring, to get the desired
     * information.
     *
     * @param prefixMessage - prefix string to search
     * @param suffixMessage - suffix string to search
     * @return C/CXX compiler
     */
    private String hackyGetLangExecutable(
            @NonNull String prefixMessage, @NonNull String suffixMessage) {
        if (configureMessages == null || configureMessages.isEmpty()) {
            return "";
        }

        for (InteractiveMessage message : configureMessages) {
            if (message.message == null
                    || !message.message.startsWith(prefixMessage)
                    || !message.message.endsWith(suffixMessage)) {
                continue;
            }
            return message.message.substring(
                    prefixMessage.length(), message.message.length() - suffixMessage.length());
        }

        return "";
    }

    /**
     * Initializes the server.
     *
     * @throws IOException I/O failure
     */
    private void init() throws IOException {
        if (process == null) {
            ProcessBuilder processBuilder = getCmakeServerProcessBuilder();
            processBuilder.environment().putAll(new ProcessBuilder().environment());
            process = processBuilder.start();
        }

        if (input == null) {
            input = new BufferedReader(new InputStreamReader(process.getInputStream()));
        }
        if (output == null) {
            output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        }
    }

    /**
     * Prints diagnostic messages.
     *
     * @param format - diagnostic message format
     * @param args - diagnostic message arguments
     */
    private void diagnostic(String format, Object... args) {
        if (serverReceiver.getDiagnosticReceiver() != null) {
            try {
                serverReceiver.getDiagnosticReceiver().receive(String.format(format, args));
            } catch (FormatFlagsConversionMismatchException e) {
                // There was a formatting problem, just output the format string
                serverReceiver.getDiagnosticReceiver().receive(format);
            }
        }
    }

    /**
     * Prints diagnostic messages without formatting.
     *
     * @param message - diagnostic message format
     */
    private void diagnostic(String message) {
        if (serverReceiver.getDiagnosticReceiver() != null) {
            serverReceiver.getDiagnosticReceiver().receive(message);
        }
    }

    /**
     * Constructs a Cmake server process builder.
     *
     * @return process builder
     */
    private ProcessBuilder getCmakeServerProcessBuilder() {
        final String cmakeBinPath = new File(this.cmakeInstallPath, "cmake").getPath();
        return new ProcessBuilder(cmakeBinPath, "-E", "server", "--experimental", "--debug");
    }

    /**
     * Decodes the responses received during Cmake server interactions for a given request.
     *
     * @param clazz Class object that represents the response class
     * @return decoded response
     * @throws IOException I/O failure
     */
    private <T> T decodeResponse(Class<T> clazz) throws IOException {
        return decodeResponse(clazz, null);
    }

    private <T> T decodeResponse(Class<T> clazz, List<InteractiveMessage> interactiveMessages)
            throws IOException {
        Gson gson = new GsonBuilder().create();
        String message = readMessage();
        String messageType = gson.fromJson(message, TypeOfMessage.class).type;

        final List supportedTypes = Arrays.asList("message", "progress", "signal");
        // Process supported interactive messages.
        // For a given command, the CMake server would respond with message types
        // 0 or more of (message | progress | signal)
        // and finally terminates with a message with message types
        // (hello | reply | error)
        // More info:
        // https://cmake.org/cmake/help/v3.7/manual/cmake-server.7.html#general-message-layout
        while (supportedTypes.contains(messageType)) {
            switch (messageType) {
                case "message":
                    if (serverReceiver.getMessageReceiver() != null) {
                        InteractiveMessage interactiveMessage =
                                gson.fromJson(message, InteractiveMessage.class);
                        serverReceiver.getMessageReceiver().receive(interactiveMessage);
                        // Record the interactive messages only if need be.
                        if (interactiveMessages != null) {
                            serverReceiver.getMessageReceiver().receive(interactiveMessage);
                            interactiveMessages.add(interactiveMessage);
                        }
                    }
                    break;
                case "progress":
                    if (serverReceiver.getProgressReceiver() != null) {
                        serverReceiver
                                .getProgressReceiver()
                                .receive(gson.fromJson(message, InteractiveProgress.class));
                        break;
                    }
                    break;
                case "signal":
                    if (serverReceiver.getProgressReceiver() != null) {
                        serverReceiver
                                .getProgressReceiver()
                                .receive(gson.fromJson(message, InteractiveProgress.class));
                        break;
                    }
            }
            message = readMessage();
            messageType = gson.fromJson(message, TypeOfMessage.class).type;
        }

        // Process the final message.
        switch (messageType) {
            case "hello":
            case "reply":
                if (serverReceiver.getDeserializationMonitor() != null) {
                    serverReceiver.getDeserializationMonitor().receive(message, clazz);
                }
                return gson.fromJson(message, clazz);
            case "error":
                if (serverReceiver.getMessageReceiver() != null) {
                    InteractiveMessage interactiveMessage =
                            gson.fromJson(message, InteractiveMessage.class);
                    serverReceiver.getMessageReceiver().receive(interactiveMessage);
                }
                return gson.fromJson(message, clazz);
            default:
                throw new RuntimeException(
                        "Unsupported message type " + messageType + " received from CMake server.");
        }
    }

    /**
     * Appends the messages from the given list of InteractiveMessage to return it as a single
     * string.
     *
     * @param interactiveMessages - list of interactive messages received from Cmake server
     * @return A single string with all the messages from interactive messages.
     */
    private static String getInteractiveMessagesAsString(
            List<InteractiveMessage> interactiveMessages) {
        StringBuilder result = new StringBuilder();
        for (InteractiveMessage interactiveMessage : interactiveMessages) {
            result.append(interactiveMessage.message).append("\n");
        }

        return result.toString();
    }

    /**
     * Reads a line from Cmake server
     *
     * @return a line read from Cmake servers response
     * @throws IOException I/O failure
     */
    private String readLine() throws IOException {
        final String line = input.readLine();
        diagnostic(line + "\n");
        return line;
    }

    /**
     * Writes a string to Cmake server
     *
     * @throws IOException I/O failure
     */
    private void writeLine(String message) throws IOException {
        diagnostic("%s\n", message);
        output.write(message);
        output.newLine();
    }

    /**
     * Reads until the expected string is found. Skip unexpected (or non-conforming) messages if
     * need be until the expected string is found. Note: The CMake server sometimes writes
     * non-conforming messages (by deviating from the general message layout: https://goo.gl/d4XMmB)
     * to stdout, these are harmless (i.e., they don't break the build) and hence need to be
     * ignored.
     */
    private void readExpected(@NonNull String expectedString) throws IOException {
        String line = readLine();
        while (!line.equals(expectedString)) {
            // Skip a blank line if there is one.
            if (!line.isEmpty() && serverReceiver.getDiagnosticReceiver() != null) {
                serverReceiver.getDiagnosticReceiver().receive(line);
            }
            line = readLine();
        }
    }

    /**
     * Reads a message send by CMake server. CMake Server sends the messages wrapped within a
     * defined header and footer string, this function reads everything inbetween the header and
     * footer and returns it. General message layout we expect from CMake Server:
     *
     * <p>[non-conforming messages from CMake Server]
     *
     * <p>[== "CMake Server" ==[
     *
     * <p>InteractiveMessage
     *
     * <p>[non-conforming messages from CMake Server]
     *
     * <p>]== "CMake Server" ==]
     *
     * @return The string contained within the header and footer
     * @throws IOException I/O failure
     */
    private String readMessage() throws IOException {
        readExpected(CMAKE_SERVER_HEADER_MSG);
        final String line = readLine();
        readExpected(CMAKE_SERVER_FOOTER_MSG);
        return line;
    }

    /**
     * Writes a message wrapped within the header and footer.
     *
     * @param message string to be sent to Cmake server
     * @throws IOException I/O failure
     */
    private void writeMessage(String message) throws IOException {
        writeLine(CMAKE_SERVER_HEADER_MSG);
        writeLine(message);
        writeLine(CMAKE_SERVER_FOOTER_MSG);
        output.flush();
    }
}
