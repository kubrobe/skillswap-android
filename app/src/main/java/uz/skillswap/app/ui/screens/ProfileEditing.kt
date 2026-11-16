package uz.skillswap.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uz.skillswap.app.data.User
import uz.skillswap.app.data.languageList
import uz.skillswap.app.data.remote.ProfileEdit
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.ui.components.Pill
import uz.skillswap.app.ui.components.Red
import uz.skillswap.app.ui.i18n.LocalStrings
import uz.skillswap.app.ui.i18n.Strings
import uz.skillswap.app.util.Validators

/** What the line under the @username field says. */
private enum class NameState { EMPTY, SAME, CHECKING, FREE, UNKNOWN, TAKEN, SHORT, LONG, START, CHARS, RESERVED }

private val BLOCKING = setOf(NameState.TAKEN, NameState.SHORT, NameState.LONG, NameState.START, NameState.CHARS, NameState.RESERVED)

private fun nameMessage(state: NameState, tr: Strings): String = when (state) {
    NameState.EMPTY, NameState.SAME, NameState.UNKNOWN -> tr.usernameRules
    NameState.CHECKING -> tr.usernameChecking
    NameState.FREE -> tr.usernameFree
    NameState.TAKEN -> tr.usernameTaken
    NameState.SHORT -> tr.usernameTooShort
    NameState.LONG -> tr.usernameTooLong
    NameState.START -> tr.usernameStartLetter
    NameState.CHARS -> tr.usernameBadChars
    NameState.RESERVED -> tr.usernameReserved
}

/**
 * The @username field with a live check: the rules are checked on the phone at once, and 400 ms after the last
 * key press the server is asked whether the name is free (green ✓ / red ✗).
 * [current] is my saved username; typing it again needs no check. [onOk] says whether saving makes sense now.
 */
@Composable
fun UsernameField(
    vm: SkillSwapViewModel, value: String, onValueChange: (String) -> Unit,
    current: String, onOk: (Boolean) -> Unit, modifier: Modifier = Modifier
) {
    val tr = LocalStrings.current
    val clean = Validators.cleanUsername(value)
    var state by remember { mutableStateOf(NameState.EMPTY) }

    LaunchedEffect(clean, current) {
        val first = when {
            clean.isEmpty() -> NameState.EMPTY
            clean == current -> NameState.SAME
            else -> when (Validators.usernameProblem(clean)) {
                "short" -> NameState.SHORT
                "long" -> NameState.LONG
                "start" -> NameState.START
                "chars" -> NameState.CHARS
                "reserved" -> NameState.RESERVED
                else -> NameState.CHECKING
            }
        }
        state = first
        onOk(first !in BLOCKING)
        if (first == NameState.CHECKING) {
            delay(400)                                  // typing again cancels this and starts over (debounce)
            val answer = vm.usernameAvailable(clean)
            val checked = when {
                answer == null -> NameState.UNKNOWN     // offline: the server checks again when saving
                answer.available -> NameState.FREE
                else -> NameState.TAKEN
            }
            state = checked
            onOk(checked !in BLOCKING)
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(21)) },
        label = { Text(tr.username) },
        prefix = { Text("@") },
        singleLine = true,
        isError = state in BLOCKING,
        supportingText = {
            Text(
                nameMessage(state, tr),
                color = when {
                    state == NameState.FREE -> Green
                    state in BLOCKING -> Red
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * "Pick your @username": shown once, right after the first login, while the account has no username yet.
 * It covers the app (and takes the touches). "Later" closes it for good; the profile can set a username any time.
 */
@Composable
fun PickUsernameScreen(vm: SkillSwapViewModel) {
    val tr = LocalStrings.current
    var text by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = true) { }                      // the Back key must not move the screens underneath

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr.pickUsernameTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(tr.pickUsernameSub, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            UsernameField(vm, text, { text = it; error = null }, current = "", onOk = { ok = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            Button(
                onClick = {
                    saving = true
                    vm.saveUsername(Validators.cleanUsername(text)) { err ->
                        saving = false
                        error = err                       // on success the account has a username and this screen goes away
                    }
                },
                enabled = !saving && ok && Validators.isValidUsername(text),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (saving) CircularProgressIndicator(Modifier.height(24.dp), color = Color.White) else Text(tr.save)
            }
            TextButton(onClick = { vm.skipUsername() }, enabled = !saving) { Text(tr.later) }
        }
    }
}

private val SUGGESTED_LANGUAGES = listOf("Uzbek", "English", "Russian", "Korean")
private val VISIBILITY = listOf("everyone", "matches", "nobody")

/** Edit profile: name, @username, bio, Telegram username, phone, faculty, year, languages and who sees the contacts. */
@Composable
fun ProfileEditor(vm: SkillSwapViewModel, user: User, onDismiss: () -> Unit) {
    val tr = LocalStrings.current
    val savedUsername = user.username ?: ""
    var name by remember { mutableStateOf(user.fullName) }
    var username by remember { mutableStateOf(savedUsername) }
    var usernameOk by remember { mutableStateOf(true) }
    var bio by remember { mutableStateOf(user.bio) }
    var telegram by remember { mutableStateOf(user.telegramUsername ?: "") }
    var phone by remember { mutableStateOf(user.phone ?: "") }
    var faculty by remember { mutableStateOf(user.faculty) }
    var year by remember { mutableStateOf(user.studyYear) }
    var languages by remember { mutableStateOf(user.languageList()) }
    var newLanguage by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(user.contactVisibility ?: "matches") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldShape = RoundedCornerShape(14.dp)

    fun addLanguage() {
        val l = newLanguage.trim().take(30)
        if (l.isNotEmpty() && languages.none { it.equals(l, ignoreCase = true) } && languages.size < 8) languages = languages + l
        newLanguage = ""
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(tr.editProfile) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr.fullName) },
                    singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
                if (user.username != null) {             // an older server has no usernames: the field is left out
                    UsernameField(vm, username, { username = it }, current = savedUsername, onOk = { usernameOk = it })
                }
                OutlinedTextField(bio, { bio = it }, label = { Text(tr.shortBio) },
                    shape = fieldShape, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(telegram, { telegram = it.take(40) }, label = { Text(tr.telegramUsernameLabel) },
                    prefix = { Text("@") }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii))
                OutlinedTextField(phone, { phone = it.take(24) }, label = { Text(tr.phoneLabel) },
                    placeholder = { Text("+998 90 123 45 67") }, singleLine = true, shape = fieldShape,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(faculty, { faculty = it.take(80) }, label = { Text(tr.faculty) },
                    singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())

                Text(tr.studyYear, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("—", year == 0, { year = 0 })
                    (1..4).forEach { n -> Pill(n.toString(), year == n, { year = n }) }
                }

                Text(tr.languagesLabel, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // the usual four, plus anything else this person already added; tap to switch on or off
                    val shown = SUGGESTED_LANGUAGES + languages.filter { l -> SUGGESTED_LANGUAGES.none { it.equals(l, ignoreCase = true) } }
                    shown.forEach { l ->
                        val on = languages.any { it.equals(l, ignoreCase = true) }
                        Pill(l, on, {
                            languages = if (on) languages.filterNot { it.equals(l, ignoreCase = true) }
                            else if (languages.size < 8) languages + l else languages
                        })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newLanguage, { newLanguage = it.take(30) }, label = { Text(tr.addLanguage) },
                        singleLine = true, shape = fieldShape, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addLanguage() }, enabled = newLanguage.isNotBlank()) { Text(tr.add) }
                }

                Text(tr.contactVisibility, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VISIBILITY.forEach { v ->
                        val label = when (v) { "everyone" -> tr.visEveryone; "nobody" -> tr.visNobody; else -> tr.visMatches }
                        Pill(label, visibility == v, { visibility = v })
                    }
                }
                Text(tr.visMatchesHint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    addLanguage()                         // a language typed but not added yet still counts
                    error = when {
                        !usernameOk -> tr.usernameRules
                        !Validators.isValidTelegramUsername(telegram) -> tr.telegramUsernameInvalid
                        !Validators.isValidPhone(phone) -> tr.phoneInvalid
                        else -> null
                    }
                    if (error == null) {
                        saving = true
                        val edit = ProfileEdit(
                            fullName = name.trim(), bio = bio.trim(), username = Validators.cleanUsername(username),
                            telegramUsername = Validators.cleanTelegramUsername(telegram), phone = Validators.cleanPhone(phone),
                            faculty = faculty.trim(), studyYear = year, languages = languages, contactVisibility = visibility
                        )
                        vm.saveProfile(edit) { err ->
                            saving = false
                            if (err == null) onDismiss() else error = err
                        }
                    }
                }
            ) { Text(tr.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(tr.close) } }
    )
}
