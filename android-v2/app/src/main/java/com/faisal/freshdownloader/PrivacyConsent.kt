package com.faisal.freshdownloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val PRIVACY_URL = "https://trustrankseo.github.io/video-downloader/privacy.html"
private const val PRIVACY_PREFS = "universal_downloader_privacy"
private const val PRIVACY_ACCEPTED_VERSION = "accepted_policy_version"
private const val CURRENT_POLICY_VERSION = 2

private data class PolicySection(val title: String, val body: String)

private val EN_POLICY = listOf(
    PolicySection(
        "Information we process",
        "Universal Downloader is designed to minimize data collection. The app may process an app-generated installation ID, referral/trial state, Premium entitlement state, app version, basic device/app diagnostics, and URLs you enter to perform requested downloads. The app does not request your name, precise location, contacts, SMS, call history, IMEI, or device serial number."
    ),
    PolicySection(
        "How information is used",
        "Information is used to provide Single, Bulk, Channel/Playlist/Profile, MP4 and MP3 features where supported; manage trials, referrals and Premium access; save app preferences; prevent duplicate trial/referral abuse; and troubleshoot problems you choose to report."
    ),
    PolicySection(
        "Downloads and third-party services",
        "Links you enter are processed only to perform the requested download or discovery action. Downloaded files are saved locally on your device. Third-party websites and media services operate independently under their own terms and privacy practices. Only download content you own or are authorized or otherwise permitted to save."
    ),
    PolicySection(
        "Payments",
        "If Premium is offered, payment and subscription processing is handled by the applicable app store, including HUAWEI AppGallery where applicable. Universal Downloader does not receive or store full payment-card details. The app may receive entitlement or transaction status needed to activate or restore Premium."
    ),
    PolicySection(
        "Sharing, retention and support",
        "Universal Downloader does not sell personal data. Local settings and identifiers normally remain on your device until app data is cleared or the app is removed. A problem report is sent only when you choose to send it through your email app and may include your written description plus basic app/device diagnostics."
    ),
    PolicySection(
        "Your rights and contact",
        "Depending on your region, you may request access, correction or deletion of personal information associated with the app. For privacy questions or requests, contact the developer from Contact Us in the app or email sayadzubair0786@gmail.com."
    )
)

private val ZH_POLICY = listOf(
    PolicySection(
        "我们处理的信息",
        "Universal Downloader 以尽量少收集数据为原则。根据您使用的功能，本应用可能处理由应用生成的安装标识符、推荐/试用状态、Premium 权益状态、应用版本、基本设备/应用诊断信息，以及您为执行下载而输入的链接。本应用不会要求提供您的姓名、精确位置、联系人、短信、通话记录、IMEI 或设备序列号。"
    ),
    PolicySection(
        "信息的使用方式",
        "相关信息仅用于在支持的情况下提供单个下载、批量下载、频道/播放列表/主页、MP4 和 MP3 功能；管理试用、推荐奖励和 Premium 权益；保存应用偏好；防止重复领取试用或推荐奖励；以及处理您主动提交的问题报告。"
    ),
    PolicySection(
        "下载及第三方服务",
        "您输入的链接仅用于执行您请求的下载或内容发现操作。下载文件保存在您的设备本地。第三方网站和媒体服务独立运营，并适用其各自的服务条款和隐私规则。请仅下载您拥有、已获授权或依法及依相关服务条款允许保存的内容。"
    ),
    PolicySection(
        "支付",
        "如提供 Premium 服务，付款及订阅由相应的应用商店处理；在适用情况下包括 HUAWEI AppGallery。Universal Downloader 不接收或保存完整的银行卡信息。本应用可能接收用于开通或恢复 Premium 所必需的购买状态、交易状态或权益信息。"
    ),
    PolicySection(
        "共享、保存期限及技术支持",
        "Universal Downloader 不出售个人信息。本地设置和安装标识符通常保存在您的设备上，直至您清除应用数据或卸载应用。只有当您主动选择通过电子邮件发送问题报告时，报告才会发出；报告可能包含您填写的问题描述以及基本的应用/设备诊断信息。"
    ),
    PolicySection(
        "您的权利及联系方式",
        "根据您所在地区适用的法律，您可请求查询、更正或删除与本应用相关的个人信息。如有隐私问题或相关请求，可通过应用内“Contact Us”联系开发者，或发送邮件至 sayadzubair0786@gmail.com。"
    ),
    PolicySection(
        "中国大陆用户说明",
        "本应用的大部分设置、试用和推荐状态保存在设备本地。为完成您主动请求的下载，您输入的链接可能会发送至对应的第三方内容服务；付款信息由 HUAWEI AppGallery 等相应应用商店处理。本应用不会出售个人信息，也不会在未经您主动操作的情况下发送问题报告。"
    )
)

@Composable
fun PrivacyConsentGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
    }
    var accepted by remember {
        mutableStateOf(prefs.getInt(PRIVACY_ACCEPTED_VERSION, 0) >= CURRENT_POLICY_VERSION)
    }

    if (accepted) {
        content()
    } else {
        FirstLaunchPrivacyScreen(
            onAccept = {
                prefs.edit().putInt(PRIVACY_ACCEPTED_VERSION, CURRENT_POLICY_VERSION).apply()
                accepted = true
            },
            onDecline = {
                (context as? Activity)?.finishAffinity()
            }
        )
    }
}

@Composable
private fun FirstLaunchPrivacyScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    var chinese by rememberSaveable {
        mutableStateOf(Locale.getDefault().language.lowercase().startsWith("zh"))
    }
    var confirmedRead by rememberSaveable { mutableStateOf(false) }
    val sections = if (chinese) ZH_POLICY else EN_POLICY

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (chinese) "隐私政策与用户同意" else "Privacy Policy & User Consent",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (chinese)
                    "首次使用前，请阅读以下隐私政策。只有在您明确同意后，应用的下载和其他主要功能才会开放。"
                else
                    "Before using the app for the first time, please read this Privacy Policy. Download and other main app features are available only after you explicitly agree.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !chinese,
                    onClick = { chinese = false },
                    label = { Text("English") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = chinese,
                    onClick = { chinese = true },
                    label = { Text("简体中文") },
                    modifier = Modifier.weight(1f)
                )
            }

            sections.forEach { section ->
                PolicyCard(section)
            }

            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (chinese) "在线查看完整隐私政策" else "Read full Privacy Policy online")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = confirmedRead,
                    onCheckedChange = { confirmedRead = it }
                )
                Text(
                    if (chinese)
                        "我已阅读并理解上述隐私政策。"
                    else
                        "I have read and understand the Privacy Policy.",
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onAccept,
                enabled = confirmedRead,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (chinese) "同意并继续" else "Agree & Continue",
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (chinese) "不同意并退出" else "Decline & Exit")
            }
        }
    }
}

@Composable
fun PrivacyPolicyPage() {
    val context = LocalContext.current
    var chinese by rememberSaveable {
        mutableStateOf(Locale.getDefault().language.lowercase().startsWith("zh"))
    }
    val sections = if (chinese) ZH_POLICY else EN_POLICY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (chinese) "隐私政策" else "Privacy Policy",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            if (chinese) "更新日期：2026年9月10日" else "Last updated: September 10, 2026",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !chinese,
                onClick = { chinese = false },
                label = { Text("English") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = chinese,
                onClick = { chinese = true },
                label = { Text("简体中文") },
                modifier = Modifier.weight(1f)
            )
        }

        sections.forEach { section -> PolicyCard(section) }

        Button(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (chinese) "在线查看完整版本" else "OPEN FULL ONLINE POLICY")
        }
    }
}

@Composable
private fun PolicyCard(section: PolicySection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(section.title, fontWeight = FontWeight.Bold)
            Text(
                section.body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
