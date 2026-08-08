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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.johnstocktoniv.reminders.alarm.isRecurring
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.database.dateFormatter
import net.johnstocktoniv.reminders.database.timeFormatter
import net.johnstocktoniv.reminders.ui.theme.OnSuccessContainer
import net.johnstocktoniv.reminders.ui.theme.SuccessContainer
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ReminderListItem(
    reminderWithSchedules: ReminderWithSchedules,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    showDescription: Boolean = true,
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
        reminder.date.atTime(reminder.effectiveTime()).isBefore(now)
    // A one-off (pinned-year) reminder has schedules too — just none producing a match beyond
    // its own instant — so this checks for an actual future occurrence rather than merely
    // "has any schedule" to avoid showing a repeat icon on something that will only ever fire once.
    val showsRecurringIcon = isRecurring(reminderWithSchedules.schedules, reminder.date.atTime(reminder.effectiveTime()))
    val scope = rememberCoroutineScope()
    val dismissThreshold = 0.25f
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState(
        positionalThreshold = { d -> dismissThreshold * d }
    )
    // SwipeToDismissBoxState.targetValue picks the geometrically closest anchor, which flips at
    // a fixed 50% of the item's width regardless of positionalThreshold (that value only feeds
    // the release/fling decision, not the live-drag target) — so the background's own reveal has
    // to track raw offset against this item's measured width to honor a threshold other than 50%.
    var itemWidthPx by remember { mutableIntStateOf(0) }

    SwipeToDismissBox(
        state = swipeToDismissBoxState,
        modifier = modifier.fillMaxWidth()
                           .padding(horizontal = 9.dp, vertical = 4.dp)
                           .clip(RoundedCornerShape(16.dp))
                           .background(MaterialTheme.colorScheme.surfaceVariant)
                           .onSizeChanged { itemWidthPx = it.width },
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
            val offsetPx = if (swipeToDismissBoxState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                0f
            } else {
                swipeToDismissBoxState.requireOffset()
            }
            val swipeFraction = if (itemWidthPx > 0) abs(offsetPx) / itemWidthPx else 0f
            val revealedDirection = if (swipeFraction >= dismissThreshold) {
                swipeToDismissBoxState.dismissDirection
            } else {
                SwipeToDismissBoxValue.Settled
            }
            when (revealedDirection) {
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
                               .padding(horizontal = 14.dp, vertical = 9.dp)
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
                if (showsRecurringIcon) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Recurring",
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showDescription && reminder.description != "") {
                Text(reminder.description)
            }
        }
    }
}