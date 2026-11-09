package uz.skillswap.app.ui.screens

import uz.skillswap.app.ui.i18n.LanguagePicker
import uz.skillswap.app.ui.i18n.Strings
import uz.skillswap.app.ui.i18n.LocalStrings
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
import uz.skillswap.app.ui.components.bottomBarSpace
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
fun ProfileScreen(vm: SkillSwapViewModel, onLoggedOut: () -> Unit, onImpact: () -> Unit) {
    val user by vm.currentUser.collectAsStateWithLifecycle()
    val learning by vm.learning.collectAsStateWithLifecycle()
    val teaching by vm.teaching.collectAsStateWithLifecycle()
    val myOffers by vm.myOffers.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val name = user?.fullName ?: ""
    val tr = LocalStrings.current
    val lang by vm.lang.collectAsStateWithLifecycle()

    val learned = learning.count { it.status == STATUS_DONE }
    val taught = teaching.count { it.status == STATUS_DONE }
    val done = learned + taught
    val level = memberLevel(done)
    val reviewCount = myOffers.sumOf { it.reviewCount }
    val reputation = weightedRating(myOffers.map { it.rating to it.reviewCount })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomBarSpace())) {
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
            Text(if (bio.isBlank()) tr.noBio else bio,
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(10.dp))
            Row {
                Pill(tr.editProfile, false, { editing = true },
                    idleBg = Color.White.copy(alpha = 0.2f), idleFg = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(myOffers.size, tr.statTeaching)
                StatCard(learned, tr.statLearned)
                StatCard(taught, tr.statTaught)
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(tr.reputation)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (reviewCount == 0) tr.noReviewsShort else "★ $reputation",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (reviewCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else Gold,
                            modifier = Modifier.weight(1f))
                        if (reviewCount > 0) {
                            Text(tr.fromReviews(reviewCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(tr.memberLevel)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (level == "Newcomer") "🌱" else levelMedal(level), fontSize = 30.sp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(tr.level(level), style = MaterialTheme.typography.titleMedium)
                            Text(tr.finishedLessons(done),
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
                        Text(tr.toNextLevel(done, target),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(tr.topLevel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            wallet?.let { w ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionTitle(tr.timeBank)
                        Row(verticalAlignment = Alignment.Bottom) {
                            CountUpText(w.balance, MaterialTheme.typography.displaySmall, Gold)
                            Text(tr.creditsWord, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Text(tr.timeBankRule,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        w.history.take(5).forEach { h ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(walletLabel(h, tr), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(if (h.amount > 0) "+${h.amount}" else "${h.amount}",
                                    style = MaterialTheme.typography.labelLarge, color = if (h.amount > 0) Green else Red)
                            }
                        }
                    }
                }
            }

            // what everybody achieved together
            AppCard(Modifier.fillMaxWidth(), onClick = onImpact) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🌍", fontSize = 30.sp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr.communityImpact, style = MaterialTheme.typography.titleMedium)
                        Text(tr.communityImpactSub,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = Gold)
                }
            }

            // English · Oʻzbekcha · Русский: the whole app switches at once
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("🌐 " + tr.language)
                    LanguagePicker(lang, { vm.setLanguage(it) })
                }
            }

            OutlinedButton(
                onClick = { vm.logout(); onLoggedOut() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Red),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(tr.logOut, color = Red) }
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
    val tr = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr.editProfile) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr.fullName) },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bio, { bio = it }, label = { Text(tr.shortBio) },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, bio) { e -> error = e } }) { Text(tr.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr.close) } }
    )
}

private fun walletLabel(h: WalletLine, tr: Strings): String = when (h.reason) {
    "welcome" -> tr.walletWelcome
    "booked" -> tr.walletBooked(h.skillName)
    "refund" -> tr.walletRefund(h.skillName)
    "taught" -> tr.walletTaught(h.skillName)
    else -> h.reason
}

