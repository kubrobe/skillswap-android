package uz.skillswap.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.skillswap.app.data.STATUS_DONE
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.components.CountUpText
import uz.skillswap.app.ui.components.Gold
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.data.remote.WalletLine
import uz.skillswap.app.ui.components.GradientHeader
import uz.skillswap.app.ui.components.Pill
import uz.skillswap.app.ui.components.Red
import uz.skillswap.app.ui.components.SectionTitle
import uz.skillswap.app.ui.components.levelColor
import uz.skillswap.app.ui.components.levelMedal
import uz.skillswap.app.util.memberLevel
import uz.skillswap.app.util.nextLevelTarget
import uz.skillswap.app.util.weightedRating

@Composable
fun ProfileScreen(vm: SkillSwapViewModel, onLoggedOut: () -> Unit) {
    val user by vm.currentUser.collectAsStateWithLifecycle()
    val learning by vm.learning.collectAsStateWithLifecycle()
    val teaching by vm.teaching.collectAsStateWithLifecycle()
    val myOffers by vm.myOffers.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val name = user?.fullName ?: ""

    val learned = learning.count { it.status == STATUS_DONE }
    val taught = teaching.count { it.status == STATUS_DONE }
    val done = learned + taught
    val level = memberLevel(done)
    val reviewCount = myOffers.sumOf { it.reviewCount }
    val reputation = weightedRating(myOffers.map { it.rating to it.reviewCount })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f))
            val bio = user?.bio ?: ""
            Text(if (bio.isBlank()) "No bio yet. Tell others what you like to teach." else bio,
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(10.dp))
            Row {
                Pill("Edit profile", false, { editing = true },
                    idleBg = Color.White.copy(alpha = 0.2f), idleFg = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(myOffers.size, "teaching")
                StatCard(learned, "learned")
                StatCard(taught, "taught")
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Reputation")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (reviewCount == 0) "No reviews yet" else "★ $reputation",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (reviewCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else Gold,
                            modifier = Modifier.weight(1f))
                        if (reviewCount > 0) {
                            Text("from $reviewCount review${if (reviewCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Member level")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (level == "Newcomer") "🌱" else levelMedal(level), fontSize = 30.sp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(level, style = MaterialTheme.typography.titleMedium)
                            Text("$done finished lesson${if (done == 1) "" else "s"} (learned + taught)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val target = nextLevelTarget(done)
                    if (target != null) {
                        val progress by animateFloatAsState(
                            (done.toFloat() / target).coerceIn(0f, 1f), tween(600), label = "levelProgress"
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = if (level == "Newcomer") MaterialTheme.colorScheme.primary else levelColor(level),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text("$done of $target lessons to reach the next level",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Top level reached. Thank you for sharing your skills!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            wallet?.let { w ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionTitle("🪙 Time bank")
                        Row(verticalAlignment = Alignment.Bottom) {
                            CountUpText(w.balance, MaterialTheme.typography.displaySmall, Gold)
                            Text("  credits", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Text("Teach 1 hour → earn 1 credit · Learn 1 hour → spend 1. No money needed.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        w.history.take(5).forEach { h ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(walletLabel(h), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(if (h.amount > 0) "+${h.amount}" else "${h.amount}",
                                    style = MaterialTheme.typography.labelLarge, color = if (h.amount > 0) Green else Red)
                            }
                        }
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("Account")
                    Text(if (sync.online) "☁️ Online account" else "📱 Saved on this phone only",
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (sync.online) "Synced with the SkillSwap server. The same e-mail and password work on the website."
                        else "Works without internet. Log in while online to use your real SkillSwap account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = { vm.logout(); onLoggedOut() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Red),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Log out", color = Red) }
        }
    }

    if (editing) {
        EditProfileDialog(
            startName = name, startBio = user?.bio ?: "",
            onSave = { n, b, onError -> vm.updateProfile(n, b) { err -> if (err == null) editing = false else onError(err) } },
            onDismiss = { editing = false }
        )
    }
}

@Composable
private fun RowScope.StatCard(value: Int, label: String) {
    AppCard(Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            CountUpText(value, MaterialTheme.typography.headlineMedium, MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditProfileDialog(
    startName: String, startBio: String,
    onSave: (String, String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(startName) }
    var bio by remember { mutableStateOf(startBio) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bio, { bio = it }, label = { Text("Short bio") },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, bio) { e -> error = e } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun walletLabel(h: WalletLine): String = when (h.reason) {
    "welcome" -> "🎁 Welcome gift"
    "booked" -> "📚 Booked ${h.skillName}"
    "refund" -> "↩️ Refund · ${h.skillName}"
    "taught" -> "🧑‍🏫 Taught ${h.skillName}"
    else -> h.reason
}

