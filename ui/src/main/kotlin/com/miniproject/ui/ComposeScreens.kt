package com.miniproject.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf("onboarding") }

    when (currentScreen) {
        "onboarding" -> OnboardingScreen(onComplete = { currentScreen = "contacts" })
        "contacts" -> ContactsScreen(
            onAddContact = { currentScreen = "add_contact" },
            onContactClick = { currentScreen = "chat" }
        )
        "add_contact" -> AddContactScreen(
            onBack = { currentScreen = "contacts" },
            onAdded = { currentScreen = "contacts" }
        )
        "chat" -> ChatScreen(onBack = { currentScreen = "contacts" })
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to BrambleChat", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        var nickname by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Nickname") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master Password") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onComplete) {
            Text("Create Account")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(onAddContact: () -> Unit, onContactClick: () -> Unit) {
    val contacts = listOf("Alice", "Bob") // Mock data
    
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(contacts) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    onClick = onContactClick
                ) {
                    Text(text = contact, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun AddContactScreen(onBack: () -> Unit, onAdded: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Add Contact via QR Code", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(32.dp))
        // Placeholder for QR Scanner/Generator
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("[QR Code Scanner Here]")
        }
        Spacer(modifier = Modifier.height(32.dp))
        var link by remember { mutableStateOf("") }
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text("briar:// link") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = onAdded) { Text("Add") }
        }
    }
}

@Composable
fun ChatScreen(onBack: () -> Unit) {
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("<") }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chat with Alice", style = MaterialTheme.typography.titleLarge)
        }
        
        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
            items(messages) { msg ->
                Text(msg, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        
        Row(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (message.isNotBlank()) {
                        messages.add("You: $message")
                        message = ""
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}
