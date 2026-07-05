#!/bin/bash

# One more fix for BodyMapScreen Add button
sed -i 's/Icon(if (isAddingMole) Icons.Default.Close else Icons.Default.Add, contentDescription = null)/Icon(if (isAddingMole) Icons.Default.Close else Icons.Default.Add, contentDescription = stringResource(if (isAddingMole) R.string.desc_cancel_add else R.string.desc_add_mole))/' android-app/app/src/main/java/com/example/skinhistoryscanner/ui/BodyMapScreen.kt
