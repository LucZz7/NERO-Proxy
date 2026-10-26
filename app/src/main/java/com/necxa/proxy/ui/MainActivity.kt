package com.necxa.proxy.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necxa.proxy.core.FormState
import com.necxa.proxy.core.ProxyType
import com.necxa.proxy.vpn.*
import kotlinx.coroutines.delay

private val Charcoal = Color(0xFF161616)
private val Gray = Color(0xFF8A8A8A)
private val Line = Color(0xFF2A2A2A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color.Black, surface = Charcoal, onBackground = Color.White,
                onSurface = Color.White, primary = Color.White, onPrimary = Color.Black, outline = Line)) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { App() }
            }
        }
    }
}

enum class Screen { DASH, CONFIG, LOGS, ABOUT }

@Composable
fun App(vm: ProxyViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.DASH) }
    BackHandler(enabled = screen != Screen.DASH) { screen = Screen.DASH }
    when (screen) {
        Screen.DASH -> Dashboard(vm) { screen = it }
        Screen.CONFIG -> ConfigScreen(vm) { screen = Screen.DASH }
        Screen.LOGS -> LogsScreen { screen = Screen.DASH }
        Screen.ABOUT -> AboutScreen { screen = Screen.DASH }
    }
}

private fun startVpn(ctx: Context) {
    ContextCompat.startForegroundService(ctx, Intent(ctx, NeroVpnService::class.java).setAction(NeroVpnService.ACTION_START))
}
private fun stopVpn(ctx: Context) {
    ctx.startService(Intent(ctx, NeroVpnService::class.java).setAction(NeroVpnService.ACTION_STOP))
}
private fun launchPrepare(ctx: Context, l: ManagedActivityResultLauncher<Intent, ActivityResult>) {
    val i = VpnService.prepare(ctx)
    if (i == null) startVpn(ctx) else l.launch(i)
}
private fun fmtBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}
private fun fmtDur(ms: Long): String { val s = ms / 1000; return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }

@Composable
fun Dashboard(vm: ProxyViewModel, go: (Screen) -> Unit) {
    val f by vm.form.collectAsState(); val saved by vm.saved.collectAsState()
    val s by VpnState.flow.collectAsState()
    val ctx = LocalContext.current
    val active = s.status == VpnStatus.CONNECTED || s.status == VpnStatus.RECONNECTING
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { if (active) while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) startVpn(ctx)
        else VpnState.update { it.copy(status = VpnStatus.ERROR, message = "VPN permission was denied. NERO Proxy cannot work without it.") }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchPrepare(ctx, vpnLauncher) }

    val onConnect: () -> Unit = {
        when (s.status) {
            VpnStatus.CONNECTED, VpnStatus.RECONNECTING -> stopVpn(ctx)
            VpnStatus.DISCONNECTED, VpnStatus.ERROR ->
                if (!saved) go(Screen.CONFIG)
                else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else launchPrepare(ctx, vpnLauncher)
            else -> {}
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("NERO PROXY", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            IconButton(onClick = { go(Screen.ABOUT) }) { Icon(Icons.Default.Settings, contentDescription = "Settings and about") }
        }
        Spacer(Modifier.height(8.dp))
        Text(s.status.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(if (saved) "${f.type} · ${f.host}:${f.port}" else "No proxy configured", color = Gray)
        if (active) Text(fmtDur(now - s.startedAt), color = Gray, fontFamily = FontFamily.Monospace)
        Button(onClick = onConnect, enabled = s.status != VpnStatus.CONNECTING && s.status != VpnStatus.STOPPING,
            modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(when (s.status) {
                VpnStatus.CONNECTED, VpnStatus.RECONNECTING -> "Disconnect"
                VpnStatus.CONNECTING -> "Connecting…"
                VpnStatus.STOPPING -> "Stopping…"
                else -> if (saved) "Connect" else "Set up proxy"
            }, fontSize = 18.sp)
        }
        s.message?.let { m ->
            Card(colors = CardDefaults.cardColors(containerColor = Charcoal), border = BorderStroke(1.dp, Gray)) {
                Text(m, Modifier.padding(14.dp), fontSize = 14.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Upload", fmtBytes(s.upBytes), "${fmtBytes(s.upSpeed)}/s", Modifier.weight(1f))
            StatCard("Download", fmtBytes(s.downBytes), "${fmtBytes(s.downSpeed)}/s", Modifier.weight(1f))
        }
        Text("Proxy latency: " + (s.latencyMs?.let { "$it ms" } ?: "—") + "   (TCP connect to proxy)", color = Gray, fontSize = 13.sp)
        if (s.status == VpnStatus.CONNECTED)
            Text(if (s.forwardingVerified) "Forwarding verified: traffic is flowing through the proxy."
            else "Connected. Waiting for first traffic to verify forwarding.", color = Gray, fontSize = 13.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Charcoal), border = BorderStroke(1.dp, Line)) {
            Text("TCP only. UDP, QUIC and IPv6 are blocked, never sent directly. DNS is resolved by your proxy.",
                Modifier.padding(14.dp), color = Gray, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { go(Screen.CONFIG) }) { Text("Proxy & test") }
            OutlinedButton(onClick = { go(Screen.LOGS) }) { Text("Logs") }
        }
    }
}

@Composable
private fun StatCard(title: String, total: String, speed: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Charcoal), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Gray, fontSize = 12.sp)
            Text(total, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(speed, color = Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun ConfigScreen(vm: ProxyViewModel, back: () -> Unit) {
    val f by vm.form.collectAsState(); val errs by vm.errors.collectAsState()
    val steps by vm.steps.collectAsState(); val testing by vm.testing.collectAsState()
    var show by remember { mutableStateOf(false) }
    fun set(n: FormState) = vm.update(n)
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = back) { Text("← Back") }
        Text("Proxy configuration", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProxyType.values().forEach { t ->
                if (f.type == t) Button(onClick = { set(f.copy(type = t)) }) { Text(if (t == ProxyType.HTTP) "HTTP" else "SOCKS5") }
                else OutlinedButton(onClick = { set(f.copy(type = t)) }) { Text(if (t == ProxyType.HTTP) "HTTP" else "SOCKS5") }
            }
        }
        OutlinedTextField(f.host, { set(f.copy(host = it)) }, label = { Text("Server host / IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(f.port, { set(f.copy(port = it.filter(Char::isDigit).take(5))) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(f.username, { set(f.copy(username = it)) }, label = { Text("Username (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(f.password, { set(f.copy(password = it)) }, label = { Text("Password (optional)") }, singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(f.testUrl, { set(f.copy(testUrl = it)) }, label = { Text("HTTPS test endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("The endpoint sees your proxy's exit address, not necessarily your phone's.", color = Gray, fontSize = 12.sp)
        errs.forEach { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.save() }) { Text("Save") }
            Button(onClick = { vm.test() }, enabled = !testing) { Text(if (testing) "Testing…" else "Test connection") }
        }
        steps.forEach {
            Text((if (it.ok) "✓ " else "✗ ") + it.name, fontWeight = FontWeight.Medium)
            Text(it.detail, color = Gray, fontSize = 12.sp)
        }
        if (steps.isNotEmpty()) {
            val level = when {
                steps.any { it.name == "End-to-end request" && it.ok } -> "End-to-end test successful (proxy tunnel works)."
                steps.any { it.name == "Tunnel established" && it.ok } -> "Proxy tunnel established; end-to-end request failed."
                steps.any { it.name == "Authentication" && it.ok } -> "Authentication successful."
                steps.any { it.name == "Proxy reachable" && it.ok } -> "Proxy server reachable only. Not proven to work."
                else -> "Proxy not reachable."
            }
            Text(level, fontWeight = FontWeight.SemiBold)
            Text("Full-device VPN forwarding is verified only while connected (see dashboard).", color = Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun LogsScreen(back: () -> Unit) {
    val lines by LogBuffer.flow.collectAsState()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = back) { Text("← Back") }
            TextButton(onClick = { LogBuffer.clear() }) { Text("Clear") }
        }
        if (lines.isEmpty()) Text("No log entries yet.", color = Gray)
        LazyColumn { items(lines.reversed()) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Gray, modifier = Modifier.padding(vertical = 2.dp)) } }
    }
}

@Composable
fun AboutScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = back) { Text("← Back") }
        Text("NERO Proxy 0.2.0", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Routing & DNS", fontWeight = FontWeight.Medium)
        Text("All IPv4 TCP traffic from other apps is sent through your upstream proxy. DNS queries are answered locally with " +
            "placeholder addresses and the real name is resolved by your proxy. UDP, QUIC and IPv6 are dropped (fail-closed). " +
            "This app's own traffic bypasses the VPN. Android does not guarantee complete leak protection and this app does not promise anonymity.",
            color = Gray, fontSize = 13.sp)
        Text("Privacy", fontWeight = FontWeight.Medium)
        Text("No ads, analytics, accounts or servers. Proxy settings are stored on this device; the password is encrypted with an " +
            "Android Keystore key. Logs stay in memory and never contain passwords or visited sites. HTTPS is never intercepted or decrypted.",
            color = Gray, fontSize = 13.sp)
        Text("Open-source licenses", fontWeight = FontWeight.Medium)
        Text("hev-socks5-tunnel (heiher) – MIT, with bundled lwIP (BSD), libyaml (MIT), hev-task-system (MIT). " +
            "AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines – Apache 2.0. See each project's LICENSE file.",
            color = Gray, fontSize = 13.sp)
    }
}
