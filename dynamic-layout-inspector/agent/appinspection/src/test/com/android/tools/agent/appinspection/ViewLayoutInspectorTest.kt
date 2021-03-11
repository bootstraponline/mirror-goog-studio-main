/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.agent.appinspection

import android.content.Context
import android.content.res.Resources
import android.graphics.Picture
import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewRootImpl
import android.view.WindowManagerGlobal
import android.webkit.WebView
import android.widget.TextView
import com.android.tools.agent.appinspection.proto.StringTable
import com.android.tools.agent.appinspection.testutils.FrameworkStateRule
import com.android.tools.agent.appinspection.testutils.MainLooperRule
import com.android.tools.agent.appinspection.testutils.inspection.InspectorRule
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.agent.appinspection.util.decompress
import com.google.common.truth.Truth.assertThat
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchCommand
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class ViewLayoutInspectorTest {

    @get:Rule
    val mainLooperRule = MainLooperRule()

    @get:Rule
    val inspectorRule = InspectorRule()

    @get:Rule
    val frameworkRule = FrameworkStateRule()

    @Test
    fun canStartAndStopInspector() = createViewInspector { viewInspector ->

        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
        }

        val stopFetchCommand = Command.newBuilder().apply {
            stopFetchCommand = StopFetchCommand.getDefaultInstance()
        }.build()
        viewInspector.onReceiveCommand(
            stopFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.STOP_FETCH_RESPONSE)
        }
    }

    @Test
    fun canCaptureTreeInContinuousMode() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(2)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val resourceNames = mutableMapOf<Int, String>()
        val resources = Resources(resourceNames)
        val context = Context("view.inspector.test", resources)
        val tree1 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val tree2 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val tree3 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(tree1, tree2))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        assertThat(eventQueue).isEmpty()

        val tree1FakePicture1 = Picture(byteArrayOf(1, 1))
        val tree1FakePicture2 = Picture(byteArrayOf(1, 2))
        val tree1FakePicture3 = Picture(byteArrayOf(1, 3))
        val tree1FakePicture4 = Picture(byteArrayOf(1, 4))
        val tree2FakePicture = Picture(byteArrayOf(2))
        val tree3FakePicture = Picture(byteArrayOf(3))

        tree1.forcePictureCapture(tree1FakePicture1)
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId
            )
        }

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.SKP)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture1.bytes)
            }
        }

        tree2.forcePictureCapture(tree2FakePicture)
        // Roots event not resent, as roots haven't changed
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree2.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree2FakePicture.bytes)
            }
        }

        WindowManagerGlobal.getInstance().rootViews.add(tree3)

        tree1.forcePictureCapture(tree1FakePicture2)
        // As a side-effect, this capture discovers the newly added third tree
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId,
                tree3.uniqueDrawingId
            )
        }

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture2.bytes)
            }
        }

        // Roots changed - this should generate a new roots event
        WindowManagerGlobal.getInstance().rootViews.remove(tree2)
        tree1.forcePictureCapture(tree1FakePicture3)

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree3.uniqueDrawingId
            )
        }

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture3.bytes)
            }
        }

        val stopFetchCommand = Command.newBuilder().apply {
            stopFetchCommand = StopFetchCommand.getDefaultInstance()
        }.build()
        viewInspector.onReceiveCommand(
            stopFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for the stop command to run its course

        // Normally, stopping the inspector triggers invalidate calls, but in fake android, those
        // do nothing. Instead, we emulate this by manually firing capture events.
        tree1.forcePictureCapture(tree1FakePicture4)
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture4.bytes)

            }
        }
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.PROPERTIES_EVENT)
            assertThat(event.propertiesEvent.rootId).isEqualTo(tree1.uniqueDrawingId)
        }

        tree3.forcePictureCapture(tree3FakePicture)
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree3.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree3FakePicture.bytes)

            }
        }
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.PROPERTIES_EVENT)
            assertThat(event.propertiesEvent.rootId).isEqualTo(tree3.uniqueDrawingId)
        }
    }

    @Test
    fun nodeBoundsCapturedAsExpected() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(2)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val resourceNames = mutableMapOf<Int, String>()
        val resources = Resources(resourceNames)
        val context = Context("view.inspector.test", resources)
        val mainScreen = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            width = 400
            height = 800
        }
        val floatingDialog = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            width = 300
            height = 200
        }
        val stubPicture = Picture(byteArrayOf(0))

        // Used for root offset
        floatingDialog.locationInSurface.apply {
            x = 10
            y = 20
        }
        // Used for absolution position of dialog root
        floatingDialog.locationOnScreen.apply {
            x = 80
            y = 200
        }

        mainScreen.addView(ViewGroup(context).apply {
            scrollX = 5
            scrollY = 100
            left = 20
            top = 30
            width = 40
            height = 50

            addView(View(context).apply {
                left = 40
                top = 10
                width = 20
                height = 30
            })

            addView(View(context).apply {
                left = 40
                top = 10
                width = 20
                height = 30
                setTransformedPoints(floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f))
            })
        })

        floatingDialog.addView(ViewGroup(context).apply {
            scrollX = 5
            scrollY = 100
            left = 20
            top = 30
            width = 40
            height = 50

            addView(View(context).apply {
                left = 40
                top = 10
                width = 20
                height = 30
            })

            addView(View(context).apply {
                left = 40
                top = 10
                width = 20
                height = 30
                setTransformedPoints(floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f))
            })
        })

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(mainScreen, floatingDialog))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing
        assertThat(eventQueue).isEmpty()

        mainScreen.forcePictureCapture(stubPicture)
        eventQueue.take().let { bytes ->
            // In this test, we don't care that much about this event, but we consume io get to the
            // layout event
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
        }

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                layoutEvent.rootOffset.let { rootOffset ->
                    assertThat(rootOffset.x).isEqualTo(0)
                    assertThat(rootOffset.y).isEqualTo(0)
                }

                val root = layoutEvent.rootView
                val parent = root.getChildren(0)
                val child0 = parent.getChildren(0)
                val child1 = parent.getChildren(1)

                assertThat(root.id).isEqualTo(mainScreen.uniqueDrawingId)
                root.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(0)
                    assertThat(rect.y).isEqualTo(0)
                    assertThat(rect.w).isEqualTo(400)
                    assertThat(rect.h).isEqualTo(800)
                }
                parent.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(20)
                    assertThat(rect.y).isEqualTo(30)
                    assertThat(rect.w).isEqualTo(40)
                    assertThat(rect.h).isEqualTo(50)
                }
                child0.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(55)
                    assertThat(rect.y).isEqualTo(-60)
                    assertThat(rect.w).isEqualTo(20)
                    assertThat(rect.h).isEqualTo(30)
                }
                child1.bounds.render.let { quad ->
                    assertThat(quad.x0).isEqualTo(10)
                    assertThat(quad.y0).isEqualTo(20)
                    assertThat(quad.x1).isEqualTo(30)
                    assertThat(quad.y1).isEqualTo(40)
                    assertThat(quad.x2).isEqualTo(50)
                    assertThat(quad.y2).isEqualTo(60)
                    assertThat(quad.x3).isEqualTo(70)
                    assertThat(quad.y3).isEqualTo(80)
                }
            }
        }

        floatingDialog.forcePictureCapture(stubPicture)
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                layoutEvent.rootOffset.let { rootOffset ->
                    assertThat(rootOffset.x).isEqualTo(10)
                    assertThat(rootOffset.y).isEqualTo(20)
                }

                val root = layoutEvent.rootView
                val parent = root.getChildren(0)
                val child0 = parent.getChildren(0)
                val child1 = parent.getChildren(1)
                assertThat(root.id).isEqualTo(floatingDialog.uniqueDrawingId)

                root.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(80)
                    assertThat(rect.y).isEqualTo(200)
                    assertThat(rect.w).isEqualTo(300)
                    assertThat(rect.h).isEqualTo(200)
                }
                parent.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(100)
                    assertThat(rect.y).isEqualTo(230)
                    assertThat(rect.w).isEqualTo(40)
                    assertThat(rect.h).isEqualTo(50)
                }
                child0.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(135)
                    assertThat(rect.y).isEqualTo(140)
                    assertThat(rect.w).isEqualTo(20)
                    assertThat(rect.h).isEqualTo(30)
                }
                child1.bounds.render.let { quad ->
                    assertThat(quad.x0).isEqualTo(10)
                    assertThat(quad.y0).isEqualTo(20)
                    assertThat(quad.x1).isEqualTo(30)
                    assertThat(quad.y1).isEqualTo(40)
                    assertThat(quad.x2).isEqualTo(50)
                    assertThat(quad.y2).isEqualTo(60)
                    assertThat(quad.x3).isEqualTo(70)
                    assertThat(quad.y3).isEqualTo(80)
                }
            }
        }
    }

    // TODO: Add test for testing snapshot mode (which will require adding more support for fetching
    //  view properties in fake-android.

    @Test
    fun canFetchPropertiesForView() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val resourceNames = mutableMapOf<Int, String>()
        val resources = Resources(resourceNames)
        val context = Context("view.inspector.test", resources)
        val root = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            addView(View(context))
            addView(TextView(context, "Placeholder Text"))
        }

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        run { // Search for properties for View
            val viewChild = root.getChildAt(0)

            val getPropertiesCommand = Command.newBuilder().apply {
                getPropertiesCommandBuilder.apply {
                    rootViewId = root.uniqueDrawingId
                    viewId = viewChild.uniqueDrawingId
                }
            }.build()
            viewInspector.onReceiveCommand(
                getPropertiesCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.GET_PROPERTIES_RESPONSE)
                response.getPropertiesResponse.let { propertiesResponse ->
                    val strings = StringTable.fromStringEntries(propertiesResponse.stringsList)
                    val propertyGroup = propertiesResponse.propertyGroup
                    assertThat(propertyGroup.viewId).isEqualTo(viewChild.uniqueDrawingId)
                    assertThat(propertyGroup.propertyList.map { strings[it.name] }).containsExactly(
                        "visibility", "layout_width", "layout_height"
                    )
                }
            }
        }

        run { // Search for properties for TextView
            val textChild = root.getChildAt(1)

            val getPropertiesCommand = Command.newBuilder().apply {
                getPropertiesCommandBuilder.apply {
                    rootViewId = root.uniqueDrawingId
                    viewId = textChild.uniqueDrawingId
                }
            }.build()
            viewInspector.onReceiveCommand(
                getPropertiesCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.GET_PROPERTIES_RESPONSE)
                response.getPropertiesResponse.let { propertiesResponse ->
                    val strings = StringTable.fromStringEntries(propertiesResponse.stringsList)
                    val propertyGroup = propertiesResponse.propertyGroup
                    assertThat(propertyGroup.viewId).isEqualTo(textChild.uniqueDrawingId)
                    assertThat(propertyGroup.propertyList.map { strings[it.name] }).containsExactly(
                        "text", "visibility", "layout_width", "layout_height"
                    )
                }
            }
        }
    }

    // WebView trampolines onto a different thread when reading properties, so just make sure things
    // continue to work in that case.
    @Test
    fun canFetchPropertiesForWebView() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val resourceNames = mutableMapOf<Int, String>()
        val resources = Resources(resourceNames)
        val context = Context("view.inspector.test", resources)
        val root = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            addView(View(context))
            addView(WebView(context))
            addView(View(context))
        }

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        run { // Search for properties for WebView
            val viewChild = root.getChildAt(1)

            val getPropertiesCommand = Command.newBuilder().apply {
                getPropertiesCommandBuilder.apply {
                    rootViewId = root.uniqueDrawingId
                    viewId = viewChild.uniqueDrawingId
                }
            }.build()
            viewInspector.onReceiveCommand(
                getPropertiesCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.GET_PROPERTIES_RESPONSE)
                response.getPropertiesResponse.let { propertiesResponse ->
                    val strings = StringTable.fromStringEntries(propertiesResponse.stringsList)
                    val propertyGroup = propertiesResponse.propertyGroup
                    assertThat(propertyGroup.viewId).isEqualTo(viewChild.uniqueDrawingId)
                    assertThat(propertyGroup.propertyList.map { strings[it.name] }).containsExactly(
                        "visibility", "layout_width", "layout_height"
                    )
                }
            }
        }
    }

    @Test
    fun settingScreenshotTypeAffectsCaptureOutput() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(2)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val fakeBitmapHeader = byteArrayOf(1, 2, 3) // trailed by 0s
        val fakePicture1 = Picture(byteArrayOf(2, 1)) // Will be ignored because of BITMAP mode
        val fakePicture2 = Picture(byteArrayOf(2, 2))

        val resourceNames = mutableMapOf<Int, String>()
        val resources = Resources(resourceNames)
        val context = Context("view.inspector.test", resources)
        val scale = 2
        val root = ViewGroup(context).apply {
            width = 100
            height = 200
            setAttachInfo(View.AttachInfo())
        }
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
        }
        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing
        assertThat(eventQueue).isEmpty()

        run { // Start first by setting type to BITMAP
            val updateScreenshotTypeCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    type = Screenshot.Type.BITMAP
                    this.scale = scale.toFloat()
                }
            }.build()
            viewInspector.onReceiveCommand(
                updateScreenshotTypeCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.UPDATE_SCREENSHOT_TYPE_RESPONSE)
            }
            root.viewRootImpl = ViewRootImpl()
            root.viewRootImpl.mSurface = Surface()
            root.viewRootImpl.mSurface.bitmapBytes = fakeBitmapHeader
            root.forcePictureCapture(fakePicture1)
            eventQueue.take().let { bytes ->
                val event = Event.parseFrom(bytes)
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            }
            eventQueue.take().let { bytes ->
                val event = Event.parseFrom(bytes)
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)

                event.layoutEvent.screenshot.let { screenshot ->
                    assertThat(screenshot.type).isEqualTo(Screenshot.Type.BITMAP)
                    val decompressedBytes = screenshot.bytes.toByteArray().decompress()

                    // The full screenshot byte array is width * height, normally all zeroed out,
                    // so if we just check the first few bytes to make sure they match our header,
                    // that's enough to know that all the data went through correctly.
                    assertThat(decompressedBytes.take(fakeBitmapHeader.size)).isEqualTo(
                        fakeBitmapHeader.asList()
                    )
                }
            }
        }

        run { // Now set type back to SKP
            val updateScreenshotTypeCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    type = Screenshot.Type.SKP
                }
            }.build()
            viewInspector.onReceiveCommand(
                updateScreenshotTypeCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(
                    Response.SpecializedCase.UPDATE_SCREENSHOT_TYPE_RESPONSE)
            }

            root.forcePictureCapture(fakePicture2)
            eventQueue.take().let { bytes ->
                val event = Event.parseFrom(bytes)
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)

                event.layoutEvent.screenshot.let { screenshot ->
                    assertThat(screenshot.type).isEqualTo(Screenshot.Type.SKP)
                    assertThat(screenshot.bytes.toByteArray()).isEqualTo(fakePicture2.bytes)
                }
            }
        }
    }

    // TODO: Add test for filtering system views and properties

    private fun createViewInspector(block: (ViewLayoutInspector) -> Unit) {
        // We could just create the view inspector directly, but using the factory mimics what
        // actually happens in production.
        val factory = ViewLayoutInspectorFactory()
        val viewInspector =
            factory.createInspector(inspectorRule.connection, inspectorRule.environment)

        // Save away the threads that are running before the test, so we can check there are no
        // extras after.
        val initialThreads = Looper.getLoopers().keys.toSet()

        block(viewInspector)
        viewInspector.onDispose()

        Looper.getLoopers().keys
            .filter { !initialThreads.contains(it) }
            .forEach { thread -> thread.join(TimeUnit.SECONDS.toMillis(1)) }
    }
}
