package com.kunzisoft.keepass.database.element

import android.os.Parcel
import android.os.Parcelable
import com.kunzisoft.keepass.utils.StringUtil.removeSpaceChars

class Tags : Parcelable {
    private val mTags = mutableListOf<String>()

    constructor()

    constructor(values: String) : this() {
        mTags.addAll(
            values
                .split(DELIMITER, DELIMITER1)
                .filter { it.removeSpaceChars().isNotEmpty() },
        )
    }

    constructor(parcel: Parcel) : this() {
        parcel.readStringList(mTags)
    }

    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeStringList(mTags)
    }

    override fun describeContents(): Int = 0

    fun setTags(tags: Tags) {
        mTags.clear()
        mTags.addAll(tags.mTags)
    }

    fun get(position: Int): String = mTags[position]

    fun put(tag: String) {
        if (tag.removeSpaceChars().isNotEmpty() && !mTags.contains(tag)) {
            mTags.add(tag)
        }
    }

    fun put(tags: Tags) {
        tags.mTags.forEach {
            put(it)
        }
    }

    fun contains(tag: String): Boolean = mTags.contains(tag)

    fun isEmpty(): Boolean = mTags.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun size(): Int = mTags.size

    fun clear() {
        mTags.clear()
    }

    fun toList(): List<String> = mTags

    override fun toString(): String = mTags.joinToString(DELIMITER.toString())

    companion object CREATOR : Parcelable.Creator<Tags> {
        const val DELIMITER = ','
        const val DELIMITER1 = ';'
        val DELIMITERS = listOf(',', ';')

        override fun createFromParcel(parcel: Parcel): Tags = Tags(parcel)

        override fun newArray(size: Int): Array<Tags?> = arrayOfNulls(size)
    }
}
