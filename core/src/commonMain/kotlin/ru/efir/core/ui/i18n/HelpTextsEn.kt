package ru.efir.core.ui.i18n

import ru.efir.core.ui.HelpText

/** Help texts in English: the key is the name of a section (`HelpTopic`). */
internal val HELP_EN: Map<String, HelpText> = mapOf(
    "START" to HelpText(
        "What Efir is",
        "Efir links an Android phone and a Windows computer while they are on the same local network — Wi-Fi or cable. " +
            "No internet, cloud or accounts: data goes straight between the two devices and is encrypted.\n\n" +
            "What you can do: see the other device's screen and control it, browse and transfer files, keep folders the same " +
            "(sync), share the clipboard, see the phone's notifications, camera and sound, send SMS and take calls from the computer.\n\n" +
            "Where to start. 1. Start Efir on both devices. 2. On the Devices screen press “Wait with a key” on one, and on the other " +
            "“Find a device” and enter the key shown: this is pairing, done once. 3. From then on the devices connect with the “Connect” button.\n\n" +
            "The sections of the app are in the panel on the left (on a phone, in the strip of icons at the top). Without a connection only Devices works.\n\n" +
            "The version of the app is written at the top of the help and in Settings → About.",
    ),
    "HINTS" to HelpText(
        "Hints, help and search",
        "Every button, icon, field and switch has a hint. Rest the mouse on it, or on a touch screen hold a finger on it: " +
            "a short explanation with a question mark comes up.\n\n" +
            "Pressing the hint itself opens the help at the section the element belongs to. " +
            "The help opens over the window: the Back arrow returns to where you were — the screen, the tab and typed text are kept.\n\n" +
            "The Help icon in the top bar opens the help at the section of the screen that is open now.\n\n" +
            "Search. Type a word or part of one, two letters or more, in the field at the top. The help shows every place it was found, " +
            "with the words around it. Press a place found — its section opens with the word marked. " +
            "When the whole phrase is not found, sections that have all its words are shown.\n\n" +
            "How many seconds a hint stays is set in Settings → Appearance; hints can be turned off there too.",
    ),
    "BATTERY" to HelpText(
        "Battery and background work",
        "Efir does no idle work.\n\n" +
            "The search for devices runs only while the Devices screen is open: the rest of the time the app sends nothing to the network.\n\n" +
            "Phone. So that the computer can connect while Efir is not on the screen, a notification “Efir is running” stays in the shade " +
            "(the “Stay reachable in the background” switch in Settings → General). The processor is not kept awake by it: without a connection the phone sleeps as usual, " +
            "and it is kept awake only for the time of a connection.\n\n" +
            "The screen is captured only while someone watches it. A computer takes at most 12 pictures a second and does not send a frame " +
            "that is the same as the one before: a still screen uses no network.\n\n" +
            "A minimised window, and one in the tray, reads and redraws nothing.",
    ),
    "DEVICES" to HelpText(
        "Pairing and connecting",
        "On the Devices screen devices find each other and connect. Tabs: Connection, Nearby, Trusted and This device.\n\n" +
            "The first connection is pairing. On one device press “Wait with a key”: a 12-character access key appears, " +
            "valid for two minutes and good for one use. On the other press “Find a device”: the waiting device is listed first, " +
            "marked “waiting to pair”. Choose it and enter the key; the hyphens are put in for you.\n\n" +
            "If the device is not in the list, type its address — it is written under the key and on the This device tab — " +
            "or paste the pairing data copied with the button next to the key. The QR code holds the same data.\n\n" +
            "After pairing no key is needed: the device is listed under Trusted and connects with the “Connect” button. " +
            "The “Find a device” window lists a paired device too, marked “paired”: a press connects it at once. " +
            "In the key field and the address field Enter does what the window's button does.\n\n" +
            "“Disconnect” ends the current connection; the pairing stays.",
    ),
    "NETWORK" to HelpText(
        "Network and finding devices",
        "Efir works only on a local network. Both devices must be on the same Wi-Fi or LAN, with Efir running on both.\n\n" +
            "The Nearby tab lists the devices found. The search runs only while the Devices screen is open. " +
            "The searching device asks the whole network at once and, besides, every address of its subnet one by one and the devices it knows at the address of the last time: " +
            "an Android phone often does not hear the general question over Wi-Fi while Efir is not on its screen, but hears one addressed to it. " +
            "The pass over the subnet is repeated every 15 seconds, so a phone with Efir in the background may take a moment to appear.\n\n" +
            "“Check the network” reads this device's network addresses again and searches anew. Press it when the network changed after the start: " +
            "another Wi-Fi, a cable in place of Wi-Fi, a VPN turned off. The addresses are also read again when the screen opens and before pairing.\n\n" +
            "The This device tab shows the name, address and port. The “Wait for connections” switch lets the device answer a search and accept connections; " +
            "with it off, you can only connect from here.\n\n" +
            "A device is not found? Check that it is the same network (not a guest one, not mobile data), that the router does not isolate devices from each other, " +
            "that Windows Firewall allowed Efir on private networks. You can also connect by typing the address.\n\n" +
            "Connection port. By default it is chosen automatically: 47812, or the nearest free port if another program uses that one. In Settings → Network the port can be set by hand: Efir says at once whether it is free or busy; " +
            "the new port takes effect after Efir is restarted. Nothing has to be changed on the other device: it learns the port from the search and the pairing data, " +
            "and when an address is typed by hand the port goes after a colon, 192.168.1.5:50000 for example. The device search always uses port 47813.",
    ),
    "TRUSTED" to HelpText(
        "Trusted devices",
        "A paired device is listed under Trusted. From then on the “Connect” button is enough, from either side; no key is needed.\n\n" +
            "When the device is not found on the network now, the address of the last connection is used.\n\n" +
            "On a computer the trusted devices are also in the menu of the tray icon: “Connect: name”.\n\n" +
            "“Revoke trust” stops a device from connecting until it is paired again; a connection with it that is open is closed. " +
            "Revoke the trust of a device that is lost or given away.",
    ),
    "SCREEN" to HelpText(
        "The other device's screen",
        "The Screen section shows the other device's screen and lets you control it.\n\n" +
            "Press “Show screen”. The phone asks for permission itself: an Android window about recording or casting appears on it — press “Start” there. " +
            "If Efir is not on the phone's screen, a “Share the screen?” notification comes instead: tap it. The phone waits 45 seconds for the answer. " +
            "Android 14 and later asks before every show.\n\n" +
            "A computer's screen is shown at once if “Show this computer's screen” is on there (Settings → Screen sharing). " +
            "The main monitor is shown.\n\n" +
            "Control. You can tap the picture, swipe and pinch with two fingers — the gesture is repeated on the device. " +
            "Under a phone's screen there are Back, Home and Recent apps buttons, and the field below types text where the cursor is on the device. " +
            "To control a phone, turn on the “Efir — input control” service on it (Settings → Permissions → Input control). " +
            "To control a computer, turn on “Allow control of this computer” on it: a tap is a mouse click, a long press is the right button, " +
            "a swipe is a drag, text is pasted through the clipboard.\n\n" +
            "Full screen. The “Full screen” icon opens the picture over the whole window, and on a phone over the whole screen. The turn button is there too: " +
            "each press turns the picture a quarter — a computer's wide screen is easier to watch sideways on a phone. " +
            "Gestures work when the picture is turned. Leave with “Leave full screen” or Back.\n\n" +
            "Text to a computer. If control is not turned on on the computer, the text from “Type text on the device” is not pasted " +
            "but shown on the computer in a window in Efir — with Copy and OK buttons.\n\n" +
            "If the show did not start, the reason is written under the button: not allowed on the phone, turned off on the computer, the device did not answer.",
    ),
    "SCREEN_SHARE" to HelpText(
        "Showing this device's screen",
        "The “This device's screen” card in the Screen section says whether it is being watched now. While it is shown, the top bar has a sharing icon; " +
            "pressing it, or the “Stop showing” button, ends the show at once — the picture disappears on the other device.\n\n" +
            "Computer. Settings → Screen sharing has two switches. “Show this computer's screen” is on until you turn it off: " +
            "a paired device may ask for the show, and the computer tells you with a notification when it begins. " +
            "“Allow control of this computer” is off until you turn it on: with it the other device moves the mouse, presses buttons and pastes text.\n\n" +
            "Phone. Android allows the show — with a window before it; the “Allow screen sharing” button in the card asks that question ahead of time. " +
            "While the screen is shown Android keeps an icon in the status bar, and the show can also be stopped from the shade.\n\n" +
            "Only a paired device that is connected now can see the screen.",
    ),
    "FILES" to HelpText(
        "The other device's files",
        "The Files section: viewing, downloading, sending, renaming and deleting files of the other device.\n\n" +
            "First the device's places are shown — the folders it has opened. Pressing a folder opens it, pressing a file shows a preview " +
            "(images and text). “Up one level” goes back.\n\n" +
            "“Download” saves a file or a whole folder to this device's downloads folder; files with the same name are not overwritten — the new one gets a number. " +
            "“Send files” puts the chosen files into the open folder of the other device. “New folder”, “Rename” and “Delete” act on the other device; " +
            "what is deleted there cannot be restored.\n\n" +
            "A phone shows empty folders? Access to all files is not granted on it: Settings → Permissions. " +
            "A computer shows only the home folder? Other drives and folders are added on it in Settings → Folders.",
    ),
    "TRANSFERS" to HelpText(
        "Transfers",
        "The Transfers tab of the Files section shows what is being downloaded and sent: how much is done, the speed, the time left. " +
            "A folder is one row with a count of files. The cross cancels a transfer.\n\n" +
            "A transfer cut short by a lost connection or by closing the app can go on from where it stopped: it stays in the “Unfinished transfers” list. " +
            "“Resume” finishes it, “Discard” forgets it and deletes the part already received.\n\n" +
            "Every file is checked for integrity after the transfer.",
    ),
    "DOWNLOADS" to HelpText(
        "Downloads folder",
        "What you download goes to this device's downloads folder. Its path is written at the bottom of the files card; the folder icon next to it opens it, " +
            "the pencil icon changes it.\n\n" +
            "Any folder on any drive can be chosen: Settings → Folders → Downloads folder → “Choose a folder”. " +
            "The window lists the computer's drives at the top (on a phone, the storage and the memory card); a path can also be typed, and a folder created right there.\n\n" +
            "“Default” brings back the standard folder: on a computer it is “Downloads/Efir” in the user's home folder, on a phone “Download/Efir”.\n\n" +
            "On a computer the app log is saved there too.",
    ),
    "SYNC" to HelpText(
        "Sync rules",
        "A sync rule keeps a folder on this device and a folder on the other one the same.\n\n" +
            "Folders. A rule has two folders: “here” and “there”. Each can be typed or chosen with the folder button on the right of the field. " +
            "On this device any folder on any drive will do — D:/Photos, for example. On the other device you choose among the folders it has opened: " +
            "a phone opens all of its shared storage, a computer the user's home folder. To sync with another drive of a computer, " +
            "add the drive or folder on that computer in Settings → Folders → “Folders open to the other device”.\n\n" +
            "The folder a new rule gets is set in Settings → Folders → “Folder for new rules”.\n\n" +
            "The rest of a rule: the direction (both ways or one way), when to run (manually, on schedule, on connect, on changes) " +
            "and what to do if a file was changed on both sides. Presets fill in common combinations. Masks limit which files take part.\n\n" +
            "“Show what will change” builds a plan without touching anything. “Sync now” carries it out. The switch in a rule's row turns it off without deleting it.\n\n" +
            "Turn “Carry deletions over” on with care: a file deleted on one side disappears on the other too.",
    ),
    "SYNC_CONFLICTS" to HelpText(
        "Conflicts",
        "A conflict is a file changed on both sides since the last sync.\n\n" +
            "The rule says what to do with it: the newest wins, one side wins, rename, add only, or ask.\n\n" +
            "With the “ask” strategy disputed files appear in the Conflicts card with the size and time of change on each side. " +
            "Choose which version to keep, for each file or for all at once: “Keep: here”, “Keep: there”, “Keep both” " +
            "(the second version gets another name), “Keep the newest”. “Skip” puts the decision off until the next sync.",
    ),
    "SYNC_HISTORY" to HelpText(
        "Log and version history",
        "A rule's log shows what its syncs did: which file was added, updated or deleted, when, and what failed.\n\n" +
            "With “Keep previous versions” on in the rule, a file is saved to the version history before it is overwritten. " +
            "Any version can be brought back from there with “Restore this version”; the magnifier shows the versions of one file only. " +
            "“Clear version history” deletes the saved versions and frees the space.",
    ),
    "CLIPBOARD" to HelpText(
        "Shared clipboard",
        "Copy text on one device — paste it on the other. The “Take the clipboard from the other device” button reads its clipboard: " +
            "the text goes to this device's clipboard and to the history. On connecting the clipboard is pulled by itself.\n\n" +
            "In the background, on its own, the clipboard is not sent: Efir does not poll the clipboard, so as not to use the battery or read more than needed.\n\n" +
            "The history keeps recent entries: any of them can be copied again or pinned with the star so that it survives clearing.\n\n" +
            "Android gives the clipboard only to an app that is on the screen: to pull the phone's clipboard, Efir must be open on the phone.",
    ),
    "NOTIFICATIONS" to HelpText(
        "Phone notifications",
        "The phone's notifications are shown on the computer on the Notifications tab of the Clipboard section, if notification access is granted on the phone " +
            "(Settings → Permissions → Access to the phone's notifications).\n\n" +
            "A notification can be dismissed — it goes away on the phone too — and messenger messages can be answered right here.\n\n" +
            "“Hide ongoing” removes music, navigation and downloads. In the Apps list any app whose notifications are not wanted here can be turned off.",
    ),
    "MEDIA" to HelpText(
        "Camera and sound",
        "The Camera and sound section shows the picture from the other device's camera and plays its sound.\n\n" +
            "Choose the main or the front camera and turn it on — the picture appears in the card. " +
            "Sound comes from the microphone or from the system (what is playing on the device).\n\n" +
            "On the phone this needs the camera and microphone permissions (Settings → Permissions), and the Efir app must be running. " +
            "If the camera or the sound did not start, the reason is written under the buttons.",
    ),
    "PHONE" to HelpText(
        "SMS",
        "The Phone section works when the other device is an Android phone.\n\n" +
            "Enter a number and a text and press “Send SMS”. If the phone has two SIM cards, choose the one you need. " +
            "The phone sends a long message as several SMS. Under a message you can see whether it was sent and delivered.\n\n" +
            "Below are the conversations: pressing one opens its messages and fills in the number.\n\n" +
            "The phone needs the SMS permission. If Efir was installed from a file, Android may block it as a “restricted setting” — " +
            "Settings → Permissions says how to lift that.",
    ),
    "CALLS" to HelpText(
        "Calls",
        "The handset icon in the number field starts a call from the phone.\n\n" +
            "An incoming call is shown as a bar at the top of any screen of the computer: you can answer or reject it, and end a call in progress. " +
            "The talk itself goes through the phone.\n\n" +
            "The Call log tab reads incoming, outgoing and missed calls from the phone.\n\n" +
            "The phone needs the “Calls and call log” permission.",
    ),
    "SETTINGS" to HelpText(
        "Settings",
        "The icon in the top bar opens the settings; pressing it again returns to the screen you were on.\n\n" +
            "The settings are a menu of pages: General, Appearance, Folders, Network, Screen sharing, Permissions (on a phone), Log and About. " +
            "Under the name of a page it says what is on it. The Back arrow returns to the menu.\n\n" +
            "The search at the top of the menu finds a setting by a word — “folder”, “theme”, “tray”, “version” — and opens its page.\n\n" +
            "General: the language of the interface, help and hints; how the app works. On a computer: “Close to the tray” — the window's close button hides Efir " +
            "in the notification area and the device stays reachable; “Start with the system”; “One instance only” — a second start shows the window that is already open. " +
            "A right click on the tray icon opens a menu: the window, connecting to a trusted device, sync, files, screen, downloads folder, exit. " +
            "On a phone: “Stay reachable in the background” — Efir accepts connections while it is not on the screen.\n\n" +
            "Appearance: the theme — light, dark or as in the system; how long a hint stays (5 seconds unless changed), and turning hints off.",
    ),
    "FOLDERS" to HelpText(
        "Folders",
        "The Folders page of the settings says where Efir's files live on this device.\n\n" +
            "“Downloads folder” is where what you download from the other device is saved. “Folder for new rules” is the folder of this device put " +
            "into a new sync rule. “Choose a folder” opens a window: the computer's drives at the top (on a phone, the storage and the memory card), " +
            "subfolders below; a path can be typed, D:/Exchange for example, and a new folder created. “Default” brings back the standard folder.\n\n" +
            "“Folders open to the other device” is what the paired device sees in Files and can sync with. " +
            "A computer always opens the user's home folder, a phone its shared storage. “Add a folder” opens one more drive or folder, " +
            "all of D:/ for example; the bin next to an added folder closes it. The downloads folder and the folders of sync rules are open by themselves — otherwise nothing could be written to them.\n\n" +
            "The other device cannot go outside the open folders. Efir's own folder with the pairing keys is never opened.",
    ),
    "PERMISSIONS" to HelpText(
        "Phone permissions",
        "The Permissions page is on a phone. Each permission says what it is for; a green tick means granted, " +
            "a red icon means the feature will not work. Nothing is asked at start: you grant only what you use.\n\n" +
            "“Grant” opens the right system window. “Revoke” takes one permission away, “Revoke all” takes them all. " +
            "Android removes camera, microphone, SMS and calls only when the app closes — Efir warns about it.\n\n" +
            "“Access to all files” lets the other device see the phone's storage. “Screen sharing” is asked by Android again before a show. " +
            "“Input control” is the accessibility service for taps from the other device. “Background work” keeps the system from stopping the connection when the screen is off.\n\n" +
            "If the app was installed from a file, Android 13 and later may block the input service, notification access, SMS and calls: " +
            "the list then shows a “Restricted settings” row that says how to allow them.",
    ),
    "LOG" to HelpText(
        "App log",
        "The Log page of the settings shows what happened in the app: start and version, network, device search, pairing, connection, " +
            "screen sharing, permissions, errors.\n\n" +
            "There are no keys, tokens or contents of files, clipboard, SMS and notifications in the log.\n\n" +
            "If something does not work, send the log: “Copy the whole log” puts it on the clipboard, “Save the log to a file or share it” " +
            "saves a file to the downloads folder on a computer and opens Share on a phone. Take the log on both devices. " +
            "“Clear the log” deletes the entries.",
    ),
    "ABOUT" to HelpText(
        "Version and about the app",
        "Efir's version is written at the top of the help, at the bottom of the settings menu and on the About page. On a computer it is also in the hint of the tray icon.\n\n" +
            "The phone and the computer must have the same version: after updating one device, update the other.\n\n" +
            "The About page also shows this device — its name, platform, address and port — the downloads folder and the licences of the font and icons. " +
            "“Open the help” leads here.\n\n" +
            "The version and the log are what to send when something goes wrong.",
    ),
)
