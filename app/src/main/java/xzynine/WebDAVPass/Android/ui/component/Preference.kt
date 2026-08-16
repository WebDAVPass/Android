package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SwitchColors
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

/**
 * 设置项（Preference）类型。
 */
enum class PreferenceType {
    Arrow,
    Switch,
    Spinner,
}

/**
 * 统一设置项公共组件。
 *
 * 所有原生 miuix Preference 通过本函数统一包裹，保证每个设置行的
 * 外观与默认修饰符（铺满宽度）一致，避免在各页面重复书写参数。
 *
 * 调用方只需 `import xzynine.WebDAVPass.Android.ui.component.Preference`，
 * 通过 [type] 区分类型，相关专属参数在对应类型下必填：
 * - [PreferenceType.Arrow]：无额外必填；
 * - [PreferenceType.Switch]：[checked] / [onCheckedChange]；
 * - [PreferenceType.Spinner]：[items] / [selectedIndex] / [onSelectedIndexChange]。
 */
@Composable
fun Preference(
    type: PreferenceType,
    title: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: androidx.compose.foundation.layout.PaddingValues = BasicComponentDefaults.InsideMargin,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    // Arrow
    onClick: (() -> Unit)? = null,
    // Switch
    checked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    switchColors: SwitchColors = SwitchDefaults.switchColors(),
    // Spinner
    items: List<DropdownItem> = emptyList(),
    selectedIndex: Int = 0,
    spinnerColors: DropdownColors = DropdownDefaults.dropdownColors(),
    maxHeight: androidx.compose.ui.unit.Dp? = null,
    showValue: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    when (type) {
        PreferenceType.Arrow -> {
            ArrowPreference(
                title = title,
                modifier = modifier,
                titleColor = titleColor,
                summary = summary,
                summaryColor = summaryColor,
                startAction = startAction,
                endActions = endActions,
                bottomAction = bottomAction,
                insideMargin = insideMargin,
                onClick = onClick,
                holdDownState = holdDownState,
                enabled = enabled,
            )
        }
        PreferenceType.Switch -> {
            SwitchPreference(
                checked = checked,
                onCheckedChange = onCheckedChange ?: {},
                title = title,
                modifier = modifier,
                titleColor = titleColor,
                summary = summary,
                summaryColor = summaryColor,
                startAction = startAction,
                endActions = endActions,
                bottomAction = bottomAction,
                switchColors = switchColors,
                insideMargin = insideMargin,
                holdDownState = holdDownState,
                enabled = enabled,
            )
        }
        PreferenceType.Spinner -> {
            WindowSpinnerPreference(
                items = items,
                selectedIndex = selectedIndex,
                title = title,
                modifier = modifier,
                titleColor = titleColor,
                summary = summary,
                summaryColor = summaryColor,
                spinnerColors = spinnerColors,
                startAction = startAction,
                bottomAction = bottomAction,
                insideMargin = insideMargin,
                maxHeight = maxHeight,
                enabled = enabled,
                showValue = showValue,
                onExpandedChange = onExpandedChange,
                onSelectedIndexChange = onSelectedIndexChange,
            )
        }
    }
}
