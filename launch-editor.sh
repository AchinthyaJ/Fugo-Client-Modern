#!/bin/bash
# Fugo Theme Editor - Live Server Launcher
# Starts a local HTTP server and opens the editor in your browser

PORT=8642
DIR="$(cd "$(dirname "$0")/src/main/resources/assets/fugoclient/web" && pwd)"

echo ""
echo "  ╔══════════════════════════════════════╗"
echo "  ║       FUGO THEME EDITOR              ║"
echo "  ║  Starting live server on port $PORT   ║"
echo "  ╚══════════════════════════════════════╝"
echo ""

# Check if port is in use
if lsof -i :$PORT >/dev/null 2>&1; then
    echo "⚠  Port $PORT already in use. Killing existing process..."
    fuser -k $PORT/tcp 2>/dev/null
    sleep 1
fi

# Try python3 first, then python
if command -v python3 &>/dev/null; then
    PY=python3
elif command -v python &>/dev/null; then
    PY=python
else
    echo "❌ Python not found. Install Python 3 to run the live server."
    exit 1
fi

echo "📂 Serving from: $DIR"
echo "🌐 URL: http://localhost:$PORT/editor.html"
echo ""
echo "Press Ctrl+C to stop the server."
echo ""

# Open browser after a short delay
(sleep 1.5 && xdg-open "http://localhost:$PORT/editor.html" 2>/dev/null || \
    open "http://localhost:$PORT/editor.html" 2>/dev/null || \
    echo "🔗 Open http://localhost:$PORT/editor.html in your browser") &

# Start server
cd "$DIR"
$PY -m http.server $PORT
