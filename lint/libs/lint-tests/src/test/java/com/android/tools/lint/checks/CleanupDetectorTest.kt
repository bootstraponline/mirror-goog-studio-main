/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.detector.api.Detector

class CleanupDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return CleanupDetector()
    }

    fun testRecycle() {

        val expected =
            """
            src/test/pkg/RecycleTest.java:56: Warning: This TypedArray should be recycled after use with #recycle() [Recycle]
                    final TypedArray a = getContext().obtainStyledAttributes(attrs,
                                                      ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/RecycleTest.java:63: Warning: This TypedArray should be recycled after use with #recycle() [Recycle]
                    final TypedArray a = getContext().obtainStyledAttributes(new int[0]);
                                                      ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/RecycleTest.java:79: Warning: This VelocityTracker should be recycled after use with #recycle() [Recycle]
                    VelocityTracker tracker = VelocityTracker.obtain();
                                                              ~~~~~~
            src/test/pkg/RecycleTest.java:92: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]
                    MotionEvent event1 = MotionEvent.obtain(null);
                                                     ~~~~~~
            src/test/pkg/RecycleTest.java:93: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]
                    MotionEvent event2 = MotionEvent.obtainNoHistory(null);
                                                     ~~~~~~~~~~~~~~~
            src/test/pkg/RecycleTest.java:98: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]
                    MotionEvent event2 = MotionEvent.obtainNoHistory(null); // Not recycled
                                                     ~~~~~~~~~~~~~~~
            src/test/pkg/RecycleTest.java:103: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]
                    MotionEvent event1 = MotionEvent.obtain(null);  // Not recycled
                                                     ~~~~~~
            src/test/pkg/RecycleTest.java:129: Warning: This Parcel should be recycled after use with #recycle() [Recycle]
                    Parcel myparcel = Parcel.obtain();
                                             ~~~~~~
            src/test/pkg/RecycleTest.java:190: Warning: This TypedArray should be recycled after use with #recycle() [Recycle]
                    final TypedArray a = getContext().obtainStyledAttributes(attrs,  // Not recycled
                                                      ~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 9 warnings
            """
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;



                import android.annotation.SuppressLint;
                import android.content.Context;
                import android.content.res.TypedArray;
                import android.os.Message;
                import android.os.Parcel;
                import android.util.AttributeSet;
                import android.view.MotionEvent;
                import android.view.VelocityTracker;
                import android.view.View;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "UnnecessaryLocalVariable", "UnusedAssignment", "MethodMayBeStatic"})
                public class RecycleTest extends View {
                    // ---- Check recycling TypedArrays ----

                    public RecycleTest(Context context, AttributeSet attrs, int defStyle) {
                        super(context, attrs, defStyle);
                    }

                    public void ok1(AttributeSet attrs, int defStyle) {
                        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                                R.styleable.MyView, defStyle, 0);
                        String example = a.getString(R.styleable.MyView_exampleString);
                        a.recycle();
                    }

                    public void ok2(AttributeSet attrs, int defStyle) {
                        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                                R.styleable.MyView, defStyle, 0);
                        String example = a.getString(R.styleable.MyView_exampleString);
                        // If there's complicated logic, don't flag
                        if (something()) {
                            a.recycle();
                        }
                    }

                    public TypedArray ok3(AttributeSet attrs, int defStyle) {
                        // Value passes out of method: don't flag, caller might be recycling
                        return getContext().obtainStyledAttributes(attrs, R.styleable.MyView,
                                defStyle, 0);
                    }

                    private TypedArray myref;

                    public void ok4(AttributeSet attrs, int defStyle) {
                        // Value stored in a field: might be recycled later
                        TypedArray ref = getContext().obtainStyledAttributes(attrs,
                                R.styleable.MyView, defStyle, 0);
                        myref = ref;
                    }

                    public void wrong1(AttributeSet attrs, int defStyle) {
                        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                                R.styleable.MyView, defStyle, 0);
                        String example = a.getString(R.styleable.MyView_exampleString);
                        // a.recycle();
                    }

                    public void wrong2(AttributeSet attrs, int defStyle) {
                        final TypedArray a = getContext().obtainStyledAttributes(new int[0]);
                        // a.recycle();
                    }

                    public void unknown(AttributeSet attrs, int defStyle) {
                        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                                R.styleable.MyView, defStyle, 0);
                        // We don't know what this method is (usually it will be in a different
                        // class)
                        // so don't flag it; it might recycle
                        handle(a);
                    }

                    // ---- Check recycling VelocityTracker ----

                    public void tracker() {
                        VelocityTracker tracker = VelocityTracker.obtain();
                    }

                    // ---- Check recycling Message ----

                    public void message() {
                        Message message1 = getHandler().obtainMessage();
                        Message message2 = Message.obtain();
                    }

                    // ---- Check recycling MotionEvent ----

                    public void motionEvent() {
                        MotionEvent event1 = MotionEvent.obtain(null);
                        MotionEvent event2 = MotionEvent.obtainNoHistory(null);
                    }

                    public void motionEvent2() {
                        MotionEvent event1 = MotionEvent.obtain(null); // OK
                        MotionEvent event2 = MotionEvent.obtainNoHistory(null); // Not recycled
                        event1.recycle();
                    }

                    public void motionEvent3() {
                        MotionEvent event1 = MotionEvent.obtain(null);  // Not recycled
                        MotionEvent event2 = MotionEvent.obtain(event1);
                        event2.recycle();
                    }

                    // ---- Using recycled objects ----

                    public void recycled() {
                        MotionEvent event1 = MotionEvent.obtain(null);  // Not recycled
                        event1.recycle();
                        int contents2 = event1.describeContents(); // BAD, after recycle
                        final TypedArray a = getContext().obtainStyledAttributes(new int[0]);
                        String example = a.getString(R.styleable.MyView_exampleString); // OK
                        a.recycle();
                        example = a.getString(R.styleable.MyView_exampleString); // BAD, after recycle
                    }

                    // ---- Check recycling Parcel ----

                    public void parcelOk() {
                        Parcel myparcel = Parcel.obtain();
                        myparcel.createBinderArray();
                        myparcel.recycle();
                    }

                    public void parcelMissing() {
                        Parcel myparcel = Parcel.obtain();
                        myparcel.createBinderArray();
                    }


                    // ---- Check suppress ----

                    @SuppressLint("Recycle")
                    public void recycledSuppress() {
                        MotionEvent event1 = MotionEvent.obtain(null);  // Not recycled
                        event1.recycle();
                        int contents2 = event1.describeContents(); // BAD, after recycle
                        final TypedArray a = getContext().obtainStyledAttributes(new int[0]);
                        String example = a.getString(R.styleable.MyView_exampleString); // OK
                    }

                    // ---- Stubs ----

                    static void handle(TypedArray a) {
                        // Unknown method
                    }

                    protected boolean something() {
                        return true;
                    }

                    public android.content.res.TypedArray obtainStyledAttributes(
                            AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
                        return null;
                    }

                    private static class R {
                        public static class styleable {
                            public static final int[] MyView = new int[] {};
                            public static final int MyView_exampleString = 2;
                        }
                    }

                    // Local variable tracking

                    @SuppressWarnings("UnnecessaryLocalVariable")
                    public void ok5(AttributeSet attrs, int defStyle) {
                        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                                R.styleable.MyView, defStyle, 0);
                        String example = a.getString(R.styleable.MyView_exampleString);
                        TypedArray b = a;
                        b.recycle();
                    }

                    @SuppressWarnings("UnnecessaryLocalVariable")
                    public void ok6(AttributeSet attrs, int defStyle) {
                        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                                R.styleable.MyView, defStyle, 0);
                        String example = a.getString(R.styleable.MyView_exampleString);
                        TypedArray b;
                        b = a;
                        b.recycle();
                    }

                    @SuppressWarnings({"UnnecessaryLocalVariable", "UnusedAssignment"})
                    public void wrong3(AttributeSet attrs, int defStyle) {
                        final TypedArray a = getContext().obtainStyledAttributes(attrs,  // Not recycled
                                R.styleable.MyView, defStyle, 0);
                        String example = a.getString(R.styleable.MyView_exampleString);
                        TypedArray b;
                        b = a;
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testCommit() {
        val expected =
            """
            src/test/pkg/CommitTest.java:25: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                    getFragmentManager().beginTransaction(); // ERROR 1
                                         ~~~~~~~~~~~~~~~~
            src/test/pkg/CommitTest.java:30: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                    FragmentTransaction transaction2 = getFragmentManager().beginTransaction(); // ERROR 2
                                                                            ~~~~~~~~~~~~~~~~
            src/test/pkg/CommitTest.java:39: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                    getFragmentManager().beginTransaction(); // ERROR 3
                                         ~~~~~~~~~~~~~~~~
            src/test/pkg/CommitTest.java:65: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                    getSupportFragmentManager().beginTransaction(); // ERROR 4
                                                ~~~~~~~~~~~~~~~~
            src/test/pkg/CommitTest.java:123: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                    transaction = getFragmentManager().beginTransaction(); // ERROR 5
                                                       ~~~~~~~~~~~~~~~~
            src/test/pkg/CommitTest.java:132: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                    transaction = getFragmentManager().beginTransaction(); // ERROR 6
                                                       ~~~~~~~~~~~~~~~~
            0 errors, 6 warnings
            """

        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.app.Fragment;
                import android.app.FragmentManager;
                import android.app.FragmentTransaction;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "ConstantConditions", "UnusedAssignment", "MethodMayBeStatic"})
                public class CommitTest extends Activity {
                    public void ok1() {
                        getFragmentManager().beginTransaction().commit(); // OK 1
                    }

                    public void ok2() {
                        FragmentTransaction transaction = getFragmentManager().beginTransaction(); // OK 2
                        transaction.commit();
                    }

                    public void ok3() {
                        FragmentTransaction transaction = getFragmentManager().beginTransaction(); // OK 3
                        transaction.commitAllowingStateLoss();
                    }

                    public void error1() {
                        getFragmentManager().beginTransaction(); // ERROR 1
                    }

                    public void error2() {
                        FragmentTransaction transaction1 = getFragmentManager().beginTransaction(); // OK
                        FragmentTransaction transaction2 = getFragmentManager().beginTransaction(); // ERROR 2
                        transaction1.commit();
                    }

                    public void error3_public() {
                        error3();
                    }

                    private void error3() {
                        getFragmentManager().beginTransaction(); // ERROR 3
                    }

                    public void ok4(FragmentManager manager, String tag) {
                        FragmentTransaction ft = manager.beginTransaction(); // OK 4
                        ft.add(null, tag);
                        ft.commit();
                    }

                    // Support library

                    private android.support.v4.app.FragmentManager getSupportFragmentManager() {
                        return null;
                    }

                    public void ok5() {
                        getSupportFragmentManager().beginTransaction().commit(); // OK 5
                    }

                    public void ok6(android.support.v4.app.FragmentManager manager, String tag) {
                        android.support.v4.app.FragmentTransaction ft = manager.beginTransaction(); // OK 6
                        ft.add(null, tag);
                        ft.commit();
                    }

                    public void error4() {
                        getSupportFragmentManager().beginTransaction(); // ERROR 4
                    }

                    android.support.v4.app.Fragment mFragment1 = null;
                    Fragment mFragment2 = null;

                    public void ok7() {
                        getSupportFragmentManager().beginTransaction().add(android.R.id.content, mFragment1).commit(); // OK 7
                    }

                    public void ok8() {
                        getFragmentManager().beginTransaction().add(android.R.id.content, mFragment2).commit(); // OK 8
                    }

                    public void ok10() {
                        // Test chaining
                        FragmentManager fragmentManager = getFragmentManager();
                        fragmentManager.beginTransaction().addToBackStack("test").attach(mFragment2).detach(mFragment2) // OK 10
                        .disallowAddToBackStack().hide(mFragment2).setBreadCrumbShortTitle("test")
                        .show(mFragment2).setCustomAnimations(0, 0).commit();
                    }

                    public void ok9() {
                        FragmentManager fragmentManager = getFragmentManager();
                        fragmentManager.beginTransaction().commit(); // OK 9
                    }

                    public void ok11() {
                        FragmentTransaction transaction;
                        // Comment in between variable declaration and assignment
                        transaction = getFragmentManager().beginTransaction(); // OK 11
                        transaction.commit();
                    }

                    public void ok12() {
                        FragmentTransaction transaction;
                        transaction = (getFragmentManager().beginTransaction()); // OK 12
                        transaction.commit();
                    }

                    @SuppressWarnings("UnnecessaryLocalVariable")
                    public void ok13() {
                        FragmentTransaction transaction = getFragmentManager().beginTransaction(); // OK 13
                        FragmentTransaction temp;
                        temp = transaction;
                        temp.commitAllowingStateLoss();
                    }

                    @SuppressWarnings("UnnecessaryLocalVariable")
                    public void ok14() {
                        FragmentTransaction transaction = getFragmentManager().beginTransaction(); // OK 14
                        FragmentTransaction temp = transaction;
                        temp.commitAllowingStateLoss();
                    }

                    public void error5(FragmentTransaction unrelated) {
                        FragmentTransaction transaction;
                        // Comment in between variable declaration and assignment
                        transaction = getFragmentManager().beginTransaction(); // ERROR 5
                        transaction = unrelated;
                        transaction.commit();
                    }

                    public void error6(FragmentTransaction unrelated) {
                        FragmentTransaction transaction;
                        FragmentTransaction transaction2;
                        // Comment in between variable declaration and assignment
                        transaction = getFragmentManager().beginTransaction(); // ERROR 6
                        transaction2 = transaction;
                        transaction2 = unrelated;
                        transaction2.commit();
                    }
                }
                """
            ).indented(),
            // Stubs just to be able to do type resolution without needing the full appcompat jar
            fragment,
            dialogFragment,
            fragmentTransaction,
            fragmentManager
        ).run().expect(expected)
    }

    fun testElvis() {
        // Regression test for https://issuetracker.google.com/72581487
        // Elvis operator on cursor initialization -> "Missing recycle() calls" warning
        lint().files(
            kotlin(
                """
                package test.pkg
                import android.app.FragmentManager

                fun ok(f: FragmentManager) {
                    val transaction = f.beginTransaction() ?: return
                    transaction.commitAllowingStateLoss()
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testCommit2() {
        lint().files(
            classpath(),
            manifest().minSdk(4),
            // Stubs just to be able to do type resolution without needing the full appcompat jar
            fragment,
            dialogFragment,
            fragmentTransaction,
            fragmentManager
        ).run().expectClean()
    }

    fun testCommit3() {
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.support.v4.app.DialogFragment;
                import android.support.v4.app.Fragment;
                import android.support.v4.app.FragmentManager;
                import android.support.v4.app.FragmentTransaction;

                @SuppressWarnings({"unused", "MethodMayBeStatic", "ClassNameDiffersFromFileName", "ConstantConditions"})
                public class CommitTest2 {
                    private void test() {
                        FragmentTransaction transaction = getFragmentManager().beginTransaction();
                        MyDialogFragment fragment = new MyDialogFragment();
                        fragment.show(transaction, "MyTag");
                    }

                    private FragmentManager getFragmentManager() {
                        return null;
                    }

                    public static class MyDialogFragment extends DialogFragment {
                        public MyDialogFragment() {
                        }

                        @Override
                        public int show(FragmentTransaction transaction, String tag) {
                            return super.show(transaction, tag);
                        }
                    }
                }
                """
            ).indented(),
            // Stubs just to be able to do type resolution without needing the full appcompat jar
            fragment,
            dialogFragment,
            fragmentTransaction,
            fragmentManager
        ).run().expectClean()
    }

    fun testCommit4() {
        val expected =
            """
            src/test/pkg/CommitTest3.java:35: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                            getCompatFragmentManager().beginTransaction();
                                                       ~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.support.v4.app.DialogFragment;
                import android.support.v4.app.Fragment;
                import android.support.v4.app.FragmentManager;
                import android.support.v4.app.FragmentTransaction;

                @SuppressWarnings({"unused", "MethodMayBeStatic", "ConstantConditions", "ClassNameDiffersFromFileName"})
                public class CommitTest3 {
                    private void testOk() {
                        android.app.FragmentTransaction transaction =
                                getFragmentManager().beginTransaction();
                        transaction.commit();
                        android.app.FragmentTransaction transaction2 =
                                getFragmentManager().beginTransaction();
                        MyDialogFragment fragment = new MyDialogFragment();
                        fragment.show(transaction2, "MyTag");
                    }

                    private void testCompatOk() {
                        android.support.v4.app.FragmentTransaction transaction =
                                getCompatFragmentManager().beginTransaction();
                        transaction.commit();
                        android.support.v4.app.FragmentTransaction transaction2 =
                                getCompatFragmentManager().beginTransaction();
                        MyCompatDialogFragment fragment = new MyCompatDialogFragment();
                        fragment.show(transaction2, "MyTag");
                    }

                    private void testCompatWrong() {
                        android.support.v4.app.FragmentTransaction transaction =
                                getCompatFragmentManager().beginTransaction();
                        transaction.commit();
                        android.support.v4.app.FragmentTransaction transaction2 =
                                getCompatFragmentManager().beginTransaction();
                        MyCompatDialogFragment fragment = new MyCompatDialogFragment();
                        fragment.show(transaction, "MyTag"); // Note: Should have been transaction2!
                    }

                    private android.support.v4.app.FragmentManager getCompatFragmentManager() {
                        return null;
                    }

                    private android.app.FragmentManager getFragmentManager() {
                        return null;
                    }

                    public static class MyDialogFragment extends android.app.DialogFragment {
                        public MyDialogFragment() {
                        }

                        @Override
                        public int show(android.app.FragmentTransaction transaction, String tag) {
                            return super.show(transaction, tag);
                        }
                    }

                    public static class MyCompatDialogFragment extends android.support.v4.app.DialogFragment {
                        public MyCompatDialogFragment() {
                        }

                        @Override
                        public int show(android.support.v4.app.FragmentTransaction transaction, String tag) {
                            return super.show(transaction, tag);
                        }
                    }
                }
                """
            ).indented(),
            // Stubs just to be able to do type resolution without needing the full appcompat jar
            fragment,
            dialogFragment,
            fragmentTransaction,
            fragmentManager
        ).run().expect(expected)
    }

    fun testCommitChainedCalls() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=135204
        val expected =
            """
            src/test/pkg/TransactionTest.java:8: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                    android.app.FragmentTransaction transaction2 = getFragmentManager().beginTransaction();
                                                                                        ~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;
                import android.app.Activity;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class TransactionTest extends Activity {
                    void test() {
                        android.app.FragmentTransaction transaction = getFragmentManager().beginTransaction();
                        android.app.FragmentTransaction transaction2 = getFragmentManager().beginTransaction();
                        transaction.disallowAddToBackStack().commit();
                    }
                }
                """
            ).indented(),
            // Stubs just to be able to do type resolution without needing the full appcompat jar
            fragment,
            dialogFragment,
            fragmentTransaction,
            fragmentManager
        ).run().expect(expected)
    }

    fun testSurfaceTexture() {
        val expected =
            """
            src/test/pkg/SurfaceTextureTest.java:18: Warning: This SurfaceTexture should be freed up after use with #release() [Recycle]
                    SurfaceTexture texture = new SurfaceTexture(1); // Warn: texture not released
                                             ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SurfaceTextureTest.java:25: Warning: This SurfaceTexture should be freed up after use with #release() [Recycle]
                    SurfaceTexture texture = new SurfaceTexture(1); // Warn: texture not released
                                             ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SurfaceTextureTest.java:32: Warning: This Surface should be freed up after use with #release() [Recycle]
                    Surface surface = new Surface(texture); // Warn: surface not released
                                      ~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """

        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;
                import android.graphics.SurfaceTexture;
                import android.view.Surface;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SurfaceTextureTest {
                    public void test1() {
                        SurfaceTexture texture = new SurfaceTexture(1); // OK: released
                        texture.release();
                    }

                    public void test2() {
                        SurfaceTexture texture = new SurfaceTexture(1); // OK: not sure what the method does
                        unknown(texture);
                    }

                    public void test3() {
                        SurfaceTexture texture = new SurfaceTexture(1); // Warn: texture not released
                    }

                    private void unknown(SurfaceTexture texture) {
                    }

                    public void test4() {
                        SurfaceTexture texture = new SurfaceTexture(1); // Warn: texture not released
                        Surface surface = new Surface(texture);
                        surface.release();
                    }

                    public void test5() {
                        SurfaceTexture texture = new SurfaceTexture(1);
                        Surface surface = new Surface(texture); // Warn: surface not released
                        texture.release();
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testSurfaceTextureSubclass() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import android.graphics.SurfaceTexture

                class SafeSurfaceTexture(texName: Int) : SurfaceTexture(texName)
            """
            ).indented(),
            java(
                """
                package test.pkg;
                import android.graphics.SurfaceTexture;
                import android.view.Surface;

                @SuppressWarnings({"ClassNameDiffersFromFileName"})
                public class SafeSurfaceTexture2 extends SurfaceTexture {
                    public SafeSurfaceTexture2(int texName) {
                        super(texName);
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testContentProviderClient() {

        val expected =
            """
            src/test/pkg/ContentProviderClientTest.java:10: Warning: This ContentProviderClient should be freed up after use with #release() [Recycle]
                    ContentProviderClient client = resolver.acquireContentProviderClient("test"); // Warn
                                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/ContentProviderClientTest.java:11: Warning: This ContentProviderClient should be freed up after use with #release() [Recycle]
                    ContentProviderClient client2 = resolver.acquireUnstableContentProviderClient("test"); // Warn
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.content.ContentProviderClient;
                import android.content.ContentResolver;
                import android.net.Uri;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic", "UnnecessaryLocalVariable"})
                public class ContentProviderClientTest {
                    public void error1(ContentResolver resolver) {
                        ContentProviderClient client = resolver.acquireContentProviderClient("test"); // Warn
                        ContentProviderClient client2 = resolver.acquireUnstableContentProviderClient("test"); // Warn
                    }

                    public void ok1(ContentResolver resolver) {
                        ContentProviderClient client = resolver.acquireContentProviderClient("test"); // OK
                        client.release();
                        ContentProviderClient client2 = resolver.acquireUnstableContentProviderClient("test"); // OK
                        client2.release();
                    }

                    public void ok2(ContentResolver resolver) {
                        ContentProviderClient client = resolver.acquireContentProviderClient("test"); // OK
                        client.close();
                        ContentProviderClient client2 = resolver.acquireUnstableContentProviderClient("test"); // OK
                        client2.close();
                    }

                    public void ok3(ContentResolver resolver) {
                        ContentProviderClient client = resolver.acquireContentProviderClient("test"); // OK
                        unknown(client);
                        ContentProviderClient client2 = resolver.acquireUnstableContentProviderClient("test"); // OK
                        unknown(client2);
                    }

                    public void ok4(ContentResolver resolver, Uri uri) {
                        try (ContentProviderClient client = resolver.acquireContentProviderClient("test")) { // OK
                            client.refresh(uri, null, null);
                        }
                        try (ContentProviderClient client2 = resolver.acquireUnstableContentProviderClient("test")) { // OK
                            client2.refresh(uri, null, null);
                        }
                    }

                    public ContentProviderClient ok5(ContentResolver resolver) {
                        ContentProviderClient client = resolver.acquireContentProviderClient("test"); // OK
                        return client;
                    }

                    public ContentProviderClient ok6(ContentResolver resolver) {
                        ContentProviderClient client = resolver.acquireUnstableContentProviderClient("test"); // OK
                        return client;
                    }

                    private void unknown(ContentProviderClient client) {
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.content.ContentResolver
                import android.net.Uri

                fun ok1(resolver: ContentResolver, uri: Uri) {
                    resolver.acquireContentProviderClient("test")?.use { client ->  // OK
                        client?.refresh(uri, null, null)
                    }
                    resolver.acquireUnstableContentProviderClient("test")?.use { client ->  // OK
                        client?.refresh(uri, null, null)
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testDatabaseCursor() {

        val expected =
            """
            src/test/pkg/CursorTest.java:14: Warning: This Cursor should be freed up after use with #close() [Recycle]
                    Cursor cursor = db.query("TABLE_TRIPS",
                                       ~~~~~
            src/test/pkg/CursorTest.java:23: Warning: This Cursor should be freed up after use with #close() [Recycle]
                    Cursor cursor = db.query("TABLE_TRIPS",
                                       ~~~~~
            src/test/pkg/CursorTest.java:74: Warning: This Cursor should be freed up after use with #close() [Recycle]
                    Cursor query = provider.query(uri, null, null, null, null);
                                            ~~~~~
            src/test/pkg/CursorTest.java:75: Warning: This Cursor should be freed up after use with #close() [Recycle]
                    Cursor query2 = resolver.query(uri, null, null, null, null);
                                             ~~~~~
            src/test/pkg/CursorTest.java:76: Warning: This Cursor should be freed up after use with #close() [Recycle]
                    Cursor query3 = client.query(uri, null, null, null, null);
                                           ~~~~~
            0 errors, 5 warnings
            """
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.content.ContentProvider;
                import android.content.ContentProviderClient;
                import android.content.ContentResolver;
                import android.database.Cursor;
                import android.database.sqlite.SQLiteDatabase;
                import android.net.Uri;
                import android.os.RemoteException;

                @SuppressWarnings({"UnusedDeclaration", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CursorTest {
                    public void error1(SQLiteDatabase db, long route_id) {
                        Cursor cursor = db.query("TABLE_TRIPS",
                                new String[]{"KEY_TRIP_ID"},
                                "ROUTE_ID=?",
                                new String[]{Long.toString(route_id)},
                                null, null, null);
                    }

                    public int error2(SQLiteDatabase db, long route_id, String table, String whereClause, String id) {
                        int total_deletions = 0;
                        Cursor cursor = db.query("TABLE_TRIPS",
                                new String[]{"KEY_TRIP_ID"},
                                "ROUTE_ID=?",
                                new String[]{Long.toString(route_id)},
                                null, null, null);

                        while (cursor.moveToNext()) {
                            total_deletions += db.delete(table, whereClause + "=?",
                                    new String[]{Long.toString(cursor.getLong(0))});
                        }

                        // Not closed!
                        //cursor.close();

                        total_deletions += db.delete(table, id + "=?", new String[]{Long.toString(route_id)});

                        return total_deletions;
                    }

                    public int ok(SQLiteDatabase db, long route_id, String table, String whereClause, String id) {
                        int total_deletions = 0;
                        Cursor cursor = db.query("TABLE_TRIPS",
                                new String[]{
                                        "KEY_TRIP_ID"},
                                "ROUTE_ID=?",
                                new String[]{Long.toString(route_id)},
                                null, null, null);

                        while (cursor.moveToNext()) {
                            total_deletions += db.delete(table, whereClause + "=?",
                                    new String[]{Long.toString(cursor.getLong(0))});
                        }
                        cursor.close();

                        return total_deletions;
                    }

                    public Cursor getCursor(SQLiteDatabase db) {
                        @SuppressWarnings("UnnecessaryLocalVariable")
                        Cursor cursor = db.query("TABLE_TRIPS",
                                new String[]{
                                        "KEY_TRIP_ID"},
                                "ROUTE_ID=?",
                                new String[]{Long.toString(5)},
                                null, null, null);

                        return cursor;
                    }

                    void testProviderQueries(Uri uri, ContentProvider provider, ContentResolver resolver,
                                             ContentProviderClient client) throws RemoteException {
                        Cursor query = provider.query(uri, null, null, null, null);
                        Cursor query2 = resolver.query(uri, null, null, null, null);
                        Cursor query3 = client.query(uri, null, null, null, null);
                    }

                    void testProviderQueriesOk(Uri uri, ContentProvider provider, ContentResolver resolver,
                                               ContentProviderClient client) throws RemoteException {
                        Cursor query = provider.query(uri, null, null, null, null);
                        Cursor query2 = resolver.query(uri, null, null, null, null);
                        Cursor query3 = client.query(uri, null, null, null, null);
                        query.close();
                        query2.close();
                        query3.close();
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testDatabaseCleanupKotlinAssignments() {
        // Regression test for
        // https://issuetracker.google.com/141889131
        // "false positive warning about cursor that doesn't get closed"
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.app.Activity
                import android.net.Uri
                import android.os.Bundle

                class Test : Activity() {
                    fun testIf(uri: Uri, projection: Array<String>, bundle: Bundle) {
                        val query =
                        if (true)
                            if (false) {
                                contentResolver.query(uri, projection, bundle, null)
                            } else {
                                contentResolver.query(uri, projection, bundle, null)
                            }
                        else
                            if (true) {
                                val x = 5
                                contentResolver.query(uri, projection, bundle, null)
                            } else
                                contentResolver.query(uri, projection, bundle, null)
                        query?.close()
                    }

                    fun testWhen(uri: Uri, projection: Array<String>, bundle: Bundle) {
                        val query =
                            when {
                                true -> when {
                                    false -> contentResolver.query(uri, projection, bundle, null)
                                    else -> contentResolver.query(uri, projection, bundle, null)
                                }
                                true -> {
                                    val x = 5
                                    contentResolver.query(uri, projection, bundle, null)
                                }
                                else -> {
                                    contentResolver.query(uri, projection, bundle, null)
                                }
                            }
                        query?.close()
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testDatabaseCursorReassignment() {
        lint().files(
            java(
                "src/test/pkg/CursorTest.java",
                """
                package test.pkg;

                import android.app.Activity;
                import android.database.Cursor;
                import android.database.sqlite.SQLiteException;
                import android.net.Uri;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class CursorTest extends Activity {
                    public void testSimple() {
                        Cursor cursor;
                        try {
                            cursor = getContentResolver().query(Uri.parse("blahblah"),
                                    new String[]{"_id", "display_name"}, null, null, null);
                        } catch (SQLiteException e) {
                            // Fallback
                            cursor = getContentResolver().query(Uri.parse("blahblah"),
                                    new String[]{"_id2", "display_name"}, null, null, null);
                        }
                        assert cursor != null;
                        cursor.close();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    // Shared preference tests

    fun test() {
        val expected =
            """
            src/test/pkg/SharedPrefsTest.java:16: Warning: Consider using apply() instead; commit writes its data to persistent storage immediately, whereas apply will handle it in the background [ApplySharedPref]
                    editor.commit();
                           ~~~~~~~~
            src/test/pkg/SharedPrefsTest.java:54: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    SharedPreferences.Editor editor = preferences.edit();
                                                      ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsTest.java:62: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    SharedPreferences.Editor editor = preferences.edit();
                                                      ~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;
                import android.app.Activity;
                import android.content.Context;
                import android.os.Bundle;
                import android.widget.Toast;
                import android.content.SharedPreferences; import android.content.SharedPreferences.Editor;
                import android.preference.PreferenceManager;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "AccessStaticViaInstance", "MethodMayBeStatic"}) public class SharedPrefsTest extends Activity {
                    // OK 1
                    public void onCreate1(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("foo", "bar");
                        editor.putInt("bar", 42);
                        editor.commit();
                    }

                    // OK 2
                    public void onCreate2(Bundle savedInstanceState, boolean apply) {
                        super.onCreate(savedInstanceState);
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("foo", "bar");
                        editor.putInt("bar", 42);
                        if (apply) {
                            editor.apply();
                        }
                    }

                    // OK 3
                    public boolean test1(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("foo", "bar");
                        editor.putInt("bar", 42);
                        editor.apply(); return true;
                    }

                    // Not a bug
                    public void test(Foo foo) {
                        Bar bar1 = foo.edit();
                        Bar bar2 = Foo.edit();
                        Bar bar3 = edit();


                    }

                    // Bug
                    public void bug1(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("foo", "bar");
                        editor.putInt("bar", 42);
                    }

                    // Constructor test
                    public SharedPrefsTest(Context context) {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("foo", "bar");
                    }

                    private Bar edit() {
                        return null;
                    }

                    private static class Foo {
                        static Bar edit() { return null; }
                    }

                    private static class Bar {

                    }
                 }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testSharedPrefsApplyLocation() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.SharedPreferences

                class AuthenticatedWebViewActivity {
                    lateinit var webViewUrlSharedPrefs: SharedPreferences
                    fun test() {
                        webViewUrlSharedPrefs.edit()
                            .putString("key", "value")
                            .commit()
                    }
                }
                """
            )
        ).run().expect(
            """
            src/test/pkg/AuthenticatedWebViewActivity.kt:11: Warning: Consider using apply() instead; commit writes its data to persistent storage immediately, whereas apply will handle it in the background [ApplySharedPref]
                                        .commit()
                                         ~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun test2() {
        // Regression test 1 for http://code.google.com/p/android/issues/detail?id=34322

        val expected =
            """
            src/test/pkg/SharedPrefsTest2.java:13: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    SharedPreferences.Editor editor = preferences.edit();
                                                      ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsTest2.java:17: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    Editor editor = preferences.edit();
                                    ~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import android.app.Activity;
                import android.content.SharedPreferences;
                import android.content.SharedPreferences.Editor;
                import android.os.Bundle;
                import android.preference.PreferenceManager;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SharedPrefsTest2 extends Activity {
                    public void test1(SharedPreferences preferences) {
                        SharedPreferences.Editor editor = preferences.edit();
                    }

                    public void test2(SharedPreferences preferences) {
                        Editor editor = preferences.edit();
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun test3() {
        // Regression test 2 for http://code.google.com/p/android/issues/detail?id=34322
        val expected =
            """
            src/test/pkg/SharedPrefsTest3.java:13: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    Editor editor = preferences.edit();
                                    ~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import android.app.Activity;
                import android.content.SharedPreferences;
                import android.content.SharedPreferences.*;
                import android.os.Bundle;
                import android.preference.PreferenceManager;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SharedPrefsTest3 extends Activity {
                    public void test(SharedPreferences preferences) {
                        Editor editor = preferences.edit();
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun test4() {
        // Regression test 3 for http://code.google.com/p/android/issues/detail?id=34322

        val expected =
            """
            src/test/pkg/SharedPrefsTest4.java:13: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    Editor editor = preferences.edit();
                                    ~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings"""
        lint().files(
            java(
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import android.app.Activity;
                import android.content.SharedPreferences;
                import android.content.SharedPreferences.Editor;
                import android.os.Bundle;
                import android.preference.PreferenceManager;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SharedPrefsTest4 extends Activity {
                    public void test(SharedPreferences preferences) {
                        Editor editor = preferences.edit();
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun test5() {
        // Check fields too: http://code.google.com/p/android/issues/detail?id=39134
        val expected =
            """
            src/test/pkg/SharedPrefsTest5.java:16: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    mPreferences.edit().putString(PREF_FOO, "bar");
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsTest5.java:17: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    mPreferences.edit().remove(PREF_BAZ).remove(PREF_FOO);
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsTest5.java:26: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    preferences.edit().putString(PREF_FOO, "bar");
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsTest5.java:27: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    preferences.edit().remove(PREF_BAZ).remove(PREF_FOO);
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsTest5.java:32: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    preferences.edit().putString(PREF_FOO, "bar");
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsTest5.java:33: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    preferences.edit().remove(PREF_BAZ).remove(PREF_FOO);
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsTest5.java:38: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    Editor editor = preferences.edit().putString(PREF_FOO, "bar");
                                    ~~~~~~~~~~~~~~~~~~
            0 errors, 7 warnings
            """

        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.content.SharedPreferences;
                import android.content.SharedPreferences.Editor;
                import android.preference.PreferenceManager;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic", "ConstantConditions"})
                class SharedPrefsTest5 {
                    SharedPreferences mPreferences;
                    private static final String PREF_FOO = "foo";
                    private static final String PREF_BAZ = "bar";

                    private void wrong() {
                        // Field reference to preferences
                        mPreferences.edit().putString(PREF_FOO, "bar");
                        mPreferences.edit().remove(PREF_BAZ).remove(PREF_FOO);
                    }

                    private void ok() {
                        mPreferences.edit().putString(PREF_FOO, "bar").commit();
                        mPreferences.edit().remove(PREF_BAZ).remove(PREF_FOO).commit();
                    }

                    private void wrong2(SharedPreferences preferences) {
                        preferences.edit().putString(PREF_FOO, "bar");
                        preferences.edit().remove(PREF_BAZ).remove(PREF_FOO);
                    }

                    private void wrong3(Context context) {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                        preferences.edit().putString(PREF_FOO, "bar");
                        preferences.edit().remove(PREF_BAZ).remove(PREF_FOO);
                    }

                    private void wrong4(Context context) {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                        Editor editor = preferences.edit().putString(PREF_FOO, "bar");
                    }

                    private void ok2(Context context) {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                        preferences.edit().putString(PREF_FOO, "bar").commit();
                    }

                    private final SharedPreferences mPrefs = null;

                    public void ok3() {
                        final SharedPreferences.Editor editor = mPrefs.edit().putBoolean(
                                PREF_FOO, true);
                        editor.putString(PREF_BAZ, "");
                        editor.apply();
                    }
                }
                """
            ).indented()
        ).issues(CleanupDetector.SHARED_PREF).run().expect(expected)
    }

    fun test6() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=68692
        val expected =
            """
            src/test/pkg/SharedPrefsTest7.java:13: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    settings.edit().putString(MY_PREF_KEY, myPrefValue);
                    ~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """

        lint().files(
            java(
                """
                package test.pkg;

                import android.content.SharedPreferences;
                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SharedPrefsTest7 {
                    private static final String PREF_NAME = "MyPrefName";
                    private static final String MY_PREF_KEY = "MyKey";
                    SharedPreferences getSharedPreferences(String key, int deflt) {
                        return null;
                    }
                    public void test(String myPrefValue) {
                        SharedPreferences settings = getSharedPreferences(PREF_NAME, 0);
                        settings.edit().putString(MY_PREF_KEY, myPrefValue);
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun test8() {
        val expected =
            """
            src/test/pkg/SharedPrefsTest8.java:11: Warning: Consider using apply() instead; commit writes its data to persistent storage immediately, whereas apply will handle it in the background [ApplySharedPref]
                    editor.commit();
                           ~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(manifest().minSdk(11), sharedPrefsTest8).run().expect(expected)
            .expectFixDiffs(
                """
                Fix for src/test/pkg/SharedPrefsTest8.java line 10: Replace commit() with apply():
                @@ -11 +11
                -         editor.commit();
                +         editor.apply();
                """
            )
    }

    fun testChainedCalls() {
        val expected =
            """
            src/test/pkg/Chained.java:12: Warning: Consider using apply() instead; commit writes its data to persistent storage immediately, whereas apply will handle it in the background [ApplySharedPref]
                            .commit();
                             ~~~~~~~~
            src/test/pkg/Chained.java:24: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                    PreferenceManager
                    ^
            0 errors, 2 warnings
            """
        lint().files(
            java(
                "src/test/pkg/Chained.java",
                """
                package test.pkg;

                import android.content.Context;
                import android.preference.PreferenceManager;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Chained {
                    private static void falsePositive(Context context) {
                        PreferenceManager
                                .getDefaultSharedPreferences(context)
                                .edit()
                                .putString("wat", "wat")
                                .commit();
                    }

                    private static void falsePositive2(Context context) {
                        boolean var = PreferenceManager
                                .getDefaultSharedPreferences(context)
                                .edit()
                                .putString("wat", "wat")
                                .commit();
                    }

                    private static void truePositive(Context context) {
                        PreferenceManager
                                .getDefaultSharedPreferences(context)
                                .edit()
                                .putString("wat", "wat");
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    // sample code with warnings
    fun testCommitDetector() {
        lint().files(
            java(
                "src/test/pkg/CommitTest.java",
                """
                package test.pkg;

                import android.app.Activity;
                import android.app.FragmentManager;
                import android.app.FragmentTransaction;
                import android.content.Context;
                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic", "ConstantConditions"})
                public class CommitTest {
                    private Context mActivity;
                    public void selectTab1() {
                        FragmentTransaction trans = null;
                        if (mActivity instanceof Activity) {
                            trans = ((Activity)mActivity).getFragmentManager().beginTransaction()
                                    .disallowAddToBackStack();
                        }

                        if (trans != null && !trans.isEmpty()) {
                            trans.commit();
                        }
                    }

                    public void select(FragmentManager fragmentManager) {
                        FragmentTransaction trans = fragmentManager.beginTransaction().disallowAddToBackStack();
                        trans.commit();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    // sample code with warnings
    fun testCommitDetectorOnParameters() {
        // Handle transactions assigned to parameters (this used to not work)
        lint().files(
            java(
                "src/test/pkg/CommitTest2.java",
                """
                package test.pkg;

                import android.app.FragmentManager;
                import android.app.FragmentTransaction;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CommitTest2 {
                    private void navigateToFragment(FragmentTransaction transaction,
                                                    FragmentManager supportFragmentManager) {
                        if (transaction == null) {
                            transaction = supportFragmentManager.beginTransaction();
                        }

                        transaction.commit();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    // sample code with warnings
    fun testReturn() {
        // If you return the object to be cleaned up, it doesn'st have to be cleaned up (caller
        // may do that)
        lint().files(
            java(
                "src/test/pkg/SharedPrefsTest.java",
                """
                package test.pkg;

                import android.content.Context;
                import android.content.SharedPreferences;
                import android.preference.PreferenceManager;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName"})
                public abstract class SharedPrefsTest extends Context {
                    private SharedPreferences.Editor getEditor() {
                        return getPreferences().edit();
                    }

                    private boolean editAndCommit() {
                        return getPreferences().edit().commit();
                    }

                    private SharedPreferences getPreferences() {
                        return PreferenceManager.getDefaultSharedPreferences(this);
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    // sample code with warnings
    fun testCommitNow() {
        lint().files(
            java(
                "src/test/pkg/CommitTest.java",
                """
                package test.pkg;

                import android.app.FragmentManager;
                import android.app.FragmentTransaction;
                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CommitTest {
                    public void select(FragmentManager fragmentManager) {
                        FragmentTransaction trans = fragmentManager.beginTransaction().disallowAddToBackStack();
                        trans.commitNow();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAutoCloseable() {
        // Regression test for:
        //   https://code.google.com/p/android/issues/detail?id=214086
        //   https://issuetracker.google.com/239504900
        //
        // Queries assigned to try/catch resource variables are automatically
        // closed.
        lint().files(
            java(
                "src/test/pkg/TryWithResources.java",
                """
                package test.pkg;

                import android.content.ContentResolver;
                import android.content.Context;
                import android.content.res.TypedArray;
                import android.database.Cursor;
                import android.net.Uri;
                import android.os.Build;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class TryWithResources {
                    public void test(ContentResolver resolver, Uri uri, String[] projection) {
                        try (Cursor cursor = resolver.query(uri, projection, null, null, null)) {
                            if (cursor != null) {
                                //noinspection StatementWithEmptyBody
                                while (cursor.moveToNext()) {
                                    // ..
                                }
                            }
                        }
                    }

                    public static int testTypedArray(Context context) {
                        try (TypedArray a = context.obtainStyledAttributes(null, null)) {
                            return a.getColor(0, 0);
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAutoCloseableKotlin() {
        lint().files(
            kotlin(
                "src/test/pkg/AutoCloseableKotlin.kt",
                """
                package test.pkg

                import android.content.Context

                object Test {
                    fun test(context: Context): Int {
                        context.obtainStyledAttributes(0, intArrayOf()).use { a ->
                            return a.getColor(0, 0)
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testApplyOnPutMethod() {
        // Regression test for
        //    https://code.google.com/p/android/issues/detail?id=214196
        //
        // Ensure that if you call commit/apply on a put* call
        // (not the edit field itself, but put passes it through)
        // we correctly consider the editor operation finished.
        lint().files(
            java(
                "src/test/pkg/CommitPrefTest.java",
                """
                package test.pkg;

                import android.content.Context;
                import android.content.SharedPreferences;
                import android.preference.PreferenceManager;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName"})
                public abstract class CommitPrefTest extends Context {
                    public void test() {
                        SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
                        edit.putInt("foo", 1).apply();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    // sample code with warnings
    fun testCommitNowAllowingStateLoss() {
        // Handle transactions assigned to parameters (this used to not work)
        lint().files(
            java(
                "src/test/pkg/CommitTest2.java",
                """
                package test.pkg;

                import android.app.FragmentManager;
                import android.app.FragmentTransaction;

                @SuppressWarnings({"unused", "MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                public class CommitTest2 {
                    private void navigateToFragment(FragmentManager supportFragmentManager) {
                        FragmentTransaction transaction = supportFragmentManager.beginTransaction();
                        transaction.commitNowAllowingStateLoss();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testFields() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=224435
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Service;
                import android.content.SharedPreferences;
                import android.preference.PreferenceManager;
                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public abstract class CommitFromField extends Service {
                    private SharedPreferences prefs;
                    @SuppressWarnings("FieldCanBeLocal")
                    private SharedPreferences.Editor editor;

                    @Override
                    public void onCreate() {
                        prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    }

                    private void engine() {
                        editor = prefs.edit();
                        editor.apply();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testUnrelatedSharedPrefEdit() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=234868
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.SharedPreferences;
                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public abstract class PrefTest {
                    public static void something(SomePref pref) {
                        pref.edit(1, 2, 3);
                    }

                    public interface SomePref extends SharedPreferences {
                        void edit(Object...args);
                    }
                }
                """
            )
        ).issues(CleanupDetector.SHARED_PREF).run().expectClean()
    }

    fun testCommitVariable() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=237776
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.app.Fragment;
                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CommitTest extends Activity {
                    public void test() {
                        final int id = getFragmentManager().beginTransaction()
                                .add(new Fragment(), null)
                                .addToBackStack(null)
                                .commit();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testKotlinCommitViaLambda() {
        // Regression test for 69407565: commit/apply warnings when using with, apply, let etc
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.app.Activity

                fun test(activity: Activity) {
                    with(activity.fragmentManager.beginTransaction()) {
                        addToBackStack(null)
                        commit()
                    }

                    activity.fragmentManager.beginTransaction().run {
                        addToBackStack(null)
                        commit()
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testKotlinEditViaLambda() {
        // Regression test for 70036345: Lint doesn't understand Kotlin standard functions
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.SharedPreferences

                private inline fun SharedPreferences.update(transaction: SharedPreferences.Editor.() -> Unit) =
                        edit().run {
                            transaction()
                            apply()
                        }

                private inline fun SharedPreferences.update2(transaction: SharedPreferences.Editor.() -> Unit) =
                        with(edit()) {
                            transaction()
                            apply()
                        }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAndroidKtxSharedPrefs() {
        // Regression for
        // 74388337: False "SharedPreferences.edit() without a corresponding commit() call"
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.SharedPreferences
                import androidx.core.content.edit

                fun test(sharedPreferences: SharedPreferences, key: String, value: Boolean) {
                    sharedPreferences.edit {
                        putBoolean(key, value)
                    }
                }
                """
            ).indented(),
            kotlin(
                "src/androidx/core/content/SharedPreferences.kt",
                """
                package androidx.core.content

                import android.annotation.SuppressLint
                import android.content.SharedPreferences

                @SuppressLint("ApplySharedPref")
                inline fun SharedPreferences.edit(
                    commit: Boolean = false,
                    action: SharedPreferences.Editor.() -> Unit
                ) {
                    val editor = edit()
                    action(editor)
                    if (commit) {
                        editor.commit()
                    } else {
                        editor.apply()
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testParcelableKotlin() {
        // Regression for https://issuetracker.google.com/79716779
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.os.Parcel
                import android.os.Parcelable

                fun testCleanup(parcelable: Parcelable): ByteArray? {
                    val parcel = Parcel.obtain()
                    parcel.writeParcelable(parcelable, 0)
                    try {
                        return parcel.marshall()
                    } finally {
                        parcel.recycle()
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testKotlinRunStatements() {
        // Regression test for 79905342: recycle() lint warning not detecting call
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.util.AttributeSet

                @Suppress("unused", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
                fun test(context: Context, attrs: AttributeSet, id: Int) {
                    var columnWidth = 0
                    context.obtainStyledAttributes(attrs, intArrayOf(id)).run {
                        columnWidth = getDimensionPixelSize(0, -1)
                        recycle()
                    }
                }

                """
            ).indented()
        ).run().expectClean()
    }

    fun testKotlinAlsoStatements() {
        // Regression test for
        // 139566120: Lint check showing incorrect warning with TypedArray recycle call
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Context

                fun test1(context: Context, attrs: IntArray) {
                    context.obtainStyledAttributes(attrs).also {
                        //some code using it value
                    }.recycle()
                }

                fun test2(context: Context, attrs: IntArray) {
                    context.obtainStyledAttributes(attrs).apply {
                        //some code using it value
                    }.recycle()
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testKtxUseStatement() {
        // Regression test for
        //  140344435 Lint does not realised that TypedArray.recycle() will be done by
        //    KTX function of TypedArray.use
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.util.AttributeSet
                import androidx.core.content.res.use

                fun test(context: Context, attrs: AttributeSet, resource: Int) {
                    var text: String? = null
                    context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.text))
                        .use { text = it.getString(0) }
                }
                """
            ).indented(),
            kotlin(
                """
                package androidx.core.content.res
                import android.content.res.TypedArray

                inline fun <R> TypedArray.use(block: (TypedArray) -> R): R {
                    return block(this).also {
                        recycle()
                    }
                }
                """
            )
        ).run().expectClean()
    }

    fun testUse1() {
        // Regression test from 62377185
        lint().files(
            kotlin(
                """
                package test.pkg
                import android.content.ContentResolver

                class MyTest {
                    fun onCreate(resolver: ContentResolver) {
                        val cursorOpened = resolver.query(null, null, null, null, null) // ERROR
                        val cursorClosed = resolver.query(null, null, null, null, null) // OK
                        cursorClosed.close()
                        val cursorUsed = resolver.query(null, null, null, null, null) // OK
                        cursorUsed.use {  }
                        resolver.query(null, null, null, null, null).use { } // OK

                        val cursorUsed2 = try {
                            resolver.query(null, null, null, null, null) // OK
                        } catch (e: Exception) {
                            null
                        }

                        cursorUsed2?.use { }

                        val cursorUsed3 = try {
                            resolver.query(null, null, null, null, null) // ERROR
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/MyTest.kt:6: Warning: This Cursor should be freed up after use with #close() [Recycle]
                    val cursorOpened = resolver.query(null, null, null, null, null) // ERROR
                                                ~~~~~
            src/test/pkg/MyTest.kt:22: Warning: This Cursor should be freed up after use with #close() [Recycle]
                        resolver.query(null, null, null, null, null) // ERROR
                                 ~~~~~
            0 errors, 2 warnings
        """
        )
    }

    fun testUse2() {
        // Regression test from 79936228
        lint().files(
            kotlin(
                """
                package test.pkg
                import android.content.ContentProviderClient
                import android.database.Cursor
                import android.net.Uri

                internal inline fun <T, U> ContentProviderClient.queryOne(
                    uri: Uri,
                    projection: Array<String>?,
                    selection: String?,
                    args: Array<String>?,
                    wrapper: (Cursor) -> T,
                    mapper: (T) -> U
                ): U? {
                    return query(uri, projection, selection, args, null)?.use { cursor ->
                        val wrapped = wrapper.invoke(cursor)
                        if (cursor.moveToFirst()) {
                            val result = mapper.invoke(wrapped)
                            check(!cursor.moveToNext()) { "Cursor has more than one item" }
                            return result
                        }
                        return null
                    }
                }

                internal inline fun <T, U> ContentProviderClient.queryList(
                    uri: Uri,
                    projection: Array<String>?,
                    selection: String?,
                    args: Array<String>?,
                    wrapper: (Cursor) -> T,
                    mapper: (T) -> U
                ): List<U> {
                    return query(uri, projection, selection, args, null)?.use { cursor ->
                        val wrapped = wrapper.invoke(cursor)
                        return List(cursor.count) { index ->
                            cursor.moveToPosition(index)
                            mapper.invoke(wrapped)
                        }
                    } ?: emptyList()
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testUseHasLambdaParameter() {
        lint().files(
            kotlin(
                """
                package test.pkg;

                import android.content.ContentResolver;
                import android.database.Cursor;
                import android.net.Uri;

                fun test(resolver: ContentResolver, uri: Uri, projection: Array<String>) {
                    resolver.query(uri, projection, null, null, null).use { cursor -> // OK
                        cursor.moveToNext()
                    }

                    resolver.query(uri, projection, null, null, null).use() // ERROR

                    resolver.query(uri, projection, null, null, null).use(1) // ERROR
                }

                // These use() functions don't have a matching signature
                fun Cursor.use() {
                }

                fun Cursor.use(n: Int) {
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:12: Warning: This Cursor should be freed up after use with #close() [Recycle]
                resolver.query(uri, projection, null, null, null).use() // ERROR
                         ~~~~~
            src/test/pkg/test.kt:14: Warning: This Cursor should be freed up after use with #close() [Recycle]
                resolver.query(uri, projection, null, null, null).use(1) // ERROR
                         ~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun test117794883() {
        // Regression test for 117794883
        lint().files(
            kotlin(
                """
                @file:Suppress("UNUSED_VARIABLE")

                import android.app.Activity
                import android.os.Bundle
                import android.view.VelocityTracker

                class MainActivity : Activity() {

                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)

                        VelocityTracker./*This `VelocityTracker` should be recycled after use with `#recycle()`*/obtain/**/()

                        VelocityTracker.obtain().recycle()

                        val v1 = VelocityTracker./*This `VelocityTracker` should be recycled after use with `#recycle()`*/obtain/**/()

                        val v2 = VelocityTracker.obtain()
                        v2.recycle()
                    }
                }
                """
            ).indented()
        ).run().expectInlinedMessages(true)
    }

    fun test117792318() {
        // Regression test for 117792318
        lint().files(
            kotlin(
                """
                @file:Suppress("UNUSED_VARIABLE")

                import android.app.Activity
                import android.app.FragmentTransaction
                import android.app.FragmentManager
                import android.os.Bundle

                class MainActivity : Activity() {

                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)

                        //OK
                        val transaction = fragmentManager.beginTransaction()
                        val transaction2: FragmentTransaction
                        transaction2 = fragmentManager.beginTransaction()
                        transaction.commit()
                        transaction2.commit()

                        //WARNING
                        val transaction3 = fragmentManager./*This transaction should be completed with a `commit()` call*/beginTransaction/**/()

                        //OK
                        fragmentManager.beginTransaction().commit()
                        fragmentManager.beginTransaction().add(null, "A").commit()

                        //OK KT-14470
                        Runnable {
                            val a = fragmentManager.beginTransaction()
                            a.commit()
                        }
                    }

                    // KT-14780: Kotlin Lint: "Missing commit() calls" false positive when the result of `commit()` is assigned or used as receiver
                    fun testResultOfCommit(fm: FragmentManager) {
                        val r1 = fm.beginTransaction().hide(fm.findFragmentByTag("aTag")).commit()
                        val r2 = fm.beginTransaction().hide(fm.findFragmentByTag("aTag")).commit().toString()
                    }
                }
                """
            ).indented()
        ).run().expectInlinedMessages(true)
    }

    fun testAnimation() {
        // 36991569: Lint warning for animation created but not .start()ed
        // (ViewPropertyAnimator itself is no longer flagged because of b/169690812,
        // but continuing to report ValueAnimators, ObjectAnimators, etc.
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.animation.AnimatorSet
                import android.animation.ObjectAnimator
                import android.animation.ValueAnimator
                import android.view.View
                import android.view.ViewPropertyAnimator
                import android.widget.TextView

                fun viewAnimator(view: View) {
                    view.animate().translationX(100.0f) // Also OK, see 169690812
                    view.animate().translationX(100.0f).start(); // OK
                }

                fun viewAnimatorOk(view: View): ViewPropertyAnimator {
                    val animator = view.animate() // OK
                    animator.start()
                    return view.animate().translationY(5f) // OK
                }

                fun animator(textView: TextView) {
                    // Kotlin style
                    ValueAnimator.ofFloat(0f, 100f).apply { // ERROR
                        duration = 1000
                        //start()
                    }
                    // Java style
                    val animation = ValueAnimator.ofFloat(0f, 100f)  // ERROR
                    animation.setDuration(1000)
                    //animation.start()

                    val objectAnimator = ObjectAnimator.ofFloat(textView, "translationX", 100f)  // ERROR
                    objectAnimator.setDuration(1000);
                    //objectAnimator.start();

                    val animatorSet = AnimatorSet()  // ERROR
                    //animatorSet.start();
                }

                fun animatorOk(textView: TextView) {
                    // Kotlin style
                    ValueAnimator.ofFloat(0f, 100f).apply {
                        duration = 1000
                        start()
                    }
                    // Java style
                    val animation = ValueAnimator.ofFloat(0f, 100f)
                    animation.setDuration(1000)
                    animation.start()

                    val objectAnimator = ObjectAnimator.ofFloat(textView, "translationX", 100f); // OK
                    objectAnimator.setDuration(1000);
                    objectAnimator.start();

                    // Note that if you pass something into an AnimatorSet then it's no longer required
                    // to be .started()
                    val bouncer = AnimatorSet() // OK
                    val fadeAnim = ObjectAnimator.ofFloat(textView, "alpha", 1f, 0f).apply { // OK
                        duration = 250
                    }
                    AnimatorSet().apply {
                        play(bouncer).before(fadeAnim)
                        start()
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:23: Warning: This animation should be started with #start() [Recycle]
                ValueAnimator.ofFloat(0f, 100f).apply { // ERROR
                              ~~~~~~~
            src/test/pkg/test.kt:28: Warning: This animation should be started with #start() [Recycle]
                val animation = ValueAnimator.ofFloat(0f, 100f)  // ERROR
                                              ~~~~~~~
            src/test/pkg/test.kt:32: Warning: This animation should be started with #start() [Recycle]
                val objectAnimator = ObjectAnimator.ofFloat(textView, "translationX", 100f)  // ERROR
                                                    ~~~~~~~
            src/test/pkg/test.kt:36: Warning: This animation should be started with #start() [Recycle]
                val animatorSet = AnimatorSet()  // ERROR
                                  ~~~~~~~~~~~
            0 errors, 4 warnings
            """
        )
    }

    fun testAnimatorApply() {
        lint().files(
            kotlin(
                """
                import android.animation.AnimatorSet
                import android.animation.ValueAnimator

                fun main() {
                  AnimatorSet().apply {
                    playTogether(
                      ValueAnimator.ofFloat().apply { }
                    )
                  }.start()
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testNotNullAssertionOperator() {
        // 165534909: Recycle for Cursor closed with `use` in Kotlin
        lint().files(
            kotlin(
                """
                import android.content.ContentResolver

                /** Get the count of existing call logs. */
                fun getCallLogCount(): Int {
                    val contentResolver: ContentResolver = context.getContentResolver()
                    return contentResolver.query(
                        /*uri=*/ Calls.CONTENT_URI,
                        /*projection=*/ null,
                        /*selection=*/ null,
                        /*selectionArgs=*/ null,
                        /*sortOrder=*/ null
                    )!!.use {
                        it.count
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testValueAnimator() {
        // Repro scenario from false positive scenario in
        // frameworks/base/core/java/com/android/internal/app/ChooserActivity.java
        lint().files(
            java(
                """
                package test.pkg;

                import static android.animation.ObjectAnimator.ofFloat;

                import android.animation.ObjectAnimator;
                import android.animation.ValueAnimator;
                import android.view.View;

                @SuppressWarnings("unused")
                public class FadeTestJava {
                    public void setViewVisibility1(View v) {
                        ValueAnimator fadeAnim = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0f);
                        fadeAnim.start();
                    }

                    public void setViewVisibility2(View v) {
                        ValueAnimator fadeAnim = ofFloat(v, "alpha", 1.0f, 0f);
                        fadeAnim.start();
                    }

                    public void setViewVisibility3(View v) {
                        ValueAnimator fadeAnim;
                        fadeAnim = ofFloat(v, "alpha", 1.0f, 0f);
                        fadeAnim.start();
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.animation.ValueAnimator
                import android.animation.ObjectAnimator
                import android.animation.ObjectAnimator.ofFloat
                import android.view.View

                class FadeTestKotlin {
                    fun setViewVisibility(v: View) {
                        val fadeAnim: ValueAnimator = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0f)
                        fadeAnim.start()
                    }

                    fun setViewVisibility2(v: View) {
                        val fadeAnim: ValueAnimator = ofFloat(v, "alpha", 1.0f, 0f)
                        fadeAnim.start()
                    }

                    fun setViewVisibility3(v: View) {
                        val fadeAnim: ValueAnimator
                        fadeAnim = ofFloat(v, "alpha", 1.0f, 0f)
                        fadeAnim.start()
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAssetFileDescriptor() {
        val expected =
            """
            src/test/pkg/AssetFileDescriptorTest.java:15: Warning: This AssetFileDescriptor should be freed up after use with #close() [Recycle]
                    client.openAssetFile(uri, "mode", null); // Warn
                           ~~~~~~~~~~~~~
            src/test/pkg/AssetFileDescriptorTest.java:16: Warning: This AssetFileDescriptor should be freed up after use with #close() [Recycle]
                    client.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // Warn
                           ~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssetFileDescriptorTest.java:17: Warning: This AssetFileDescriptor should be freed up after use with #close() [Recycle]
                    client.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // Warn
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssetFileDescriptorTest.java:18: Warning: This AssetFileDescriptor should be freed up after use with #close() [Recycle]
                    resolver.openAssetFile(uri, "mode", null); // Warn
                             ~~~~~~~~~~~~~
            src/test/pkg/AssetFileDescriptorTest.java:19: Warning: This AssetFileDescriptor should be freed up after use with #close() [Recycle]
                    resolver.openAssetFileDescriptor(uri, "mode", null); // Warn
                             ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssetFileDescriptorTest.java:20: Warning: This AssetFileDescriptor should be freed up after use with #close() [Recycle]
                    resolver.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // Warn
                             ~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssetFileDescriptorTest.java:21: Warning: This AssetFileDescriptor should be freed up after use with #close() [Recycle]
                    resolver.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // Warn
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 7 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.ContentProviderClient;
                import android.content.ContentResolver;
                import android.content.res.AssetFileDescriptor;
                import android.net.Uri;

                class AssetFileDescriptorTest {
                    ContentProviderClient client;
                    ContentResolver resolver;
                    Uri uri;
                    AssetFileDescriptor fileField;

                    void error1() {
                        client.openAssetFile(uri, "mode", null); // Warn
                        client.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // Warn
                        client.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // Warn
                        resolver.openAssetFile(uri, "mode", null); // Warn
                        resolver.openAssetFileDescriptor(uri, "mode", null); // Warn
                        resolver.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // Warn
                        resolver.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // Warn
                    }

                    void ok1() {
                        AssetFileDescriptor file = client.openAssetFile(uri, "mode", null); // OK
                        file.close();
                        AssetFileDescriptor file2 = client.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // OK
                        file2.close();
                        AssetFileDescriptor file3 = client.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // OK
                        file3.close();
                        AssetFileDescriptor file4 = resolver.openAssetFile(uri, "mode", null); // OK
                        file4.close();
                        AssetFileDescriptor file5 = resolver.openAssetFileDescriptor(uri, "mode", null); // OK
                        file5.close();
                        AssetFileDescriptor file6 = resolver.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // OK
                        file6.close();
                        AssetFileDescriptor file7 = resolver.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // OK
                        file7.close();
                    }

                    void ok2() {
                        AssetFileDescriptor file = client.openAssetFile(uri, "mode", null); // OK
                        unknown(file);
                        AssetFileDescriptor file2 = client.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // OK
                        unknown(file2);
                        AssetFileDescriptor file3 = client.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // OK
                        unknown(file3);
                        AssetFileDescriptor file4 = resolver.openAssetFile(uri, "mode", null); // OK
                        unknown(file4);
                        AssetFileDescriptor file5 = resolver.openAssetFileDescriptor(uri, "mode", null); // OK
                        unknown(file5);
                        AssetFileDescriptor file6 = resolver.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // OK
                        unknown(file6);
                        AssetFileDescriptor file7 = resolver.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // OK
                        unknown(file7);
                    }

                    void ok3() {
                        fileField = client.openAssetFile(uri, "mode", null); // OK
                        fileField = client.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // OK
                        fileField = client.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // OK
                        fileField = resolver.openAssetFile(uri, "mode", null); // OK
                        fileField = resolver.openAssetFileDescriptor(uri, "mode", null); // OK
                        fileField = resolver.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // OK
                        fileField = resolver.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // OK
                    }

                    void ok4() {
                        try (AssetFileDescriptor file = client.openAssetFile(uri, "mode", null)) { // OK
                            file.getLength();
                        }
                        try (AssetFileDescriptor file2 = client.openTypedAssetFile(uri, "mimeTypeFilter", null, null)) { // OK
                            file2.getLength();
                        }
                        try (AssetFileDescriptor file3 = client.openTypedAssetFileDescriptor(uri, "mimeType", null, null)) { // OK
                            file3.getLength();
                        }
                        try (AssetFileDescriptor file4 = resolver.openAssetFile(uri, "mode", null)) { // OK
                            file4.getLength();
                        }
                        try (AssetFileDescriptor file5 = resolver.openAssetFileDescriptor(uri, "mode", null)) { // OK
                            file5.getLength();
                        }
                        try (AssetFileDescriptor file6 = resolver.openTypedAssetFile(uri, "mimeTypeFilter", null, null)) { // OK
                            file6.getLength();
                        }
                        try (AssetFileDescriptor file7 = resolver.openTypedAssetFileDescriptor(uri, "mimeType", null, null)) { // OK
                            file7.getLength();
                        }
                    }

                    AssetFileDescriptor ok5() {
                        AssetFileDescriptor file = client.openAssetFile(uri, "mode", null); // OK
                        return file;
                    }

                    AssetFileDescriptor ok6() {
                        AssetFileDescriptor file = client.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // OK
                        return file;
                    }

                    AssetFileDescriptor ok7() {
                        AssetFileDescriptor file = client.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // OK
                        return file;
                    }

                    AssetFileDescriptor ok8() {
                        AssetFileDescriptor file = resolver.openAssetFile(uri, "mode", null); // OK
                        return file;
                    }

                    AssetFileDescriptor ok9() {
                        AssetFileDescriptor file = resolver.openAssetFileDescriptor(uri, "mode", null); // OK
                        return file;
                    }

                    AssetFileDescriptor ok10() {
                        AssetFileDescriptor file = resolver.openTypedAssetFile(uri, "mimeTypeFilter", null, null); // OK
                        return file;
                    }

                    AssetFileDescriptor ok11() {
                        AssetFileDescriptor file = resolver.openTypedAssetFileDescriptor(uri, "mimeType", null, null); // OK
                        return file;
                    }

                    void unknown(AssetFileDescriptor file) {
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testParcelFileDescriptor() {

        val expected =
            """
            src/test/pkg/ParcelFileDescriptorTest.java:15: Warning: This ParcelFileDescriptor should be freed up after use with #close() [Recycle]
                    client.openFile(uri, "mode", null); // Warn
                           ~~~~~~~~
            src/test/pkg/ParcelFileDescriptorTest.java:16: Warning: This ParcelFileDescriptor should be freed up after use with #close() [Recycle]
                    resolver.openFile(uri, "mode", null); // Warn
                             ~~~~~~~~
            src/test/pkg/ParcelFileDescriptorTest.java:17: Warning: This ParcelFileDescriptor should be freed up after use with #close() [Recycle]
                    resolver.openFileDescriptor(uri, "mode", null); // Warn
                             ~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.ContentProviderClient;
                import android.content.ContentResolver;
                import android.net.Uri;
                import android.os.ParcelFileDescriptor;

                class ParcelFileDescriptorTest {
                    ContentProviderClient client;
                    ContentResolver resolver;
                    Uri uri;
                    ParcelFileDescriptor fileField;

                    void error1() {
                        client.openFile(uri, "mode", null); // Warn
                        resolver.openFile(uri, "mode", null); // Warn
                        resolver.openFileDescriptor(uri, "mode", null); // Warn
                    }

                    void ok1() {
                        ParcelFileDescriptor file = client.openFile(uri, "mode", null); // OK
                        file.close();
                        ParcelFileDescriptor file2 = resolver.openFile(uri, "mode", null); // OK
                        file2.close();
                        ParcelFileDescriptor file3 = resolver.openFileDescriptor(uri, "mode", null); // OK
                        file3.close();
                    }

                    void ok2() {
                        ParcelFileDescriptor file = client.openFile(uri, "mode", null); // OK
                        file.closeWithError("msg");
                        ParcelFileDescriptor file2 = resolver.openFile(uri, "mode", null); // OK
                        file2.closeWithError("msg");
                        ParcelFileDescriptor file3 = resolver.openFileDescriptor(uri, "mode", null); // OK
                        file3.closeWithError("msg");
                    }

                    void ok3() {
                        ParcelFileDescriptor file = client.openFile(uri, "mode", null); // OK
                        unknown(file);
                        ParcelFileDescriptor file2 = resolver.openFile(uri, "mode", null); // OK
                        unknown(file2);
                        ParcelFileDescriptor file3 = resolver.openFileDescriptor(uri, "mode", null); // OK
                        unknown(file3);
                    }

                    void ok4() {
                        fileField = client.openFile(uri, "mode", null); // OK
                        fileField = resolver.openFile(uri, "mode", null); // OK
                        fileField = resolver.openFileDescriptor(uri, "mode", null); // OK
                    }

                    void ok5() {
                        try (ParcelFileDescriptor file = client.openFile(uri, "mode", null)) { // OK
                            file.getStatSize();
                        }
                        try (ParcelFileDescriptor file2 = resolver.openFile(uri, "mode", null)) { // OK
                            file2.getStatSize();
                        }
                        try (ParcelFileDescriptor file3 = resolver.openFileDescriptor(uri, "mode", null)) { // OK
                            file3.getStatSize();
                        }
                    }

                    ParcelFileDescriptor ok6() {
                        ParcelFileDescriptor file = client.openFile(uri, "mode", null); // OK
                        return file;
                    }

                    ParcelFileDescriptor ok7() {
                        ParcelFileDescriptor file = resolver.openFile(uri, "mode", null); // OK
                        return file;
                    }

                    ParcelFileDescriptor ok8() {
                        ParcelFileDescriptor file = resolver.openFileDescriptor(uri, "mode", null); // OK
                        return file;
                    }

                    void unknown(ParcelFileDescriptor file) {
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testOpenStreams() {
        val expected =
            """
            src/test/pkg/OpenStreamsTest.java:15: Warning: This InputStream should be freed up after use with #close() [Recycle]
                    resolver.openInputStream(uri); // Warn
                             ~~~~~~~~~~~~~~~
            src/test/pkg/OpenStreamsTest.java:16: Warning: This OutputStream should be freed up after use with #close() [Recycle]
                    resolver.openOutputStream(uri); // Warn
                             ~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.ContentResolver;
                import android.net.Uri;
                import java.io.InputStream;
                import java.io.OutputStream;

                class OpenStreamsTest {
                    ContentResolver resolver;
                    Uri uri;
                    InputStream inputStreamField;
                    InputStream outputStreamField;

                    void error1() {
                        resolver.openInputStream(uri); // Warn
                        resolver.openOutputStream(uri); // Warn
                    }

                    void ok1() {
                        InputStream inputStream = resolver.openInputStream(uri); // OK
                        inputStream.close();
                        OutputStream outputStream = resolver.openOutputStream(uri); // OK
                        outputStream.close();
                    }

                    void ok2() {
                        InputStream inputStream = resolver.openInputStream(uri); // OK
                        unknown(inputStream);
                        OutputStream outputStream = resolver.openOutputStream(uri); // OK
                        unknown(outputStream);
                    }

                    void ok3() {
                        inputStreamField = resolver.openInputStream(uri); // OK
                        outputStreamField = resolver.openOutputStream(uri); // OK
                    }

                    void ok4() {
                        try (InputStream inputStream = resolver.openInputStream(uri)) { // OK
                            inputStream.read();
                        }
                        try (OutputStream outputStream = resolver.openOutputStream(uri)) { // OK
                            outputStream.flush();
                        }
                    }

                    ParcelFileDescriptor ok5() {
                        InputStream inputStream = resolver.openInputStream(uri); // OK
                        return inputStream;
                    }

                    ParcelFileDescriptor ok6() {
                        OutputStream outputStream = resolver.openOutputStream(uri);
                        return outputStream;
                    }

                    void unknown(InputStream inputStream) {
                    }

                    void unknown(OutputStream outputStream) {
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun test225936245() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.animation.Animator;
                import android.animation.ValueAnimator;

                @SuppressWarnings({"SameParameterValue", "UnnecessaryLocalVariable"})
                public class AnimationTester {

                    private static Animator createVideoViewAlphaAnimator(boolean testBool) {
                        ValueAnimator alphaAnimator = testBool
                                ? ValueAnimator.ofFloat(0.0f, 0.5f)
                                : ValueAnimator.ofFloat(0.1f, 1.0f);
                        return alphaAnimator;
                    }

                    public static void doAnims() {
                        createVideoViewAlphaAnimator(true).start();

                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun test224869924() {
        // Regression test for 224869924: CommitTransaction Lint False Positive
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.pm.PackageManager;
                import android.os.Bundle;

                import android.support.v7.app.AppCompatActivity;
                import android.support.v4.app.FragmentTransaction;

                public class EngineSettings extends AppCompatActivity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                            FragmentTransaction transaction =
                                    getSupportFragmentManager()
                                            .beginTransaction()
                                            // nonexistent, simulating broken classpath here (should be just "replace")
                                            .replaceUnresolvable(
                                                    R.id.content_frame,
                                                    new AaeGeneralSettingsFragment(),
                                                    AaeGeneralSettingsFragment.class.getSimpleName());
                            // The above method is unresolved so we can't really be sure here that
                            // transaction is still the right instance. Since we *do* se a
                            // commitNow, we'll stay silent.
                            transaction.commitNow();
                        }
                    }

                    public static class AaeGeneralSettingsFragment extends android.support.v4.app.Fragment {
                    }
                }
                """
            ).indented(),
            rClass("test.pkg", "@id/content_frame"),
            fragment,
            fragmentManager,
            fragmentTransaction,
            // Stubs
            java(
                """
                package android.support.v7.app;
                import android.support.v4.app.FragmentManager;
                public class AppCompatActivity extends android.app.Activity {
                    public FragmentManager getSupportFragmentManager() {
                        return null;
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    private val dialogFragment = java(
        """
        package android.support.v4.app;

        /** Stub to make unit tests able to resolve types without having a real dependency
         * on the appcompat library */
        @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
        public abstract class DialogFragment extends Fragment {
            public void show(FragmentManager manager, String tag) { }
            public int show(FragmentTransaction transaction, String tag) { return 0; }
            public void dismiss() { }
        }
        """
    ).indented()

    private val fragment = java(
        """
        package android.support.v4.app;

        /** Stub to make unit tests able to resolve types without having a real dependency
         * on the appcompat library */
        @SuppressWarnings("ClassNameDiffersFromFileName")
        public class Fragment {
        }
        """
    ).indented()

    private val fragmentManager = java(
        """
        package android.support.v4.app;

        /** Stub to make unit tests able to resolve types without having a real dependency
         * on the appcompat library */
        @SuppressWarnings("ClassNameDiffersFromFileName")
        public abstract class FragmentManager {
            public abstract FragmentTransaction beginTransaction();
        }
        """
    ).indented()

    private val fragmentTransaction = java(
        """
        package android.support.v4.app;

        /** Stub to make unit tests able to resolve types without having a real dependency
         * on the appcompat library */
        @SuppressWarnings("ClassNameDiffersFromFileName")
        public abstract class FragmentTransaction {
            public abstract int commit();
            public abstract int commitAllowingStateLoss();
            public abstract FragmentTransaction show(Fragment fragment);
            public abstract FragmentTransaction hide(Fragment fragment);
            public abstract FragmentTransaction attach(Fragment fragment);
            public abstract FragmentTransaction detach(Fragment fragment);
            public abstract FragmentTransaction add(int containerViewId, Fragment fragment);
            public abstract FragmentTransaction add(Fragment fragment, String tag);
            public abstract FragmentTransaction addToBackStack(String name);
            public abstract FragmentTransaction disallowAddToBackStack();
            public abstract FragmentTransaction setBreadCrumbShortTitle(int res);
            public abstract FragmentTransaction setCustomAnimations(int enter, int exit);
            public abstract FragmentTransaction replace(int containerViewId, Fragment fragment, String tag);
        }
        """
    ).indented()

    private val sharedPrefsTest8 = java(
        """
        package test.pkg;

        import android.app.Activity;
        import android.content.SharedPreferences;
        import android.preference.PreferenceManager;
        @SuppressWarnings({"ClassNameDiffersFromFileName", "UnusedAssignment", "ConstantConditions"})
        public class SharedPrefsTest8 extends Activity {
            public void commitWarning1() {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.commit();
            }

            public void commitWarning2() {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                boolean b = editor.commit(); // OK: reading return value
            }
            public void commitWarning3() {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                boolean c;
                c = editor.commit(); // OK: reading return value
            }

            public void commitWarning4() {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                if (editor.commit()) { // OK: reading return value
                    //noinspection UnnecessaryReturnStatement
                    return;
                }
            }

            public void commitWarning5() {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                boolean c = false;
                c |= editor.commit(); // OK: reading return value
            }

            public void commitWarning6() {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                foo(editor.commit()); // OK: reading return value
            }

            public void foo(boolean x) {
            }

            public void noWarning() {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.apply();
            }
        }
        """
    ).indented()

    fun testUnresolvableFragmentTransactions() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.app.FragmentManager
                import android.app.FragmentTransaction

                lateinit var fragmentManager: FragmentManager
                lateinit var unrelated: FragmentTransaction

                fun select() {
                    val transaction = fragmentManager.beginTransaction() // OK
                    transaction.commitNow()
                }

                fun test1a() {
                    // Don't flag a commit call if there's an unresolvable reference *and* we have
                    // a commit call (even if we're not certain which instance it's on)
                    val transaction = fragmentManager.beginTransaction().unknown() // OK
                    unrelated.commitNow()
                }

                fun test1b() {
                    // Like 1a, but using assignment instead of declaration initialization
                    val transaction: FragmentTransaction
                    transaction = fragmentManager.beginTransaction().unknown() // OK
                    unrelated.commitNow()
                }

                fun test2(fragmentManager: FragmentManager) {
                    // If there's an unresolvable reference, DO complain if there's *no* commit call anywhere
                    fragmentManager.beginTransaction().unknown() // ERROR 1
                }

                fun test3a() {
                    // If the unresolved call has nothing to do with updating the
                    // transaction instance (e.g. is not associated with an
                    // assignment and has no further chained calls) don't revert to
                    // non-instance checking
                    val transaction = fragmentManager.beginTransaction() // ERROR 2
                    transaction.unknown()
                    unrelated.commitNow()
                }

                fun test3b() {
                    // Like 3a, but here we're making calls on the result of the
                    // unresolved call; those could be cleanup calls, so in that case
                    // we're not confident enough
                    val transaction = fragmentManager.beginTransaction() // OK
                    transaction.unknown().something()
                    unrelated.commitNow()
                }

                //private fun FragmentTransaction.unknown(): FragmentTransaction = error("not yet implemented")
                //private fun FragmentTransaction.something(): Unit = error("not yet implemented")
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:30: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                fragmentManager.beginTransaction().unknown() // ERROR 1
                                ~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:38: Warning: This transaction should be completed with a commit() call [CommitTransaction]
                val transaction = fragmentManager.beginTransaction() // ERROR 2
                                                  ~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testUnresolvableSharedPrefsEditors() {
        // Like testUnresolvableFragmentTransactions but for SharedPreference editors
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.SharedPreferences
                lateinit var webViewUrlSharedPrefs: SharedPreferences
                lateinit var unrelated: SharedPreferences.Editor

                fun test1a() {
                    // Don't flag a apply call if there's an unresolvable reference *and* we have
                    // a apply call (even if we're not certain which instance it's on)
                    val edits = webViewUrlSharedPrefs.edit().unknown() // OK
                    unrelated.apply()
                }

                fun test1b() {
                    // Like 1a, but using assignment instead of declaration initialization
                    val edits: SharedPreferences.Editor
                    edits = webViewUrlSharedPrefs.edit().unknown() // OK
                    unrelated.apply()
                }

                fun test2() {
                    // If there's an unresolvable reference, DO complain if there's *no* apply call anywhere
                    webViewUrlSharedPrefs.edit().unknown() // ERROR 1
                }

                fun test3a() {
                    // If the unresolved call has nothing to do with updating
                    // the shared prefs editor instance (e.g. is not associated with an
                    // assignment and has no further chained calls) don't revert to
                    // non-instance checking
                    val edits = webViewUrlSharedPrefs.edit() // ERROR 2
                    edits.unknown()
                    unrelated.apply()
                }

                fun test3b() {
                    // Like 3a, but here we're making calls on the result of the
                    // unresolved call; those could be cleanup calls, so in that case
                    // we're not confident enough
                    val edits = webViewUrlSharedPrefs.edit() // OK
                    edits.unknown().something()
                    unrelated.apply()
                }

                //private fun SharedPreferences.Editor.unknown(): SharedPreferences.Editor = error("not yet implemented")
                //private fun SharedPreferences.Editor.something(): Unit = error("not yet implemented")
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:23: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                webViewUrlSharedPrefs.edit().unknown() // ERROR 1
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:31: Warning: SharedPreferences.edit() without a corresponding commit() or apply() call [CommitPrefEdits]
                val edits = webViewUrlSharedPrefs.edit() // ERROR 2
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testUnresolvableRecycle() {
        // Like testUnresolvableFragmentTransactions but for the recycle scenarios (animations/typed arrays/cursors, etc)
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.animation.AnimatorSet
                import android.view.ViewPropertyAnimator

                lateinit var unrelated: AnimatorSet

                fun test1a() {
                    // Don't flag a start call if there's an unresolvable reference *and* we have
                    // a start call (even if we're not certain which instance it's on)
                    val animations = AnimatorSet().unknown() // OK
                    unrelated.start()
                }

                fun test1b() {
                    // Like 1a, but using assignment instead of declaration initialization
                    val animations: AnimatorSet
                    animations = AnimatorSet().unknown() // OK
                    unrelated.start()
                }

                fun test2() {
                    // If there's an unresolvable reference, DO complain if there's *no* start call anywhere
                    AnimatorSet().unknown() // ERROR 1
                }

                fun test3a() {
                    // If the unresolved call has nothing to do with updating
                    // the animation set instance (e.g. is not associated with an
                    // assignment and has no further chained calls) don't revert to
                    // non-instance checking
                    val animations = AnimatorSet() // ERROR 2
                    animations.unknown()
                    unrelated.start()
                }

                fun test3b() {
                    // Like 3a, but here we're making calls on the result of the
                    // unresolved call; those could be cleanup calls, so in that case
                    // we're not confident enough
                    val animations = AnimatorSet() // OK
                    animations.unknown().something()
                    unrelated.start()
                }

                //private fun AnimatorSet.unknown(): AnimatorSet = error("not yet implemented")
                //private fun AnimatorSet.something(): Unit = error("not yet implemented")
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:24: Warning: This animation should be started with #start() [Recycle]
                AnimatorSet().unknown() // ERROR 1
                ~~~~~~~~~~~
            src/test/pkg/test.kt:32: Warning: This animation should be started with #start() [Recycle]
                val animations = AnimatorSet() // ERROR 2
                                 ~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }
}
