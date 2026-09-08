package com.faisal.freshdownloader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SubscriptionCard(
    premium: Boolean,
    trialsRemaining: Int,
    priceText: String,
    status: String,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit
) {
    val cyan = Color(0xFF55DDF7)
    val purple = Color(0xFF9D6CFF)
    val card = Color(0xFF111D31)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF14243F), Color(0xFF261B46))))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (premium) "Premium Active" else "Universal Premium",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (premium) "Bulk + Channel/Profile unlocked" else "$trialsRemaining of 3 premium trials remaining",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (premium) cyan.copy(alpha = 0.15f) else purple.copy(alpha = 0.18f)
                ) {
                    Text(
                        if (premium) "ACTIVE" else priceText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (premium) cyan else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                if (premium) status else "Single downloads stay free. Bulk and Channel/Profile use the 3 free trials, then require Premium.",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!premium) {
                    Button(
                        onClick = onUpgrade,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B7CFF))
                    ) {
                        Text("GO PREMIUM", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Text("RESTORE", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PremiumRequiredDialog(
    priceText: String,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Premium required") },
        text = {
            Text("Your 3 free Bulk/Channel trials are finished. Single downloads remain free. Upgrade for $priceText to unlock Bulk and Channel/Profile.")
        },
        confirmButton = {
            Button(onClick = onUpgrade) { Text("GO PREMIUM") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRestore) { Text("RESTORE") }
                TextButton(onClick = onDismiss) { Text("LATER") }
            }
        }
    )
}
