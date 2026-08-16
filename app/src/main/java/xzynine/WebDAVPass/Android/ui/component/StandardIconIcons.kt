package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.HourglassDisabled
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalPostOffice
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.NotesFill
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Unlock

/**
 * KeePass 标准图标（ID 0-68）→ 现代 ImageVector 映射表。
 *
 * 图标语义依据 KeePass 官方标准图标定义（keepass2android PwEnums.cs 的 PwIcon 枚举）。
 * 来源优先级：Miuix 图标 > Material Icons（material-icons-extended，与 keepass2android
 * 引入的 baseline_* 官方 Material 图标同源）。
 * 未映射的 ID（28 PaperQ、62 Tux、63 Feather、64 Apple 无现代对应）回落 icon-pack material 资源。
 */
internal val standardIconVectorMap: Map<Int, ImageVector> =
    mapOf(
        0 to Icons.Rounded.VpnKey, // Key
        1 to Icons.Rounded.Public, // World
        2 to Icons.Rounded.WarningAmber, // Warning
        3 to Icons.Rounded.Dns, // NetworkServer
        4 to MiuixIcons.Folder, // MarkedDirectory
        5 to MiuixIcons.Messages, // UserCommunication
        6 to Icons.Rounded.Extension, // Parts
        7 to MiuixIcons.Notes, // Notepad
        8 to MiuixIcons.Link, // WorldSocket
        9 to Icons.Rounded.Badge, // Identity
        10 to MiuixIcons.File, // PaperReady
        11 to Icons.Rounded.PhotoCamera, // Digicam
        12 to Icons.Rounded.Sensors, // IRCommunication
        13 to Icons.Rounded.VpnKey, // MultiKeys
        14 to Icons.Rounded.Bolt, // Energy
        15 to MiuixIcons.Scan, // Scanner
        16 to MiuixIcons.Favorites, // WorldStar
        17 to MiuixIcons.Album, // CDRom
        18 to MiuixIcons.ScreenMirroring, // Monitor
        19 to MiuixIcons.Email, // EMail
        20 to MiuixIcons.Tune, // Configuration
        21 to MiuixIcons.Paste, // ClipboardReady
        22 to MiuixIcons.Create, // PaperNew
        23 to MiuixIcons.ScreenCapture, // Screen
        24 to Icons.Rounded.BatteryAlert, // EnergyCareful
        25 to Icons.Rounded.LocalPostOffice, // EMailBox
        26 to Icons.Rounded.Save, // Disk
        27 to Icons.Rounded.Storage, // Drive
        29 to Icons.Rounded.Terminal, // TerminalEncrypted
        30 to Icons.Rounded.Code, // Console
        31 to Icons.Rounded.Print, // Printer
        32 to MiuixIcons.GridView, // ProgramIcons
        33 to MiuixIcons.Play, // Run
        34 to MiuixIcons.Settings, // Settings
        35 to Icons.Rounded.Computer, // WorldComputer
        36 to MiuixIcons.Backup, // Archive
        37 to MiuixIcons.BankCards, // Homebanking
        38 to Icons.Rounded.Storage, // DriveWindows
        39 to MiuixIcons.Timer, // Clock
        40 to MiuixIcons.Search, // EMailSearch
        41 to MiuixIcons.Pin, // PaperFlag
        42 to MiuixIcons.Layers, // Memory
        43 to MiuixIcons.Delete, // TrashBin
        44 to MiuixIcons.NotesFill, // Note
        45 to Icons.Rounded.HourglassDisabled, // Expired
        46 to MiuixIcons.Info, // Info
        47 to Icons.Rounded.Inventory2, // Package
        48 to MiuixIcons.Folder, // Folder
        49 to MiuixIcons.FolderFill, // FolderOpen
        50 to Icons.Rounded.FolderZip, // FolderPackage
        51 to MiuixIcons.Unlock, // LockOpen
        52 to MiuixIcons.Lock, // PaperLocked
        53 to MiuixIcons.Ok, // Checked
        54 to MiuixIcons.Edit, // Pen
        55 to MiuixIcons.Image, // Thumbnail
        56 to Icons.Rounded.MenuBook, // Book
        57 to MiuixIcons.ListView, // List
        58 to Icons.Rounded.VpnKey, // UserKey
        59 to Icons.Rounded.Construction, // Tool
        60 to MiuixIcons.Home, // Home
        61 to MiuixIcons.FavoritesFill, // Star
        66 to Icons.Rounded.AttachMoney, // Money
        67 to Icons.Rounded.Verified, // Certificate
        68 to MiuixIcons.Phone, // BlackBerry
    )
