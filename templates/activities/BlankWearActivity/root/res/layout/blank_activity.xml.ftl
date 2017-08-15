<?xml version="1.0" encoding="utf-8"?>

<android.support.wear.widget.BoxInsetLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:background="@color/dark_grey"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${activityClass}"
    tools:deviceIds="wear"
    android:padding="@dimen/box_inset_layout_padding">

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="@dimen/inner_frame_layout_padding"
        app:boxedEdges="all">

        <TextView
            android:id="@+id/text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/hello_world" />

    </FrameLayout>
</android.support.wear.widget.BoxInsetLayout>

