package xzynine.WebDAVPass.Android.ui.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode

sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Home : Route

    @Parcelize
    @Serializable
    data object Settings : Route

    @Parcelize
    @Serializable
    data object DatabaseSettings : Route

    @Parcelize
    @Serializable
    data object Welcome : Route

    @Parcelize
    @Serializable
    data object TokenList : Route

    @Parcelize
    @Serializable
    data class PasswordList(val listMode: PasswordListMode) : Route

    @Parcelize
    @Serializable
    data class PasswordEntryDetail(val entryId: Long) : Route

    @Parcelize
    @Serializable
    data object SecurityCheck : Route
}
