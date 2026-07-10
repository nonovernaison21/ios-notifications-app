package com.noah.iosnotifications

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0A84FF),
                    background = Color(0xFFF2F2F7)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        onOpenNotificationAccess = { openNotificationAccessSettings() },
                        onOpenOverlayPermission = { openOverlayPermissionSettings() }
                    )
                }
            }
        }
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

@Composable
fun SetupScreen(
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlayPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Notifications style iOS 26",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Deux autorisations sont nécessaires pour remplacer les popups Android par des bannières façon iOS.",
            fontSize = 14.sp,
            color = Color(0xFF6D6D72)
        )

        Spacer(modifier = Modifier.height(32.dp))

        StepCard(
            number = "1",
            title = "Accès aux notifications",
            description = "Autorise l'appli à lire les notifications système.",
            buttonLabel = "Ouvrir les réglages",
            onClick = onOpenNotificationAccess
        )

        Spacer(modifier = Modifier.height(16.dp))

        StepCard(
            number = "2",
            title = "Affichage par-dessus",
            description = "Autorise l'appli à afficher la bannière au-dessus des autres apps.",
            buttonLabel = "Ouvrir les réglages",
            onClick = onOpenOverlayPermission
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Une fois les deux activées, ferme cette appli : les notifications s'afficheront automatiquement en style iOS.",
            fontSize = 13.sp,
            color = Color(0xFF8E8E93)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun StepCard(
    number: String,
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0A84FF),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(number, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, fontSize = 13.sp, color = Color(0xFF6D6D72))
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
            ) {
                Text(buttonLabel)
            }
        }
    }
}
