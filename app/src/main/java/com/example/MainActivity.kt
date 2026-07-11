package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.data.PuppetScript
import com.example.data.Scene
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "prompt",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("prompt") {
                            PromptScreen(viewModel, navController)
                        }
                        composable("editor") {
                            EditorScreen(viewModel, navController)
                        }
                        composable("player") {
                            PlayerScreen(viewModel, navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PromptScreen(viewModel: MainViewModel, navController: NavController) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.script) {
        if (state.script != null) {
            navController.navigate("editor") {
                popUpTo("prompt")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Puppet Studio",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "AI Video Creator",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::onPromptChange,
            label = { Text("What should the puppet video be about?") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isGeneratingScript) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Writing script...", color = MaterialTheme.colorScheme.onSurface)
        } else {
            Button(
                onClick = viewModel::generateScript,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = state.prompt.isNotBlank()
            ) {
                Text("Generate Script", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (state.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.error ?: "",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EditorScreen(viewModel: MainViewModel, navController: NavController) {
    val state by viewModel.state.collectAsState()
    val scenes = state.script?.scenes ?: emptyList()
    val isReadyToPlay = scenes.isNotEmpty() && state.sceneImages.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Storyboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "Add a photo for each scene to create your puppet video.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(scenes) { scene ->
                    SceneEditorCard(
                        scene = scene,
                        imageUri = state.sceneImages[scene.id],
                        onImageSelected = { uri -> viewModel.onImageSelected(scene.id, uri) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isReadyToPlay,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            FloatingActionButton(
                onClick = { navController.navigate("player") },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = MaterialTheme.colorScheme.onSecondary)
            }
        }
    }
}

@Composable
fun SceneEditorCard(scene: Scene, imageUri: Uri?, onImageSelected: (Uri?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> onImageSelected(uri) }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable {
                        launcher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Scene Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add Photo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Scene ${scene.id}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = scene.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(viewModel: MainViewModel, navController: NavController) {
    val state by viewModel.state.collectAsState()
    val scenes = state.script?.scenes ?: emptyList()
    
    var currentSceneIndex by remember { mutableIntStateOf(0) }
    var displayedText by remember { mutableStateOf("") }
    
    val currentScene = scenes.getOrNull(currentSceneIndex)
    val currentImage = currentScene?.let { state.sceneImages[it.id] }
    
    // Puppet Animation (bobbing up and down)
    val infiniteTransition = rememberInfiniteTransition(label = "puppet_bounce")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    LaunchedEffect(currentSceneIndex) {
        if (currentSceneIndex < scenes.size) {
            val fullText = currentScene?.text ?: ""
            displayedText = ""
            // Typewriter effect
            for (i in fullText.indices) {
                displayedText += fullText[i]
                delay(30) // Typing speed
            }
            delay(2000) // Pause at the end of the scene
            currentSceneIndex++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (currentSceneIndex < scenes.size) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentImage != null) {
                        AsyncImage(
                            model = currentImage,
                            contentDescription = "Puppet Image",
                            modifier = Modifier
                                .size(250.dp)
                                .graphicsLayer {
                                    translationY = offsetY
                                },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback if they didn't upload an image for this scene
                        Box(
                            modifier = Modifier
                                .size(250.dp)
                                .background(Color.Gray, RoundedCornerShape(16.dp))
                                .graphicsLayer {
                                    translationY = offsetY
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Missing Photo", color = Color.White)
                        }
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(24.dp)
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayedText,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "The End",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Row {
                    Button(onClick = { currentSceneIndex = 0 }) {
                        Text("Replay")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedButton(onClick = { navController.popBackStack() }) {
                        Text("Back to Editor", color = Color.White)
                    }
                }
            }
        }
    }
}
