package com.example.helloworldcompose

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.ui.foundation.Text
import androidx.ui.core.setContent
import androidx.ui.layout.Column
import androidx.ui.core.Modifier
import androidx.ui.layout.padding
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloWorldColumn()
        }
    }
}

@Composable
fun HelloWorldColumn() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Hello, World1")
        Text("Hello, World2")
        Text("Hello, World3")
    }
}

@Preview
@Composable
fun PreviewGreeting() {
    HelloWorldColumn()
}