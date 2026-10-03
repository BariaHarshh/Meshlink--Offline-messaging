---
phase: 6a
plan: 1
type: implementation
autonomous: true
wave: 1
---

# PHASE 6A — MeshLink Design System + Core Navigation + 4 Screens

## Objective
Replace the emergency-red design system with a soft pastel MeshLink design language.
Introduce a 4-tab bottom navigation (Home / Chats / Mesh / Profile).
Redesign 4 primary screens: Home, Chats, Chat Detail, Mesh Network.
Add DeliveryStatus visual indicators.
Create reusable components.
DO NOT touch core/crypto, core/data security, MeshRouter, ACK/retry logic, Room schema, or database.

## Context Files
- app/src/main/java/com/meshlink/app/ui/theme/Color.kt
- app/src/main/java/com/meshlink/app/ui/theme/Theme.kt
- app/src/main/java/com/meshlink/app/ui/theme/Type.kt
- app/src/main/java/com/meshlink/app/ui/navigation/Screen.kt
- app/src/main/java/com/meshlink/app/ui/navigation/MeshLinkNavHost.kt
- app/src/main/java/com/meshlink/app/ui/navigation/BottomNavBar.kt
- app/src/main/java/com/meshlink/app/MainActivity.kt
- app/src/main/java/com/meshlink/app/ui/home/HomeScreen.kt
- app/src/main/java/com/meshlink/app/ui/home/HomeViewModel.kt
- app/src/main/java/com/meshlink/app/ui/chat/ChatScreen.kt
- app/src/main/java/com/meshlink/app/ui/chat/ChatViewModel.kt
- app/src/main/java/com/meshlink/app/ui/discovery/DiscoveryScreen.kt
- app/src/main/java/com/meshlink/app/ui/discovery/DiscoveryViewModel.kt
- app/src/main/java/com/meshlink/app/ui/components/MeshAvatar.kt
- core/domain/src/main/java/com/meshlink/app/domain/model/DeliveryStatus.kt
- core/domain/src/main/java/com/meshlink/app/domain/model/Message.kt
- core/domain/src/main/java/com/meshlink/app/domain/model/ConnectionState.kt
- core/domain/src/main/java/com/meshlink/app/domain/model/KnownDevice.kt
- core/domain/src/main/java/com/meshlink/app/domain/model/VerificationStatus.kt
- core/domain/src/main/java/com/meshlink/app/domain/repository/UserProfileManager.kt

## Architecture Rules (HARD CONSTRAINTS)
- DO NOT modify any file in core/crypto/
- DO NOT modify any file in core/mesh/
- DO NOT modify core/data/src/main/java/com/meshlink/app/data/local/ (entities, DAOs, migrations)
- DO NOT modify core/data/src/main/java/com/meshlink/app/data/security/
- DO NOT modify NearbyRepositoryImpl.kt routing/networking behavior
- DO NOT modify MeshRouter.kt, RoutingTable.kt, SeenMessageCache.kt, PacketValidator.kt
- DO NOT change DeliveryStatus enum values
- DO NOT change ConnectionState enum values
- DO NOT change Message data class fields
- DO NOT change MessageRepository interface or its implementations
- UI must only observe state — no business logic in Composables

## Tasks

### Task 1: Update Design System (Color.kt, Theme.kt, Type.kt, new Shape.kt)
Replace the emergency-red palette with the MeshLink pastel design system.

**Color.kt** — Replace all colors with:
```
Background:   #FBF2FB  (soft warm lavender/pink-white)
Surface:      #FFFFFF  (warm white)
SurfaceVariant: #F5EEF5 (slightly tinted off-white for cards)
Primary:      #C4717A  (dusty rose / muted crimson)
PrimaryContainer: #FADADD (light rose container)
Secondary:    #A87BAF  (soft lavender/purple)
SecondaryContainer: #EDE0F0
Tertiary:     #6A9E72  (soft green — success/connected)
TertiaryContainer: #D6EDD9
TextPrimary:  #1A0F1E  (near-black with warm tone)
TextSecondary: #6B5D6E (muted mauve/gray)
TextMuted:    #B8A9BB  (very muted)
Outline:      #E8DCE8  (soft lavender dividers)
OutlineVariant: #F0E8F0
Error:        #B85C5C  (muted red, not harsh)
ErrorContainer: #FADADD
```

Shape.kt — Create new file with centralized corner radii:
```kotlin
object MeshLinkShapes {
    val Small = 12.dp
    val Medium = 18.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
    val Full = 50.dp
}
```

Type.kt — Keep existing scale but update comments, no font changes needed.

Theme.kt — Update lightColorScheme with new colors. Keep MeshLinkTheme composable.

**Files to modify:**
- app/src/main/java/com/meshlink/app/ui/theme/Color.kt (full rewrite)
- app/src/main/java/com/meshlink/app/ui/theme/Theme.kt (update color scheme)
- app/src/main/java/com/meshlink/app/ui/theme/Type.kt (minor update to comments)
- app/src/main/java/com/meshlink/app/ui/theme/Shape.kt (NEW FILE)

**Verification:** File compiles — no syntax errors. Palette is coherent.

### Task 2: Create Reusable Components
Create the component library in `app/src/main/java/com/meshlink/app/ui/components/`

**MeshAvatar.kt** — Update avatar color palette to match new pastel theme. Keep logic identical.

**DeliveryStatusIndicator.kt** (NEW) — Composable that renders delivery status icon + label:
- PENDING → Clock icon, muted color, "Preparing..."
- QUEUED → Circle with dots, amber-ish, "Waiting for nearby device"
- SENT → Single check, primary, "Sent"
- DELIVERED → Double check, green, "Delivered"
- FAILED → Warning triangle, error color, "Couldn't deliver"

Signature:
```kotlin
@Composable
fun DeliveryStatusIndicator(
    status: DeliveryStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    compact: Boolean = true
)
```

**SecurityBadge.kt** (NEW) — Composable for E2E encryption indicator:
```kotlin
@Composable
fun SecurityBadge(
    isEncrypted: Boolean,
    verificationStatus: VerificationStatus? = null,
    modifier: Modifier = Modifier
)
```
Shows: "🔒 End-to-end encrypted" with optional verification state.
If VerificationStatus.VERIFIED → show verified indicator.
If UNVERIFIED or UNKNOWN → do NOT show "verified".
If REVOKED → show warning prominently.

**MeshLinkCard.kt** (NEW) — Base card composable:
```kotlin
@Composable
fun MeshLinkCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    elevation: Dp = 0.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
)
```

**Files:**
- app/src/main/java/com/meshlink/app/ui/components/MeshAvatar.kt (modify palette)
- app/src/main/java/com/meshlink/app/ui/components/DeliveryStatusIndicator.kt (NEW)
- app/src/main/java/com/meshlink/app/ui/components/SecurityBadge.kt (NEW)
- app/src/main/java/com/meshlink/app/ui/components/MeshLinkCard.kt (NEW)

**Verification:** Components compile. DeliveryStatusIndicator covers all 5 states.

### Task 3: Update Navigation — 4-Tab Bottom Nav + New Screens

**Screen.kt** — Add new destinations:
```kotlin
data object HomeTab : Screen("home_tab")
data object Chats : Screen("chats")
data object MeshNetwork : Screen("mesh_network")
data object ProfileTab : Screen("profile_tab")
// Keep existing:
// Chat, MedicalProfile, Broadcast
// Rename old Home to something internal if needed
```
Keep Chat route with deviceId/deviceName args unchanged.

**BottomNavBar.kt** — Replace 3-tab with 4-tab:
- Home (house icon)
- Chats (chat bubble icon)
- Mesh (wifi/network icon)
- Profile (person icon)

Use the new pastel color scheme for selected/unselected states.
Selected item: primary color + pill indicator.
Unselected: muted text color.
Bottom nav background: white/surface with subtle top border.

**MeshLinkNavHost.kt** — Add all new composable routes. Keep Chat, MedicalProfile, Broadcast routes working.

**MainActivity.kt** — Update bottomNavRoutes to match new 4-tab routes.

**Files:**
- app/src/main/java/com/meshlink/app/ui/navigation/Screen.kt (modify)
- app/src/main/java/com/meshlink/app/ui/navigation/BottomNavBar.kt (full rewrite)
- app/src/main/java/com/meshlink/app/ui/navigation/MeshLinkNavHost.kt (modify)
- app/src/main/java/com/meshlink/app/MainActivity.kt (minor update)

**Verification:** App compiles. Can navigate between all 4 tabs. Chat route still works.

### Task 4: Redesign HomeScreen → Dashboard

Transform HomeScreen.kt into a genuine MeshLink home dashboard.

**Layout:**
```
[ Status Bar Spacer ]
[ Greeting: "Good morning, Harsh" ]   [ Avatar ]
[ Mesh Network Status Card ]
  ● Mesh Network Active / Offline
  4 nearby peers · 2 routes
  Network healthy →
[ "Recent Conversations" header + "See all" link ]
[ 3 most-recent conversations with delivery status ]
[ Empty state if no conversations ]
```

**Data bindings (use existing HomeViewModel):**
- `viewModel.conversations` — real conversation list
- `viewModel.peerCount` — real peer count from NearbyRepository
- User greeting: call `UserProfileManager.getDisplayName()` for the name
  → Inject `UserProfileManager` into HomeViewModel OR read it from a new simple state

**Greeting logic:**
- Use current hour to pick "Good morning / Good afternoon / Good evening"
- Name: inject UserProfileManager into HomeViewModel (it already uses @HiltViewModel and DI)
  Add: `@Named` or inject `UserProfileManager` and expose `val userName: String`

**Mesh Network Status Card:**
- If peerCount > 0: "● Mesh Network Active" (green dot)
- If peerCount == 0: "○ No nearby peers" (gray)
- Show peerCount: "N nearby peers"
- Routes: if unavailable from VM, omit gracefully
- Card should be visually prominent: rounded 24dp, full width, soft shadow

**Recent Conversations (max 3):**
Each row:
- MeshAvatar (peer name initials)
- Peer name (bold)
- Last message snippet (muted, 1 line)
- Timestamp (right)
- DeliveryStatusIndicator (compact, icon only)

**HomeViewModel.kt** — Add UserProfileManager injection:
```kotlin
@Named or regular @Inject val userProfileManager: UserProfileManager
val userName: String get() = userProfileManager.getDisplayName()
```
Also expose `val userName: String` as a simple val, not a StateFlow (it's read-once sync).

Do NOT add fake route count. Omit it from the card if not available.

**Files:**
- app/src/main/java/com/meshlink/app/ui/home/HomeScreen.kt (full redesign)
- app/src/main/java/com/meshlink/app/ui/home/HomeViewModel.kt (add UserProfileManager)

**Verification:** Home tab shows real peer count, real conversations. Greeting uses real name.

### Task 5: Create ChatsListScreen

Create a new dedicated Chats screen at:
`app/src/main/java/com/meshlink/app/ui/chats/ChatsListScreen.kt`

This replaces the "chat list" responsibility of the old HomeScreen.

**Layout:**
```
[ Top: "Chats" title + search icon ]
[ Search bar (rounded, pastel bg) — filters locally ]
[ Conversation list ]
  Each card:
    [ Avatar | Name + timestamp | Last message + DeliveryStatus ]
[ FAB or empty state ]
```

**ChatsListViewModel.kt** — Create at `app/src/main/java/com/meshlink/app/ui/chats/ChatsListViewModel.kt`

It can reuse the same logic as HomeViewModel (same repos, same Conversation data class).
```kotlin
@HiltViewModel
class ChatsListViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val nearbyRepository: NearbyRepository,
    @Named("localDeviceId") private val localDeviceId: String
) : ViewModel() {
    val conversations: StateFlow<List<Conversation>> = ...  // same logic as HomeViewModel
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    val filteredConversations: StateFlow<List<Conversation>> = combine(conversations, searchQuery) { convs, q ->
        if (q.isBlank()) convs else convs.filter { 
            it.deviceName.contains(q, ignoreCase = true) || it.lastMessage.contains(q, ignoreCase = true)
        }
    }.stateIn(...)
    
    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
}
```

**Conversation data class** — Add deliveryStatus to Conversation in HomeViewModel:
```kotlin
data class Conversation(
    val deviceId: String,
    val deviceName: String,
    val lastMessage: String,
    val timestamp: Long,
    val formattedTime: String,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.DELIVERED  // from latest message
)
```
Update the mapping in HomeViewModel.conversations to include `deliveryStatus = msg.deliveryStatus`.

**Polished empty state:** centered icon, "No conversations yet", "Start by discovering nearby peers"

**Files:**
- app/src/main/java/com/meshlink/app/ui/chats/ChatsListScreen.kt (NEW)
- app/src/main/java/com/meshlink/app/ui/chats/ChatsListViewModel.kt (NEW)
- app/src/main/java/com/meshlink/app/ui/home/HomeViewModel.kt (update Conversation data class)

**Verification:** Chats tab shows real conversations. Search filters. Empty state looks polished.

### Task 6: Redesign ChatScreen (Chat Detail)

Modernize ChatScreen.kt without changing messaging behavior.

**Header redesign:**
```
[ ← Back ]  [ Avatar ] [ Peer Name ]     [ ⋮ More ]
                        ● Connected through mesh
```
Use new pastel styles. Clean, no heavy red accents.

**Security banner (below header):**
```
🔒 End-to-end encrypted · P-256 verified
```
- Use SecurityBadge component
- Show "P-256 verified" ONLY IF verificationStatus == VERIFIED from KnownDevice
- For now, if VerificationStatus is unavailable, show "🔒 End-to-end encrypted" only (no verified claim)
- The ChatViewModel already knows the peer deviceId. Read KnownDevice from DeviceRepository.
- Add to ChatViewModel: `val peerDevice: StateFlow<KnownDevice?>` that queries DeviceRepository

**Message bubbles:**
- Incoming: SurfaceVariant background, dark text
- Outgoing: Primary color background (#C4717A), white text
- Rounded corners: 18dp, with smaller radius on the "tail" corner
- No more emergency red

**Delivery status on outgoing messages:**
- Add DeliveryStatusIndicator(compact=true) below each outgoing bubble
- Bound to `message.deliveryStatus`

**Status bar (between header and messages):**
- Replace the orange "DIRECT MESH LINK ACTIVE" bar with a soft SecurityBadge row
- Only show if connected. If offline, show subtle "Waiting for route..." indicator

**Input bar:**
- Rounded input field (24dp radius)
- Soft surface background
- Primary-colored send button (circle)
- Keep all existing functionality (+ quick actions, etc.)

**Remove emergency-specific UI:**
- Remove "CRITICAL ALERT" card specific styling ONLY if it interferes with new design
- Keep the SafeSignalBubble (rename styling to match new palette)
- Keep QuickActionsPanel but restyle with new colors

**ChatViewModel.kt additions:**
```kotlin
val peerDevice: StateFlow<KnownDevice?> = peerDeviceId
    .flatMapLatest { id -> deviceRepository.getAllDevices().map { list -> list.find { it.deviceId == id } } }
    .stateIn(...)
```
Requires injecting DeviceRepository into ChatViewModel.

**Files:**
- app/src/main/java/com/meshlink/app/ui/chat/ChatScreen.kt (full redesign)
- app/src/main/java/com/meshlink/app/ui/chat/ChatViewModel.kt (add peerDevice state + DeviceRepository)

**Verification:** Chat screen shows new design. Delivery status icons visible. Security badge shows correct state.

### Task 7: Create MeshNetworkScreen

Create a visual mesh network screen at:
`app/src/main/java/com/meshlink/app/ui/mesh/MeshNetworkScreen.kt`
`app/src/main/java/com/meshlink/app/ui/mesh/MeshNetworkViewModel.kt`

This replaces/transforms DiscoveryScreen for the Mesh tab.

**MeshNetworkViewModel:**
```kotlin
@HiltViewModel
class MeshNetworkViewModel @Inject constructor(
    private val nearbyRepository: NearbyRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = nearbyRepository.discoveredDevices
        .stateIn(...)
    
    val connectionStates: StateFlow<Map<String, ConnectionState>> = nearbyRepository.connectionStates
        .stateIn(...)
    
    val connectedPeerCount: StateFlow<Int> = connectionStates
        .map { it.values.count { s -> s == ConnectionState.CONNECTED } }
        .stateIn(...)
    
    // Keep DiscoveryViewModel's connect/disconnect logic
    private val _navigateToChat = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val navigateToChat: SharedFlow<Pair<String, String>> = _navigateToChat.asSharedFlow()
    
    fun onDeviceClick(device: DiscoveredDevice) { ... same as DiscoveryViewModel ... }
    fun onDisconnectClick(endpointId: String) { nearbyRepository.disconnect(endpointId) }
}
```

**MeshNetworkScreen layout:**
```
[ Top bar: "Mesh Network" + ⋮ menu ]
[ Network Summary Card ]
   N nodes  ·  N connected  ·  Mesh active
[ Visual topology section ]
   [ Canvas / Box with peer nodes arranged around "YOU" center ]
[ Nearby Peers list ]
   Each peer: avatar, name, connection state badge, connect/disconnect button
```

**Visual topology:**
Use a Box with absolute positioning or Canvas to draw:
- Center circle: "YOU" (with app-colored background)
- Surrounding circles: one per discovered peer (MeshAvatar)
- Lines connecting YOU to each connected peer (Canvas drawLine)
- Pulse animation on center node
- If no peers: empty illustration with "Scanning for nearby devices..."

This uses REAL data from `discoveredDevices` and `connectionStates`.
Do NOT fabricate node positions from hardcoded coordinates — compute them on a circle around center.

**Stats row:**
Show:
- `discoveredDevices.size` → "N Nodes"
- `connectedPeerCount` → "N Connected"
- If all 0: "No nearby peers"

**Navigation from Mesh tab:**
Tapping a connected peer navigates to ChatScreen (same as DiscoveryScreen does).

**Keep DiscoveryScreen.kt untouched** — it is currently used by the old navigation for the SOS/Discover tab. The new Mesh tab will use MeshNetworkScreen.

**Files:**
- app/src/main/java/com/meshlink/app/ui/mesh/MeshNetworkScreen.kt (NEW)
- app/src/main/java/com/meshlink/app/ui/mesh/MeshNetworkViewModel.kt (NEW)

**Verification:** Mesh tab shows real discovered peers. YOU node is centered. Connected peers show lines.

### Task 8: Create ProfileTab Placeholder

Create a minimal profile placeholder at:
`app/src/main/java/com/meshlink/app/ui/profile/ProfileScreen.kt`

This is a PLACEHOLDER — full Profile implementation is Phase 6B.

**Layout:**
```
[ Top bar: "My Profile" ]
[ Centered large avatar with user initials ]
[ Display name ]
[ "#MeshLink User" subtitle ]
[ Your Identity card (greyed out / Coming soon) ]
[ Section rows: Security & Privacy / App Settings / Backup ]
[ All rows show "Coming soon" or navigate nowhere ]
```

Use UserProfileManager.getDisplayName() for the name.
Show the first 2 initials in MeshAvatar.

No ViewModel needed — can use a simple @Composable with remember { } for name.

**File:**
- app/src/main/java/com/meshlink/app/ui/profile/ProfileScreen.kt (NEW)

**Verification:** Profile tab shows. Avatar and name visible. No crashes.

### Task 9: Wire All Navigation + Update MeshLinkNavHost

Update MeshLinkNavHost.kt to route to all new screens:

```kotlin
NavHost(startDestination = Screen.HomeTab.route) {
    composable(Screen.HomeTab.route) {
        HomeScreen(
            onConversationClick = { id, name -> navController.navigate(Screen.Chat.createRoute(id, name)) },
            onSeeAllChats = { navController.navigate(Screen.Chats.route) }
        )
    }
    composable(Screen.Chats.route) {
        ChatsListScreen(
            onConversationClick = { id, name -> navController.navigate(Screen.Chat.createRoute(id, name)) }
        )
    }
    composable(Screen.MeshNetwork.route) {
        MeshNetworkScreen(
            onDeviceClick = { id, name -> navController.navigate(Screen.Chat.createRoute(id, name)) }
        )
    }
    composable(Screen.ProfileTab.route) {
        ProfileScreen()
    }
    // Keep existing: Chat, MedicalProfile, Broadcast
    composable(Screen.Chat.route, ...) { ... }
    composable(Screen.MedicalProfile.route) { ... }
    composable(Screen.Broadcast.route) { ... }
    // Keep old Sos and Discovery routes for backward compat if needed, or remove if nothing references them
}
```

Update BottomNavBar to use new 4 tabs.
Update MainActivity bottomNavRoutes set.

**Files:**
- app/src/main/java/com/meshlink/app/ui/navigation/MeshLinkNavHost.kt (full rewrite)
- app/src/main/java/com/meshlink/app/ui/navigation/Screen.kt (update)
- app/src/main/java/com/meshlink/app/ui/navigation/BottomNavBar.kt (already done in Task 3)
- app/src/main/java/com/meshlink/app/MainActivity.kt (update bottomNavRoutes)

**Verification:** All 4 bottom tabs navigate correctly. Chat opens from Home and Chats. Mesh tab works.

### Task 10: Run Tests + Build Verification

Run unit tests:
```powershell
cd c:\Meshlink--Offline-messaging
.\gradlew test 2>&1 | Select-String -Pattern "(FAIL|ERROR|BUILD|tests)" | Select-Object -Last 30
```

Run assembleDebug:
```powershell
.\gradlew assembleDebug 2>&1 | Select-String -Pattern "(FAIL|ERROR|BUILD)" | Select-Object -Last 20
```

Verify:
- BUILD SUCCESSFUL for both
- No crypto/database/routing test failures
- No regression in Phase 4E tests

Then run git status:
```powershell
git status
git diff --stat HEAD~10 HEAD
```

Confirm:
- No changes to core/crypto/
- No changes to core/mesh/ routing files
- No changes to core/data/ entity/DAO/migration files
- No changes to NearbyRepositoryImpl.kt packet handling

**Files:** (none new — this is verification only)

## Verification Criteria
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- `./gradlew test` → no test failures in core/crypto, core/mesh, core/data
- 4-tab bottom navigation functional
- Home screen shows real peer count and conversations
- Chats screen shows real conversations with delivery status
- Chat detail shows pastel bubbles + delivery status + security badge
- Mesh network screen shows real discovered peers with visual topology
- Profile tab placeholder renders without crash
- DeliveryStatusIndicator covers all 5 states
- SecurityBadge shows correct verified/unverified state
- No fake data in production code paths
- No core backend files modified

## Success Criteria
All 10 tasks complete, BUILD SUCCESSFUL, tests pass, no backend changes.
