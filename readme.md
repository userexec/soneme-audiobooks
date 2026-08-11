# Soneme Audiobooks

![Soneme Audiobooks Icon](https://github.com/userexec/soneme-audiobooks/blob/master/soneme-audiobooks-icon.png?raw=true)

Soneme Audiobooks is a small, keypad-friendly Android audiobook player built for simple local playback without accounts, streaming services, or unnecessary dependencies.

The interface can be operated almost entirely without opening your eyes. Playback, seeking, queue navigation, repeat mode, and sleep timer controls are all mapped to reasonably intuitive keypad keys. There is no more need to flashbang yourself at 2am to add an hour to your sleep timer.

It is designed for the Sonim XP3Plus XP3900 and should also function on the XP3Plus 5G X320 if Sonim ever fixes the bug in DocumentsUI that prevents all X320s from *picking folders in folder picker dialogs*, though that's the least of the X320's problems. It may be useful for other Android devices with physical navigation keys, though you'd likely need to do some remapping and rebuild the apk.

**Note it will not function on 99% of Android phones because it doesn't have touch controls for major functions of the interface. The Sonim flip phones it's designed for do not have touchscreens, and your phone probably doesn't have 3 softkeys mapped to Sonim's keycodes.**

Note this app is primarily designed for audiobooks that are a single MP3 file. No chapters, no character lists, no funny business. I made it for myself, and I use an XP3900 in 2026, so this shouldn't be all that surprising. If you have legitimate audiobooks in fancy formats, go use a real audiobook player.

![Player interface](https://github.com/userexec/soneme-audiobooks/blob/master/screenshot-player.png?raw=true)  ![Books interface](https://github.com/userexec/soneme-audiobooks/blob/master/screenshot-books.png?raw=true)

## Features

* Local MP3 audiobook playback
* Multiple source folders from internal storage or SD card
* Persistent playback progress
* Books, Recent, Queue, and Player views
* Background playback with screen off or phone closed
* Headset and Bluetooth media controls
* Persistent playback queue
* Repeat Off, Repeat 1, and Repeat All
* Playback speed control
* Adjustable rewind and fast-forward intervals
* Sleep timer
* Resume from the position where the sleep timer was last set
* Hardware keypad shortcuts
* Sonim softkey integration
* Haptic confirmation for repeat mode and sleep-timer adjustments
* No network access, accounts, analytics, or cloud services

## Tested Devices

Soneme has been developed and tested on:

* Sonim XP3plus XP3900 — Android 11
* Sonim XP3plus 5G X320 — Android 14 (will work fine if Sonim ever decides to fix the X320's implementation of softkeys in folder picker dialogs--not this app's problem, not worth my time)

The app uses standard Android APIs wherever practical, but the keypad and native softkey behavior is specifically tuned for these Sonim devices.

## Installing

Soneme is distributed as a normal Android APK.

Copy the APK to the device and install it, or install from a connected computer with ADB:

```sh
adb install soneme-audiobooks.apk
```

If updating an existing release signed with the same release key:

```sh
adb install -r soneme-audiobooks.apk
```

Android may require permission to install apps from unknown sources when installing directly on the phone.

## Adding Audiobooks

Open **Books**, then choose **Sources** from the left softkey.

Add one or more folders containing MP3 audiobooks. Soneme uses Android's system folder picker and remembers access to the selected folders.

Internal storage and removable SD-card folders are treated the same way.

After adding or changing files, use **Refresh** to rescan the configured sources.

## Main Views

Soneme has four main tabs:

### Books

Displays audiobooks found in configured source folders. Directories can be opened and navigated normally.

Selecting an audiobook loads it for playback.

### Recent

Shows recently played audiobooks and their saved progress.

### Queue

Shows the current playback queue in the order items were added.

When Repeat is Off, playback advances through the queue and stops after the final item.

### Player

Shows the current audiobook and playback controls, including:

* Sleep timer
* Repeat mode
* Playback speed
* Elapsed, total, percentage, and remaining time
* Seek position
* Play/Pause
* Previous/Next
* Rewind/Fast-forward

Long titles automatically marquee across the title line.

## Keypad Shortcuts

While on the Player screen:

| Key | Action                            |
| --- | --------------------------------- |
| `1` | Rewind 10 seconds                 |
| `4` | Rewind 1 minute                   |
| `7` | Rewind 10 minutes                 |
| `*` | Rewind 1 hour                     |
| `2` | Previous queue item               |
| `5` | Next queue item                   |
| `3` | Forward 10 seconds                |
| `6` | Forward 1 minute                  |
| `9` | Forward 10 minutes                |
| `#` | Forward 1 hour                    |
| `8` | Cycle Repeat mode                 |
| `0` | Add 10 minutes to the sleep timer |

Using a shortcut also moves focus to its corresponding on-screen control when possible.

Previous and Next only receive focus when that action is currently available.

## Sonim Softkeys

Soneme uses the native three-position Sonim softkey bar.

The available actions change with the current view. On the Player screen the softkeys provide:

* **Left:** Controls
* **Center:** Play/Pause
* **Right:** Sleep

Other views expose context-appropriate actions such as Sources, Refresh, Queue, Add, Remove, or Clear.

## Repeat Haptics

Changing Repeat mode with keypad `8` gives a tactile indication of the newly selected mode:

* **Repeat Off:** one long vibration
* **Repeat 1:** two long vibrations
* **Repeat All:** two groups of three short vibrations

This makes repeat mode usable without looking at the display.

## Sleep Timer

The Sleep control can disable the timer or set a timed playback stop.

Pressing keypad `0` adds 10 minutes to the current sleep timer and gives a short confirmation vibration.

Whenever the sleep timer is set or adjusted, Soneme remembers the current playback position for that audiobook. This allows you to easily go back to where you set the timer before you fell asleep, then seek forward to the last thing you remember hearing.

If playback continues after you fall asleep, open the Sleep menu later and choose:

**Resume at last timer set**

Soneme will:

1. turn off any active sleep timer,
2. seek back to the position where the timer was last set,
3. discard that saved timer position after using it.

This option is disabled if no saved timer position exists.

Naturally allowing the sleep timer to expire does **not** discard the saved position. Manually turning an active timer Off does.

## Playback Progress

Playback progress is saved automatically during normal use, including pauses, seeking, track changes, sleep-timer events, and periodic checkpoints.

Completed audiobooks retain 100% progress. Replaying a completed book records new progress normally.

## Navigation

The D-pad moves through tabs, lists, and Player controls.

Pressing **Back**:

* moves up one directory while browsing Books,
* moves backward through the app's tab/navigation state,
* exits Soneme to the launcher when already at the top-level Books view.

## Storage and Privacy

Soneme is intentionally local-only.

It does not require:

* an account,
* internet access,
* cloud storage,
* analytics,
* advertising,
* a subscription,
* Google Play services.

Audiobooks, queue state, progress, sleep positions, and settings remain on the device.

## Building

Soneme is a standard Gradle Android project.

A debug build can be produced with:

```sh
./gradlew assembleDebug
```

A configured release build can be produced with:

```sh
./gradlew assembleRelease
```

The resulting APK is written beneath:

```text
app/build/outputs/apk/
```

Release builds must be signed with an Android signing key before installation. Keep the release keystore and its credentials backed up securely; future updates must use the same signing identity. You're free to do whatever you want with this project, sign it with your own keys, whatever. License is public domain.
