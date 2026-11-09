package uz.skillswap.app.ui.i18n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.skillswap.app.ui.components.Pill

/** English · Oʻzbekcha · Русский - the whole app switches at once. */
@Composable
fun LanguagePicker(current: Lang, onPick: (Lang) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.entries.forEach { l -> Pill(l.label, l == current, { onPick(l) }) }
    }
}
