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
 * Modified for WebDAVPass: 调整包名至 xzynine.WebDAVPass.Autofill.core。
 */

package xzynine.WebDAVPass.Autofill.core

import android.app.assist.AssistStructure

/**
 * 自动填充组件：承载 AssistStructure 与可选的兼容内联建议请求，
 * 经 PendingIntent 在系统填充服务与宿主选择界面之间传递。
 */
data class AutofillComponent(
    val assistStructure: AssistStructure,
    val compatInlineSuggestionsRequest: CompatInlineSuggestionsRequest?,
)
