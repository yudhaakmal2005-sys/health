package id.sehati.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.theme.*

/** Lebar konten maksimum agar nyaman di tablet; gutter 16–20dp di ponsel. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    horizontal: Int = 20,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = horizontal.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, modifier = Modifier.semantics { })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        trailing?.invoke()
    }
}

@Composable
fun SehatiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = CardWhite,
    border: Color = BorderColor,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val base = modifier.fillMaxWidth().animateContentSize()
    val mod = if (onClick != null) base.pressScale(source).clip(shape).clickable(
        interactionSource = source, indication = null, role = Role.Button, onClick = onClick,
    ) else base
    Surface(mod, shape = shape, color = container, border = BorderStroke(1.dp, border), tonalElevation = 0.dp) {
        Column(Modifier.padding(contentPadding.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    tag: String = "",
) {
    Button(
        onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        else {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, tag: String = "") {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryDark),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SehatiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    supporting: String? = null,
    suffix: String? = null,
    singleLine: Boolean = true,
    password: Boolean = false,
    enabled: Boolean = true,
    tag: String = "",
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        isError = error != null, enabled = enabled, singleLine = singleLine,
        supportingText = { val t = error ?: supporting; if (t != null) Text(t) },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = BorderColor, focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    tagPrefix: String = "chip",
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${tagPrefix}_${label(o)}"),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryLight, selectedLabelColor = PrimaryDark),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChips(options: List<T>, selected: Set<T>, onToggle: (T) -> Unit, label: (T) -> String, tagPrefix: String = "mchip") {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${tagPrefix}_${label(o)}"),
                leadingIcon = if (o in selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) } } else null,
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryLight, selectedLabelColor = PrimaryDark),
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit, tag: String = "") {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, background: Color, size: Int = 44, contentDescription: String? = null) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun StatusPill(text: String, color: Color, background: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(100.dp), color = background) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = TextPrimary) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.weight(0.5f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(0.5f))
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String = "Batal", tag: String = "confirm_dialog") {
    AlertDialog(
        onDismissRequest = onDismiss, modifier = Modifier.testTag(tag),
        title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_confirm")) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(dismiss) } },
        shape = RoundedCornerShape(24.dp), containerColor = CardWhite,
    )
}
