with open('android-app/app/src/main/java/com/example/skinhistoryscanner/ui/BodyMap3DScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "                            val hitResult = collisionSystem.hitTest(motionEvent.x, motionEvent.y)\n                            if (hitResult != null) {\n                                // We use a dummy coordinate here just so it compiles. In a real app we would use HitResult or screenToWorld raycasting properly from Sceneview 2.0+ API.\n                                viewModel.addMole(0f, 0f, 0f, \"#FF0000\")\n                                isAddingMole = false\n                            }",
    "                            val hitResult = collisionSystem.hitTest(motionEvent.x, motionEvent.y)\n                            val worldPosition = hitResult?.point\n                            if (worldPosition != null) {\n                                viewModel.addMole(worldPosition.x, worldPosition.y, worldPosition.z, \"#FF0000\") // Default red\n                                isAddingMole = false\n                            }"
)

with open('android-app/app/src/main/java/com/example/skinhistoryscanner/ui/BodyMap3DScreen.kt', 'w') as f:
    f.write(content)
