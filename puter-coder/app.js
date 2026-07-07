const ignoredDirs = new Set([
  ".git",
  ".gradle",
  "build",
  "run",
  "node_modules",
  ".idea",
  ".vscode"
]);

const state = {
  rootHandle: null,
  files: [],
  selectedFiles: new Set(),
  proposedChanges: [],
  modelId: "claude-sonnet-4-6"
};

const els = {
  connectFolder: document.querySelector("#connect-folder"),
  refreshFiles: document.querySelector("#refresh-files"),
  signIn: document.querySelector("#sign-in"),
  modelSelect: document.querySelector("#model-select"),
  folderName: document.querySelector("#folder-name"),
  fileCount: document.querySelector("#file-count"),
  authState: document.querySelector("#auth-state"),
  fileList: document.querySelector("#file-list"),
  selectRecommended: document.querySelector("#select-recommended"),
  taskInput: document.querySelector("#task-input"),
  includeTree: document.querySelector("#include-tree"),
  streamOutput: document.querySelector("#stream-output"),
  runTask: document.querySelector("#run-task"),
  runState: document.querySelector("#run-state"),
  rawOutput: document.querySelector("#raw-output"),
  changeSummary: document.querySelector("#change-summary"),
  changeList: document.querySelector("#change-list"),
  applyChanges: document.querySelector("#apply-changes"),
  fileItemTemplate: document.querySelector("#file-item-template"),
  changeItemTemplate: document.querySelector("#change-item-template")
};

boot();

async function boot() {
  bindEvents();
  await loadModels();
  await refreshAuthState();
  renderFiles();
  renderChanges();
}

function bindEvents() {
  els.connectFolder.addEventListener("click", chooseFolder);
  els.refreshFiles.addEventListener("click", refreshFiles);
  els.signIn.addEventListener("click", signIn);
  els.selectRecommended.addEventListener("click", selectRecommendedFiles);
  els.runTask.addEventListener("click", runTask);
  els.applyChanges.addEventListener("click", applyChanges);
  els.modelSelect.addEventListener("change", () => {
    state.modelId = els.modelSelect.value;
  });
}

async function loadModels() {
  try {
    const models = await puter.ai.listModels("claude");
    const filtered = models
      .filter((model) => model.provider === "claude")
      .sort((a, b) => a.id.localeCompare(b.id));

    const choices = filtered.length
      ? filtered
      : [{ id: "claude-sonnet-4-6", name: "Claude Sonnet 4.6" }];

    els.modelSelect.innerHTML = "";
    for (const model of choices) {
      const option = document.createElement("option");
      option.value = model.id;
      option.textContent = model.name ? `${model.name} (${model.id})` : model.id;
      els.modelSelect.appendChild(option);
    }

    const defaultOption = choices.find((model) => model.id === state.modelId) ?? choices[0];
    state.modelId = defaultOption.id;
    els.modelSelect.value = state.modelId;
  } catch (error) {
    els.modelSelect.innerHTML = '<option value="claude-sonnet-4-6">claude-sonnet-4-6</option>';
    state.modelId = "claude-sonnet-4-6";
  }
}

async function refreshAuthState() {
  try {
    const signedIn = await puter.auth.isSignedIn();
    if (!signedIn) {
      els.authState.textContent = "Signed out";
      return;
    }

    const user = await puter.auth.getUser();
    els.authState.textContent = user?.username ? `@${user.username}` : "Signed in";
  } catch (error) {
    els.authState.textContent = "Unavailable";
  }
}

async function signIn() {
  try {
    await puter.auth.signIn();
    await refreshAuthState();
  } catch (error) {
    showError(error);
  }
}

async function chooseFolder() {
  if (!window.showDirectoryPicker) {
    showError(new Error("This browser does not support the File System Access API."));
    return;
  }

  try {
    state.rootHandle = await window.showDirectoryPicker({ mode: "readwrite" });
    els.folderName.textContent = state.rootHandle.name;
    els.refreshFiles.disabled = false;
    els.selectRecommended.disabled = false;
    await refreshFiles();
  } catch (error) {
    if (error?.name !== "AbortError") {
      showError(error);
    }
  }
}

async function refreshFiles() {
  if (!state.rootHandle) {
    return;
  }

  state.files = await collectFiles(state.rootHandle);
  state.selectedFiles = new Set(state.files.slice(0, 12).map((file) => file.path));
  els.fileCount.textContent = String(state.files.length);
  els.runTask.disabled = state.files.length === 0;
  renderFiles();
}

async function collectFiles(rootHandle) {
  const found = [];

  async function walk(handle, currentPath = "") {
    for await (const [name, child] of handle.entries()) {
      const nextPath = currentPath ? `${currentPath}/${name}` : name;
      if (child.kind === "directory") {
        if (ignoredDirs.has(name)) {
          continue;
        }
        await walk(child, nextPath);
        continue;
      }

      if (shouldIncludeFile(name)) {
        found.push({ path: nextPath, handle: child });
      }
    }
  }

  await walk(rootHandle);
  found.sort((a, b) => a.path.localeCompare(b.path));
  return found;
}

function shouldIncludeFile(name) {
  const blocked = [".png", ".jpg", ".jpeg", ".gif", ".ico", ".jar", ".class", ".lock", ".zip", ".mp4", ".webm", ".woff", ".woff2"];
  return !blocked.some((ext) => name.endsWith(ext));
}

function renderFiles() {
  if (!state.files.length) {
    els.fileList.className = "file-list empty";
    els.fileList.textContent = "Choose a folder to load files.";
    return;
  }

  els.fileList.className = "file-list";
  els.fileList.textContent = "";

  for (const file of state.files) {
    const node = els.fileItemTemplate.content.firstElementChild.cloneNode(true);
    const checkbox = node.querySelector(".file-checkbox");
    const path = node.querySelector(".file-path");

    checkbox.checked = state.selectedFiles.has(file.path);
    checkbox.addEventListener("change", () => {
      if (checkbox.checked) {
        state.selectedFiles.add(file.path);
      } else {
        state.selectedFiles.delete(file.path);
      }
    });

    path.textContent = file.path;
    els.fileList.appendChild(node);
  }
}

function selectRecommendedFiles() {
  const priorityPatterns = [
    /^README/i,
    /build\.gradle$/,
    /settings\.gradle$/,
    /gradle\.properties$/,
    /src\/main\//,
    /package\.json$/,
    /\.md$/
  ];

  const chosen = state.files
    .filter((file) => priorityPatterns.some((pattern) => pattern.test(file.path)))
    .slice(0, 20)
    .map((file) => file.path);

  state.selectedFiles = new Set(chosen);
  renderFiles();
}

async function runTask() {
  if (!state.rootHandle) {
    showError(new Error("Choose a folder first."));
    return;
  }

  const prompt = els.taskInput.value.trim();
  if (!prompt) {
    showError(new Error("Enter a task before running Claude."));
    return;
  }

  try {
    setRunState("running", "Running");
    els.rawOutput.textContent = "";
    state.proposedChanges = [];
    renderChanges();

    const contextFiles = await loadSelectedFiles();
    const messages = buildMessages(prompt, contextFiles);
    const options = {
      model: state.modelId,
      stream: els.streamOutput.checked,
      temperature: 0.2
    };

    if (options.stream) {
      const response = await puter.ai.chat(messages, options);
      let streamed = "";
      for await (const part of response) {
        if (part?.text) {
          streamed += part.text;
          els.rawOutput.textContent = streamed;
        } else if (part?.type === "error") {
          throw new Error(part.message || "Streaming request failed.");
        }
      }
      handleModelResponse(streamed);
      return;
    }

    const response = await puter.ai.chat(messages, options);
    const text = extractText(response);
    els.rawOutput.textContent = text;
    handleModelResponse(text);
  } catch (error) {
    setRunState("error", "Error");
    showError(error);
  }
}

async function loadSelectedFiles() {
  const selected = state.files.filter((file) => state.selectedFiles.has(file.path));
  const entries = [];

  for (const file of selected) {
    const blob = await file.handle.getFile();
    const content = await blob.text();
    entries.push({ path: file.path, content });
  }

  return entries;
}

function buildMessages(task, contextFiles) {
  const systemPrompt = [
    "You are a careful coding agent editing a local project.",
    "Return only valid JSON and no markdown fences.",
    "The JSON schema is:",
    '{"summary":"string","files":[{"path":"relative/path","action":"create|update|delete","explanation":"string","content":"full file content or empty string for delete"}]}',
    "Rules:",
    "- Use repository-relative paths.",
    "- For updates and creates, provide the full resulting file content.",
    "- Keep changes focused on the user request.",
    "- If you need no file edits, return an empty files array."
  ].join("\n");

  const parts = [
    `User task:\n${task}`,
    els.includeTree.checked ? `Project file tree:\n${state.files.map((file) => `- ${file.path}`).join("\n")}` : "",
    contextFiles.length
      ? `Selected file contents:\n${contextFiles.map((file) => `FILE: ${file.path}\n${file.content}`).join("\n\n")}`
      : "No file contents were selected."
  ].filter(Boolean);

  return [
    { role: "system", content: systemPrompt },
    { role: "user", content: parts.join("\n\n") }
  ];
}

function extractText(response) {
  if (typeof response === "string") {
    return response;
  }

  const content = response?.message?.content;
  if (typeof content === "string") {
    return content;
  }

  if (Array.isArray(content)) {
    return content.map((item) => item.text || "").join("");
  }

  return JSON.stringify(response, null, 2);
}

function handleModelResponse(text) {
  const parsed = parseJson(text);
  if (!parsed || !Array.isArray(parsed.files)) {
    throw new Error("Claude did not return the expected JSON structure.");
  }

  state.proposedChanges = parsed.files;
  els.changeSummary.textContent = parsed.summary || "No summary provided.";
  renderChanges();
  setRunState("done", "Ready");
}

function parseJson(text) {
  const trimmed = text.trim();
  if (!trimmed) {
    return null;
  }

  try {
    return JSON.parse(trimmed);
  } catch (error) {
    const fenceMatch = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
    if (fenceMatch) {
      return JSON.parse(fenceMatch[1].trim());
    }
    return null;
  }
}

function renderChanges() {
  if (!state.proposedChanges.length) {
    els.changeList.className = "change-list empty";
    els.changeList.textContent = "Claude should return JSON file edits here.";
    els.applyChanges.disabled = true;
    return;
  }

  els.changeList.className = "change-list";
  els.changeList.textContent = "";

  for (const change of state.proposedChanges) {
    const node = els.changeItemTemplate.content.firstElementChild.cloneNode(true);
    node.dataset.path = change.path;
    node.querySelector(".change-path").textContent = change.path;
    node.querySelector(".change-action").textContent = change.action || "update";
    node.querySelector(".change-explanation").textContent = change.explanation || "No explanation provided.";
    node.querySelector(".change-content").textContent = change.content || "";
    els.changeList.appendChild(node);
  }

  els.applyChanges.disabled = false;
}

async function applyChanges() {
  if (!state.rootHandle) {
    return;
  }

  const checkedPaths = [...els.changeList.querySelectorAll(".change-item")].filter((item) => {
    const checkbox = item.querySelector(".change-checkbox");
    return checkbox.checked;
  }).map((item) => item.dataset.path);

  const selectedChanges = state.proposedChanges.filter((change) => checkedPaths.includes(change.path));

  try {
    for (const change of selectedChanges) {
      await applySingleChange(change);
    }
    await refreshFiles();
    els.changeSummary.textContent = `Applied ${selectedChanges.length} change(s).`;
    setRunState("done", "Applied");
  } catch (error) {
    setRunState("error", "Apply Failed");
    showError(error);
  }
}

async function applySingleChange(change) {
  const segments = change.path.split("/").filter(Boolean);
  const fileName = segments.pop();
  let directory = state.rootHandle;

  for (const segment of segments) {
    directory = await directory.getDirectoryHandle(segment, { create: true });
  }

  if (change.action === "delete") {
    await directory.removeEntry(fileName);
    return;
  }

  const fileHandle = await directory.getFileHandle(fileName, { create: true });
  const writable = await fileHandle.createWritable();
  await writable.write(change.content ?? "");
  await writable.close();
}

function setRunState(mode, label) {
  els.runState.className = `pill ${mode}`;
  els.runState.textContent = label;
}

function showError(error) {
  const message = error?.message || String(error);
  els.rawOutput.textContent = `Error: ${message}`;
}
