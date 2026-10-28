# Changelog

All notable changes to Taskora are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Desktop app for Linux (early preview)** in `desktop/`. Lists, tasks, subtasks, notes, due dates and times, and priorities, laid out as three panes (lists, tasks, task details). Hover over a task to delete it, with undo. Shortcuts: Ctrl+N goes to the new-task field, and Esc closes the details pane. It has its own data on your computer and doesn't connect to the phone yet.
- Desktop: **search** with status, list, priority and due-date filters (Ctrl+F); **sorting** per list with direction; **drag to reorder** by the handle that appears on hover; **repeating tasks**; **reminders** as desktop notifications with Complete, Snooze and Open buttons.
- Desktop: **right-click menu** on a task (Today, Tomorrow, pick date, priority, move, complete, delete) and **multi-select** (Ctrl+click, Shift+click for a range) with a bar for bulk changes. Esc clears the selection.
- Desktop: **system tray** icon. Closing the window keeps Taskora running there so reminders still show; quit from the tray menu or with Ctrl+Q.
- Desktop: **settings** for theme (Dark, Light, or Follow system, which reads the KDE/GNOME light/dark preference), keeping it in the tray, and **start on login** (installed app only).
- Desktop: **window pinning** from the pin button next to Settings: keep the window always on top, normal, or always below other windows, and set its **opacity**. Always below and opacity work on X11 and on KDE Wayland (through XWayland).
- Desktop: **sticky note**, a small borderless window with one list's open tasks (or All tasks). Add and tick off tasks there; clicking one opens it in the main window. It has its own pin and opacity, starts pinned on top, and remembers where you left it. Show it from the pin menu or the tray.
- Desktop: **Linux packages**: a PKGBUILD for Arch Linux, plus a script that builds an AppImage, .deb and .rpm. Each one bundles its own Java runtime and installs a menu entry and icons, and the taskbar shows Taskora's own icon for its windows. Start on login also works from an AppImage.
- Desktop: **GNOME** support. Without a tray, closing the window keeps Taskora running in the background, and opening Taskora again brings it back. On GNOME the app follows the system **accent color** by default (Settings → "Use system accent color", also offered on KDE). Notifications show under Taskora's own name and icon. Opacity is turned off on GNOME, which doesn't support it.
- Desktop: only **one copy** runs at a time; opening Taskora again shows the running window.
- Desktop: **Windows** support with an MSI installer. Includes the tray, start on login, following the Windows light/dark setting, and always below, opacity and the sticky note. Reminders show as tray messages; clicking one opens the task.

## [1.1.0] - 2026-10-20

### Added

- **Task actions**: long-press a task for a sheet with Select, Today, Tomorrow, Pick date, Remove due date, priority, Move to list, Mark complete and Delete (with undo).
- **Multi-select**: pick several tasks and set their due date (Today, Tomorrow, a chosen date or none), change priority, complete, move or delete them together.
- **Today and Tomorrow chips** for the due date on the task screen.
- **Sort direction**: sort each list ascending or descending.
- **Drag to reorder**: long-press a task and drag it to a new place. Letting go without moving opens the task actions instead. Dragging switches the list to the new "My order" sort mode.
- **All tasks** view in the drawer: every task from every list, grouped under the name of its list.

### Changed

- "Date created" sorting now follows when the task was created, not its position in the list. Use "My order" for a hand-picked order.

## [1.0.0] - 2026-10-20

First public release.

### Added

- **Tasks**: add tasks quickly from a sheet that stays open for entering several in a row. Each task has notes, a due date with an optional time, a priority (low, medium or high) and subtasks.
- **Completing and deleting**: tick tasks off into a collapsible Completed section and clear it in one go. Swipe a task away to delete it, with Undo.
- **Lists**: create, rename, recolor and delete color-coded lists from the side menu, which also shows each list's open-task count. The Inbox is always there. Tasks can be moved between lists.
- **Sorting**: each list remembers its own sort order: date created, due date, priority or title.
- **Reminders**: notifications with Complete and Snooze (10 minutes) buttons. Tapping a notification opens the task. Reminders are rescheduled after a reboot or an app update.
- **Reminder warnings**: the app warns when notifications are off, when reminders may arrive a few minutes late, or when the phone may block reminders in the background (on Xiaomi, vivo, OPPO, Samsung and similar phones). Each warning has a button to fix it.
- **Recurring tasks**: daily, weekly (optionally on chosen days), monthly or yearly, repeating every *n* days, weeks, months or years. Completing a recurring task creates the next one, with its reminder moved forward and its subtasks unchecked.
- **Search and filters**: search task titles and notes across all lists, and filter by status, list, priority or due date (overdue, today, next 7 days, no date).
- **Home screen widget**: "Today" shows tasks due today or overdue, and you can tick them off from the home screen.
- **Themes**: dark by default, with a light theme or follow-system option. Wallpaper colors (Material You) are available on Android 12 and newer.
- **App icon**: a themed icon for Android 13 and newer.

### Known issues

- Monthly tasks due on the 29th–31st move to the last day of shorter months and stay on that day afterwards.
- Unticking a completed recurring task doesn't remove the next occurrence that was already created.
- The widget can't add tasks yet. Tapping its header opens the app.
- On some phones, reminders only arrive once Taskora is allowed to use the battery in the background. The app shows a warning with a Fix button when this applies.

