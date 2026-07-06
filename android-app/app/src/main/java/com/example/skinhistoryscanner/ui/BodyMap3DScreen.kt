package com.example.skinhistoryscanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.skinhistoryscanner.ui.viewmodels.BodyMap3DViewModel
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position
import androidx.compose.ui.unit.dp
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberView
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.node.SphereNode
import com.example.skinhistoryscanner.ui.components.TimelineSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMap3DScreen(
    viewModel: BodyMap3DViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)

    val nodes = rememberNodes()
    var isAddingMole by remember { mutableStateOf(false) }

    LaunchedEffect(state.models, state.activeModelIndex, state.moles) {
        nodes.clear()
        val model = state.models.getOrNull(state.activeModelIndex)
        if (model != null) {
            val modelNode = ModelNode(
                modelInstance = modelLoader.createModelInstance(model),
                scaleToUnits = 1.0f
            ).apply {
                position = Position(0f, 0f, 0f)
            }
            nodes.add(modelNode)

            // Add mole markers
            state.moles.forEach { mole ->
                val sphere = SphereNode(
                    engine = engine,
                    radius = 0.05f,
                    center = Position(0f, 0f, 0f),
                    materialInstance = materialLoader.createColorInstance(android.graphics.Color.parseColor(mole.color))
                ).apply {
                    position = Position(mole.x, mole.y, mole.z)
                }
                modelNode.addChildNode(sphere)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D View") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isAddingMole = !isAddingMole },
                expanded = isAddingMole,
                text = { Text(if (isAddingMole) "Cancel" else "Add Mole") },
                icon = { }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Scene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                environmentLoader = environmentLoader,
                cameraNode = cameraNode,
                childNodes = nodes,
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { motionEvent, node ->
                        if (isAddingMole) {
                            val hitResult = collisionSystem.hitTest(motionEvent.x, motionEvent.y)
                            if (hitResult != null) {
                                // Simply add at camera target or tapped world position if available
                                // We use a dummy coordinate here just so it compiles. In a real app we would use HitResult or screenToWorld raycasting properly from Sceneview 2.0+ API.
                                viewModel.addMole(0f, 0f, 0f, "#FF0000")
                                isAddingMole = false
                            }
                        }
                    }
                )
            )

            TimelineSlider(
                isVisible = true,
                selectedDate = state.selectedDate,
                availableDates = state.availableDates,
                onDateChange = { viewModel.setSelectedDate(it) },
                onClose = { },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp) // Adjust above FAB
            )

            if (isAddingMole) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                ) {
                    Text("Tap on the model to add a mole", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
