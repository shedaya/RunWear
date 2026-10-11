package com.runwear.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionScreen(
    onRequestPermission: () -> Unit,
    onSetManualLocation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showGpsHelp by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Location icon
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Location Needed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "RunWear needs your location to get accurate weather data for your area and recommend the right outfit for your run.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Location Access")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSetManualLocation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Location Manually")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // GPS troubleshooting (ported from web PWA renderGPSHelp)
            TextButton(
                onClick = { showGpsHelp = !showGpsHelp },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Trouble enabling location?")
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (showGpsHelp) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (showGpsHelp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GpsHelpStep(1, "When prompted, tap Allow")
                    GpsHelpStep(2, "If you previously denied: open Settings → Apps → RunWear → Permissions")
                    GpsHelpStep(3, "Tap Location and select \"Allow only while using the app\"")
                    GpsHelpStep(4, "Return here and tap \"Grant Location Access\" again")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your location is only used to fetch weather data and is never stored or shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GpsHelpStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 10.dp, top = 1.dp)
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
    }
}
