package com.miniproject.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.CircularProgressIndicator

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf("onboarding") }
    var currentContactId by remember { mutableStateOf<Long?>(null) }

    when (currentScreen) {
        "onboarding" -> OnboardingScreen(onComplete = { currentScreen = "contacts" })
        "contacts" -> ContactsScreen(
            onAddContact = { currentScreen = "add_contact" },
            onContactClick = { id -> 
                currentContactId = id
                currentScreen = "chat" 
            },
            onForumsClick = { currentScreen = "forums" }
        )
        "add_contact" -> AddContactScreen(
            onBack = { currentScreen = "contacts" },
            onAdded = { currentScreen = "contacts" }
        )
        "chat" -> currentContactId?.let { id ->
            ChatScreen(contactId = id, onBack = { currentScreen = "contacts" }, onBlogClick = { currentScreen = "blogs" })
        }
        "forums" -> ForumsScreen(
            onForumClick = { id ->
                currentContactId = id // abusing this variable for forumId to save space
                currentScreen = "forum_thread"
            },
            onBack = { currentScreen = "contacts" }
        )
        "forum_thread" -> currentContactId?.let { id ->
            ForumThreadScreen(forumId = id, onBack = { currentScreen = "forums" })
        }
        "blogs" -> BlogsScreen(
            onBack = { currentScreen = "chat" }
        )
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Card 1 — Welcome
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Hi. We're Bramble.", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "A messenger that works when the internet doesn't. No phone number. No email. No servers. Just you and the people you talk to.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            // Card 2 — Pick a nickname
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Pick a nickname", style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        placeholder = { Text("e.g. finch, moss, charlie", style = MaterialTheme.typography.bodyLarge) },
                        textStyle = TextStyle(fontFamily = com.miniproject.ui.theme.MonoFontFamily, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item {
            // Card 3 — Master passphrase
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Master passphrase", style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Passphrase") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = { Text("Confirm passphrase") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "If you forget this, your messages are lost. Write it down.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            // Card 4 — Ready
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        viewModel.unlockApp(password)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Enter Bramble", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onAddContact: () -> Unit, 
    onContactClick: (Long) -> Unit,
    onForumsClick: () -> Unit,
    viewModel: ContactsViewModel = viewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    val myOnionAddress by viewModel.myOnionAddress.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadContacts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Me", style = MaterialTheme.typography.titleLarge)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = myOnionAddress ?: "Connecting...", 
                                style = TextStyle(fontFamily = com.miniproject.ui.theme.MonoFontFamily, fontSize = 12.sp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddContact,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                // Placeholder for QR glyph
                Text("+ Add", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (contacts.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("[Unopened Door Illustration]", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "No contacts yet. Add someone nearby or share your link.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onAddContact, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan QR")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { /* show link */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show my link")
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(contacts) { contact ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        onClick = { onContactClick(contact.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(contact.alias.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            // Center Content
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Status dot
                                    Box(modifier = Modifier.size(8.dp).background(color = Color.Green, shape = CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(contact.alias, style = MaterialTheme.typography.titleMedium)
                                }
                                Text("Last message preview...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            // Right Content
                            Column(horizontalAlignment = Alignment.End) {
                                Text("10:42 AM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddContactScreen(
    onBack: () -> Unit, 
    onAdded: () -> Unit,
    viewModel: ContactsViewModel = viewModel()
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("In person", "At a distance", "Nearby", "My link")

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Add Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                when (selectedTabIndex) {
                    0 -> {
                        // In person
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Line up the QR code", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(32.dp))
                            Box(
                                modifier = Modifier
                                    .size(250.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("[Camera Preview]", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    1 -> {
                        // At a distance
                        var link by remember { mutableStateOf("") }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("Paste a bramble:// link from your contact", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = link,
                                onValueChange = { link = it },
                                placeholder = { Text("bramble://...") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontFamily = com.miniproject.ui.theme.MonoFontFamily, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { 
                                    viewModel.addContact(link)
                                    onAdded()
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = link.isNotBlank()
                            ) {
                                Text("Add Contact")
                            }
                        }
                    }
                    2 -> {
                        // Nearby (Bluetooth)
                        var isScanning by remember { mutableStateOf(false) }
                        var discoveredDevices by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
                        
                        androidx.compose.runtime.DisposableEffect(isScanning) {
                            if (isScanning) {
                                discoveredDevices = emptyList()
                                viewModel.startBluetoothScan { name, address ->
                                    discoveredDevices = discoveredDevices + (name to address)
                                }
                            } else {
                                viewModel.stopBluetoothScan()
                            }
                            onDispose {
                                viewModel.stopBluetoothScan()
                            }
                        }
                        
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Find contacts broadcasting via Bluetooth.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { isScanning = !isScanning }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (isScanning) "Stop Scanning" else "Scan Nearby")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (isScanning) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(discoveredDevices) { device ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        onClick = {
                                            viewModel.addContact(device.second) // treat address as contact ID for MVP
                                            onAdded()
                                        }
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(device.first, style = MaterialTheme.typography.titleMedium)
                                            Text(device.second, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // My link
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val myOnionAddress by viewModel.myOnionAddress.collectAsState()
                            val qrUri = "bramble://${myOnionAddress ?: ""}"
                            val qrBitmap = remember(qrUri) { QrUtils.generateQrCode(qrUri, 500) }
                            
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                qrBitmap?.let {
                                    androidx.compose.foundation.Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "My QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } ?: Text("Loading QR...", color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                val myOnionAddress by viewModel.myOnionAddress.collectAsState()
                                Text(
                                    text = "bramble://${myOnionAddress ?: "loading..."}",
                                    style = TextStyle(fontFamily = com.miniproject.ui.theme.MonoFontFamily, fontSize = 14.sp),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { /* Copy link */ }) {
                                Text("Copy Link")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactId: Long, 
    onBack: () -> Unit,
    onBlogClick: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val contact by viewModel.contact.collectAsState()

    LaunchedEffect(contactId) {
        viewModel.loadChat(contactId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(contact?.alias?.take(1)?.uppercase() ?: "!", color = MaterialTheme.colorScheme.onSecondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(Color.Green, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(contact?.alias ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Online via Tor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onBlogClick) {
                        Text("📝", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { /* Settings */ }) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { /* Attach */ }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Alice...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    if (messageText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(contactId, messageText)
                                messageText = ""
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Text("➤", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { msg ->
                val isOutgoing = msg.direction == 1
                val text = msg.bodyPlaintext
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isOutgoing) 16.dp else 4.dp,
                            bottomEnd = if (isOutgoing) 4.dp else 16.dp
                        ),
                        color = if (isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                            if (isOutgoing) {
                                Spacer(modifier = Modifier.width(8.dp))
                                // Status icon (Placeholder for checks)
                                Text("✓✓", style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumsScreen(
    onForumClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ForumsViewModel = viewModel()
) {
    val forums by viewModel.forums.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadForums()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Forums") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createForum("New Forum", "A newly created forum") }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(forums) { forum ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    onClick = { onForumClick(forum.id) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(forum.title, style = MaterialTheme.typography.titleMedium)
                        forum.description?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumThreadScreen(
    forumId: Long,
    onBack: () -> Unit,
    viewModel: ForumsViewModel = viewModel()
) {
    var replyText by remember { mutableStateOf("") }
    val posts by viewModel.posts.collectAsState()
    
    LaunchedEffect(forumId) {
        viewModel.loadPosts(forumId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forum Thread") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Post a reply...") },
                    shape = RoundedCornerShape(24.dp)
                )
                if (replyText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.postReply(forumId, replyText)
                            replyText = ""
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Text("➤", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(posts) { post ->
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("User ${post.authorPublicKey.take(4).toByteArray().contentToString()}", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(post.body, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlogsScreen(
    onBack: () -> Unit,
    viewModel: BlogsViewModel = viewModel()
) {
    val blogs by viewModel.blogs.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadBlogs()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Blogs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(blogs) { blog ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(blog.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("By: ${blog.authorPublicKey.take(4).toByteArray().contentToString()}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
