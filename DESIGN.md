# OrbitFS Android Application — Production UI/UX Design System Specification

This document serves as the absolute, single source of truth for the OrbitFS Android application frontend built with Jetpack Compose (Material Design 3). It details the design tokens, component behavior, state transitions, precise typography, dimensions, color palettes, and step-by-step UX flows screen by screen.

---

## 1. Global Design System Tokens

### 1.1 Color Palette

All color values are defined as 8-digit hexadecimal ARGB strings.

| Token Name | Hex Code | Functional Usage |
| --- | --- | --- |
| `ColorBackground` | `#FF1E1035` | App-wide screen background canvas. |
| `ColorSurface` | `#FF2E1065` | Cards, elevated containers, dialogs, bottom sheets. |
| `ColorSurfaceVariant` | `#FF3B177C` | Active row highlights, selected card fills, hover states. |
| `ColorPrimaryAccent` | `#FF38BDF8` | Sky Blue. Primary buttons, selected icons, active switches, progress indicators. |
| `ColorSecondaryAccent` | `#FF818CF8` | Indigo. Folder icons, secondary chips, active tab text. |
| `ColorHighlight` | `#FFC084FC` | Purple Spark. Live state badges, attention indicators. |
| `ColorTextPrimary` | `#FFFFFFFF` | Pure White. High-emphasis typography (Titles, Headers, Row primary text). |
| `ColorTextSecondary` | `#FF9CA3AF` | Neutral Gray. Medium-emphasis typography (Subtitles, metadata, timestamps). |
| `ColorTextDisabled` | `#FF6B7280` | Muted Gray. Low-emphasis typography, disabled actions. |
| `ColorStatusGreen` | `#FF22C55E` | Online status indicator, successful operation badges. |
| `ColorStatusRed` | `#FFEF4444` | Offline status, destructive delete icons, critical errors. |
| `ColorStatusYellow` | `#FFEAB308` | Active transmission indicator, background sync in-flight. |
| `ColorDivider` | `#1AFFFFFF` | 10% Opacity White. Horizontal separators between list items. |

---

### 1.2 Typography Hierarchy

Strictly follow Material 3 type scale mapping using system default sans-serif (`Roboto`).

| Style Token | Size | Line Height | Weight | Letter Spacing | Color Default |
| --- | --- | --- | --- | --- | --- |
| `TitleLarge` | 22 sp | 28 sp | SemiBold (600) | 0.0 sp | `ColorTextPrimary` |
| `TitleMedium` | 16 sp | 24 sp | Medium (500) | 0.15 sp | `ColorTextPrimary` |
| `TitleSmall` | 14 sp | 20 sp | Medium (500) | 0.1 sp | `ColorTextPrimary` |
| `BodyLarge` | 16 sp | 24 sp | Normal (400) | 0.5 sp | `ColorTextPrimary` |
| `BodyMedium` | 14 sp | 20 sp | Normal (400) | 0.25 sp | `ColorTextSecondary` |
| `BodySmall` | 12 sp | 16 sp | Normal (400) | 0.4 sp | `ColorTextSecondary` |
| `LabelLarge` | 14 sp | 20 sp | Medium (500) | 0.1 sp | `ColorPrimaryAccent` |
| `LabelMedium` | 12 sp | 16 sp | SemiBold (600) | 0.5 sp | `ColorTextSecondary` |
| `LabelSmall` | 10 sp | 14 sp | Medium (500) | 0.5 sp | `ColorTextSecondary` |

---

### 1.3 Layout & Grid Metrics

* **Screen Edge Margins:** `16 dp` horizontal padding on standard screens; `20 dp` on modals/dialogs.
* **Component Corner Radii:**
* Small (Chips, Badges, Text fields): `8 dp`
* Medium (Cards, Dropdown Menus): `16 dp`
* Large (Modal Dialogs): `24 dp`
* Pill / Full Circular (FAB, Status Indicators, Avatars): `50%` (`CircleShape`)


* **Touch Targets:** Minimum touch target area for all clickable icons, list rows, checkboxes, and buttons is `48 dp x 48 dp`.
* **Dividers:** `1 dp` thickness using `ColorDivider`.

---

## 2. Universal Navigation Architecture & Screen Flow

```text
[ App Launch ] ──► [ Screen 1: Node Hub ] ──► [ Screen 1A: Connection Transition ]
                          │
                          ├─► [ Screen 5: Transfers & History ]
                          ├─► [ Screen 6: Settings ]
                          │
                          ▼
             [ Screen 2: File Explorer ] ◄──► [ Screen 2A: Search / Quick Jump ]
                          │
     ┌────────────────────┴────────────────────┐
     │ (Selection Mode)                        │ (Item Tap)
     ▼                                         ▼
[ Screen 3: Contextual Top Bar ]       [ Native Viewer / Local Stream ]
     │
     ├─► [ Screen 4: File Details Popup ]
     ├─► [ Screen 4A: Save To Device Launcher ]
     └─► [ Screen 5: Active Download Stream ]

```

---

## 3. Screen-by-Screen Detailed Design & Action Specifications

### Screen 1: The Node Hub (Home Screen)

#### Purpose

Entry point for managing saved OrbitFS server endpoints, monitoring real-time socket ping health, adding new endpoints, and launching connection sessions.

```text
+-------------------------------------------------------------------+
| (👤) Profile                 ORBIT FS                 [ ⚡ 12ms ] |
+-------------------------------------------------------------------+
| SAVED SERVERS                                       [ + Add Node ]|
|                                                                   |
| +---------------------------------------------------------------+ |
| | 💻  Dev Local Machine                            [ 🟢 8ms ]   | |
| |     192.168.1.45:8080                                         | |
| |     Java 21 NIO Engine · Virtual Threads                      | |
| |                                                               | |
| |                                                [ CONNECT → ]  | |
| +---------------------------------------------------------------+ |
|                                                                   |
| +---------------------------------------------------------------+ |
| | 🗄️  Remote Staging Server                        [ 🔴 Offline ]| |
| |     10.0.0.12:9000                                            | |
| |     Unreachable (Timeout 5000ms)                              | |
| |                                                               | |
| |                                                [ RETRY 🔄 ]   | |
| +---------------------------------------------------------------+ |
+-------------------------------------------------------------------+
| [ 🌐 Nodes ]          [ 📥 Transfers ]        [ ⚙️ Settings ]     |
+-------------------------------------------------------------------+

```

#### Layout & Visual Specifications

1. **Top App Bar (`Height: 64 dp`):**
* **Left:** Profile Avatar placeholder icon (`36 dp x 36 dp` circle, fill: `ColorSecondaryAccent`, icon: `Icons.Default.Person`, tint: `ColorTextPrimary`).
* **Center:** Title `"ORBIT FS"` (`TitleMedium`, `ColorTextPrimary`).
* **Right:** Global Ping Indicator pill (`Container: ColorSurface`, `Padding: 6 dp horizontal, 4 dp vertical`, `Shape: 8 dp radius`). Displays icon `Icons.Default.⚡` (`ColorPrimaryAccent`) + `"12ms"` (`LabelSmall`, `ColorTextPrimary`).


2. **Section Header (`Height: 32 dp`):**
* Left text `"SAVED SERVERS"` (`LabelMedium`, `ColorTextSecondary`).
* Right button `"[ + Add Node ]"` (`LabelLarge`, `ColorPrimaryAccent`, zero elevation).


3. **Node Cards (`LazyColumn` item, `Margin: 12 dp bottom`):**
* **Container:** Background `ColorSurface`, Corner radius `16 dp`, Padding `16 dp`.
* **Card Row 1 (Header):**
* Left Icon: `Icons.Default.Computer` or `Icons.Default.Storage` (`24 dp`, tint: `ColorPrimaryAccent`).
* Title: Node Custom Name (e.g., `"Dev Local Machine"`) (`TitleMedium`, `ColorTextPrimary`).
* Right Pill: Status Indicator.
* If Online: Dot (`8 dp` circle, fill: `ColorStatusGreen`) + `"8ms"` (`LabelSmall`, `ColorStatusGreen`).
* If Offline: Dot (`8 dp` circle, fill: `ColorStatusRed`) + `"Offline"` (`LabelSmall`, `ColorStatusRed`).




* **Card Row 2 (Details):** `"192.168.1.45:8080"` (`BodySmall`, `ColorTextSecondary`).
* **Card Row 3 (Metadata):** `"Java 21 NIO Engine · Virtual Threads"` (`BodySmall`, `ColorTextDisabled`).
* **Card Row 4 (Action):** Button aligned right.
* Enabled State (Online): Fill `ColorPrimaryAccent`, Text `"CONNECT →"` (`LabelLarge`, `ColorBackground`, Bold). Height `36 dp`.
* Disabled State (Offline): Outlined border `1 dp ColorStatusRed`, Text `"RETRY 🔄"` (`LabelLarge`, `ColorStatusRed`).




4. **Bottom Navigation Bar (`Height: 56 dp`):**
* Background `ColorSurface`, top border `1 dp ColorDivider`.
* 3 Equal Items: `[🌐 Nodes]` (Active: `ColorPrimaryAccent`), `[📥 Transfers]` (Inactive: `ColorTextSecondary`), `[⚙️ Settings]` (Inactive: `ColorTextSecondary`).



#### Component Actions & State Rules

* **Action: Tap `[ + Add Node ]**
* *Trigger:* Opens "Add Endpoint Dialog" overlay (`ModalDialog`, `Width: 320 dp`).
* *Fields:* "Label (Optional)" (`OutlinedTextField`), "Host/IP (Required)" (`OutlinedTextField`), "Port (Required)" (`OutlinedTextField`, Number keyboard).
* *Buttons:* `"Cancel"` (`TextButton`, `ColorTextSecondary`), `"Save Endpoint"` (`Button`, `ColorPrimaryAccent`).


* **Action: Tap `[ CONNECT → ]` on Card**
* *Trigger:* Saves selected endpoint in active session and immediately triggers transition to **Screen 1A: Connection Transition State**.


* **Action: Tap `[ RETRY 🔄 ]` on Card**
* *Trigger:* Emits socket ping attempt (`pingMs`) to target Host:Port. Card status pill shows yellow pulsing indicator during socket handshaking.



---

### Screen 1A: Connection Transition Modal

#### Purpose

Displays deterministic blocking visual feedback while the client application establishes a raw TCP socket connection, frames headers, and receives the initial `listDir` payload.

```text
+-------------------------------------------------------------------+
| Connecting to Dev Local Machine...                                |
|                                                                   |
| [ ⏳ Establishing TCP Socket on 192.168.1.45:8080 ]               |
| Status: Exchanging protocol framing headers...                    |
|                                                                   |
| ██████████████████░░░░░░░░░░░░░░░░░░░░░░░░░                       |
|                                                                   |
|                                                       [ Cancel ]  |
+-------------------------------------------------------------------+

```

#### Layout & Visual Specifications

* **Modal Overlay:** `ColorBackground` with `80%` opacity backdrop blur.
* **Dialog Window:** Width `320 dp`, Background `ColorSurface`, Corner Radius `24 dp`, Padding `24 dp`.
* **Title:** `"Connecting to Dev Local Machine..."` (`TitleLarge`, `ColorTextPrimary`).
* **Sub-status 1:** `"Establishing TCP Socket on 192.168.1.45:8080"` (`BodyMedium`, `ColorPrimaryAccent`).
* **Sub-status 2:** `"Status: Exchanging protocol framing headers..."` (`BodySmall`, `ColorTextSecondary`).
* **Progress Bar:** Linear Progress Indicator, Height `6 dp`, Corner Radius `3 dp`, Track Color `ColorBackground`, Progress Indicator Color `ColorPrimaryAccent`.
* **Cancel Button:** Aligned bottom-right. Text `"Cancel"` (`LabelLarge`, `ColorStatusRed`).

#### Component Actions & State Rules

* **State Event: Socket Established & Root Dir Payload Received**
* *Transition:* Instantly dismisses modal and pushes **Screen 2: File Explorer Mode** onto screen stack with smooth horizontal slide animation.


* **State Event: Socket Timeout / Refused**
* *Transition:* Modal closes; Node Hub Card updates to `ColorStatusRed` (`"Offline · Connection Refused"`). Displays a `Snackbar` (`"Failed to connect to 192.168.1.45:8080"`).


* **Action: Tap `[ Cancel ]**
* *Trigger:* Cancels active coroutine socket job, closes modal, returns to Screen 1.



---

### Screen 2: File Explorer Mode (Normal State)

#### Purpose

Primary interface for exploring directory listings exposed by the OrbitFS server node using interactive path breadcrumbs.

```text
+-------------------------------------------------------------------+
| ← [ Dev Local Machine ]          [ 🔍 ]       [ 🟢 192.168.1.45 ] |
+-------------------------------------------------------------------+
| 📂 root  >  tmp  >  orbitfs_sandbox  >  project                    |
+-------------------------------------------------------------------+
| [ ]  📁  src/                                    Today, 11:20 AM  |
| ----------------------------------------------------------------- |
| [ ]  📁  build/                                  Yesterday        |
| ----------------------------------------------------------------- |
| [ ]  📄  app.log                                 42.8 KB · 11:42 AM |
| ----------------------------------------------------------------- |
| [ ]  📄  database.db                             142.5 MB · 10:15AM|
+-------------------------------------------------------------------+
| [ ⬆️ Upload File ]                              [ 📁 New Dir ]    |
+-------------------------------------------------------------------+

```

#### Layout & Visual Specifications

1. **Top App Bar (`Height: 64 dp`):**
* **Left:** Back Navigation Arrow (`Icons.Default.ArrowBack`, tint: `ColorTextPrimary`). Tapping closes active session and returns to Screen 1.
* **Title Block:** Server Name `"Dev Local Machine"` (`TitleMedium`, `ColorTextPrimary`).
* **Action 1:** Search Icon (`Icons.Default.Search`, `24 dp`, tint: `ColorTextPrimary`).
* **Action 2 (Right):** Connection Status Pill (`Background: ColorSurface`, `Padding: 4 dp horizontal`, `Shape: 8 dp`). Green dot (`6 dp`) + `"192.168.1.45"` (`LabelSmall`, `ColorTextPrimary`).


2. **Interactive Breadcrumb Bar (`Height: 40 dp`):**
* Background `ColorBackground`, padding `12 dp horizontal`.
* Horizontal scrollable `LazyRow` of items separated by `">"` (`BodySmall`, `ColorTextDisabled`).
* Items: `"root"`, `"tmp"`, `"orbitfs_sandbox"`, `"project"`. Text style `BodySmall`, Color `ColorPrimaryAccent`.


3. **File Listing (`LazyColumn`):**
* Row Height `56 dp`, Padding `16 dp horizontal`. Separated by `1 dp ColorDivider`.
* **Element 1 (Far Left): Checkbox Target (`Touch Target: 48 dp x 48 dp`):**
* Unchecked: `Icons.Default.CheckBoxOutlineBlank`, `20 dp`, tint: `ColorTextDisabled`.


* **Element 2: Type Icon (`24 dp`):**
* Directory: `Icons.Default.Folder`, tint: `ColorSecondaryAccent`.
* File: `Icons.Default.InsertDriveFile`, tint: `ColorPrimaryAccent`.


* **Element 3: Title & Subtitle Column (Weight 1):**
* Primary Row: Item Name (e.g., `"database.db"`) (`BodyLarge`, `ColorTextPrimary`, single line, ellipsis).
* Secondary Row: If directory: `"Directory"`. If file: `"142.5 MB · Today, 10:15 AM"` (`BodySmall`, `ColorTextSecondary`).




4. **Bottom Dock Bar (`Height: 64 dp`):**
* Background `ColorSurface`, top border `1 dp ColorDivider`, padding `12 dp horizontal`.
* Button 1 (Left): `"[ ⬆️ Upload File ]"` (`Button`, Fill: `ColorSurfaceVariant`, Content: `ColorPrimaryAccent`, Text `LabelMedium`).
* Button 2 (Right): `"[ 📁 New Dir ]"` (`Button`, Fill: `ColorSurfaceVariant`, Content: `ColorPrimaryAccent`, Text `LabelMedium`).



#### Component Actions & State Rules

* **Action: Short Tap on File Row Body / Text**
* *If Item is Directory:* Emits `listDir(path)` RPC call. Breadcrumb updates; list refreshes with new directory contents.
* *If Item is File:* Triggers system native intent / preview reader for the selected file stream.


* **Action: Tap Far-Left Checkbox `[ ]` OR Long-Press Row**
* *Trigger:* Adds item ID to `selectedItems` state array (`count >= 1`). Screen instantly transforms into **Screen 3: File Explorer Mode (Selection State)**.


* **Action: Tap Breadcrumb Segment (e.g., `"tmp"`**
* *Trigger:* Truncates active path to `/tmp/`, emits `listDir("/tmp/")`, re-renders file listing.


* **Action: Tap Search Icon `[ 🔍 ]**
* *Trigger:* Opens **Screen 2A: Search / Quick Jump Modal**.



---

### Screen 2A: Search & Quick Jump Modal

#### Purpose

Allows real-time filtering of active directory contents or direct manual path navigation.

```text
+-------------------------------------------------------------------+
| 🔍  Search or enter path: /tmp/orbitfs/                        [✕]|
+-------------------------------------------------------------------+
| QUICK JUMP TO PATH                                                |
|  [ Go ]  /tmp/orbitfs_sandbox/project/database.db                |
+-------------------------------------------------------------------+
| MATCHING FILES & DIRECTORIES                                      |
|                                                                   |
|  📁  project/                        /tmp/orbitfs_sandbox/        |
|  📄  app.log                         /tmp/orbitfs_sandbox/project |
|  📄  database.db                     /tmp/orbitfs_sandbox/project |
+-------------------------------------------------------------------+

```

#### Layout & Visual Specifications

* **Full-screen Overlay Window:** Background `ColorBackground`.
* **Search Input Field (`Height: 56 dp`):**
* Margin `16 dp`, Shape `16 dp radius`, Background `ColorSurface`.
* Leading Icon: `Icons.Default.Search` (`ColorPrimaryAccent`).
* Trailing Icon: `Icons.Default.Close` (`ColorTextSecondary`). Clears input or closes search when empty.
* Text Style: `BodyLarge`, `ColorTextPrimary`. Hint: `"Search or enter path..."`.


* **Quick Jump Section:**
* Displayed when input string starts with `/`.
* Row container: `ColorSurface`, Padding `12 dp`. Button `"[ Go ]"` (`ColorPrimaryAccent`).


* **Results List (`LazyColumn`):**
* Matching File/Directory rows displaying name + parent path (`BodySmall`, `ColorTextSecondary`).



#### Component Actions & State Rules

* **Action: Tap Match Result Item**
* *Trigger:* Closes search modal. Navigates File Explorer directly to item parent path and highlights target row.


* **Action: Tap `[ Go ]` on Manual Path**
* *Trigger:* Issues `stat(manualPath)` RPC. If valid, navigates directly to target directory.



---

### Screen 3: File Explorer Mode (Selection State)

#### Purpose

Contextual operation mode activated when one or more items are checked. Standard Top Bar morphs into an action bar with an overflow kebab menu (`⋮`).

```text
+-------------------------------------------------------------------+
| [ ✕ ]  1 Selected                                           [ ⁄ ] |
+-------------------------------------------------------------------+
| 📂 root  >  tmp  >  orbitfs_sandbox  >  project                    |
+-------------------------------------------------------------------+
| [ ]  📁  src/                                  Today, 11:20 AM    |
| ----------------------------------------------------------------- |
| [ ]  📁  build/                                Yesterday          |
| ----------------------------------------------------------------- |
| [✓]  📄  database.db                           142.5 MB · 10:15 AM|
|      └─ Container Fill: ColorSurfaceVariant                        |
| ----------------------------------------------------------------- |
| [ ]  📄  temp_chunk_0.bin                      1.2 MB · Just now  |
+-------------------------------------------------------------------+
| [ ⬆️ Upload File ]                            [ 📁 New Dir ]     |
+-------------------------------------------------------------------+

```

#### Layout & Visual Specifications

1. **Contextual Top App Bar (`Height: 64 dp`):**
* **Background Color Shift:** Animates smoothly from `ColorBackground` to `ColorSurfaceVariant`.
* **Left Action:** Clear Selection Close Icon `Icons.Default.Close` (`24 dp`, tint: `ColorTextPrimary`).
* **Title:** Selection Counter (e.g., `"1 Selected"` or `"3 Selected"`) (`TitleMedium`, `ColorTextPrimary`, Bold).
* **Right Action:** Kebab Menu Icon `Icons.Default.MoreVert` (`24 dp`, tint: `ColorPrimaryAccent`).


2. **Selected File Row Visuals:**
* Row Background: Changes from transparent to `ColorSurfaceVariant`.
* Checkbox Target (Far Left): Checked State `Icons.Default.CheckBox` (`20 dp`, tint: `ColorPrimaryAccent`).



#### Contextual Dropdown Menu Specification (Tapping `[ ⁄ ]`)

* Dropdown Menu Anchor: Top right overflow button. Background `ColorSurface`, Shape `16 dp radius`, Elevation `8 dp`.

```text
                                                             [ ⁄ ]
                                      +--------------------------+
                                      | 📊 View Stat / Info      |
                                      | 💾 Save to Device...     |
                                      | 📤 Share File            |
                                      | ------------------------ |
                                      | 🗑️ Delete Selected       |
                                      +--------------------------+

```

* **Menu Item 1:** Icon `Icons.Default.Info` + Text `"View Stat / Info"` (`BodyMedium`, `ColorTextPrimary`). *(Disabled if multiple items selected)*.
* **Menu Item 2:** Icon `Icons.Default.SaveAlt` + Text `"Save to Device..."` (`BodyMedium`, `ColorTextPrimary`).
* **Menu Item 3:** Icon `Icons.Default.Share` + Text `"Share File"` (`BodyMedium`, `ColorTextPrimary`).
* Divider: `1 dp ColorDivider`.
* **Menu Item 4 (Destructive):** Icon `Icons.Default.Delete` + Text `"Delete Selected"` (`BodyMedium`, `ColorStatusRed`).

#### Component Actions & State Rules

* **Action: Tap `[ ✕ ]` in Contextual Top Bar**
* *Trigger:* Clears `selectedItems` state array (`count = 0`). Top Bar instantly reverts to normal File Explorer state.


* **Action: Tap Additional Row Checkboxes**
* *Trigger:* Increments counter (e.g., `"2 Selected"`). If count > 1, menu items `"View Stat / Info"` and `"Share File"` become disabled/grayed out.


* **Action: Select `"View Stat / Info"**
* *Trigger:* Emits `stat(selectedFilePath)` RPC call. Displays **Screen 4: File Details Popup**.


* **Action: Select `"Save to Device..."**
* *Trigger:* Launches Android System `ActivityResultContracts.CreateDocument()` (Storage Access Framework picker). Once target URI is picked, launches background RPC download streaming task.


* **Action: Select `"Delete Selected"**
* *Trigger:* Displays Guarded Delete Confirmation Modal (`"Delete 1 item permanently from server?"`). Confirming sends `delete(path)` RPC call.



---

### Screen 4: File Details Popup (`View Stat / Info`)

#### Purpose

Modal dialog presenting full canonical file metadata retrieved from the OrbitFS server stat RPC response, featuring an explicit path copy mechanism.

```text
+-------------------------------------------------------------------+
| 📊 File Details                                              [✕]  |
+-------------------------------------------------------------------+
| 📄 database.db                                                    |
| 142.5 MB (149,422,080 bytes)                                      |
+-------------------------------------------------------------------+
| FILE SYSTEM METADATA                                              |
| • File Type:      SQLite Database File                            |
| • Last Modified:  Sep 12, 2026 · 10:15 AM                         |
| • Permissions:    rw-r--r-- (0644)                                |
| • Owner / Group:  orbitfs:orbitfs                                |
+-------------------------------------------------------------------+
| CANONICAL SERVER PATH                                             |
| +---------------------------------------------------------------+ |
| | /tmp/orbitfs_sandbox/project/database.db             [📄📄]   | |
| +---------------------------------------------------------------+ |
+-------------------------------------------------------------------+
|                                                       [ Close ]   |
+-------------------------------------------------------------------+

```

#### Layout & Visual Specifications

* **Modal Dialog Window:** Width `340 dp`, Shape `24 dp radius`, Background `ColorSurface`, Padding `24 dp`.
* **Header Bar:** Title `"📊 File Details"` (`TitleLarge`, `ColorTextPrimary`) + Right Close Icon `Icons.Default.Close` (`ColorTextSecondary`).
* **Summary Block:**
* File Name: `"database.db"` (`TitleMedium`, `ColorPrimaryAccent`).
* Exact Byte Size: `"142.5 MB (149,422,080 bytes)"` (`BodySmall`, `ColorTextSecondary`).


* **Metadata Section:**
* Header: `"FILE SYSTEM METADATA"` (`LabelSmall`, `ColorSecondaryAccent`, Bold).
* Key-Value Rows (`BodyMedium`): `File Type`, `Last Modified`, `Permissions`, `Owner / Group`.


* **Canonical Path Display Box:**
* Background: Inner container `ColorBackground`, Shape `8 dp radius`, Padding `10 dp`.
* Layout: Row containing Path Text (`BodyMedium`, `ColorTextPrimary`, max 2 lines, ellipsis) + Right Copy Icon (`Touch Target: 48 dp`).
* **Copy Icon Definition:** Two overlapping paper sheets icon `Icons.Default.ContentCopy` (`20 dp`, tint: `ColorPrimaryAccent`).



#### Component Actions & State Rules

* **Action: Tap Overlapping Paper Copy Icon `[📄📄]**
* *Trigger:* Copies raw string `/tmp/orbitfs_sandbox/project/database.db` directly to Android `ClipboardManager`.
* *Feedback:* Shows Toast notification (`"Canonical path copied to clipboard"`). Icon briefly flashes `ColorStatusGreen`.



---

### Screen 5: Transfers & Downloads Screen

#### Purpose

Centralized dashboard tracking active TCP chunk streaming operations, live progress percentage, data throughput speed, and past download/upload history.

```text
===================================================================
 ← Transfers & Downloads                              [ 🗑️ Clear ]
===================================================================
 [ ⏳ Active (1) ]                           [ 📜 History (12) ]
-------------------------------------------------------------------

  ACTIVE TRANSFERS
  +-------------------------------------------------------------+
  | 📄 database.db                                              |
  |    From: Dev Local Machine (192.168.1.45)                   |
  |    59.8 MB / 142.5 MB (42%) · 4.2 MB/s                       |
  |    ████████████████░░░░░░░░░░░░░░░░░░░░░░░░                 |
  |                                                [ ✕ Cancel ] |
  +-------------------------------------------------------------+

  RECENT HISTORY
  +-------------------------------------------------------------+
  | 📁 project_assets.zip                           [ ✓ Saved ] |
  |    Saved to: /storage/emulated/0/Download/                  |
  |    45.2 MB · Completed Sep 12, 2026, 11:30 AM                |
  +-------------------------------------------------------------+
  | 📄 app.log                                      [ ✕ Failed ]|
  |    Error: Socket connection reset                           |
  |    Failed Sep 11, 2026, 04:15 PM               [ 🔄 Retry ] |
  +-------------------------------------------------------------+

```

#### Layout & Visual Specifications

1. **Top Bar (`Height: 64 dp`):**
* Title `"Transfers & Downloads"` (`TitleMedium`, `ColorTextPrimary`).
* Right Action: `"[ 🗑️ Clear ]"` (`LabelMedium`, `ColorTextSecondary`). Clears completed history items.


2. **Segmented Tab Control (`Height: 48 dp`):**
* Tab 1: `"⏳ Active (1)"` (`ColorPrimaryAccent`, indicator line `2 dp`).
* Tab 2: `"📜 History (12)"` (`ColorTextSecondary`).


3. **Active Transfer Card (`Margin: 12 dp bottom`):**
* Background `ColorSurface`, Padding `16 dp`, Shape `16 dp radius`.
* Title: File Name (`TitleMedium`, `ColorTextPrimary`).
* Source: `"From: Dev Local Machine (192.168.1.45)"` (`BodySmall`, `ColorTextSecondary`).
* Progress Stats: `"59.8 MB / 142.5 MB (42%) · 4.2 MB/s"` (`BodySmall`, `ColorPrimaryAccent`).
* Right Action: `"Cancel"` Icon `Icons.Default.Cancel` (`ColorStatusRed`).
* Linear Progress Indicator: Height `8 dp`, Shape `4 dp radius`, Track `ColorBackground`, Indicator `ColorPrimaryAccent`.


4. **History Cards:**
* Completed Item: Trailing badge `"[ ✓ Saved ]"` (`ColorStatusGreen`).
* Failed Item: Trailing badge `"[ ✕ Failed ]"` (`ColorStatusRed`) + Button `"[ 🔄 Retry ]"`.



#### Component Actions & State Rules

* **Action: Tap `[ ✕ Cancel ]` on Active Transfer**
* *Trigger:* Cancels chunk reader stream coroutine, closes target file handle, marks item as `"Cancelled"` in History.



---

### Screen 6: Settings Screen

#### Purpose

Configuration panel for socket connection timeouts, TCP chunk streaming buffer sizes, LRU client memory cache limits, and guarded deletion prompts.

```text
===================================================================
 ← Settings                                                       
===================================================================

  NETWORK & RPC
  +-------------------------------------------------------------+
  |  Socket Timeout                                    5000 ms  |
  |  Time before socket drops connection                        |
  +-------------------------------------------------------------+
  |  Chunk Read Size                                    256 KB  |
  |  Buffer size per TCP frame stream                           |
  +-------------------------------------------------------------+

  CACHE & STORAGE
  +-------------------------------------------------------------+
  |  Client Memory Cache                               512 MB  |
  |  Max memory allocated for file chunk caching                |
  +-------------------------------------------------------------+
  |  Clear Local Cache                           [ Clear Now ] |
  |  Currently using 128 MB across active sessions              |
  +-------------------------------------------------------------+

  SAFETY & DELETION
  +-------------------------------------------------------------+
  |  Guarded File Deletion                              [ ON ]  |
  |  Require confirmation before executing delete RPC           |
  +-------------------------------------------------------------+

  APPEARANCE & ABOUT
  +-------------------------------------------------------------+
  |  App Theme                                     Dark Violet  |
  +-------------------------------------------------------------+
  |  OrbitFS Client Protocol                         v1.4.0     |
  +-------------------------------------------------------------+

```

#### Layout & Visual Specifications

* **Structure:** `LazyColumn` grouped into visual setting cards (`Background: ColorSurface`, `Shape: 16 dp radius`, `Margin: 16 dp bottom`).
* **Setting Row (`Height: 64 dp`):**
* Title: (`BodyLarge`, `ColorTextPrimary`).
* Subtitle: (`BodySmall`, `ColorTextSecondary`).
* Control Element (Right):
* Numeric Fields: Value Pill (e.g., `"5000 ms"`) (`LabelMedium`, `ColorPrimaryAccent`). Tapping opens input dialog.
* Toggle Fields: Material 3 `Switch` (Checked track: `ColorPrimaryAccent`, thumb: `ColorBackground`).





---

## 4. Summary Checklist for Implementation Consistency

When implementing any screen within Jetpack Compose, ensure the following constraints are honored:

1. **Colors:** Strictly use tokens defined in Section 1.1 (Base `#1E1035`, Surface `#2E1065`, Accent `#38BDF8`).
2. **Touch Targets:** Minimum `48 dp x 48 dp` target area for checkboxes and icons.
3. **Selection Mode:** Far-left checkboxes toggle item inclusion; Top Bar morphs into `ColorSurfaceVariant` with item count and right kebab menu `[ ⁄ ]`.
4. **Canonical Path Copying:** File details modal must use the stacked paper icon `Icons.Default.ContentCopy` (`[📄📄]`) at the right of the path block to execute copy operations.
