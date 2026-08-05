package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.database.dateFormatter
import net.johnstocktoniv.reminders.database.timeFormatter
import net.johnstocktoniv.reminders.ui.theme.OnSuccessContainer
import net.johnstocktoniv.reminders.ui.theme.SuccessContainer
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ReminderListItem(
    reminderWithSchedules: ReminderWithSchedules,
    defaultReminderTime: LocalTime,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    val reminder = reminderWithSchedules.reminder
    // Composition only runs again when this item's own state changes, but "is this reminder
    // overdue" also depends on wall-clock time passing with nothing else about the item
    // changing — so without this ticker, an item shown before its due time never flips to
    // "Overdue" while the screen just sits open.
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(30_000.milliseconds)
            value = LocalDateTime.now()
        }
    }
    val isOverdue = !reminder.complete &&
        reminder.date.atTime(reminder.effectiveTime(defaultReminderTime)).isBefore(now)
    val scope = rememberCoroutineScope()
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState(
        positionalThreshold = { d -> 0.5f * d }
    )

    SwipeToDismissBox(
        state = swipeToDismissBoxState,
        modifier = modifier.fillMaxWidth()
                           .padding(horizontal = 9.dp, vertical = 4.dp)
                           .clip(RoundedCornerShape(16.dp))
                           .background(MaterialTheme.colorScheme.surfaceVariant),
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onDelete()
                SwipeToDismissBoxValue.EndToStart -> {
                    onComplete()
                    scope.launch { swipeToDismissBoxState.snapTo(SwipeToDismissBoxValue.Settled) }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            when (swipeToDismissBoxState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove item",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .wrapContentSize(Alignment.CenterStart)
                            .padding(12.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Mark Complete",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SuccessContainer)
                            .wrapContentSize(Alignment.CenterEnd)
                            .padding(12.dp),
                        tint = OnSuccessContainer
                    )
                }
                SwipeToDismissBoxValue.Settled -> {}
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                               .padding(9.dp)
                               .alpha(if (reminder.complete) 0.25f else 1.0f)
        ) {
            if (isOverdue) {
                Text(
                    "Overdue",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            val dateTimeColor = if (isOverdue) MaterialTheme.colorScheme.error else Color.Unspecified
            val dateTimeText = reminder.date.format(dateFormatter) +
                (reminder.time?.let { " at ${it.format(timeFormatter)}" } ?: "")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = dateTimeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = dateTimeColor,
                    maxLines = 1
                )
                if (reminderWithSchedules.schedules.isNotEmpty()) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Recurring",
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (reminder.description != "") {
                Text(reminder.description)
            }
        }
    }
}