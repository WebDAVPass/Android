/*
 * Copyright 2021 Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePassDX.
 *
 *  KeePassDX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePassDX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePassDX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package xzynine.WebDAVPass.Android.autofill

import android.os.Build
import android.service.autofill.FillRequest
import android.view.inputmethod.InlineSuggestionsRequest
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.R)
class CompatInlineSuggestionsRequest(
    val inlineSuggestionsRequest: InlineSuggestionsRequest?
) {
    companion object {
        @RequiresApi(Build.VERSION_CODES.R)
        fun fromFillRequest(fillRequest: FillRequest): CompatInlineSuggestionsRequest {
            return CompatInlineSuggestionsRequest(fillRequest.inlineSuggestionsRequest)
        }
    }
}
