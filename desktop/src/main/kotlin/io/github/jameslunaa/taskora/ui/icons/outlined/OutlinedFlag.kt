/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copied from androidx.compose.material:material-icons-extended 1.7.8; only the package changed,
 * so the app doesn't need the 38 MB library for a few icons.
 */

package io.github.jameslunaa.taskora.ui.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.OutlinedFlag: ImageVector
    get() {
        if (_outlinedFlag != null) {
            return _outlinedFlag!!
        }
        _outlinedFlag = materialIcon(name = "Outlined.OutlinedFlag") {
            materialPath {
                moveTo(14.0f, 6.0f)
                lineToRelative(-1.0f, -2.0f)
                lineTo(5.0f, 4.0f)
                verticalLineToRelative(17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(5.0f)
                lineToRelative(1.0f, 2.0f)
                horizontalLineToRelative(7.0f)
                lineTo(20.0f, 6.0f)
                horizontalLineToRelative(-6.0f)
                close()
                moveTo(18.0f, 14.0f)
                horizontalLineToRelative(-4.0f)
                lineToRelative(-1.0f, -2.0f)
                lineTo(7.0f, 12.0f)
                lineTo(7.0f, 6.0f)
                horizontalLineToRelative(5.0f)
                lineToRelative(1.0f, 2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }
        return _outlinedFlag!!
    }

private var _outlinedFlag: ImageVector? = null
