package dev.andrew.plinth.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * The long-press menu, anchored to whatever row or tile hosts it. A drop-down rather
 * than a sheet: the action belongs to the item you pressed, and on a stand-mounted
 * tablet a sheet would cover the library it is talking about.
 */
@Composable
fun ItemMenu(
    expanded: Boolean,
    actions: List<MenuAction>,
    onDismissRequest: () -> Unit,
) {
    if (actions.isEmpty()) return
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = PlinthColors.Plate,
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.testTag("item-menu"),
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = action.label,
                        color = PlinthColors.Text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    // Close first: the action may navigate, and a menu left over the
                    // next screen would belong to a row that is gone.
                    onDismissRequest()
                    action.onClick()
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("menu-${action.key}"),
            )
        }
    }
}
