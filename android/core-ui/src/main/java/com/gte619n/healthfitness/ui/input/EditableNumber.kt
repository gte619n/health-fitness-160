package com.gte619n.healthfitness.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import com.gte619n.healthfitness.ui.HealthFitnessTheme
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Click-to-edit numeric field, mirroring the web app's pattern. Tap to
 * switch from a static `Text` to an inline `BasicTextField`; commit on
 * focus loss or `Enter`. Parse failure reverts to the prior value (the
 * field is restored to the formatted view of `value`). Clearing the field
 * commits `null`.
 */
@Composable
fun EditableNumber(
    value: Double?,
    onCommit: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimals: Int = 1,
    enabled: Boolean = true,
    placeholder: String = "—",
) {
    var isEditing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value.formatFor(decimals)) }
    val focusRequester = remember { FocusRequester() }

    val displayText = value.formatFor(decimals).ifEmpty { placeholder }

    if (isEditing) {
        Row(
            modifier = modifier
                .background(Hf.colors.canvasMuted, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { new ->
                    // Permit only digits, single dot, and a leading minus.
                    if (new.matches(NUMERIC_DRAFT_REGEX)) draft = new
                },
                textStyle = Hf.type.bodyMd.copy(
                    color = Hf.colors.textPrimary,
                    textAlign = TextAlign.End,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commit(draft, decimals, value, onCommit)
                        isEditing = false
                    },
                ),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && isEditing) {
                            commit(draft, decimals, value, onCommit)
                            isEditing = false
                        }
                    },
            )
            if (suffix != null) {
                Text(
                    text = " $suffix",
                    style = Hf.type.bodySm,
                    color = Hf.colors.textTertiary,
                )
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        Row(
            modifier = modifier
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onTap = {
                        draft = value.formatFor(decimals)
                        isEditing = true
                    })
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayText,
                style = Hf.type.bodyMd,
                color = if (value == null) Hf.colors.textTertiary else Hf.colors.textPrimary,
            )
            if (suffix != null && value != null) {
                Text(
                    text = " $suffix",
                    style = Hf.type.bodySm,
                    color = Hf.colors.textTertiary,
                )
            }
        }
    }
}

private val NUMERIC_DRAFT_REGEX = Regex("^-?\\d*(\\.\\d*)?$")

private fun commit(
    raw: String,
    decimals: Int,
    previousValue: Double?,
    onCommit: (Double?) -> Unit,
) {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        onCommit(null)
        return
    }
    val parsed = trimmed.toDoubleOrNull()
    if (parsed == null) {
        // Parse failure — revert by re-committing the previous value.
        onCommit(previousValue)
        return
    }
    val factor = 10.0.pow(decimals)
    val rounded = (parsed * factor).roundToLong() / factor
    onCommit(rounded)
}

private fun Double?.formatFor(decimals: Int): String =
    if (this == null) "" else "%.${decimals}f".format(this)

@Preview(showBackground = true)
@Composable
private fun EditableNumberPreview() {
    HealthFitnessTheme {
        EditableNumber(
            value = 189.2,
            onCommit = {},
            suffix = "lb",
            decimals = 1,
        )
    }
}
