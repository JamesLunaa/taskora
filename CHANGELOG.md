# Changelog

All notable changes to Taskora are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Task actions**: long-press a task for a sheet with Select, Today, Tomorrow, Pick date, Remove due date, priority, Move to list, Mark complete and Delete (with undo).
- **Multi-select**: pick several tasks and set their due date (Today, Tomorrow, a chosen date or none), change priority, complete, move or delete them together.
- **Today and Tomorrow chips** for the due date on the task screen.
- **Sort direction**: sort each list ascending or descending.
- **Drag to reorder**: a new "My order" sort mode shows drag handles to arrange tasks by hand.
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

