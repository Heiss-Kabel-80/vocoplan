package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class ReminderOption(
    val label: String,
    val minutes: Int
)

@Composable
fun ReminderSelector(
    selectedMinutes: Int,
    onReminderSelected: (minutes: Int, label: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    var isCustomExpanded by remember { mutableStateOf(false) }
    var customDays by remember { mutableFloatStateOf(0f) }
    var customHours by remember { mutableFloatStateOf(1f) }

    val quickOptions = remember {
        listOf(
            ReminderOption("1 Std.", 60),
            ReminderOption("2 Std.", 120),
            ReminderOption("3 Std.", 180),
            ReminderOption("1 Tag", 1440),
            ReminderOption("2 Tage", 2880),
            ReminderOption("3 Tage", 4320),
            ReminderOption("1 Woche", 10080)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reminder_selector_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header with title and expand toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Erinnerung",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary
                        )
                    )
                }

                TextButton(
                    onClick = { isCustomExpanded = !isCustomExpanded },
                    modifier = Modifier.testTag("toggle_custom_reminder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Individuell",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.primary
                        )
                    )
                    Icon(
                        imageVector = if (isCustomExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Selection Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickOptions.forEach { option ->
                    val isSelected = selectedMinutes == option.minutes && !isCustomExpanded
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            isCustomExpanded = false
                            onReminderSelected(option.minutes, "${option.label} vorher")
                        },
                        label = {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Expandable custom slider area for Days and Hours
            AnimatedVisibility(
                visible = isCustomExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    val daysInt = customDays.roundToInt()
                    val hoursInt = customHours.roundToInt()

                    // Days slider (0 to 30)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tage:",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (daysInt == 1) "1 Tag" else "$daysInt Tage",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        )
                    }

                    Slider(
                        value = customDays,
                        onValueChange = {
                            customDays = it
                            val newDays = it.roundToInt()
                            val newHours = customHours.roundToInt()
                            val totalMins = (newDays * 24 * 60) + (newHours * 60)
                            val label = buildCustomLabel(newDays, newHours)
                            onReminderSelected(totalMins, label)
                        },
                        valueRange = 0f..30f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth().testTag("custom_days_slider")
                    )

                    // Hours slider (0 to 23)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stunden:",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (hoursInt == 1) "1 Std." else "$hoursInt Std.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        )
                    }

                    Slider(
                        value = customHours,
                        onValueChange = {
                            customHours = it
                            val newDays = customDays.roundToInt()
                            val newHours = it.roundToInt()
                            val totalMins = (newDays * 24 * 60) + (newHours * 60)
                            val label = buildCustomLabel(newDays, newHours)
                            onReminderSelected(totalMins, label)
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier.fillMaxWidth().testTag("custom_hours_slider")
                    )
                }
            }
        }
    }
}

private fun buildCustomLabel(days: Int, hours: Int): String {
    return when {
        days == 0 && hours == 0 -> "Zur Terminzeit"
        days == 0 -> if (hours == 1) "1 Std. vorher" else "$hours Std. vorher"
        hours == 0 -> if (days == 1) "1 Tag vorher" else "$days Tage vorher"
        else -> "$days T. und $hours Std. vorher"
    }
}
