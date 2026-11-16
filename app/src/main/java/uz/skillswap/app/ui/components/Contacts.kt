package uz.skillswap.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uz.skillswap.app.ui.i18n.LocalStrings

/** Telegram's brand blue, for the "Continue with Telegram" and "Message on Telegram" buttons. */
val TelegramBlue = Color(0xFF2AABEE)

private const val TELEGRAM_APP = "org.telegram.messenger"

/**
 * Opens a t.me link: in the Telegram app when it is installed, otherwise in whatever can open a web link
 * (a browser, or another Telegram client). Returns false when nothing on the phone can open it.
 */
fun openInTelegram(context: Context, url: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(Intent(view).setPackage(TELEGRAM_APP))
        true
    } catch (e: Exception) {                 // Telegram is not installed
        try {
            context.startActivity(view)
            true
        } catch (e2: Exception) {
            false
        }
    }
}

/** Opens the chat with a person: https://t.me/<username>. */
fun messageOnTelegram(context: Context, telegramUsername: String): Boolean =
    openInTelegram(context, "https://t.me/" + Uri.encode(telegramUsername.removePrefix("@")))

/** Opens the phone's dialer with the number typed in (the user presses the call button; no permission needed). */
fun dialPhone(context: Context, phone: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

/**
 * One-tap contact. The SERVER decides what may be seen: when it hid the contacts, [telegramUsername] and [phone]
 * are null and [hidden] is true, so this can only show the lock line. Nothing is drawn when there is nothing to show.
 */
@Composable
fun ContactButtons(telegramUsername: String?, phone: String?, hidden: Boolean, modifier: Modifier = Modifier) {
    val tr = LocalStrings.current
    val context = LocalContext.current
    if (hidden) {
        Text(tr.contactsLocked, modifier = modifier, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val telegram = telegramUsername?.takeIf { it.isNotBlank() }
    val number = phone?.takeIf { it.isNotBlank() }
    if (telegram == null && number == null) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (telegram != null) {
            Button(
                onClick = { messageOnTelegram(context, telegram) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue, contentColor = Color.White)
            ) {
                AppIcon("plane", size = 18.dp, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(tr.messageOnTelegram, maxLines = 1)
            }
        }
        if (number != null) {
            OutlinedButton(onClick = { dialPhone(context, number) }, shape = RoundedCornerShape(14.dp)) {
                Text(tr.call, maxLines = 1)
            }
        }
    }
}
