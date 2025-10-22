#!/bin/bash

echo "ðŸš€ Setting up Android development environment..."

# Create local.properties with Android SDK path
cat > local.properties << EOF
sdk.dir=$ANDROID_HOME
EOF

echo "âœ… local.properties created"

# Set permissions
chmod +x gradlew || echo "gradlew not found (will be created when project syncs)"

# Update SDK if needed
echo "ðŸ“¦ Updating Android SDK..."
sdkmanager --update || true

echo "ðŸŽ‰ Android SDK setup complete!"
echo ""
echo "ðŸ“ ANDROID_HOME: $ANDROID_HOME"
echo "ðŸ“ JAVA_HOME: $JAVA_HOME"
echo "ðŸ“ Gradle: $(gradle --version | head -n 1)"
echo ""
echo "ðŸ”¨ To build your Android app, run:"
echo "   ./gradlew build"
echo ""
echo "ðŸ“± To list available SDK packages:"
echo "   sdkmanager --list"