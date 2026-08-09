# Soneme Audiobooks

Audiobook player targeting the Sonim XP3Plus 5G (X320) flip phone. May also be useful for other Android flip phones. Meant to work on small screen sizes and take advantage of the phone's numeric keypad and softkeys for quick navigation.

# Target device properties

The Sonim XP3Plus 5G has the following constraints:

- 480x854
- Android 14
- No touchscreen
- Options menu softkeys
- No Google Play Store or services
- App must be sideloaded as an .apk

# Application overview

Soneme Audiobooks has a tabbed interface with four tabs: Books, Recent, Queue, and Player. The tab bar is always present, and the current view's tab is indicated as active.

Each tab is considered a view. Each view (except the special Sources view) is made of the tab bar, a main content area, an options menu, and modals if needed.

UI should use default UI library components and generally look like a stock application.

On app startup:
 - If the Recents list has entries, the app starts in Player view for the most recently listened audio file.
 - If the Recents list does not have entries, the app starts in Player view for the first item in the Queue.
 - If there are no items in the queue, the app starts in the Books view.

# Views

## Books

### Controls

 - Left and right buttons switch tabs
 - Up and down cycle through the list
 - Clicking a title clears the queue, adds the title to the queue, and opens it in Player view
 - Back button goes up a level if in a subfolder; at top level it exits the app and returns to the launcher

### Main content

Listing of titles that can be selected and opened in player view.
List shows only audio files and subfolders of the source folders.
All source folders are scanned for audio files and subfolders and presented together as a single list. For example, if there is only one source folder, then the listing is just its audio files and subfolders; if there are two or more source folders, then the listing is the audio files and subfolders of all three source folders combined.
Subfolders appear first in the listing in alphabetical order, followed by audio files in alphabetical order by their Title, or if no title in metadata, their filename.
Audio files are represented by their metadata Title if present, or filename if not. Below the title the metadata Artist is shown if present, or "Unknown" if not. To the right of the Title, right aligned with the item's area, is the total runtime of the file in format h:m. Below that is the current listening percentage--blank if never played, progress percentage if previously played.

### Options menu

 - Sources

   Opens Sources view
   
 - Refresh

   Re-scans sources for new files

 - Queue

   Adds selected audio file to the Queue. Only present if audio file is focused, option disappears if folder is focused.


## Recents

### Controls

 - Left and right buttons switch tabs
 - Back button goes to Book view
 - Up and down cycle through the list
 - Clicking a title clears the queue, adds the title to the queue, and opens it in Player view

### Main content

Listing of titles that can be selected and opened in player view.
Played titles are listed here in descending order of recency of their last play.
Items follow same formatting as audio files in Book view.

### Options menu

 - (blank)
 - (blank)
 - Clear

 Clears the recents list


## Queue

### Controls

 - Left and right buttons switch tabs
 - Back button goes to Recents view
 - Up and down cycle through the list
 - Clicking a title opens it in Player view. Queue is not cleared

### Main content

Listing of titles that can be selected and opened in player view.
Played titles are listed here in descending order of recency of their last play.
Items follow same formatting as audio files in Book view.

### Options menu

 - (blank)
 - (blank)
 - Clear

 Clears the queue


## Player

### Controls

 - Left and right buttons switch tabs only if focus is on tab bar.
 - Back button goes to Queue view.
 - 1 button rewinds 10 seconds
 - 2 button starts previous title in queue at 0:00 if previous exists, or last title in queue at 0:00 if currently playing is first in queue. Does nothing if only one title in queue.
 - 3 button fast-forwards 10 seconds
 - 4 button rewinds 60 seconds
 - 5 button starts next title in queue at 0:00 if next exists, or first title in queue at 0:00 if currently playing is last in queue. Does nothing if only one title in queue.
 - 6 button fast-forwards 60 seconds
 - 7 button rewinds 600 seconds
 - 8 button cycles repeat setting
 - 9 button fast-forwards 600 seconds
 - * button rewinds 3600 seconds
 - 0 button adds 10 minutes to sleep timer
 - \# button fast-forwards 3600 seconds

### Main content

Sleep timer remaining countdown in format h:m. Click on sleep timer opens sleep modal.
Repeat icon that reflects current setting. Click on repeat icon opens repeat modal.
Playback speed indicator that reflects current setting. Click on playback speed indicator opens playback speed modal. 
Wiper indicator of current playing position. When wiper focused, clicking left button rewinds by interval setting, clicking right fast-forwards by interval setting. Holding left or right pauses the audio, repeats their action once per second until hold released, then returns to playing audio in the new position.
Above wiper, time elapsed is shown on the left in format h:mm:ss, total track time and listening progress are centered in format "1h 40m - 47%", and time remaining is shown on the right in format h:mm:ss. All three appear on the same row.
Play/pause indicator below wiper.
Previous and Next track buttons to jump to titles in queue. Grayed out if only one title is in queue.
Rewind and Fast-forward indicators with numbers associated with each per settings in Rewind Interval modal and Fast-forward Interval modal. Settings are expressed here as 10s, 1m, 10m, and 1h to save space. Clicking them rewinds or fast-forwards by the selected interval. Holding them opens their respective modal to adjust their interval setting.

### Options menu

 - Controls

 Opens the Controls modal

 - Play/Pause (contextual if file is playing)

 Plays and pauses the audio

 - Sleep

 Opens the Sleep modal

### Modals

#### Controls modal

Lists controls by button in order 1,2,3,4,5,6,7,8,9,*,0,#. Back button exits modal.

#### Sleep modal

Modal with options for Off, 10 minutes, 30 minutes, 1 hour, 2 hours, 3 hours, 4 hours, 8 hours, 12 hours. Timer begins whenever set, audio pauses when timer runs out. Back button exits modal without setting (sleep timer already in progress should not be affected). Default setting Off.

#### Repeat modal

Modal with options for Off, Repeat 1, and Repeat All. Default setting Off.

#### Playback speed modal

Modal with options for: 0.5x, 0.75x, 1x, 1.25x, 1.5x, 1.75x, 2x, 3x, 4x. Default setting 1x.

#### Rewind interval modal

Modal with options for 10 seconds, 1 minute, 10 minutes, and 1 hour.

## Sources

Tab bar disappears for sources modal.

### Controls

- Up and down cycle through the list
- Back button returns to Books view

### Main content

Current folders to be used as sources are shown in a list.

### Options menu

- Add

  Opens folder picker dialog. Selecting a folder adds it to the list of sources. Duplicate folders should be discarded with an error message "Already a source", and folders contained within existing source folders should be discarded with an error message "Already inside a source".

- Remove

  Removes the focused source

- (blank)

## Updates to be performed after first build

- On app open, if app opens to Player view, track should be paused.
- Options menu implementation incorrect. Three options menu items (some blank) are provided for the options menu across the bottom of the screen. On Sonim XP3Plus devices this appears as three slots that are mapped to three corresponding softkeys below the screen. The correct presentation is the pre-Honeycomb "icon menu" presentation where items appear as a bottom bar rather than a popup menu. The first option is mapped to KEY\_MENU (139), second to KEY\_HOME (102), third to KEY\_CLEAR (355). While the options menu items do work in the current implementation, they are appearing as a pop-up menu when KEY\_MENU is pressed rather than being arranged into the three slots of the option menu and activatable with the corresponding softkeys.
- Titles are often too long to be displayed in the lists. On focus of a list item that represents an audio file, the title field should change from being truncated with ... to a marquee.
