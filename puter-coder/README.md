# Puter Claude Coder

This is a standalone static webpage that uses `puter.js` and Claude models to propose edits for a local folder.

## What it does

- Uses `puter.ai.chat()` for Claude responses.
- Uses the browser File System Access API so you can point it at this repo and let it write files here.
- Sends only the files you select as context.
- Applies generated file content back into the chosen folder.

## Run it

Serve the folder over `http://localhost`, then open the page in a Chromium-based browser:

```bash
cd puter-coder
python3 -m http.server 4173
```

Then open `http://localhost:4173`.

## Use it

1. Click `Choose Folder` and pick the repo root.
2. Sign in to Puter if prompted.
3. Select the files Claude should read for context.
4. Enter a task.
5. Click `Generate Changes`.
6. Review the proposed files.
7. Click `Apply Checked Changes`.

## Notes

- `showDirectoryPicker()` requires a supported browser.
- This app asks Claude for full file contents, not patch hunks.
- Large repos should use a smaller context selection to avoid oversized prompts.
