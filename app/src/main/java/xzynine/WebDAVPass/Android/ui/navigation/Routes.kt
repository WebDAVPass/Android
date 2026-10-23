package xzynine.WebDAVPass.Android.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey
import xzynine.WebDAVPass.Android.ui.viewmodel.PasswordListMode

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object DatabaseSettings : Route

    @Serializable
    data object GeneralSettings : Route

    @Serializable
    data object SecuritySettings : Route

    @Serializable
    data object BackupSettings : Route

    @Serializable
    data object About : Route

    @Serializable
    data object Welcome : Route

    @Serializable
    data object Locked : Route

    @Serializable
    data object TokenList : Route

    @Serializable
    data class PasswordList(
        val listMode: PasswordListMode,
    ) : Route

    @Serializable
    data class PasswordEntryDetail(
        val entryId: Long,
    ) : Route

    @Serializable
    data object SecurityCheck : Route
}
