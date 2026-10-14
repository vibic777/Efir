package ru.efir.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Общий корневой Compose-компонент приложения.
 * Используется и в Android, и в Desktop — каркас для будущей навигации.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Efir",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Аналог AirDroid: Android ↔ Windows в локальной сети",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val platform = currentPlatform()
                Text(
                    text = "Платформа: ${platform.name} ${platform.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
