package xzynine.WebDAVPass.Android.model

import android.content.res.Resources
import android.os.Parcel
import android.os.Parcelable

data class RegisterInfo(
    val searchInfo: SearchInfo,
    val username: String? = null,
    val password: String? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        searchInfo = parcel.readParcelable(SearchInfo::class.java.classLoader) ?: SearchInfo(),
        username = parcel.readString(),
        password = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(searchInfo, flags)
        parcel.writeString(username)
        parcel.writeString(password)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun getName(resources: Resources): String {
        if (username != null)
            return "$username (${searchInfo})"
        return searchInfo.toString()
    }

    override fun toString(): String {
        if (username != null)
            return "$username ($searchInfo)"
        return searchInfo.toString()
    }

    companion object CREATOR : Parcelable.Creator<RegisterInfo> {
        override fun createFromParcel(parcel: Parcel): RegisterInfo {
            return RegisterInfo(parcel)
        }

        override fun newArray(size: Int): Array<RegisterInfo?> {
            return arrayOfNulls(size)
        }
    }
}
