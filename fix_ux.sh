#!/bin/bash

# Restore the original state just to be safe
git restore .

# Add specific strings to strings.xml properly formatted
cat << 'XML' > strings_to_add_it.xml
    <!-- Accessibility -->
    <string name="desc_mole_thumbnail">Miniatura del neo</string>
    <string name="desc_crop_image">Ritaglia immagine</string>
    <string name="desc_delete_image">Elimina immagine</string>
    <string name="desc_background_image">Immagine di sfondo</string>
    <string name="desc_add_mole">Aggiungi neo</string>
    <string name="desc_cancel_add">Annulla aggiunta neo</string>
XML

cat << 'XML' > strings_to_add_en.xml
    <!-- Accessibility -->
    <string name="desc_mole_thumbnail">Mole thumbnail</string>
    <string name="desc_crop_image">Crop image</string>
    <string name="desc_delete_image">Delete image</string>
    <string name="desc_background_image">Background image</string>
    <string name="desc_add_mole">Add mole</string>
    <string name="desc_cancel_add">Cancel add mole</string>
XML

# We'll use python to cleanly add them right before </resources>
cat << 'PY' > add_strings_clean.py
import re

for file_path, xml_to_add in [
    ("android-app/app/src/main/res/values/strings.xml", "strings_to_add_it.xml"),
    ("android-app/app/src/main/res/values-en/strings.xml", "strings_to_add_en.xml")
]:
    with open(xml_to_add, "r") as f:
        to_add = f.read()

    with open(file_path, "r") as f:
        content = f.read()

    content = content.replace("</resources>", to_add + "\n</resources>")

    with open(file_path, "w") as f:
        f.write(content)
PY
python3 add_strings_clean.py

# ONLY apply contentDescription to interactive icons or images that convey info, NOT decorative ones.

# 1. BackgroundSettings: Edit Variant Dialog buttons (Crop and Delete)
sed -i 's/Icon(Icons.Default.Crop, contentDescription = null)/Icon(Icons.Default.Crop, contentDescription = stringResource(R.string.desc_crop_image))/' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/components/BackgroundSettings.kt
sed -i 's/Icon(Icons.Default.Delete, contentDescription = null)/Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.desc_delete_image))/' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/components/BackgroundSettings.kt

# 2. MoleMarker: The thumbnail image of the mole
sed -i 's/contentDescription = null,/contentDescription = stringResource(R.string.desc_mole_thumbnail),/' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/components/MoleMarker.kt

# 3. MoleDetailsScreen: The large photo of the mole
sed -i 's/contentDescription = null,/contentDescription = stringResource(R.string.desc_mole_thumbnail),/' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/MoleDetailsScreen.kt

# 4. MoleDetailsComponents: The cropped background image
sed -i 's/contentDescription = null,/contentDescription = stringResource(R.string.desc_background_image),/g' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/components/MoleDetailsComponents.kt

# 5. BodyMapScreen: The background image
sed -i 's/painter = bodyPainter,\n                                contentDescription = null,/painter = bodyPainter,\n                                contentDescription = stringResource(R.string.desc_background_image),/g' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/BodyMapScreen.kt
sed -i 's/model = targetVariant.imagePath,\n                                contentDescription = null,/model = targetVariant.imagePath,\n                                contentDescription = stringResource(R.string.desc_background_image),/g' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/BodyMapScreen.kt

# 6. SplitViewScreen: The background image
sed -i 's/contentDescription = null,/contentDescription = stringResource(R.string.desc_background_image),/' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/SplitViewScreen.kt
