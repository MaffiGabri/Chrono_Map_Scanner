import os
import re

files = [
    "android-app/app/src/main/java/com/example/skinhistoryscanner/ui/SplitViewScreen.kt",
    "android-app/app/src/main/java/com/example/skinhistoryscanner/ui/components/MoleDetailsComponents.kt",
    "android-app/app/src/main/java/com/example/skinhistoryscanner/ui/components/MoleMarker.kt",
    "android-app/app/src/main/java/com/example/skinhistoryscanner/ui/components/BackgroundSettings.kt",
    "android-app/app/src/main/java/com/example/skinhistoryscanner/ui/BodyMapScreen.kt",
    "android-app/app/src/main/java/com/example/skinhistoryscanner/ui/MoleDetailsScreen.kt"
]

for file_path in files:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Find the package declaration and insert imports after it if not present
    if "import androidx.compose.ui.res.stringResource" not in content:
        content = re.sub(
            r'(package\s+[\w.]+)',
            r'\1\n\nimport androidx.compose.ui.res.stringResource\nimport com.example.skinhistoryscanner.R',
            content
        )

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Updated {file_path}")
