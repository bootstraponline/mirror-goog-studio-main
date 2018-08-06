package com.android.tools.fakeandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Pattern;
import org.junit.Assert;

public class FakeAndroidDriver extends ProcessRunner {

    private static final String APP_LISTENING = "Test Framework Server Listening: ";
    private static final String ART_PATH = ProcessRunner.getProcessPath("art.location");
    private int myCommunicationPort;
    private String myAddress;

    public FakeAndroidDriver(String address) {
        super(
                "bash",
                ART_PATH,
                "--64",
                "--verbose",
                "-Djava.library.path="
                        + ProcessRunner.getProcessPath("agent.location")
                        + ":"
                        + ProcessRunner.getProcessPath("perfa.dir.location")
                        + ":"
                        + ProcessRunner.getProcessPath("art.lib64.location")
                        + ":"
                        + getNativeLibLocation(),
                "-cp",
                ProcessRunner.getProcessPath("perfa.dex.location"),
                "-Xbootclasspath:"
                        + ProcessRunner.getProcessPath("android-mock.dex.location")
                        + ":"
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "core-libart-hostdex.jar")
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "core-oj-hostdex.jar")
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "bouncycastle-hostdex.jar")
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "conscrypt-hostdex.jar")
                        + String.format(
                                "%s%s",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "okhttp-hostdex.jar"),
                "-Xcompiler-option",
                "--debuggable",
                "-Ximage:"
                        + ProcessRunner.getProcessPath("art.boot.location")
                        + "core-core-libart-hostdex.art",
                "com.android.tools.profiler.FakeAndroid");
        myAddress = address;
    }

    @Override
    public void start() throws IOException {
        super.start();
        Pattern pattern = Pattern.compile("(.*)(" + APP_LISTENING + ")(?<result>.*)");
        String port = waitForInput(pattern, ProcessRunner.NO_TIMEOUT);
        assertTrue(port != null && !port.isEmpty());
        myCommunicationPort = Integer.parseInt(port);
    }

    public int getCommunicationPort() {
        return myCommunicationPort;
    }

    public void loadDex(String dexPath) {
        sendRequest("load-dex", dexPath);
    }

    public void launchActivity(String activityClass) {
        sendRequest("launch-activity", activityClass);

        //Block until we verify the activity has been created.
        assertTrue(waitForInput("ActivityThread Created"));
    }

    public void triggerMethod(String activityClass, String methodName) {
        sendRequest("trigger-method", String.format("%s,%s", activityClass, methodName));
    }

    public void setProperty(String propertyKey, String propertyValue) {
        sendRequest("set-property", String.format("%s,%s", propertyKey, propertyValue));
        waitForInput(String.format("%s=%s", propertyKey, propertyValue));
    }

    private static String getNativeLibLocation() {
        String libLocation = ProcessRunner.getProcessPath("native.lib.location");
        return new java.io.File(libLocation).getParent();
    }

    private void sendRequest(String request, String value) {
        try {
            URL url =
                new URL(
                    String.format(
                        "http://%s:%s?%s=%s&",
                        myAddress,
                        myCommunicationPort,
                        request,
                        value));
            URLConnection conn = url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            assertEquals("SUCCESS", in.readLine());
            in.close();
        } catch (IOException ex) {
            Assert.fail(String.format("Failed to send request (%s): %s", request, ex));
        }
    }
}
