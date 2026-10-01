package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CalendarEventData

@Composable
fun CentralDisplayWindow(
    eventData: CalendarEventData,
    displayText: String,
    onTextChanged: (String) -> Unit,
    onClear: () -> Unit,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme

    val isEmpty = eventData.title.isBlank() && eventData.dateTime == null && eventData.location.isBlank() && eventData.notes.isBlank()
    var isEditMode by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("central_display_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header bar of the display window
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Terminkarte",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary
                        )
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isEmpty) {
                        IconButton(
                            onClick = { isEditMode = !isEditMode },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("toggle_edit_mode_button")
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "Bearbeitung abschließen" else "Text bearbeiten",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(displayText))
                                onCopied()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("copy_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Text kopieren",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                isEditMode = false
                                onClear()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("clear_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Termin leeren",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main display container (min 230dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 230.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(
                        width = 1.dp,
                        color = colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(16.dp),
                contentAlignment = if (isEmpty) Alignment.Center else Alignment.TopStart
            ) {
                AnimatedContent(
                    targetState = Pair(isEmpty, isEditMode),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "window_content"
                ) { (empty, editMode) ->
                    when {
                        empty -> {
                            // Modern empty card state with hint icon
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Bereit für deinen Termin",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Halte die Taste gedrückt und sprich deinen Termin ganz natürlich ein.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    ),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        editMode -> {
                            // Direct edit mode in clean sans-serif typography (no terminal/monospace look)
                            BasicTextField(
                                value = displayText,
                                onValueChange = onTextChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("central_display_textfield"),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(colorScheme.primary)
                            )
                        }

                        else -> {
                            // Structured modern card view with icons
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isEditMode = true },
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Anlass (Titel)
                                StructuredRow(
                                    icon = Icons.Default.Event,
                                    label = "Anlass",
                                    value = eventData.title.ifBlank { "Ohne Titel" },
                                    isPrimary = true
                                )

                                // 2. Zeitpunkt (Datum & Uhrzeit formatiert)
                                val dateFormatted = if (eventData.dateTime != null) {
                                    val formatter = java.time.format.DateTimeFormatter.ofPattern(
                                        "EEEE, dd.MM.yyyy 'um' HH:mm 'Uhr'",
                                        java.util.Locale.GERMAN
                                    )
                                    eventData.dateTime.format(formatter)
                                } else {
                                    "Kein Zeitpunkt erkannt"
                                }
                                StructuredRow(
                                    icon = Icons.Default.Schedule,
                                    label = "Zeitpunkt",
                                    value = dateFormatted
                                )

                                // 3. Ort
                                if (eventData.location.isNotBlank()) {
                                    StructuredRow(
                                        icon = Icons.Default.Place,
                                        label = "Ort",
                                        value = eventData.location
                                    )
                                }

                                // 4. Erinnerungszeitpunkt
                                StructuredRow(
                                    icon = Icons.Default.Alarm,
                                    label = "Erinnerung",
                                    value = eventData.reminderLabel.ifBlank { "1 Std. vorher" }
                                )

                                // 5. Notizen / Volltext
                                if (eventData.notes.isNotBlank()) {
                                    StructuredRow(
                                        icon = Icons.Default.Notes,
                                        label = "Notizen",
                                        value = eventData.notes
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredRow(
    icon: ImageVector,
    label: String,
    value: String,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPrimary) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            )
            Text(
                text = value,
                style = if (isPrimary) {
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                } else {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = colorScheme.onSurface
                    )
                }
            )
        }
    }
}
