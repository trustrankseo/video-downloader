package com.faisal.freshdownloader

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ReferralCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val manager = remember { ReferralManager(context) }
    var snapshot by remember { mutableStateOf(manager.snapshot()) }
    var input by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        manager.captureReferralUri((context as? android.app.Activity)?.intent?.data)
        manager.refreshInstallReferrer()
        delay(900)
        snapshot = manager.snapshot()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111D31)),
        border = BorderStroke(1.dp, Color(0xFF55DDF7).copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF10243A), Color(0xFF1D2041), Color(0xFF25193E))
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Refer & Earn", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Organic sharing • install eligibility • privacy-safe device record",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = Color(0xFF59D99A).copy(alpha = 0.14f)
                ) {
                    Text(
                        if (snapshot.eligibleForReferral) "ELIGIBLE" else "RECORDED",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = Color(0xFF59D99A),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.18f)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YOUR REFERRAL CODE", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            snapshot.referralCode,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF55DDF7)
                        )
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(snapshot.referralCode))
                            message = "Referral code copied"
                        }) { Text("COPY") }
                    }
                    Text(
                        "A referred new install can apply one code. The bonus unlocks after its first successful download.",
                        color = Color.White.copy(alpha = 0.66f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = {
                    manager.noteOrganicShare()
                    snapshot = manager.snapshot()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Universal Downloader referral")
                        putExtra(Intent.EXTRA_TEXT, manager.shareText())
                    }
                    context.startActivity(Intent.createChooser(send, "Share Universal Downloader"))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B7CFF))
            ) {
                Text("SHARE & INVITE", fontWeight = FontWeight.Bold)
            }

            if (snapshot.pendingReferralCode == null && !snapshot.referralActivated) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase().take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Referral code") },
                    placeholder = { Text("UDXXXXXXXXXX") }
                )
                OutlinedButton(
                    onClick = {
                        message = when (manager.applyReferralCode(input)) {
                            ReferralManager.ApplyResult.Accepted -> "Code accepted. Complete one successful download to unlock +1 bonus premium trial."
                            ReferralManager.ApplyResult.AlreadyUsed -> "A referral is already attached to this installation."
                            ReferralManager.ApplyResult.OwnCode -> "You cannot use your own referral code."
                            ReferralManager.ApplyResult.Invalid -> "Invalid referral code."
                            ReferralManager.ApplyResult.NotEligible -> "This installation is no longer eligible for a referral reward."
                        }
                        snapshot = manager.snapshot()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFF9D6CFF).copy(alpha = 0.55f))
                ) {
                    Text("APPLY REFERRAL CODE", color = Color.White)
                }
            }

            if (snapshot.pendingReferralCode != null && !snapshot.referralActivated) {
                Text(
                    "Referral ${snapshot.pendingReferralCode} is pending • complete one successful download to activate the bonus.",
                    color = Color(0xFFFFC857),
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (snapshot.referralActivated) {
                Text(
                    "Referral activated • ${snapshot.bonusTrials} bonus premium trial${if (snapshot.bonusTrials == 1) "" else "s"} earned on this install.",
                    color = Color(0xFF59D99A),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (message.isNotBlank()) {
                Text(message, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Text("DEVICE RECORD", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            Text(
                "Install ${snapshot.installId.take(8)}… • Source ${snapshot.installSource} • App ${BuildConfig.VERSION_NAME} • Shares ${snapshot.shareCount}",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Uses an app-generated install ID only. No IMEI, serial number, contacts, microphone, camera, or screen recording is collected.",
                color = Color.White.copy(alpha = 0.50f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
