package com.alauncher.app.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alauncher.app.MainActivity
import com.alauncher.app.ui.theme.MinimalSmartLauncherTheme

class ErrorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ErrorActivity runs in an isolated crash process (:error_process) and always renders
        // MinimalSmartLauncherTheme with its default ThemeMode.NordicPine (dark #121A18 background),
        // regardless of the OS system light/dark setting. Using SystemBarStyle.dark() guarantees
        // light status/navigation bar icons against the dark crash screen background even when the
        // device OS is in Light Mode (whereas parameterless enableEdgeToEdge() would follow OS light
        // mode and render dark icons on the dark NordicPine background).
        val darkTransparentBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(
            statusBarStyle = darkTransparentBarStyle,
            navigationBarStyle = darkTransparentBarStyle
        )
        super.onCreate(savedInstanceState)
        
        val errorMessage = intent.getStringExtra("error_message") ?: "Unknown error"
        val stackTrace = intent.getStringExtra("stack_trace") ?: ""

        setContent {
            MinimalSmartLauncherTheme {
                ErrorScreen(
                    message = errorMessage,
                    stackTrace = stackTrace,
                    onRestart = {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onSendEmail = {
                        val mailtoUri = "mailto:pradeep.alauncher@gmail.com" +
                                "?subject=" + Uri.encode("ALauncher Crash Report") +
                                "&body=" + Uri.encode("Error: $errorMessage\n\nStack Trace:\n$stackTrace")
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse(mailtoUri)
                            putExtra(Intent.EXTRA_SUBJECT, "ALauncher Crash Report")
                            putExtra(Intent.EXTRA_TEXT, "Error: $errorMessage\n\nStack Trace:\n$stackTrace")
                        }
                        try {
                            startActivity(Intent.createChooser(emailIntent, "Send crash report via..."))
                        } catch (e: Exception) {
                            // No email app?
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    stackTrace: String,
    onRestart: () -> Unit,
    onSendEmail: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Oops! Something went wrong.",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Alauncher encountered an unexpected error and needs to restart.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = "Error Detail:", fontWeight = FontWeight.Bold)
                    Text(text = message, color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Stack Trace:", fontWeight = FontWeight.Bold)
                    Text(
                        text = stackTrace,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restart App")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = onSendEmail,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Crash Report via Email")
            }
        }
    }
}
