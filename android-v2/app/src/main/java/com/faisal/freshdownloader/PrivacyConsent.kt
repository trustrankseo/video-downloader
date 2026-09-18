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

private const val PRIVACY_URL_EN = "https://trustrankseo.github.io/video-downloader/privacy.html"
private const val PRIVACY_URL_ZH_CN = "https://trustrankseo.github.io/video-downloader/privacy-zh-cn.html"
private const val PRIVACY_PREFS = "universal_downloader_privacy"
private const val PRIVACY_ACCEPTED_VERSION = "accepted_policy_version"
private const val CURRENT_POLICY_VERSION = 4

private data class PolicySection(val title: String, val body: String)

private val EN_POLICY = listOf(
    PolicySection(
        "Publisher and scope",
        "Universal Downloader is developed and published by Zubair Abbas. This policy explains how information is handled when you use the app."
    ),
    PolicySection(
        "Information the app may process",
        "The app may process an app-generated installation ID; referral source, code and status; Android version, app version, device model and basic diagnostics; URLs you enter for download operations; ad-related device/app signals and an advertising identifier where available; and support information you choose to include in a problem report. It does not request your precise location, contacts, SMS, call history, IMEI or device serial number."
    ),
    PolicySection(
        "Purposes of processing",
        "Information is used to provide Single, Bulk, Channel/Playlist/Profile, MP4 and MP3 functions where supported; manage referrals and app preferences; diagnose failures; respond to reports you choose to send; and support advertising delivery, measurement, fraud prevention and ad-frequency control."
    ),
    PolicySection(
        "Advertising and AppLovin MAX",
        "After you accept this Privacy Policy, this app may initialize AppLovin MAX to show a banner on the Downloader screen and occasional interstitial ads after successful download operations. AppLovin and its advertising partners may process device/app signals and an advertising identifier where available for ad delivery, measurement, fraud prevention and frequency control. This v1.5.0 build requests non-personalized advertising by default. AppLovin and advertising partners operate under their own privacy terms."
    ),
    PolicySection(
        "User URLs, downloads and third-party services",
        "URLs you enter are processed only to perform the requested download or discovery operation and may be sent to the relevant third-party media service. Downloaded files are stored locally on your device. Third-party sites operate independently under their own terms and privacy practices. Download only content you own or are authorized to save."
    ),
    PolicySection(
        "Independent-app notice",
        "Universal Downloader is an independent utility and is not affiliated with, endorsed by, sponsored by, or associated with any third-party social media or media platform."
    ),
    PolicySection(
        "Sharing and service providers",
        "Universal Downloader does not sell personal data. Information may be processed only as necessary by media-source, advertising or technical service providers to perform a requested function, deliver and measure ads, provide support, comply with law or prevent abuse."
    ),
    PolicySection(
        "Retention and deletion",
        "Local settings, identifiers and referral state remain on your device until you clear app data or uninstall the app. Downloaded files remain until you delete them. Ad providers may retain advertising or measurement records under their own policies. Voluntary support information is retained only as reasonably necessary to investigate the request or meet legal obligations."
    ),
    PolicySection(
        "Problem reports and support",
        "A report is sent only when you choose to open and send it through your email app. It may contain your description, app version, Android version, device model, installation ID, referral source and basic diagnostics. Nothing is silently uploaded as a problem report."
    ),
    PolicySection(
        "Children's privacy",
        "Universal Downloader is not designed as a children's app. The developer does not knowingly request personal information from children through the app."
    ),
    PolicySection(
        "Security",
        "Reasonable technical and organizational measures are used to reduce unauthorized access, alteration, disclosure or loss. No electronic storage or transmission method can be guaranteed completely secure."
    ),
    PolicySection(
        "Your rights and advertising controls",
        "Depending on applicable law, you may request access, correction, deletion, restriction, objection, portability or withdrawal of consent for personal information associated with the app. You can clear local app data in Android settings, delete downloaded files separately, and use device privacy or advertising controls where available."
    ),
    PolicySection(
        "Contact and requests",
        "For privacy questions or access, correction or deletion requests, use Contact Us in the app or email sayadzubair0786@gmail.com and identify Universal Downloader."
    ),
    PolicySection(
        "Policy changes",
        "This policy may change when app functions, legal requirements, store requirements or data practices change. The effective date and consent version will be updated for material changes."
    )
)

private val ZH_POLICY = listOf(
    PolicySection(
        "发布者及适用范围",
        "Universal Downloader 由 Zubair Abbas 开发并发布。本政策说明您使用本应用时相关信息的处理方式。"
    ),
    PolicySection(
        "本应用可能处理的信息",
        "本应用可能处理：由应用生成的安装标识符；推荐来源、推荐码及推荐状态；Android 版本、应用版本、设备型号及基础诊断信息；您为下载操作输入的链接；与广告相关的设备/应用信号，以及在可用情况下的广告标识符；以及您主动在问题报告中提供的支持信息。本应用不会要求精确位置、联系人、短信、通话记录、IMEI 或设备序列号。"
    ),
    PolicySection(
        "处理目的",
        "相关信息用于在支持的情况下提供单个下载、批量下载、频道/播放列表/主页、MP4 和 MP3 功能；管理推荐及应用偏好；诊断故障；响应您主动发送的问题报告；以及支持广告投放、效果衡量、防欺诈和广告频次控制。"
    ),
    PolicySection(
        "广告与 AppLovin MAX",
        "在您同意本隐私政策后，本应用可能初始化 AppLovin MAX，在下载器页面显示横幅广告，并在成功完成下载操作后偶尔显示插页式广告。AppLovin 及其广告合作伙伴可能处理设备/应用信号，以及在可用情况下的广告标识符，用于广告投放、效果衡量、防欺诈和频次控制。v1.5.0 版本默认请求非个性化广告。AppLovin 及广告合作伙伴适用其各自的隐私条款。"
    ),
    PolicySection(
        "用户链接、下载文件及第三方服务",
        "您输入的链接仅用于执行所请求的下载或内容发现操作，并可能发送至相应的第三方媒体服务。下载文件保存在您的设备本地。第三方服务独立运营并适用其自身条款及隐私规则。请仅下载您拥有或已获授权保存的内容。"
    ),
    PolicySection(
        "独立应用声明",
        "Universal Downloader 是独立工具，与任何第三方社交媒体或媒体平台均不存在关联、认可、赞助或合作关系。"
    ),
    PolicySection(
        "共享及服务提供方",
        "Universal Downloader 不出售个人信息。仅在执行用户请求的功能、投放和衡量广告、提供支持、遵守法律或防止滥用所必需的情况下，相关信息才可能由媒体来源、广告或技术服务提供方处理。"
    ),
    PolicySection(
        "保存期限及删除",
        "本地设置、安装标识符及推荐状态保存在设备上，直至您清除应用数据或卸载应用。下载文件会保留至您主动删除。广告服务提供方可能依据其自身政策保存广告或效果衡量记录。您自愿发送的支持信息仅在调查请求或履行法律义务所合理需要的期限内保存。"
    ),
    PolicySection(
        "问题报告及支持",
        "只有当您选择通过电子邮件应用打开并发送问题报告时，报告才会发出。报告可能包含您的描述、应用版本、Android 版本、设备型号、安装标识符、推荐来源及基础诊断信息。本应用不会在后台静默上传问题报告。"
    ),
    PolicySection(
        "未成年人隐私",
        "Universal Downloader 并非面向儿童设计的应用。开发者不会通过本应用主动要求儿童提供个人信息。"
    ),
    PolicySection(
        "安全措施",
        "我们采取合理的技术和管理措施，以降低未经授权访问、修改、披露或丢失信息的风险。但任何电子存储或传输方式均无法保证绝对安全。"
    ),
    PolicySection(
        "您的权利与广告控制",
        "根据适用法律，您可能有权请求查询、更正、删除、限制处理、提出异议、数据可携带或撤回同意。您可在 Android 设置中清除本地应用数据、另行删除下载文件，并在可用情况下使用设备隐私或广告控制。"
    ),
    PolicySection(
        "联系方式及权利请求",
        "如有隐私问题，或需要提出查询、更正或删除请求，请使用应用内“Contact Us”，或发送邮件至 sayadzubair0786@gmail.com，并注明 Universal Downloader。"
    ),
    PolicySection(
        "本政策的变更",
        "当应用功能、法律要求、应用商店要求或数据处理方式发生变化时，本政策可能更新。如有重要变更，生效日期及同意版本将同步更新。"
    )
)

fun hasAcceptedPrivacyPolicy(context: Context): Boolean =
    context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
        .getInt(PRIVACY_ACCEPTED_VERSION, 0) >= CURRENT_POLICY_VERSION

private fun isSimplifiedChineseLocale(): Boolean {
    val locale = Locale.getDefault()
    val tag = locale.toLanguageTag().lowercase()
    return locale.language.equals("zh", true) &&
        (locale.country.equals("CN", true) || locale.country.equals("SG", true) || "hans" in tag || locale.country.isBlank())
}

private fun privacyUrl(chinese: Boolean): String = if (chinese) PRIVACY_URL_ZH_CN else PRIVACY_URL_EN

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
        mutableStateOf(isSimplifiedChineseLocale())
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
                    "首次使用前，请阅读以下隐私政策。只有在您明确同意后，下载功能和广告服务才会启用。"
                else
                    "Before using the app for the first time, please read this Privacy Policy. Download features and advertising services are enabled only after you explicitly agree.",
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
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl(chinese))))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (chinese) "阅读隐私政策" else "Read Privacy Policy")
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
                Text(if (chinese) "拒绝并退出" else "Decline & Exit")
            }
        }
    }
}

@Composable
fun PrivacyPolicyPage() {
    val context = LocalContext.current
    var chinese by rememberSaveable {
        mutableStateOf(isSimplifiedChineseLocale())
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
            if (chinese) "更新日期：2026年9月18日" else "Last updated: September 18, 2026",
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
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl(chinese))))
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
