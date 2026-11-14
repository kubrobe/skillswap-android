package uz.skillswap.app.ui.screens

import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.components.IconTile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.skillswap.app.ui.i18n.LanguagePicker
import uz.skillswap.app.ui.i18n.LocalStrings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.GradientHeader
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: SkillSwapViewModel, onLoggedIn: () -> Unit, onRegisterClick: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by rememberSaveable { mutableStateOf(false) }
    val tr = LocalStrings.current
    val lang by vm.lang.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()      // carries "Your session expired" after a forced log-out

    AuthLayout(title = tr.welcomeBack, subtitle = tr.loginSubtitle) {
        GoogleSignIn(vm, onError = { error = it }, onSignedIn = onLoggedIn)
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text(tr.email) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text(tr.password) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        (error ?: sync.problem)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                loading = true
                vm.login(email, password) { err ->
                    loading = false
                    error = err
                    if (err == null) onLoggedIn()
                }
            },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (loading) CircularProgressIndicator(Modifier.height(24.dp), color = Color.White) else Text(tr.logIn)
        }
        TextButton(onClick = onRegisterClick) { Text(tr.noAccount) }
        if (loading) {
            Text(tr.wakingServer,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(tr.demoAccount, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr.sameAccount, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LanguagePicker(lang, { vm.setLanguage(it) }, Modifier.padding(top = 8.dp))
    }
}

@Composable
fun RegisterScreen(vm: SkillSwapViewModel, onRegistered: () -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by rememberSaveable { mutableStateOf(false) }
    val tr = LocalStrings.current

    AuthLayout(title = tr.createAccount, subtitle = tr.takes20s) {
        GoogleSignIn(vm, onError = { error = it }, onSignedIn = onRegistered)
        OutlinedTextField(name, { name = it }, label = { Text(tr.fullName) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text(tr.email) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, label = { Text(tr.passwordRule) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                loading = true
                vm.register(name, email, password) { err ->
                    loading = false
                    error = err
                    if (err == null) onRegistered()
                }
            },
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (loading) CircularProgressIndicator(Modifier.height(24.dp), color = Color.White) else Text(tr.register)
        }
        TextButton(onClick = onBack) { Text(tr.haveAccount) }
    }
}

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Spacer(Modifier.height(24.dp))
            IconTile("swap", "", size = 60.dp, color = GoldPrimary)
            Spacer(Modifier.height(10.dp))
            Text("SkillSwap", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(LocalStrings.current.slogan, style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(28.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/**
 * "Continue with Google" + an "or" line. Shown only when the server has a Google client id.
 * Android's Credential Manager shows the Google account sheet; Google returns a signed ID token for our
 * server's client id; the server checks it and logs in (or creates) the SkillSwap account.
 * The same account works on the website (linked by the Google account and its verified email).
 */
@Composable
private fun GoogleSignIn(vm: SkillSwapViewModel, onError: (String?) -> Unit, onSignedIn: () -> Unit) {
    val clientId by vm.googleClientId.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadGoogleConfig() }       // the server may have been asleep at start-up
    val id = clientId ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    val tr = LocalStrings.current

    OutlinedButton(
        onClick = {
            busy = true
            onError(null)
            scope.launch {
                val token = googleIdToken(context, id, onError)
                if (token == null) { busy = false; return@launch }
                vm.loginWithGoogle(token) { err ->
                    busy = false
                    onError(err)
                    if (err == null) onSignedIn()
                }
            }
        },
        enabled = !busy,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else {
            GoogleG(Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr.continueGoogle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(tr.orWord, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** Opens the Google account sheet; returns the signed ID token, or null (cancelled / error shown). */
private suspend fun googleIdToken(context: Context, serverClientId: String, onError: (String?) -> Unit): String? {
    val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            onError("Google sign-in failed. Please try again.")
            null
        }
    } catch (e: GetCredentialCancellationException) {
        null                                              // the person closed the sheet: no error
    } catch (e: GetCredentialException) {
        onError(e.message ?: "Google sign-in failed. Please try again.")
        null
    } catch (e: Exception) {
        onError(e.message ?: "Google sign-in failed. Please try again.")
        null
    }
}

/** Google's four-colour "G", drawn with arcs (no image file needed). */
@Composable
private fun GoogleG(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = w * 0.2f)
        val inset = w * 0.1f
        val box = Size(w - 2 * inset, w - 2 * inset)
        val topLeft = Offset(inset, inset)
        drawArc(Color(0xFFEA4335), 200f, 110f, false, topLeft, box, style = stroke)   // red, top
        drawArc(Color(0xFFFBBC05), 135f, 65f, false, topLeft, box, style = stroke)    // yellow, left
        drawArc(Color(0xFF34A853), 45f, 90f, false, topLeft, box, style = stroke)     // green, bottom
        drawArc(Color(0xFF4285F4), 0f, 45f, false, topLeft, box, style = stroke)      // blue, right
        drawRect(Color(0xFF4285F4), topLeft = Offset(w * 0.5f, w * 0.4f), size = Size(w * 0.4f, w * 0.2f))   // the bar
    }
}
