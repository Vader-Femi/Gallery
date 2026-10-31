#!/bin/bash
sed -i.bak '128,129d' app/src/main/java/com/femi/gallery/presentation/settings/ThemePreferences.kt
echo "}" >> app/src/main/java/com/femi/gallery/presentation/settings/ThemePreferences.kt
