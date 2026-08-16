package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference

/**
 * 通用可选条目卡片组件。
 *
 * 说明：
 * - 普通模式下显示 `SuperArrow`；
 * - 选择模式下显示 `SuperCheckbox`；
 * - 内置长按阈值防误触逻辑。
 */
@Composable
fun SelectableEntryCard(
    itemKey: Any,
    title: String,
    summary: String?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    customIconBytes: ByteArray? = null,
    standardIconId: Int? = null,
    iconPrimary: String? = null,
    iconSecondary: String? = null,
    contentDescription: String = "条目图标",
    startAction: (@Composable () -> Unit)? = null
) {
    val defaultStartAction: @Composable () -> Unit = {
        EntryIcon(
            customIconBytes = customIconBytes,
            standardIconId = standardIconId,
            primary = iconPrimary,
            secondary = iconSecondary,
            modifier = Modifier.size(32.dp),
            contentDescription = contentDescription
        )
    }
    val startIconAction = startAction ?: defaultStartAction

    if (isSelectionMode && onCheckedChange != null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            startIconAction()
            CheckboxPreference(
                title = title,
                checked = isSelected,
                onCheckedChange = { checked ->
                    onCheckedChange(checked)
                },
                summary = summary,
                checkboxLocation = CheckboxLocation.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
        return
    }

    val viewConfiguration = LocalViewConfiguration.current
    var holdDownState by remember(itemKey) { mutableStateOf(false) }
    var skipNextClick by remember(itemKey) { mutableStateOf(false) }

    val pointerModifier = if (onLongClick == null) {
        Modifier
    } else {
        Modifier.pointerInput(itemKey, isSelectionMode, isSelected) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                holdDownState = true

                val longPressReached = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + 400L) {
                    waitForUpOrCancellation()
                    false
                } ?: true

                if (longPressReached) {
                    // 仅当达到长按阈值才触发长按，避免滚动取消被误判。
                    skipNextClick = true
                    onLongClick.invoke()
                    waitForUpOrCancellation()
                }

                holdDownState = false
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Preference(
                type = PreferenceType.Arrow,
                title = title,
                summary = summary,
                startAction = {
                    startIconAction()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(pointerModifier),
                holdDownState = holdDownState,
                onClick = {
                    if (skipNextClick) {
                        skipNextClick = false
                        return@Preference
                    }
                    onClick()
                }
            )
        }
    }
}
