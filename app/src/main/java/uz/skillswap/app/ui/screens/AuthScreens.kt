package uz.skillswap.app.ui.screens

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

@Composable
fun LoginScreen(vm: SkillSwapViewModel, onLoggedIn: () -> Unit, onRegisterClick: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by rememberSaveable { mutableStateOf(false) }
    val tr = LocalStrings.current
    val lang by vm.lang.collectAsStateWithLifecycle()

    AuthLayout(title = tr.welcomeBack, subtitle = tr.loginSubtitle) {
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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
            Text("🔄", fontSize = 44.sp)
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
