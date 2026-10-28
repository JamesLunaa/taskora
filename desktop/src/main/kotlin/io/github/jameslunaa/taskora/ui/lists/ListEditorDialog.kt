package io.github.jameslunaa.taskora.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.cancel
import io.github.jameslunaa.taskora.resources.create
import io.github.jameslunaa.taskora.resources.edit_list
import io.github.jameslunaa.taskora.resources.list_color
import io.github.jameslunaa.taskora.resources.list_name
import io.github.jameslunaa.taskora.resources.new_list
import io.github.jameslunaa.taskora.resources.save
import io.github.jameslunaa.taskora.ui.theme.NightGround
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import io.github.jameslunaa.taskora.ui.theme.listColor
import org.jetbrains.compose.resources.stringResource

/** Creates a list when [isNew], otherwise edits one. */
@Composable
fun ListEditorDialog(
    isNew: Boolean,
    initialName: String,
    initialColor: Int,
    onConfirm: (name: String, color: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }
    val focusRequester = remember { FocusRequester() }
    val confirm = {
        if (name.isNotBlank()) {
            onConfirm(name.trim(), color)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) Res.string.new_list else Res.string.edit_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text(stringResource(Res.string.list_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ListColors.forEach { option ->
                        ColorSwatch(color = option, selected = option == color, onClick = { color = option })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = name.isNotBlank()) {
                Text(stringResource(if (isNew) Res.string.create else Res.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(Res.string.list_color)
    // Glyphs are night-ground on dark tiles and white on day tiles.
    val glyph = if (TaskoraTheme.colors.isDark) NightGround else Color.White
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(listColor(color))
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = glyph)
    }
}
