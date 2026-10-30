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

package io.github.jameslunaa.taskora.ui.icons.automirrored.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.AutoMirrored.Outlined.StickyNote2: ImageVector
    get() {
        if (_stickyNote2 != null) {
            return _stickyNote2!!
        }
        _stickyNote2 = materialIcon(name = "AutoMirrored.Outlined.StickyNote2", autoMirror = true) {
            materialPath {
                moveTo(19.0f, 5.0f)
                verticalLineToRelative(9.0f)
                lineToRelative(-5.0f, 0.0f)
                lineToRelative(0.0f, 5.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(5.0f)
                horizontalLineTo(19.0f)
                moveTo(19.0f, 3.0f)
                horizontalLineTo(5.0f)
                curveTo(3.9f, 3.0f, 3.0f, 3.9f, 3.0f, 5.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                lineToRelative(6.0f, -6.0f)
                verticalLineTo(5.0f)
                curveTo(21.0f, 3.9f, 20.1f, 3.0f, 19.0f, 3.0f)
                close()
                moveTo(12.0f, 14.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(17.0f, 10.0f)
                horizontalLineTo(7.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(10.0f)
                verticalLineTo(10.0f)
                close()
            }
        }
        return _stickyNote2!!
    }

private var _stickyNote2: ImageVector? = null
