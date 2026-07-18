#!/usr/bin/env bash

# Fugo Client Custom Multi-Version Gradle Wrapper
USE_GRADLE_9=false
EXTRA_ARGS=()
for arg in "$@"; do
    if [[ "$arg" == *"1.21"* ]]; then
        USE_GRADLE_9=true
        break
    fi
done

if [ "$USE_GRADLE_9" = true ]; then
    EXTRA_ARGS+=("-Pfugo.includeModern=true")
fi

if [ "$USE_GRADLE_9" = true ]; then
    GRADLE_VERSION="9.2.0"
else
    GRADLE_VERSION="8.8"
fi

DIST_DIR="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

# Locate if requested Gradle is already downloaded/extracted in the Gradle cache
GRADLE_BIN=""
if [ -d "$DIST_DIR" ]; then
    GRADLE_BIN=$(find "$DIST_DIR" -path "*/bin/gradle" | head -n 1)
fi

if [ -z "$GRADLE_BIN" ] || [ ! -f "$GRADLE_BIN" ]; then
    echo "[Fugo Builder] Gradle ${GRADLE_VERSION} not found in cache. Downloading from official services..."
    TEMP_DIR=$(mktemp -d)
    ZIP_PATH="${TEMP_DIR}/gradle.zip"
    
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$ZIP_PATH" "$ZIP_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ZIP_PATH" "$ZIP_URL"
    else
        echo "Error: Neither curl nor wget found in PATH. Please install one of them to download Gradle."
        exit 1
    fi
    
    echo "[Fugo Builder] Extracting Gradle ${GRADLE_VERSION}..."
    TARGET_DIR="${DIST_DIR}/custom-extracted"
    mkdir -p "$TARGET_DIR"
    
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$ZIP_PATH" -d "$TARGET_DIR"
    else
        # Fallback to python if unzip is not installed
        python3 -c "import zipfile; zipfile.ZipFile('$ZIP_PATH').extractall('$TARGET_DIR')"
    fi
    
    GRADLE_BIN=$(find "$TARGET_DIR" -path "*/bin/gradle" | head -n 1)
    rm -rf "$TEMP_DIR"
    
    if [ -z "$GRADLE_BIN" ] || [ ! -f "$GRADLE_BIN" ]; then
        echo "Error: Extraction failed. Gradle binary not found."
        exit 1
    fi
    echo "[Fugo Builder] Gradle ${GRADLE_VERSION} installed successfully."
fi

# Execute the Gradle command with arguments
exec "$GRADLE_BIN" "${EXTRA_ARGS[@]}" "$@"
