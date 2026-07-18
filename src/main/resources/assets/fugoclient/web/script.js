// State variables that persist
// Driven by Java side session timer
let currentUiState = { inWorld: true, isWebTitle: false, isOverlay: true, editMode: false };
if (window.location.pathname.includes('titlescreen.html')) {
    currentUiState.inWorld = false;
    currentUiState.isWebTitle = true;
    currentUiState.isOverlay = false;
}

function formatTime(totalSeconds) {
    const hrs = String(Math.floor(totalSeconds / 3600)).padStart(2, '0');
    const mins = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, '0');
    const secs = String(totalSeconds % 60).padStart(2, '0');
    return `${hrs}:${mins}:${secs}`;
}

// Two-way Java Communication Bridge
window.MinecraftBridge = {
    handlers: {},
    
    // Send action to Java
    send: function(action, payload = {}) {
        return new Promise((resolve, reject) => {
            if (window.mcefQuery) {
                window.mcefQuery({
                    request: JSON.stringify({ action: action, payload: payload }),
                    persistent: false,
                    onSuccess: function(response) {
                        resolve(JSON.parse(response));
                    },
                    onFailure: function(errCode, errMsg) {
                        reject(new Error(errMsg + " (" + errCode + ")"));
                    }
                });
            } else {
                console.warn("[Bridge] mcefQuery not found. Running in browser mock mode.");
                // Mock success for development testing in standard browser
                setTimeout(() => {
                    resolve({ status: "mocked_success", action: action });
                }, 100);
            }
        });
    },

    // Register listener for events from Java
    on: function(event, cb) {
        if (!this.handlers[event]) {
            this.handlers[event] = [];
        }
        this.handlers[event].push(cb);
    },

    // Trigger local events
    trigger: function(event, data) {
        if (this.handlers[event]) {
            this.handlers[event].forEach(cb => cb(data));
        }
    }
};

function updateUIState(state) {
    if (!state) return;
    currentUiState.inWorld = state.inWorld !== undefined ? state.inWorld : currentUiState.inWorld;
    currentUiState.isWebTitle = state.isWebTitle !== undefined ? state.isWebTitle : currentUiState.isWebTitle;
    currentUiState.isOverlay = state.isOverlay !== undefined ? state.isOverlay : currentUiState.isOverlay;
    currentUiState.editMode = state.editMode !== undefined ? state.editMode : currentUiState.editMode;

    const titleContainer = document.getElementById('title-screen-container');
    const overlayContainer = document.getElementById('overlay-container');
    const settingsPanel = document.getElementById('overlay-settings-panel');
    
    // 1. Title screen (main menu) visibility
    if (currentUiState.isWebTitle && !currentUiState.isOverlay) {
        if (titleContainer) titleContainer.classList.remove('hidden');
    } else {
        if (titleContainer) titleContainer.classList.add('hidden');
    }
    
    // 2. Overlay container (mods menu / HUD) visibility
    if (currentUiState.inWorld || currentUiState.isOverlay) {
        if (overlayContainer) {
            overlayContainer.classList.remove('hidden');
            if (currentUiState.isOverlay && !currentUiState.editMode) {
                overlayContainer.classList.add('backdrop-active');
            } else {
                overlayContainer.classList.remove('backdrop-active');
            }
        }
    } else {
        if (overlayContainer) {
            overlayContainer.classList.add('hidden');
            overlayContainer.classList.remove('backdrop-active');
        }
    }

    if (currentUiState.isOverlay) {
        if (settingsPanel) {
            if (currentUiState.editMode) {
                settingsPanel.classList.add('hidden');
                showHudEditBanner(true);
                updateInteractiveDragState(true);
            } else {
                settingsPanel.classList.remove('hidden');
                showHudEditBanner(false);
                updateInteractiveDragState(false);
                renderModsGrid();
                loadProfilesList();
                if (typeof refreshHubData === 'function') refreshHubData();
            }
        }
    } else {
        if (settingsPanel) settingsPanel.classList.add('hidden');
        showHudEditBanner(false);
        updateInteractiveDragState(false);
    }
    
    // 3. HUD widgets visibility
    const widgets = document.querySelectorAll('.hud-widget');
    const crosshair = document.getElementById('custom-crosshair');
    widgets.forEach(w => {
        if (currentUiState.inWorld) {
            w.classList.remove('inworld-hidden');
        } else {
            w.classList.add('inworld-hidden');
        }
    });
    if (crosshair) {
        if (currentUiState.inWorld) {
            crosshair.classList.remove('inworld-hidden');
        } else {
            crosshair.classList.add('inworld-hidden');
        }
    }

    applyConfigToLayout(true);
    manageSnowfallState();
}

// Listen for overlay toggle from Java
window.MinecraftBridge.on('overlay:toggle', function(data) {
    const visible = typeof data === 'object' ? data.visible : data;
    const editMode = typeof data === 'object' ? data.editMode : false;
    updateUIState({
        isWebTitle: true,
        isOverlay: visible,
        editMode: editMode
    });
});

// Listen for consolidated UI state changes from Java
window.MinecraftBridge.on('ui:state_change', function(state) {
    updateUIState(state);
});

// Tab Switching inside settings (Backward compatibility & subtab router)
function switchTab(tabId) {
    if (tabId === 'tab-mods' || tabId === 'tab-huds' || tabId === 'huds') {
        switchHubApp('huds');
    } else if (tabId === 'tab-crosshair' || tabId === 'crosshair') {
        switchHubApp('crosshair');
    } else if (tabId === 'tab-accounts' || tabId === 'tab-settings' || tabId === 'profiles') {
        switchHubApp('profiles');
    }
}

function switchSettingsTab(tabId) {
    switchTab(tabId);
}

// --- Profile / Account Manager Logic ---
let currentProfileType = 'offline';

function setProfileType(type) {
    currentProfileType = type;
    const container = document.getElementById('profile-type-selector');
    if (container) {
        container.querySelectorAll('.style-option').forEach(btn => {
            if (btn.getAttribute('data-value') === type) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }
    toggleProfileTypeInputs();
}

function toggleProfileTypeInputs() {
    const type = currentProfileType;
    const altInputs = document.getElementById('profile-alt-inputs');
    const btnAdd = document.getElementById('btn-add-profile-action');
    const btnMs = document.getElementById('btn-ms-login-action');
    
    if (type === 'alt') {
        altInputs.classList.remove('hidden');
        btnAdd.classList.remove('hidden');
        btnMs.classList.add('hidden');
    } else if (type === 'microsoft') {
        altInputs.classList.add('hidden');
        btnAdd.classList.add('hidden');
        btnMs.classList.remove('hidden');
    } else { // offline
        altInputs.classList.add('hidden');
        btnAdd.classList.remove('hidden');
        btnMs.classList.add('hidden');
    }
}

function loadProfilesList() {
    const container = document.getElementById('profiles-sidebar-list');
    if (!container) return;
    
    container.innerHTML = '<div style="font-size:0.7rem;color:var(--text-muted);text-align:center;">Loading...</div>';
    
    window.MinecraftBridge.send('profiles:get')
        .then(data => {
            container.innerHTML = '';
            const profiles = data.profiles || [];
            const current = data.current;
            
            if (profiles.length === 0) {
                container.innerHTML = '<div style="font-size:0.7rem;color:var(--text-muted);text-align:center;padding:0.5rem;">No profiles</div>';
                return;
            }
            
            profiles.forEach(p => {
                const isActive = p.username === current;
                const item = document.createElement('div');
                item.className = `profile-sidebar-item ${isActive ? 'active' : ''}`;
                
                item.onclick = () => {
                    if (!isActive) {
                        handleSelectProfile(p.username);
                    }
                };
                
                item.innerHTML = `
                    <div class="profile-item-content">
                        ${isActive ? '<span class="profile-active-check">✓</span>' : '<span class="profile-inactive-dot"></span>'}
                        <div class="profile-info">
                            <span class="profile-name">${escapeHtml(p.username)}</span>
                        </div>
                    </div>
                    <button class="profile-delete-btn" onclick="event.stopPropagation(); handleDeleteProfile('${escapeJsString(p.username)}')">✕</button>
                `;
                container.appendChild(item);
            });
        })
        .catch(err => {
            container.innerHTML = `<div style="font-size:0.7rem;color:#ff3b1f;text-align:center;">Error</div>`;
        });
}

function handleAddProfile() {
    const username = document.getElementById('profile-new-username').value.trim();
    const type = currentProfileType;
    const statusDiv = document.getElementById('profile-auth-status');
    
    if (!username) {
        statusDiv.innerText = 'Please enter a username';
        statusDiv.style.color = '#ff3b1f';
        return;
    }
    
    statusDiv.innerText = 'Adding profile...';
    statusDiv.style.color = 'var(--accent)';
    
    if (type === 'offline') {
        window.MinecraftBridge.send('profiles:add_offline', { username: username })
            .then(() => {
                document.getElementById('profile-new-username').value = '';
                statusDiv.innerText = 'Profile added successfully!';
                statusDiv.style.color = '#4caf50';
                loadProfilesList();
            })
            .catch(err => {
                statusDiv.innerText = 'Error: ' + err.message;
                statusDiv.style.color = '#ff3b1f';
            });
    } else if (type === 'alt') {
        const uuid = document.getElementById('profile-new-uuid').value.trim();
        const token = document.getElementById('profile-new-token').value.trim();
        window.MinecraftBridge.send('profiles:add_alt', { username: username, uuid: uuid, token: token })
            .then(() => {
                document.getElementById('profile-new-username').value = '';
                document.getElementById('profile-new-uuid').value = '';
                document.getElementById('profile-new-token').value = '';
                statusDiv.innerText = 'Profile added successfully!';
                statusDiv.style.color = '#4caf50';
                loadProfilesList();
            })
            .catch(err => {
                statusDiv.innerText = 'Error: ' + err.message;
                statusDiv.style.color = '#ff3b1f';
            });
    }
}

function handleSelectProfile(username) {
    const statusDiv = document.getElementById('profile-auth-status');
    statusDiv.innerText = `Switching to ${username}...`;
    statusDiv.style.color = 'var(--accent)';
    
    window.MinecraftBridge.send('profiles:select', { username: username })
        .then(() => {
            statusDiv.innerText = `Successfully switched to ${username}!`;
            statusDiv.style.color = '#4caf50';
            loadProfilesList();
        })
        .catch(err => {
            statusDiv.innerText = 'Error: ' + err.message;
            statusDiv.style.color = '#ff3b1f';
        });
}

function handleDeleteProfile(username) {
    if (confirm(`Delete profile "${username}"?`)) {
        window.MinecraftBridge.send('profiles:delete', { username: username })
            .then(() => {
                loadProfilesList();
            })
            .catch(err => {
                alert('Error deleting profile: ' + err.message);
            });
    }
}

function handleMicrosoftLogin() {
    const statusDiv = document.getElementById('profile-auth-status');
    statusDiv.innerText = 'Redirecting to Microsoft Login... Please sign in.';
    statusDiv.style.color = 'var(--accent)';
    
    window.MinecraftBridge.send('profiles:microsoft_login')
        .catch(err => {
            statusDiv.innerText = 'Failed to launch login: ' + err.message;
            statusDiv.style.color = '#ff3b1f';
        });
}

// Register global listeners for Microsoft authentication callbacks
window.MinecraftBridge.on('profile:add_success', function(profile) {
    const statusDiv = document.getElementById('profile-auth-status');
    if (statusDiv) {
        statusDiv.innerText = `Microsoft login success! Profile "${profile.username}" added.`;
        statusDiv.style.color = '#4caf50';
    }
    loadProfilesList();
});

window.MinecraftBridge.on('profile:add_failure', function(errorMsg) {
    const statusDiv = document.getElementById('profile-auth-status');
    if (statusDiv) {
        statusDiv.innerText = `Microsoft login failed: ${errorMsg}`;
        statusDiv.style.color = '#ff3b1f';
    }
});

// Helper utilities
function escapeHtml(str) {
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

function escapeJsString(str) {
    return str.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"');
}

// Config Management (Persistent in Local Storage)
const DEFAULT_CONFIG = {
    toggles: {
        'toggle-top-left': true,
        'toggle-top-right': true,
        'toggle-bottom-left': true,
        'toggle-bottom-right': true,
        'toggle-armor': true,
        'toggle-potion-effects': true,
        'toggle-item-info': true,
        'toggle-player-status': true,
        'toggle-combat-stats': true,
        'toggle-target-info': true,
        'toggle-session-stats': true,
        'toggle-server-stats': true,
        'toggle-movement-stats': true,
        'toggle-bedwars-stats': true,
        'toggle-utility-widgets': true,
        'toggle-scoreboard': true,
        'toggle-crosshair': true,
        'toggle-fullbright': false,
        'toggle-autoclicker': false
    },
    scales: {},
    colors: {},
    coordsFormat: 'xyz',
    keystrokesLayout: 'wasd',
    speedometerUnit: 'bps',
    crosshair: {
        style: 'cross',
        size: 12,
        gap: 4,
        thickness: 2,
        color: '#ff3b1f'
    },
    autoclickerCps: 12,
    autoclickerKeybind: 'G',
    autoclickerButton: 'left'
};

const ALL_WIDGETS = [
    'hud-top-left',
    'hud-top-right',
    'hud-bottom-left',
    'hud-bottom-right',
    'hud-armor',
    'hud-potion-effects',
    'hud-item-info',
    'hud-player-status',
    'hud-combat-stats',
    'hud-target-info',
    'hud-session-stats',
    'hud-server-stats',
    'hud-movement-stats',
    'hud-bedwars-stats',
    'hud-utility-widgets'
];

// Helper to safely merge schema migrations
function mergeConfig(userConfig, defaultConfig) {
    if (!userConfig) return JSON.parse(JSON.stringify(defaultConfig));
    const merged = JSON.parse(JSON.stringify(defaultConfig));
    
    if (userConfig.toggles) {
        merged.toggles = { ...merged.toggles, ...userConfig.toggles };
    }
    if (userConfig.scales) {
        merged.scales = { ...merged.scales, ...userConfig.scales };
    }
    if (userConfig.crosshair) {
        merged.crosshair = { ...merged.crosshair, ...userConfig.crosshair };
    }
    if (userConfig.colors) {
        merged.colors = { ...merged.colors, ...userConfig.colors };
    }
    if (userConfig.coordsFormat) {
        merged.coordsFormat = userConfig.coordsFormat;
    }
    if (userConfig.keystrokesLayout) {
        merged.keystrokesLayout = userConfig.keystrokesLayout;
    }
    if (userConfig.speedometerUnit) {
        merged.speedometerUnit = userConfig.speedometerUnit;
    }
    if (userConfig.positions) {
        merged.positions = { ...userConfig.positions };
    }
    return merged;
}

let config = DEFAULT_CONFIG;
try {
    const raw = localStorage.getItem('fugo_hud_config');
    if (raw) {
        config = mergeConfig(JSON.parse(raw), DEFAULT_CONFIG);
    }
} catch (e) {
    console.error("Failed to load config:", e);
}

// Apply local config properties to UI values on load
function loadConfigToUI() {
    // 1. Render Mods grid
    renderModsGrid();

    // 2. Load profiles sidebar list
    loadProfilesList();

    // 2. Crosshair values
    const sizeEl = document.getElementById('crosshair-size');
    const gapEl = document.getElementById('crosshair-gap');
    const thickEl = document.getElementById('crosshair-thickness');

    if (sizeEl) sizeEl.value = config.crosshair.size;
    if (gapEl) gapEl.value = config.crosshair.gap;
    if (thickEl) thickEl.value = config.crosshair.thickness;

    updatePaletteSelection('crosshair-color-palette', 'crosshair-hex-input', config.crosshair.color || '#ff3b1f');

    // Set style-selector active class
    const style = config.crosshair.style || 'cross';
    const styleContainer = document.getElementById('crosshair-style-selector');
    if (styleContainer) {
        styleContainer.querySelectorAll('.style-option').forEach(btn => {
            if (btn.getAttribute('data-value') === style) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }

    updateValDisplay('size', config.crosshair.size);
    updateValDisplay('gap', config.crosshair.gap);
    updateValDisplay('thickness', config.crosshair.thickness);

    // Apply custom colors if defined
    if (config.colors) {
        for (const [modId, color] of Object.entries(config.colors)) {
            const widgetId = modId.replace('toggle-', 'hud-');
            const el = document.getElementById(widgetId);
            if (el) {
                el.style.color = color;
                el.style.borderColor = color;
            }
        }
    }

    applyConfigToLayout(false);
}

function updateValDisplay(name, val) {
    const el = document.getElementById(`val-crosshair-${name}`);
    if (el) el.innerText = val + "px";
}

function updateHUDToggles() {
    config.toggles['toggle-top-left'] = document.getElementById('toggle-top-left').checked;
    config.toggles['toggle-top-right'] = document.getElementById('toggle-top-right').checked;
    config.toggles['toggle-bottom-left'] = document.getElementById('toggle-bottom-left').checked;
    config.toggles['toggle-bottom-right'] = document.getElementById('toggle-bottom-right').checked;
    config.toggles['toggle-armor'] = document.getElementById('toggle-armor').checked;
    config.toggles['toggle-potion-effects'] = document.getElementById('toggle-potion-effects').checked;
    config.toggles['toggle-item-info'] = document.getElementById('toggle-item-info').checked;
    config.toggles['toggle-player-status'] = document.getElementById('toggle-player-status').checked;
    config.toggles['toggle-combat-stats'] = document.getElementById('toggle-combat-stats').checked;
    config.toggles['toggle-scoreboard'] = document.getElementById('toggle-scoreboard').checked;
    config.toggles['toggle-crosshair'] = document.getElementById('toggle-crosshair').checked;
    
    saveConfig();
}

function setCrosshairStyle(style) {
    config.crosshair.style = style;
    const container = document.getElementById('crosshair-style-selector');
    if (container) {
        container.querySelectorAll('.style-option').forEach(btn => {
            if (btn.getAttribute('data-value') === style) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }
    saveConfig();
}

function updateCrosshairConfig() {
    config.crosshair.size = parseInt(document.getElementById('crosshair-size').value);
    config.crosshair.gap = parseInt(document.getElementById('crosshair-gap').value);
    config.crosshair.thickness = parseInt(document.getElementById('crosshair-thickness').value);
    
    const hexInput = document.getElementById('crosshair-hex-input');
    if (hexInput && hexInput.value.length === 6) {
        config.crosshair.color = '#' + hexInput.value;
    }
    
    updateValDisplay('size', config.crosshair.size);
    updateValDisplay('gap', config.crosshair.gap);
    updateValDisplay('thickness', config.crosshair.thickness);
    
    saveConfig();
}

function saveConfig() {
    localStorage.setItem('fugo_hud_config', JSON.stringify(config));
    applyConfigToLayout(true);
}

function applyConfigToLayout(syncWithJava = true) {
    const showCustomCrosshair = currentUiState.inWorld && config.toggles['toggle-crosshair'];

    // 1. Visibility Toggles
    toggleVisibility('hud-top-left', config.toggles['toggle-top-left']);
    toggleVisibility('hud-top-right', config.toggles['toggle-top-right']);
    toggleVisibility('hud-bottom-left', config.toggles['toggle-bottom-left']);
    toggleVisibility('hud-bottom-right', config.toggles['toggle-bottom-right']);
    toggleVisibility('hud-armor', config.toggles['toggle-armor']);
    toggleVisibility('hud-potion-effects', config.toggles['toggle-potion-effects']);
    toggleVisibility('hud-item-info', config.toggles['toggle-item-info']);
    toggleVisibility('hud-player-status', config.toggles['toggle-player-status']);
    toggleVisibility('hud-combat-stats', config.toggles['toggle-combat-stats']);
    toggleVisibility('hud-target-info', config.toggles['toggle-target-info']);
    toggleVisibility('hud-session-stats', config.toggles['toggle-session-stats']);
    toggleVisibility('hud-server-stats', config.toggles['toggle-server-stats']);
    toggleVisibility('hud-movement-stats', config.toggles['toggle-movement-stats']);
    toggleVisibility('hud-bedwars-stats', config.toggles['toggle-bedwars-stats']);
    toggleVisibility('hud-utility-widgets', config.toggles['toggle-utility-widgets']);
    toggleVisibility('hud-scoreboard', config.toggles['toggle-scoreboard']);
    toggleVisibility('custom-crosshair', showCustomCrosshair);

    // Sync vanilla crosshair disable state with Java (only after state is loaded)
    if (syncWithJava && window.MinecraftBridge && window.MinecraftBridge.send) {
        window.MinecraftBridge.send('toggle_vanilla_crosshair', { disable: showCustomCrosshair });
    }
    
    // 2. Apply Scales dynamically
    ALL_WIDGETS.forEach(id => {
        let scale = 100;
        if (config.scales) {
            if (config.scales[id] !== undefined) {
                scale = config.scales[id];
            } else {
                // Map legacy scale config properties
                if (id === 'hud-top-left') scale = config.scales['scale-top-left'] || 100;
                else if (id === 'hud-top-right') scale = config.scales['scale-top-right'] || 100;
                else if (id === 'hud-bottom-left') scale = config.scales['scale-bottom-left'] || 100;
                else if (id === 'hud-bottom-right') scale = config.scales['scale-bottom-right'] || 100;
                else if (id === 'hud-armor') scale = config.scales['scale-armor'] || 100;
            }
        }
        applyScaleToElement(id, scale);
    });

    // 3. Crosshair shape rendering
    renderCrosshair();
}

function applyScaleToElement(id, percent) {
    const el = document.getElementById(id);
    if (!el) return;
    const factor = percent / 100;
    const centered = ['hud-item-info', 'hud-player-status', 'hud-target-info', 'hud-utility-widgets'];
    if (centered.includes(id)) {
        el.style.transform = `translateX(-50%) scale(${factor})`;
    } else {
        el.style.transform = `scale(${factor})`;
    }
}

function toggleVisibility(id, visible) {
    const el = document.getElementById(id);
    if (!el) return;
    if (visible) {
        el.classList.remove('hidden');
    } else {
        el.classList.add('hidden');
    }
}

// Custom Crosshair HTML shape renderer
function renderCrosshair() {
    const container = document.getElementById('custom-crosshair');
    const el = document.getElementById('crosshair-element');
    if (!container || !el) return;
    
    container.style.setProperty('--crosshair-color', config.crosshair.color);
    container.style.setProperty('--crosshair-size', config.crosshair.size + 'px');
    container.style.setProperty('--crosshair-gap', config.crosshair.gap + 'px');
    container.style.setProperty('--crosshair-thickness', config.crosshair.thickness + 'px');
    
    el.className = ""; 
    el.innerHTML = ""; 
    el.removeAttribute('style');
    
    const style = config.crosshair.style;
    if (style === 'dot') {
        el.classList.add('crosshair-dot');
        el.style.width = config.crosshair.thickness + 'px';
        el.style.height = config.crosshair.thickness + 'px';
    } else if (style === 'circle') {
        el.classList.add('crosshair-circle');
    } else if (style === 'diamond') {
        el.classList.add('crosshair-diamond');
    } else if (style === 'square') {
        el.classList.add('crosshair-square');
    } else if (style === 't-shape') {
        el.classList.add('crosshair-cross-hollow');
        el.innerHTML = `
            <div class="crosshair-line crosshair-line-bottom"></div>
            <div class="crosshair-line crosshair-line-left"></div>
            <div class="crosshair-line crosshair-line-right"></div>
        `;
    } else if (style === 'cross-dot') {
        el.classList.add('crosshair-cross-hollow');
        el.innerHTML = `
            <div class="crosshair-line crosshair-line-top"></div>
            <div class="crosshair-line crosshair-line-bottom"></div>
            <div class="crosshair-line crosshair-line-left"></div>
            <div class="crosshair-line crosshair-line-right"></div>
            <div class="crosshair-center-dot"></div>
        `;
    } else if (style === 'arrow') {
        el.classList.add('crosshair-cross-hollow');
        el.innerHTML = `
            <div class="crosshair-arrow-line crosshair-arrow-top"></div>
            <div class="crosshair-arrow-line crosshair-arrow-bottom"></div>
            <div class="crosshair-arrow-line crosshair-arrow-left"></div>
            <div class="crosshair-arrow-line crosshair-arrow-right"></div>
        `;
    } else if (style === 'cross') {
        if (config.crosshair.gap > 0) {
            el.classList.add('crosshair-cross-hollow');
            el.innerHTML = `
                <div class="crosshair-line crosshair-line-top"></div>
                <div class="crosshair-line crosshair-line-bottom"></div>
                <div class="crosshair-line crosshair-line-left"></div>
                <div class="crosshair-line crosshair-line-right"></div>
            `;
        } else {
            el.classList.add('crosshair-cross');
            el.style.width = config.crosshair.size + 'px';
            el.style.height = config.crosshair.size + 'px';
        }
    }
}

// Armor Status Bar Renderer
function updateArmorHUD(id, data) {
    const el = document.getElementById('armor-' + id);
    if (!el) return;
    
    if (data.empty) {
        if (el.style.display !== 'none') {
            el.style.display = 'none';
        }
        return;
    }
    
    if (el.style.display !== 'flex') {
        el.style.display = 'flex';
    }
    
    const bar = el.querySelector('.armor-bar');
    const text = el.querySelector('.armor-text');
    
    if (text) {
        const percentText = Math.round(data.percent) + "%";
        if (text.innerText !== percentText) {
            text.innerText = percentText;
        }
    }
    
    if (bar) {
        const widthVal = data.percent + "%";
        if (bar.style.width !== widthVal) {
            bar.style.width = widthVal;
            
            if (data.percent < 25) {
                bar.style.backgroundColor = '#ff3b1f'; 
                bar.style.boxShadow = '0 0 5px rgba(255, 59, 31, 0.5)';
            } else if (data.percent < 50) {
                bar.style.backgroundColor = '#ffb020'; 
                bar.style.boxShadow = '0 0 5px rgba(255, 176, 32, 0.5)';
            } else {
                bar.style.backgroundColor = '#20ff60'; 
                bar.style.boxShadow = '0 0 5px rgba(32, 255, 96, 0.3)';
            }
        }
    }
}

let lastInteractiveState = null;

// 1. Consolidated HUD Stats Update (10 FPS)
window.MinecraftBridge.on('hud:update_stats', function(data) {
    // FPS & Ping
    const fpsEl = document.getElementById('hud-fps');
    if (fpsEl) fpsEl.innerText = data.fps;
    const pingEl = document.getElementById('hud-ping');
    if (pingEl) pingEl.innerText = data.ping + "ms";

    // Coordinates & Direction & Session Timer
    const coordsEl = document.getElementById('hud-coords');
    if (coordsEl) {
        const fmt = config.coordsFormat || 'xyz';
        if (fmt === 'compact') {
            coordsEl.innerText = `${data.x.toFixed(0)}, ${data.y.toFixed(0)}, ${data.z.toFixed(0)}`;
        } else if (fmt === 'direction') {
            coordsEl.innerText = `X: ${data.x.toFixed(0)} Y: ${data.y.toFixed(0)} Z: ${data.z.toFixed(0)} (${data.facing})`;
        } else {
            coordsEl.innerText = `X: ${data.x.toFixed(1)} Y: ${data.y.toFixed(1)} Z: ${data.z.toFixed(1)}`;
        }
    }
    const dirEl = document.getElementById('hud-dir');
    if (dirEl) {
        const fmt = config.coordsFormat || 'xyz';
        dirEl.innerText = data.facing;
        dirEl.style.display = (fmt === 'direction') ? 'none' : 'block';
    }
    const overlayTimerEl = document.getElementById('overlay-timer');
    if (overlayTimerEl) overlayTimerEl.innerText = formatTime(data.sessionTime);

    // Speedometer (BPS/KMH)
    const bpsEl = document.getElementById('hud-bps');
    if (bpsEl) {
        const unit = config.speedometerUnit || 'bps';
        if (unit === 'kmh') {
            const kmh = data.bps * 3.6;
            bpsEl.innerText = kmh.toFixed(1) + " KM/H";
        } else {
            bpsEl.innerText = data.bps.toFixed(2) + " BPS";
        }
    }
    const speedBarEl = document.getElementById('hud-speed-bar');
    if (speedBarEl) {
        const percentage = Math.min((data.bps / 25) * 100, 100);
        speedBarEl.style.width = percentage + "%";
    }

    // Armor items - use actual Minecraft armor textures
    updateArmorDisplay(data.armor.helmet, data.armor.chest, data.armor.leggings, data.armor.boots);

    // Potion Effects
    const potionsContainer = document.getElementById('potions-container');
    if (potionsContainer) {
        if (!data.potions || data.potions.length === 0) {
            potionsContainer.innerHTML = '<div style="font-size: 0.65rem; color: var(--text-muted);">No active effects</div>';
        } else {
            potionsContainer.innerHTML = data.potions.map(p => `
                <div class="potion-item">
                    <span class="potion-name" style="color: ${p.beneficial ? '#20ff60' : '#ff3b1f'}">${p.name} ${p.amp > 1 ? p.amp : ''}</span>
                    <span class="potion-time">${p.duration}</span>
                </div>
            `).join('');
        }
    }

    // Held Item Info
    const itemNameEl = document.getElementById('item-name');
    const itemDurabilityEl = document.getElementById('item-durability');
    const itemCooldownBar = document.getElementById('item-cooldown-bar');
    if (itemNameEl) itemNameEl.innerText = data.item.name || "None";
    if (itemDurabilityEl) {
        if (data.item.maxDurability > 0) {
            itemDurabilityEl.innerText = `${data.item.durability}/${data.item.maxDurability}`;
        } else {
            itemDurabilityEl.innerText = "N/A";
        }
    }
    if (itemCooldownBar) {
        itemCooldownBar.style.width = (data.item.cooldown * 100) + "%";
    }

    // Player Status
    const healthBar = document.getElementById('status-health');
    const healthVal = document.getElementById('status-health-val');
    const hungerBar = document.getElementById('status-hunger');
    const hungerVal = document.getElementById('status-hunger-val');
    const xpBar = document.getElementById('status-xp');
    const xpVal = document.getElementById('status-xp-val');
    
    if (healthBar) healthBar.style.width = Math.min((data.health / data.maxHealth) * 100, 100) + "%";
    if (healthVal) healthVal.innerText = Math.round(data.health);
    if (hungerBar) hungerBar.style.width = (data.hunger / 20) * 100 + "%";
    if (hungerVal) hungerVal.innerText = data.hunger;
    if (xpBar) xpBar.style.width = (data.xp * 100) + "%";
    if (xpVal) xpVal.innerText = `Lvl ${data.xpLevel}`;

    // Combat Stats
    const comboEl = document.getElementById('combat-combo');
    if (comboEl) {
        comboEl.innerText = data.combo;
        if (data.combo > 0) {
            comboEl.style.color = '#ff3b1f';
            comboEl.style.textShadow = '0 0 8px rgba(255, 59, 31, 0.6)';
        } else {
            comboEl.style.color = 'var(--text-main)';
            comboEl.style.textShadow = 'none';
        }
    }
    const reachEl = document.getElementById('combat-reach');
    if (reachEl) reachEl.innerText = data.reach.toFixed(2) + "m";

    // Target Info
    const targetInfoEl = document.getElementById('hud-target-info');
    if (targetInfoEl) {
        if (data.targetName && data.targetHealth > 0) {
            targetInfoEl.classList.remove('hidden');
            const targetNameEl = document.getElementById('target-name');
            const targetHealthBar = document.getElementById('target-health-bar');
            const targetDistEl = document.getElementById('target-dist');
            const targetArmorDisplay = document.getElementById('target-armor-display');
            
            if (targetNameEl) targetNameEl.innerText = data.targetName;
            if (targetHealthBar) targetHealthBar.style.width = Math.min((data.targetHealth / data.targetMaxHealth) * 100, 100) + "%";
            if (targetDistEl) targetDistEl.innerText = data.targetDistance.toFixed(1) + "m";
            if (targetArmorDisplay) {
                targetArmorDisplay.innerHTML = data.targetArmor.map(item => {
                    if (!item) return '';
                    let icon = '🛡️';
                    if (item.includes('helmet')) icon = '🪖';
                    else if (item.includes('chestplate') || item.includes('elytra')) icon = '👕';
                    else if (item.includes('leggings')) icon = '👖';
                    else if (item.includes('boots')) icon = '🥾';
                    return `<span class="armor-badge" title="${item}">${icon}</span>`;
                }).join('');
            }
        } else {
            targetInfoEl.classList.add('hidden');
        }
    }

    // Session Stats
    const sKills = document.getElementById('session-kills');
    const sDeaths = document.getElementById('session-deaths');
    const sKD = document.getElementById('session-kd');
    const sStreak = document.getElementById('session-streak');
    if (sKills) sKills.innerText = data.kills;
    if (sDeaths) sDeaths.innerText = data.deaths;
    if (sKD) {
        const kd = data.deaths > 0 ? (data.kills / data.deaths) : data.kills;
        sKD.innerText = kd.toFixed(2);
    }
    if (sStreak) sStreak.innerText = data.currentStreak;

    // Server Stats
    const sName = document.getElementById('server-name');
    const sIp = document.getElementById('server-ip');
    const sPlayers = document.getElementById('server-players');
    if (sName) sName.innerText = data.serverName;
    if (sIp) sIp.innerText = data.serverIp;
    if (sPlayers) sPlayers.innerText = data.serverPlayers;

    // Movement Stats
    const mVel = document.getElementById('movement-velocity');
    const mFall = document.getElementById('movement-falldist');
    if (mVel) mVel.innerText = data.bps.toFixed(2) + " m/s";
    if (mFall) mFall.innerText = data.utility.day > 0 ? "Fall Dist: N/A" : "0.0m";

    // Scoreboard
    const scoreboardContainer = document.getElementById('scoreboard-lines-container');
    if (scoreboardContainer) {
        if (!data.scoreboard || data.scoreboard.length === 0) {
            scoreboardContainer.innerHTML = '<div style="font-size: 0.65rem; color: var(--text-muted);">No scoreboard</div>';
        } else {
            scoreboardContainer.innerHTML = data.scoreboard.map(line => `
                <div style="font-size: 0.72rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${line}</div>
            `).join('');
        }
    }

    // Utility Widgets
    const uTime = document.getElementById('utility-time');
    const uDay = document.getElementById('utility-day');
    const uWeather = document.getElementById('utility-weather');
    if (uTime) uTime.innerText = data.utility.time;
    if (uDay) uDay.innerText = data.utility.day;
    if (uWeather) uWeather.innerText = data.utility.weather;
});

// 2. Keystrokes & CPS (Event-driven / Ultra-low latency)
window.MinecraftBridge.on('hud:update_keys', function(data) {
    toggleKey('key-w', data.w);
    toggleKey('key-a', data.a);
    toggleKey('key-s', data.s);
    toggleKey('key-d', data.d);
    toggleKey('key-space', data.space);
    toggleKey('key-lmb', data.lmb);
    toggleKey('key-rmb', data.rmb);
    toggleKey('key-shift', data.shift);
    toggleKey('key-sprint', data.sprint);
    
    const lmbCpsEl = document.getElementById('cps-lmb');
    const rmbCpsEl = document.getElementById('cps-rmb');
    if (lmbCpsEl) {
        const cpsStr = data.lmbCps + " CPS";
        if (lmbCpsEl.innerText !== cpsStr) {
            lmbCpsEl.innerText = cpsStr;
        }
    }
    if (rmbCpsEl) {
        const cpsStr = data.rmbCps + " CPS";
        if (rmbCpsEl.innerText !== cpsStr) {
            rmbCpsEl.innerText = cpsStr;
        }
    }
});

// 3. Interactive State
window.MinecraftBridge.on('hud:update_interactive', function(data) {
    const interactive = typeof data === 'object' ? data.interactive : data;
    const editMode = typeof data === 'object' ? data.editMode : false;
    updateUIState({
        isWebTitle: interactive,
        isOverlay: interactive,
        editMode: editMode
    });
});

function toggleKey(id, pressed) {
    const el = document.getElementById(id);
    if (!el) return;
    const hasActive = el.classList.contains('active');
    if (pressed && !hasActive) {
        el.classList.add('active');
    } else if (!pressed && hasActive) {
        el.classList.remove('active');
    }
}

// Function to trigger client actions
function triggerAction(action) {
    console.log("[Bridge] Triggering action: " + action);
    if (typeof window.mcefQuery === 'undefined') {
        console.warn("[Bridge] mcefQuery not available - buttons are decorative only");
        return;
    }
    window.MinecraftBridge.send(action)
        .then(response => {
            console.log("[Bridge] Action succeeded: ", response);
        })
        .catch(err => {
            console.error("[Bridge] Action failed: ", err);
        });
}

// --- Dynamic Mods Overlay / Grid & HUD Editor logic ---
const MODS_DATA = [
    {
        id: 'toggle-top-left',
        name: 'General Stats',
        desc: 'Displays FPS and ping in the top left corner.',
        icon: '⚡',
        category: 'server'
    },
    {
        id: 'toggle-top-right',
        name: 'Coordinates',
        desc: 'Shows coordinates and direction.',
        icon: '📍',
        category: 'hud'
    },
    {
        id: 'toggle-bottom-left',
        name: 'Speedometer',
        desc: 'Displays speed in blocks per second (BPS).',
        icon: '🏎️',
        category: 'hud'
    },
    {
        id: 'toggle-bottom-right',
        name: 'Keystrokes',
        desc: 'Visualizes WASD, Space, LMB/RMB and CPS.',
        icon: '⌨️',
        category: 'hud'
    },
    {
        id: 'toggle-armor',
        name: 'Armor Status',
        desc: 'Displays durability of equipped armor.',
        icon: '🛡️',
        category: 'mechanic'
    },
    {
        id: 'toggle-potion-effects',
        name: 'Potion Effects',
        desc: 'Displays active potion effects and duration.',
        icon: '🧪',
        category: 'mechanic'
    },
    {
        id: 'toggle-item-info',
        name: 'Item Info',
        desc: 'Shows details about your held item.',
        icon: '⚔️',
        category: 'hud'
    },
    {
        id: 'toggle-player-status',
        name: 'Player Status',
        desc: 'Visual indicators for health, hunger, and XP.',
        icon: '❤️',
        category: 'hud'
    },
    {
        id: 'toggle-combat-stats',
        name: 'Combat Stats',
        desc: 'Tracks attack combo counts and reach distance.',
        icon: '⚔️',
        category: 'mechanic'
    },
    {
        id: 'toggle-target-info',
        name: 'Target Info',
        desc: 'Shows health and armor of the opponent you target.',
        icon: '🎯',
        category: 'mechanic'
    },
    {
        id: 'toggle-session-stats',
        name: 'Session Stats',
        desc: 'Tracks kills, deaths, and session play time.',
        icon: '⏱️',
        category: 'server'
    },
    {
        id: 'toggle-server-stats',
        name: 'Server Info',
        desc: 'Displays current Server IP and TPS.',
        icon: '🌐',
        category: 'server'
    },
    {
        id: 'toggle-movement-stats',
        name: 'Movement Status',
        desc: 'Displays your current sprint, sneak, and toggle keys.',
        icon: '🏃',
        category: 'hud'
    },
    {
        id: 'toggle-bedwars-stats',
        name: 'Bedwars Stats',
        desc: 'Tracks Bedwars stats like beds broken and final kills.',
        icon: '🛏️',
        category: 'server'
    },
    {
        id: 'toggle-utility-widgets',
        name: 'Utility Widgets',
        desc: 'Includes custom client calendar, clock, and memory usage.',
        icon: '⚙️',
        category: 'hud'
    },
    {
        id: 'toggle-scoreboard',
        name: 'Scoreboard Overlay',
        desc: 'Displays the server scoreboard custom overlay.',
        icon: '📋',
        category: 'server'
    },
    {
        id: 'toggle-crosshair',
        name: 'Custom Crosshair',
        desc: 'Replaces the default crosshair with a custom one.',
        icon: '🎯',
        category: 'hud'
    },
    {
        id: 'toggle-fullbright',
        name: 'Fullbright',
        desc: 'Allows you to see clearly in dark areas.',
        icon: '💡',
        category: 'mechanic'
    },
    {
        id: 'xaeros-minimap',
        name: "Xaero's Minimap",
        desc: 'Displays a custom minimap in the top right.',
        icon: '🗺️',
        category: 'hud'
    },
    {
        id: 'flashback',
        name: 'Flashback Replays',
        desc: 'Record and edit replays of your gameplay.',
        icon: '🎥',
        category: 'mechanic'
    },
    {
        id: 'toggle-autoclicker',
        name: 'Autoclicker',
        desc: 'Automatically clicks Left or Right mouse button at target CPS.',
        icon: '🖱️',
        category: 'mechanic'
    },
    {
        id: 'tool-time-control',
        name: 'Time Control',
        desc: 'Set the in-game time of day using presets or a precise slider.',
        icon: '⏰',
        category: 'mechanic',
        special: 'time-control'
    },
    {
        id: 'tool-weather-control',
        name: 'Weather Control',
        desc: 'Change in-game weather instantly to Clear, Rain, or Thunder.',
        icon: '🌤',
        category: 'mechanic',
        special: 'weather-control'
    }
];

let activeModsCategory = 'all';
let modsSearchQuery = '';

function renderModsGrid() {
    const container = document.getElementById('mods-grid-container');
    if (!container) return;
    
    container.innerHTML = '';
    
    const filteredMods = MODS_DATA.filter(mod => {
        const matchesCategory = activeModsCategory === 'all' || mod.category === activeModsCategory;
        const matchesSearch = mod.name.toLowerCase().includes(modsSearchQuery.toLowerCase()) || 
                             mod.desc.toLowerCase().includes(modsSearchQuery.toLowerCase());
        return matchesCategory && matchesSearch;
    });
    
    if (filteredMods.length === 0) {
        container.innerHTML = '<div style="grid-column: span 3; text-align: center; color: var(--text-muted); padding: 2rem; font-size: 0.85rem;">No mods found</div>';
        return;
    }
    
    filteredMods.forEach(mod => {
        const isSpecial = !!mod.special;
        const isEnabled = isSpecial ? true : config.toggles[mod.id] !== false;
        const card = document.createElement('div');
        card.className = 'mod-card';
        card.innerHTML = `
            <div class="mod-info">
                <div class="mod-name-row">
                    <span class="mod-icon">${mod.icon}</span>
                    <span class="mod-name">${mod.name}</span>
                </div>
                <div class="mod-desc">${mod.desc}</div>
            </div>
            <div class="mod-control-group">
                <button class="mod-ctrl-btn mod-ctrl-options" onclick="goToModOptions('${mod.id}')">OPEN</button>
                ${isSpecial ? '' : `<button class="mod-ctrl-btn mod-ctrl-status ${isEnabled ? 'enabled' : 'disabled'}" onclick="toggleModStatus('${mod.id}')">
                    ${isEnabled ? 'ENABLED' : 'DISABLED'}
                </button>`}
            </div>
        `;
        container.appendChild(card);
    });
}

function filterMods(category) {
    activeModsCategory = category;
    document.querySelectorAll('.cat-btn').forEach(btn => {
        if (btn.innerText.toLowerCase() === category.toLowerCase()) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
    renderModsGrid();
}

function handleModsSearch() {
    const input = document.getElementById('mods-search-input');
    if (input) {
        modsSearchQuery = input.value;
        renderModsGrid();
    }
}

function toggleModStatus(modId) {
    const isEnabled = config.toggles[modId] !== false;
    config.toggles[modId] = !isEnabled;
    saveConfig();
    renderModsGrid();
    if (modId === 'toggle-fullbright') {
        console.log("[Fugo] Sending toggle_fullbright: " + config.toggles['toggle-fullbright']);
        window.MinecraftBridge.send('toggle_fullbright', { enabled: config.toggles['toggle-fullbright'] })
            .catch(err => console.error("[Fugo] Failed to toggle fullbright:", err));
    } else if (modId === 'toggle-crosshair') {
        console.log("[Fugo] Crosshair toggled: " + config.toggles['toggle-crosshair']);
        applyConfigToLayout(true);
    } else if (modId === 'toggle-autoclicker') {
        syncAutoclickerSettings();
    }
}

let currentEditingModId = null;

function goToModOptions(modId) {
    if (modId === 'xaeros-minimap' || modId === 'flashback') {
        window.MinecraftBridge.send('open_mod_config', { modId: modId });
        return;
    }
    if (modId === 'tool-time-control') {
        openTimeControlOverlay();
        return;
    }
    if (modId === 'tool-weather-control') {
        openWeatherControlOverlay();
        return;
    }
    currentEditingModId = modId;
    const mod = MODS_DATA.find(m => m.id === modId);
    if (!mod) return;

    // Set title
    document.getElementById('modal-hud-title').innerText = mod.name + ' Options';
    
    // Set scale slider
    const widgetId = modId.replace('toggle-', 'hud-');
    const currentScale = (config.scales && config.scales[widgetId]) || 100;
    document.getElementById('modal-hud-scale-slider').value = currentScale;
    document.getElementById('modal-hud-scale-val').innerText = currentScale;

    // Toggle color picker visibility (some mods might have colors)
    const colorPickerContainer = document.getElementById('modal-color-option-container');
    if (colorPickerContainer) {
        const colorableMods = ['toggle-top-left', 'toggle-top-right', 'toggle-bottom-left', 'toggle-bottom-right', 'toggle-item-info', 'toggle-player-status', 'toggle-combat-stats', 'toggle-target-info', 'toggle-session-stats', 'toggle-server-stats', 'toggle-movement-stats', 'toggle-bedwars-stats', 'toggle-utility-widgets'];
        if (colorableMods.includes(modId)) {
            colorPickerContainer.classList.remove('hidden');
            const color = config.colors && config.colors[modId] ? config.colors[modId] : '#f2f1f5';
            document.getElementById('modal-hud-color-picker').value = color.startsWith('#') && color.length === 7 ? color : '#f2f1f5';
            document.getElementById('modal-hud-color-text').value = color;
        } else {
            colorPickerContainer.classList.add('hidden');
        }
    }

    // Build custom options slot based on the mod ID
    const customSlot = document.getElementById('modal-custom-options');
    customSlot.innerHTML = '';

    if (modId === 'toggle-crosshair') {
        customSlot.innerHTML = `
            <div style="background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06); border-radius: 8px; padding: 0.8rem; font-size: 0.8rem; text-align: center; color: var(--text-muted);">
                For advanced Crosshair shapes and preview, please use the dedicated <a href="#" onclick="closeHudOptionsModal(); switchTab('tab-crosshair'); return false;" style="color: var(--primary); text-decoration: underline;">Crosshair Tab</a>.
            </div>
        `;
    } else if (modId === 'toggle-top-right') {
        const format = config.coordsFormat || 'xyz';
        customSlot.innerHTML = `
            <div class="setting-item-col">
                <span class="setting-label">Display Format</span>
                <div class="style-selector" id="opt-coords-format">
                    <button class="style-option ${format === 'xyz' ? 'active' : ''}" onclick="setCustomModOption('coordsFormat', 'xyz')">XYZ</button>
                    <button class="style-option ${format === 'compact' ? 'active' : ''}" onclick="setCustomModOption('coordsFormat', 'compact')">Compact</button>
                    <button class="style-option ${format === 'direction' ? 'active' : ''}" onclick="setCustomModOption('coordsFormat', 'direction')">With Dir</button>
                </div>
            </div>
        `;
    } else if (modId === 'toggle-bottom-right') {
        const layout = config.keystrokesLayout || 'wasd';
        customSlot.innerHTML = `
            <div class="setting-item-col">
                <span class="setting-label">Keystrokes Layout</span>
                <div class="style-selector" id="opt-keys-layout">
                    <button class="style-option ${layout === 'wasd' ? 'active' : ''}" onclick="setCustomModOption('keystrokesLayout', 'wasd')">WASD</button>
                    <button class="style-option ${layout === 'arrows' ? 'active' : ''}" onclick="setCustomModOption('keystrokesLayout', 'arrows')">Arrows</button>
                </div>
            </div>
        `;
    } else if (modId === 'toggle-bottom-left') {
        const unit = config.speedometerUnit || 'bps';
        customSlot.innerHTML = `
            <div class="setting-item-col">
                <span class="setting-label">Speedometer Unit</span>
                <div class="style-selector" id="opt-speed-unit">
                    <button class="style-option ${unit === 'bps' ? 'active' : ''}" onclick="setCustomModOption('speedometerUnit', 'bps')">BPS</button>
                    <button class="style-option ${unit === 'kmh' ? 'active' : ''}" onclick="setCustomModOption('speedometerUnit', 'kmh')">KM/H</button>
                </div>
            </div>
        `;
    } else if (modId === 'toggle-autoclicker') {
        const cps = config.autoclickerCps || 12;
        const keybind = config.autoclickerKeybind || 'G';
        const button = config.autoclickerButton || 'left';
        customSlot.innerHTML = `
            <div class="setting-item-col" style="margin-bottom: 1rem;">
                <span class="setting-label">Clicks Per Second (CPS): <span id="val-autoclicker-cps">${cps} CPS</span></span>
                <input type="range" id="autoclicker-cps-range" min="1" max="25" value="${cps}" oninput="updateAutoclickerCps(this.value)" style="width: 100%;">
            </div>
            <div class="setting-item-col" style="margin-bottom: 1rem;">
                <span class="setting-label">Click Button</span>
                <div class="style-selector" id="opt-autoclicker-button">
                    <button class="style-option ${button === 'left' ? 'active' : ''}" onclick="setAutoclickerButton('left')">Left (Attack)</button>
                    <button class="style-option ${button === 'right' ? 'active' : ''}" onclick="setAutoclickerButton('right')">Right (Use)</button>
                </div>
            </div>
            <div class="setting-item-col">
                <span class="setting-label">Toggle Keybind</span>
                <div class="keybind-selector" onclick="startRebindingAutoclicker(event)">
                    <span>Click to Bind</span>
                    <span id="autoclicker-keybind-btn" class="keybind-badge">${keybind}</span>
                </div>
            </div>
        `;
    }

    // Show modal
    document.getElementById('hud-options-modal').classList.remove('hidden');
}

function closeHudOptionsModal() {
    document.getElementById('hud-options-modal').classList.add('hidden');
    currentEditingModId = null;
}

function updateModalScale(val) {
    document.getElementById('modal-hud-scale-val').innerText = val;
    if (currentEditingModId) {
        if (!config.scales) config.scales = {};
        const widgetId = currentEditingModId.replace('toggle-', 'hud-');
        config.scales[widgetId] = parseInt(val);
        applyScaleToElement(widgetId, parseInt(val));
    }
}

function updateModalColor(val) {
    if (!val.startsWith('#')) val = '#' + val;
    document.getElementById('modal-hud-color-picker').value = val.length === 7 ? val : '#f2f1f5';
    document.getElementById('modal-hud-color-text').value = val;
    
    if (currentEditingModId) {
        if (!config.colors) config.colors = {};
        config.colors[currentEditingModId] = val;
        
        const widgetId = currentEditingModId.replace('toggle-', 'hud-');
        const el = document.getElementById(widgetId);
        if (el) {
            el.style.color = val;
            el.style.borderColor = val;
        }
    }
}

function setCustomModOption(key, val) {
    config[key] = val;
    
    let selectorId = '';
    if (key === 'coordsFormat') selectorId = 'opt-coords-format';
    else if (key === 'keystrokesLayout') selectorId = 'opt-keys-layout';
    else if (key === 'speedometerUnit') selectorId = 'opt-speed-unit';
    
    const selector = document.getElementById(selectorId);
    if (selector) {
        selector.querySelectorAll('.style-option').forEach(btn => {
            if (btn.innerText.toLowerCase() === val.toLowerCase() || btn.getAttribute('onclick').includes("'" + val + "'")) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }
    
    saveConfig();
}

function resetModalHudPosition() {
    if (currentEditingModId) {
        const widgetId = currentEditingModId.replace('toggle-', 'hud-');
        const el = document.getElementById(widgetId);
        if (el) {
            el.style.left = '';
            el.style.top = '';
            el.style.right = '';
            el.style.bottom = '';
            
            if (config.positions) {
                delete config.positions[widgetId];
            }
            saveConfig();
        }
    }
}

function saveModalHudOptions() {
    saveConfig();
    closeHudOptionsModal();
}

function openCreateProfileModal() {
    switchTab('tab-settings');
    const input = document.getElementById('profile-new-username');
    if (input) {
        input.focus();
        input.scrollIntoView({ behavior: 'smooth' });
    }
}

function enterHudEditMode() {
    window.MinecraftBridge.send('enter_edit_mode')
        .catch(err => {
            console.error("Failed to enter edit mode: ", err);
            // Fallback for browser mock
            window.MinecraftBridge.trigger('hud:update_interactive', { interactive: true, editMode: true });
        });
}

function exitHudEditMode() {
    window.MinecraftBridge.send('enter_mods_mode')
        .catch(err => {
            console.error("Failed to exit edit mode: ", err);
            // Fallback for browser mock
            window.MinecraftBridge.trigger('hud:update_interactive', { interactive: true, editMode: false });
        });
}

function closeHubSettings() {
    const settingsPanel = document.getElementById('overlay-settings-panel');
    if (settingsPanel && !settingsPanel.classList.contains('hidden')) {
        settingsPanel.classList.add('hidden');
        return;
    }
    closeOverlayScreen();
}

function closeOverlayScreen() {
    window.MinecraftBridge.send('close_overlay')
        .catch(err => {
            console.error("Failed to close overlay: ", err);
            // Fallback for browser mock
            window.MinecraftBridge.trigger('overlay:toggle', false);
        });
}

function showHudEditBanner(visible) {
    const banner = document.getElementById('hud-edit-banner');
    if (banner) {
        if (visible) {
            banner.classList.remove('hidden');
        } else {
            banner.classList.add('hidden');
        }
    }
    const overlayContainer = document.getElementById('overlay-container');
    if (overlayContainer) {
        if (visible) {
            overlayContainer.classList.add('edit-active');
        } else {
            overlayContainer.classList.remove('edit-active');
        }
    }
}

// Get initial state on page load
window.addEventListener('DOMContentLoaded', () => {
    // Synchronize UI config on load
    loadConfigToUI();
    setupDraggableHUDs();
    loadQuickJoinServers();

    window.MinecraftBridge.send('get_state')
        .then(state => {
            const playerNameEl = document.getElementById('player-name');
            const clientVersionEl = document.getElementById('client-version');
            if (playerNameEl) playerNameEl.innerText = state.username || "Player";
            if (clientVersionEl) clientVersionEl.innerText = state.version || "1.21.11";

            updateTitleProfileButton(state.username || "Guest");
            updateUIState(state);

            // Sync fullbright and crosshair state with Java after state is loaded
            if (window.MinecraftBridge && window.MinecraftBridge.send) {
                window.MinecraftBridge.send('toggle_fullbright', { enabled: !!config.toggles['toggle-fullbright'] });
                syncAutoclickerSettings();
            }
        })
        .catch(err => {
            console.warn("Could not retrieve Java state. Using mock state.");
            const playerNameEl = document.getElementById('player-name');
            if (playerNameEl) playerNameEl.innerText = "Developer";

            updateTitleProfileButton("Developer");
            updateUIState({ inWorld: true, isWebTitle: false, isOverlay: false, editMode: false });

            // Sync fullbright state even in mock mode
            if (window.MinecraftBridge && window.MinecraftBridge.send) {
                window.MinecraftBridge.send('toggle_fullbright', { enabled: !!config.toggles['toggle-fullbright'] });
                syncAutoclickerSettings();
            }

            // Trigger simulated HUD updates for browser mocking/testing
            startMockHudSimulation();
        });

    // Global ESC key listener - closes settings panel first, then overlay
    document.addEventListener('keydown', (e) => {
        if (e.code === 'Escape') {
            e.preventDefault();
            const hudModal = document.getElementById('hud-options-modal');
            if (hudModal && !hudModal.classList.contains('hidden')) {
                closeHudOptionsModal();
                return;
            }
            closeHubSettings();
        }
    });
});

// Browser mock HUD simulation
function startMockHudSimulation() {
    console.log("[Mock] Starting simulated HUD updates...");
    
    window.MinecraftBridge.trigger('overlay:toggle', true);
    
    let mockX = 142.5;
    let mockY = 64.0;
    let mockZ = -829.1;
    let ticks = 0;
    let mockSessionSeconds = 0;

    setInterval(() => {
        ticks++;
        mockX += (Math.random() - 0.5) * 0.4;
        mockZ += (Math.random() - 0.5) * 0.4;
        
        const isW = Math.sin(ticks / 5) > 0;
        const isA = Math.cos(ticks / 7) > 0.3;
        const isS = Math.sin(ticks / 10) > 0.5;
        const isD = Math.cos(ticks / 4) > 0.2;
        const isSpace = Math.sin(ticks / 8) > 0.6;
        const isLmb = Math.sin(ticks / 3) > 0.4;
        const isRmb = Math.cos(ticks / 6) > 0.5;
        
        const mockBps = (isW || isA || isS || isD) ? 4.3 + Math.random() * 2 + (isSpace ? 3.0 : 0) : 0.0;
        
        const mockLmbCps = isLmb ? Math.floor(6 + Math.random() * 4) : 0;
        const mockRmbCps = isRmb ? Math.floor(4 + Math.random() * 3) : 0;

        if (ticks % 10 === 0) {
            mockSessionSeconds++;
        }

        // Trigger keystrokes (Event-driven mockup)
        window.MinecraftBridge.trigger('hud:update_keys', {
            w: isW,
            a: isA,
            s: isS,
            d: isD,
            space: isSpace,
            lmb: isLmb,
            rmb: isRmb,
            shift: isS,
            sprint: isW,
            lmbCps: mockLmbCps,
            rmbCps: mockRmbCps
        });

        // Trigger consolidated HUD stats at 10 FPS
        const simulatedStats = {
            fps: 144 + Math.floor(Math.random() * 15),
            ping: 28 + Math.floor(Math.random() * 5),
            bps: mockBps,
            memUsed: 1240 + Math.floor(Math.random() * 100),
            memMax: 4096,
            frameTime: 6.9,
            x: mockX,
            y: mockY,
            z: mockZ,
            facing: "WEST",
            biome: "plains",
            cx: Math.floor(mockX) >> 4,
            cz: Math.floor(mockZ) >> 4,
            netherX: mockX / 8.0,
            netherZ: mockZ / 8.0,
            health: 20.0 - (ticks % 40 < 5 ? 4.0 : 0.0),
            maxHealth: 20.0,
            hunger: 19,
            absorption: 0.0,
            air: 300,
            maxAir: 300,
            xp: (ticks % 100) / 100,
            xpLevel: 12,
            sneaking: false,
            sprinting: isW,
            combo: ticks % 20 < 4 ? Math.floor(ticks % 20) : 0,
            reach: 3.0 + Math.random() * 0.4,
            targetName: ticks % 20 < 4 ? "EnemyPlayer" : "",
            targetHealth: 15.0,
            targetMaxHealth: 20.0,
            targetDistance: 2.8,
            targetArmor: ["netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"],
            sessionTime: mockSessionSeconds,
            kills: 5,
            deaths: 2,
            winStreak: 3,
            currentStreak: 3,
            serverName: "Hypixel Network",
            serverIp: "mc.hypixel.net",
            serverPlayers: 18504,
            scoreboard: ["HYPIXEL", "Players: 18,504", "Active Game", "Map: Plains"],
            potions: [
                { name: "Speed", duration: "1:24", amp: 2, beneficial: true, color: 8171462 },
                { name: "Strength", duration: "0:45", amp: 1, beneficial: true, color: 9643043 }
            ],
            item: {
                name: "Netherite Sword",
                durability: 1845,
                maxDurability: 2031,
                count: 1,
                cooldown: (ticks % 10) / 10
            },
            armor: {
                helmet: { empty: false, id: "netherite_helmet", durability: 320, max: 407, percent: 78.6 },
                chest: { empty: false, id: "netherite_chestplate", durability: 412, max: 592, percent: 69.5 },
                leggings: { empty: false, id: "netherite_leggings", durability: 120, max: 555, percent: 21.6 },
                boots: { empty: false, id: "netherite_boots", durability: 399, max: 481, percent: 82.9 },
                offhand: { empty: false, id: "totem_of_undying", durability: 0, max: 0, percent: 100.0, count: 1 }
            },
            utility: {
                day: 4,
                weather: "Clear",
                time: "14:30"
            },
            yaw: (ticks * 2) % 360,
            entities: [
                { dx: 12 + Math.sin(ticks/5)*5, dz: -8 + Math.cos(ticks/5)*3, type: 'player', name: 'ProGamer' },
                { dx: -15 + Math.cos(ticks/10)*2, dz: 22 + Math.sin(ticks/10)*2, type: 'hostile', name: 'Zombie' },
                { dx: 5, dz: 18, type: 'passive', name: 'Sheep' }
            ]
        };

        window.MinecraftBridge.trigger('hud:update_stats', simulatedStats);
    }, 100);
}

// Drag and Drop HUD Customizer implementation
let isHudEditable = false;
let isDragging = false;
let activeDragElement = null;
let dragStartX = 0;
let dragStartY = 0;
let elementStartX = 0;
let elementStartY = 0;

function setupDraggableHUDs() {
    ALL_WIDGETS.forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        
        // Load positions from config
        if (config.positions && config.positions[id]) {
            const pos = config.positions[id];
            el.style.left = pos.left;
            el.style.top = pos.top;
            el.style.right = 'auto';
            el.style.bottom = 'auto';
        }
        
        el.addEventListener('mousedown', (e) => {
            if (!isHudEditable) return;
            
            isDragging = true;
            activeDragElement = el;
            
            const rect = el.getBoundingClientRect();
            elementStartX = rect.left;
            elementStartY = rect.top;
            
            dragStartX = e.clientX;
            dragStartY = e.clientY;
            
            el.style.cursor = 'grabbing';
            el.style.right = 'auto';
            el.style.bottom = 'auto';
            
            e.preventDefault();
        });

        // Wheel listener for cursor-hovered scaling
        el.addEventListener('wheel', (e) => {
            if (!isHudEditable) return;
            
            e.preventDefault();
            
            if (!config.scales) {
                config.scales = {};
            }
            
            let currentScale = 100;
            if (config.scales[id] !== undefined) {
                currentScale = config.scales[id];
            } else {
                if (id === 'hud-top-left') currentScale = config.scales['scale-top-left'] || 100;
                else if (id === 'hud-top-right') currentScale = config.scales['scale-top-right'] || 100;
                else if (id === 'hud-bottom-left') currentScale = config.scales['scale-bottom-left'] || 100;
                else if (id === 'hud-bottom-right') currentScale = config.scales['scale-bottom-right'] || 100;
                else if (id === 'hud-armor') currentScale = config.scales['scale-armor'] || 100;
            }
            
            const dy = (e.deltaY !== undefined && e.deltaY !== 0) ? e.deltaY : (e.wheelDelta ? -e.wheelDelta : e.detail);
            if (dy < 0) {
                currentScale = Math.min(currentScale + 5, 200);
            } else {
                currentScale = Math.max(currentScale - 5, 50);
            }
            
            config.scales[id] = currentScale;
            applyScaleToElement(id, currentScale);
            saveConfig();
        }, { passive: false });
    });
    
    document.addEventListener('mousemove', (e) => {
        if (!isDragging || !activeDragElement) return;
        
        const dx = e.clientX - dragStartX;
        const dy = e.clientY - dragStartY;
        
        const newLeft = elementStartX + dx;
        const newTop = elementStartY + dy;
        
        activeDragElement.style.left = newLeft + 'px';
        activeDragElement.style.top = newTop + 'px';
    });
    
    document.addEventListener('mouseup', () => {
        if (isDragging && activeDragElement) {
            activeDragElement.style.cursor = 'grab';
            
            if (!config.positions) {
                config.positions = {};
            }
            config.positions[activeDragElement.id] = {
                left: activeDragElement.style.left,
                top: activeDragElement.style.top
            };
            saveConfig();
            
            isDragging = false;
            activeDragElement = null;
        }
    });
}

function updateInteractiveDragState(interactive) {
    isHudEditable = interactive;
    ALL_WIDGETS.forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        
        if (interactive) {
            el.style.pointerEvents = 'auto';
            el.style.cursor = 'grab';
            el.classList.add('widget-editing');
        } else {
            el.style.pointerEvents = 'none';
            el.style.cursor = 'default';
            el.classList.remove('widget-editing');
        }
    });
}

function resetHUDPositions() {
    config.positions = {};
    config.scales = {};
    saveConfig();
    location.reload();
}

// Minecraft armor material textures mapped to Unicode blocks
const armorMaterialMap = {
    'leather': { char: '▮', color: '#8B5A3C', glow: '#A0622A' },
    'chainmail': { char: '⬢', color: '#A9A9A9', glow: '#C0C0C0' },
    'iron': { char: '■', color: '#C0C0C0', glow: '#D3D3D3' },
    'diamond': { char: '◆', color: '#33EBCB', glow: '#5FFFFF' },
    'golden': { char: '★', color: '#FFD700', glow: '#FFF44F' },
    'netherite': { char: '●', color: '#443A3B', glow: '#6B5B5B' }
};

function getArmorIcon(itemId) {
    if (!itemId) return { text: '?', color: '#666', glow: '#888' };

    const material = itemId.split('_')[0];
    const armorData = armorMaterialMap[material] || { char: '?', color: '#666', glow: '#888' };

    return armorData;
}

// Create texture canvas for armor items
function createArmorTexture(itemId) {
    if (itemId && itemId.startsWith('data:')) {
        return itemId;
    }
    const canvas = document.createElement('canvas');
    canvas.width = 32;
    canvas.height = 32;
    const ctx = canvas.getContext('2d');

    // Material colors based on Minecraft textures
    const textures = {
        'leather_helmet': '#A06020',
        'leather_chestplate': '#8B5020',
        'leather_leggings': '#7A4620',
        'leather_boots': '#6B3D1A',
        'chainmail_helmet': '#888888',
        'chainmail_chestplate': '#999999',
        'chainmail_leggings': '#777777',
        'chainmail_boots': '#666666',
        'iron_helmet': '#D0D0D0',
        'iron_chestplate': '#C8C8C8',
        'iron_leggings': '#B8B8B8',
        'iron_boots': '#A8A8A8',
        'diamond_helmet': '#33FFCC',
        'diamond_chestplate': '#2FFFCC',
        'diamond_leggings': '#2BFFCC',
        'diamond_boots': '#27FFCC',
        'golden_helmet': '#FFDD00',
        'golden_chestplate': '#FFDD00',
        'golden_leggings': '#FFD700',
        'golden_boots': '#FFC700',
        'netherite_helmet': '#5A5A5A',
        'netherite_chestplate': '#505050',
        'netherite_leggings': '#464646',
        'netherite_boots': '#3C3C3C'
    };

    const color = textures[itemId] || '#888888';

    // Draw base armor shape
    ctx.fillStyle = color;
    ctx.shadowColor = 'rgba(0,0,0,0.5)';
    ctx.shadowBlur = 4;

    // Simple armor icon (rounded rectangle)
    ctx.fillRect(6, 6, 20, 20);
    ctx.beginPath();
    ctx.arc(16, 10, 8, 0, Math.PI * 2);
    ctx.fill();

    // Add texture detail
    ctx.strokeStyle = 'rgba(255,255,255,0.3)';
    ctx.lineWidth = 1;
    ctx.strokeRect(6, 6, 20, 20);

    return canvas.toDataURL('image/png');
}

function updateArmorDisplay(helmetData, chestData, leggingsData, bootsData) {
    const armorParts = [
        { id: 'armor-helmet', data: helmetData, fallback: '🪖' },
        { id: 'armor-chest', data: chestData, fallback: '👕' },
        { id: 'armor-leggings', data: leggingsData, fallback: '👖' },
        { id: 'armor-boots', data: bootsData, fallback: '🥾' }
    ];

    armorParts.forEach(part => {
        const el = document.getElementById(part.id);
        if (!el) return;

        const iconEl = el.querySelector('.armor-icon');
        const barFillEl = el.querySelector('.bar-fill');
        const durBarEl = el.querySelector('.durability-bar');

        if (part.data.empty) {
            iconEl.innerHTML = part.fallback;
            iconEl.style.opacity = '0.25';
            if (durBarEl) durBarEl.style.display = 'none';
        } else {
            iconEl.innerHTML = mcIcon(part.data.id, part.fallback);
            iconEl.style.opacity = '1';

            // Durability Bar (only show if item is damageable and actually has damage)
            if (durBarEl && barFillEl) {
                const percent = Math.round(part.data.percent);
                const isDamaged = part.data.durability < part.data.max;
                if (part.data.max > 0 && isDamaged) {
                    durBarEl.style.display = 'block';
                    barFillEl.style.width = percent + '%';
                    const hue = percent > 50 ? 120 : (percent > 25 ? 60 : 0);
                    barFillEl.style.backgroundColor = `hsl(${hue}, 100%, 50%)`;
                } else {
                    durBarEl.style.display = 'none';
                }
            }
        }
    });
}

// Scroll-to-resize for HUD widgets (no size limits)
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.hud-widget').forEach(widget => {
        widget.addEventListener('wheel', function(e) {
            if (!config.editMode) return;
            e.preventDefault();

            const scrollDelta = e.deltaY > 0 ? 10 : -10;
            const currentWidth = widget.offsetWidth;
            const currentHeight = widget.offsetHeight;

            widget.style.width = (currentWidth + scrollDelta) + 'px';
            widget.style.height = (currentHeight + scrollDelta) + 'px';

            saveConfig();
        }, { passive: false });
    });
});

function updateTitleProfileButton(username) {
    const titleProfileNameEl = document.getElementById('title-profile-name');
    if (titleProfileNameEl) {
        titleProfileNameEl.innerText = username;
    }
}

function openAccountManager() {
    const modal = document.getElementById('account-manager-modal');
    if (!modal) return;
    
    modal.classList.remove('hidden');
    refreshModalAccounts();
}

function closeAccountManager() {
    const modal = document.getElementById('account-manager-modal');
    if (modal) {
        modal.classList.add('hidden');
    }
}

function refreshModalAccounts() {
    window.MinecraftBridge.send('get_state')
        .then(state => {
            const activeUser = state.username || "Guest";
            const activeUserEl = document.getElementById('modal-active-user');
            if (activeUserEl) activeUserEl.innerText = activeUser;
            updateTitleProfileButton(activeUser);
            
            return window.MinecraftBridge.send('profiles:get')
                .then(data => {
                    const listEl = document.getElementById('modal-account-list');
                    if (!listEl) return;
                    listEl.innerHTML = '';
                    
                    const profiles = data.profiles || [];
                    profiles.forEach(profile => {
                        const isCurrent = profile.username === activeUser;
                        
                        const item = document.createElement('div');
                        item.className = `account-item ${isCurrent ? 'active' : ''}`;
                        item.onclick = (e) => {
                            if (e.target.closest('.remove-account-btn')) return;
                            selectModalAccount(profile.username);
                        };
                        
                        item.innerHTML = `
                            <div class="account-item-left">
                                <div class="account-avatar">${profile.username.substring(0, 2).toUpperCase()}</div>
                                <span class="account-name">${profile.username}</span>
                                <span class="account-type-tag">${profile.type || 'offline'}</span>
                            </div>
                            ${!isCurrent ? `
                                <button class="remove-account-btn" onclick="deleteModalAccount('${profile.username}')" title="Delete Account">✕</button>
                            ` : ''}
                        `;
                        listEl.appendChild(item);
                    });
                });
        })
        .catch(err => console.error("Error refreshing accounts:", err));
}

function selectModalAccount(username) {
    window.MinecraftBridge.send('profiles:select', { username: username })
        .then(() => {
            refreshModalAccounts();
        })
        .catch(err => console.error("Failed to select account:", err));
}

function deleteModalAccount(username) {
    window.MinecraftBridge.send('profiles:delete', { username: username })
        .then(() => {
            refreshModalAccounts();
        })
        .catch(err => console.error("Failed to delete account:", err));
}

function addModalAccount(type) {
    const input = document.getElementById('new-account-username');
    if (!input) return;
    const username = input.value.trim();
    if (!username) return;
    
    if (type === 'offline') {
        window.MinecraftBridge.send('profiles:add_offline', { username: username })
            .then(() => {
                input.value = '';
                return selectModalAccount(username);
            })
            .catch(err => console.error("Failed to add offline account:", err));
    } else if (type === 'microsoft') {
        window.MinecraftBridge.send('profiles:microsoft_login')
            .then(() => {
                input.value = '';
                closeAccountManager();
            })
            .catch(err => console.error("Failed to trigger Microsoft login:", err));
    }
}

// --- OPTIMIZED LOW-OVERHEAD SNOWFALL EFFECT ---
let snowAnimationId = null;
let snowCanvas = null;
let snowCtx = null;
let snowflakes = [];
const MAX_SNOWFLAKES = 35; // Reduced for absolute zero lag

function initSnowfall() {
    snowCanvas = document.getElementById('snowfall-canvas');
    if (!snowCanvas) return;
    
    // Low-overhead context creation
    snowCtx = snowCanvas.getContext('2d', { alpha: true });
    
    function resizeCanvas() {
        if (snowCanvas) {
            const dpr = window.devicePixelRatio || 1;
            const w = window.innerWidth;
            const h = window.innerHeight;
            
            // Adjust canvas resolution for high-DPI screens (removes blurriness)
            snowCanvas.width = w * dpr;
            snowCanvas.height = h * dpr;
            snowCanvas.style.width = w + 'px';
            snowCanvas.style.height = h + 'px';
            
            // Reset and apply scaling
            snowCtx.setTransform(1, 0, 0, 1, 0, 0);
            snowCtx.scale(dpr, dpr);
        }
    }
    
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();
    
    // Pre-allocate flakes to avoid GC thrashing/memory overhead
    snowflakes = [];
    for (let i = 0; i < MAX_SNOWFLAKES; i++) {
        const size = Math.random() * 3.5 + 1.2; // Size variant from 1.2px to 4.7px
        snowflakes.push({
            x: Math.random() * window.innerWidth,
            y: Math.random() * window.innerHeight,
            size: size,
            // 3D Parallax: Larger flakes are closer, so they fall faster and drift more dynamically
            speed: (size / 4.7) * 2.2 + 1.0, // Fast fall speed range (1.0 to 3.2)
            opacity: Math.random() * 0.45 + 0.25, // Variant opacities for depth
            swaySpeed: Math.random() * 0.02 + 0.01,
            swayOffset: Math.random() * Math.PI * 2
        });
    }
}

function updateAndDrawSnow() {
    if (!snowCtx || !snowCanvas) return;
    
    const w = window.innerWidth;
    const h = window.innerHeight;
    
    // Clear canvas
    snowCtx.clearRect(0, 0, w, h);
    
    // Draw all pre-allocated flakes
    for (let i = 0; i < MAX_SNOWFLAKES; i++) {
        const f = snowflakes[i];
        
        snowCtx.fillStyle = `rgba(255, 255, 255, ${f.opacity})`;
        
        if (f.size < 2.6) {
            // Background snow: simple blocky square dot
            snowCtx.fillRect(f.x, f.y, f.size, f.size);
        } else {
            // Foreground snow: pixel-art cross (+) shape
            const s = Math.round(f.size);
            const thickness = s > 4 ? 2 : 1;
            // Horizontal bar
            snowCtx.fillRect(f.x - s / 2, f.y - thickness / 2, s, thickness);
            // Vertical bar
            snowCtx.fillRect(f.x - thickness / 2, f.y - s / 2, thickness, s);
        }
        
        // Update physics
        f.y += f.speed;
        f.swayOffset += f.swaySpeed;
        f.x += Math.sin(f.swayOffset) * 0.3; // Balanced left/right sway
        
        // Wrap around boundaries correctly using logical window width/height
        if (f.y > h + 5) {
            f.y = -5;
            f.x = Math.random() * w;
        }
        if (f.x > w + 5) {
            f.x = -5;
        } else if (f.x < -5) {
            f.x = w + 5;
        }
    }
}

function tickSnowfall() {
    // Only continue animation loop if we are on the main menu and not in-world
    if (currentUiState.isWebTitle && !currentUiState.inWorld) {
        updateAndDrawSnow();
        snowAnimationId = requestAnimationFrame(tickSnowfall);
    } else {
        // Stop animation loop and free up resources
        if (snowCtx && snowCanvas) {
            snowCtx.clearRect(0, 0, snowCanvas.width, snowCanvas.height);
        }
        snowAnimationId = null;
    }
}

function manageSnowfallState() {
    // Disabled to eliminate rendering overhead and lag
}

// ==========================================
// THEME EDITOR LAUNCHER
// ==========================================

function launchThemeEditor() {
    if (window.MinecraftBridge) {
        window.MinecraftBridge.send('launch_editor')
            .then(resp => {
                console.log('[Fugo] Theme Editor launched on port:', resp.port || 8642);
                // Show a toast notification
                showAnimToast('🎨 Theme Editor opened in your browser!', 'info');
            })
            .catch(err => {
                console.error('[Fugo] Failed to launch editor:', err);
                showAnimToast('Failed to launch Theme Editor', 'error');
            });
    } else {
        // Fallback for standalone testing — just open in a new tab
        window.open('editor.html', '_blank');
    }
}

// ==========================================
// FUGO HUB WORKSPACE CONTROLLER
// ==========================================

let currentHubApp = 'home';
let selectedRecipe = null;
let selectedPotion = null;
let selectedEnchantment = null;
let selectedVillager = null;
let activeRecipesCategory = 'all';

// Real Minecraft item/block textures, fetched live from the vanilla asset mirror.
// (id -> vanilla texture filename, no extension)
const MC_TEXTURE_BASE = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.1/assets/minecraft/textures/item/";
const MC_BLOCK_TEXTURE_BASE = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.1/assets/minecraft/textures/block/";
const MC_BLOCK_ITEMS = new Set(["cobblestone", "oak_planks", "glass", "obsidian", "redstone_block"]);

function mcIcon(id, fallbackEmoji) {
    if (!id) return fallbackEmoji || '';
    const base = MC_BLOCK_ITEMS.has(id) ? MC_BLOCK_TEXTURE_BASE : MC_TEXTURE_BASE;
    return `<img src="${base}${id}.png" class="mc-item-icon" alt="${id}" onerror="this.replaceWith(document.createTextNode('${fallbackEmoji || ''}'))">`;
}

// Mock Datasets
let recipesData = [
    { name: "Diamond Sword", category: "combat", icon: "diamond_sword", grid: ['', 'diamond', '', '', 'diamond', '', '', 'stick', ''], result: "diamond_sword", ingredients: ["2x Diamond", "1x Stick"] },
    { name: "Golden Apple", category: "combat", icon: "golden_apple", grid: ['gold_ingot', 'gold_ingot', 'gold_ingot', 'gold_ingot', 'apple', 'gold_ingot', 'gold_ingot', 'gold_ingot', 'gold_ingot'], result: "golden_apple", ingredients: ["8x Gold Ingot", "1x Apple"] },
    { name: "Shield", category: "combat", icon: "shield", grid: ['oak_planks', 'iron_ingot', 'oak_planks', 'oak_planks', 'oak_planks', 'oak_planks', '', 'oak_planks', ''], result: "shield", ingredients: ["6x Wood Planks", "1x Iron Ingot"] },
    { name: "Piston", category: "redstone", icon: "piston", grid: ['oak_planks', 'oak_planks', 'oak_planks', 'cobblestone', 'iron_ingot', 'cobblestone', 'cobblestone', 'redstone', 'cobblestone'], result: "piston", ingredients: ["3x Wood Planks", "4x Cobblestone", "1x Iron Ingot", "1x Redstone Dust"] },
    { name: "Sticky Piston", category: "redstone", icon: "sticky_piston", grid: ['', '', '', '', 'slime_ball', '', '', 'piston', ''], result: "sticky_piston", ingredients: ["1x Slimeball", "1x Piston"] },
    { name: "Observer", category: "redstone", icon: "observer", grid: ['cobblestone', 'cobblestone', 'cobblestone', 'redstone', 'redstone', 'diamond', 'cobblestone', 'cobblestone', 'cobblestone'], result: "observer", ingredients: ["6x Cobblestone", "2x Redstone Dust", "1x Nether Quartz"] },
    { name: "Enchanting Table", category: "blocks", icon: "enchanting_table", grid: ['', 'book', '', 'diamond', 'obsidian', 'diamond', 'obsidian', 'obsidian', 'obsidian'], result: "enchanting_table", ingredients: ["1x Book", "2x Diamond", "4x Obsidian"] },
    { name: "Furnace", category: "blocks", icon: "furnace", grid: ['cobblestone', 'cobblestone', 'cobblestone', 'cobblestone', '', 'cobblestone', 'cobblestone', 'cobblestone', 'cobblestone'], result: "furnace", ingredients: ["8x Cobblestone"] },
    { name: "Hopper", category: "blocks", icon: "hopper", grid: ['iron_ingot', '', 'iron_ingot', 'iron_ingot', 'chest', 'iron_ingot', '', 'iron_ingot', ''], result: "hopper", ingredients: ["5x Iron Ingot", "1x Chest"] },
    { name: "Crafting Table", category: "blocks", icon: "crafting_table", grid: ['', '', '', 'oak_planks', 'oak_planks', '', 'oak_planks', 'oak_planks', ''], result: "crafting_table", ingredients: ["4x Wood Planks"] },
    { name: "Bow", category: "tools", icon: "bow", grid: ['', 'oak_planks', 'string', 'oak_planks', '', 'string', '', 'oak_planks', 'string'], result: "bow", ingredients: ["3x Stick", "3x String"] },
    { name: "Arrow", category: "tools", icon: "arrow", grid: ['', 'flint', '', '', 'stick', '', '', 'feather', ''], result: "arrow", ingredients: ["1x Flint", "1x Stick", "1x Feather"] },
    { name: "Diamond Pickaxe", category: "tools", icon: "diamond_pickaxe", grid: ['diamond', 'diamond', 'diamond', '', 'stick', '', '', 'stick', ''], result: "diamond_pickaxe", ingredients: ["3x Diamond", "2x Stick"] },
    { name: "Torches", category: "blocks", icon: "torch", grid: ['', 'coal', '', '', 'stick', '', '', '', ''], result: "torch", ingredients: ["1x Coal/Charcoal", "1x Stick"] },
    { name: "Beacon", category: "blocks", icon: "beacon", grid: ['glass', 'glass', 'glass', 'glass', 'nether_star', 'glass', 'obsidian', 'obsidian', 'obsidian'], result: "beacon", ingredients: ["5x Glass", "1x Nether Star", "3x Obsidian"] }
];

const brewingData = [
    { name: "Awkward Potion", base: "Water Bottle", ingredient: "Nether Wart", effect: "None (Base Potion for other brews)", icon: "🧪" },
    { name: "Potion of Swiftness", base: "Awkward Potion", ingredient: "Sugar", effect: "Increases movement speed by 20% for 3:00.", icon: "⚡" },
    { name: "Potion of Healing", base: "Awkward Potion", ingredient: "Glistering Melon Slice", effect: "Restores 4 health instantly.", icon: "💖" },
    { name: "Potion of Strength", base: "Awkward Potion", ingredient: "Blaze Powder", effect: "Increases melee damage by 3 for 3:00.", icon: "⚔️" },
    { name: "Potion of Poison", base: "Awkward Potion", ingredient: "Spider Eye", effect: "Inflicts 1 damage every 1.25 seconds for 0:45.", icon: "🤢" },
    { name: "Potion of Night Vision", base: "Awkward Potion", ingredient: "Golden Carrot", effect: "Grants vision in darkness for 3:00.", icon: "👁️" },
    { name: "Potion of Invisibility", base: "Potion of Night Vision", ingredient: "Fermented Spider Eye", effect: "Renders the player invisible for 3:00.", icon: "🌫️" },
    { name: "Potion of Regeneration", base: "Awkward Potion", ingredient: "Ghast Tear", effect: "Restores 1 health every 2.5 seconds for 0:45.", icon: "🍷" },
    { name: "Potion of Water Breathing", base: "Awkward Potion", ingredient: "Pufferfish", effect: "Prevents drowning under water for 3:00.", icon: "🫧" },
    { name: "Potion of Fire Resistance", base: "Awkward Potion", ingredient: "Magma Cream", effect: "Grants immunity to fire and lava damage for 3:00.", icon: "🔥" },
    { name: "Potion of Weakness", base: "Water Bottle", ingredient: "Fermented Spider Eye", effect: "Reduces melee damage by 4 for 1:30.", icon: "🥀" }
];

const enchantmentsData = [
    { name: "Sharpness", maxLevel: "V", target: "Sword, Axe", desc: "Increases melee attack damage by 1.25 extra damage per level.", conflicts: "Smite, Bane of Arthropods" },
    { name: "Smite", maxLevel: "V", target: "Sword, Axe", desc: "Increases attack damage against undead mobs (Zombies, Skeletons, Withers, etc.) by 2.5 damage per level.", conflicts: "Sharpness, Bane of Arthropods" },
    { name: "Bane of Arthropods", maxLevel: "V", target: "Sword, Axe", desc: "Increases attack damage against arthropod mobs (Spiders, Bees, Endermites) by 2.5 damage per level and applies Slowness IV.", conflicts: "Sharpness, Smite" },
    { name: "Protection", maxLevel: "IV", target: "Armor", desc: "Reduces most damage types by 4% per level. Total max reduction is capped at 80%.", conflicts: "Fire Protection, Blast Protection, Projectile Protection" },
    { name: "Fire Protection", maxLevel: "IV", target: "Armor", desc: "Reduces fire damage and burn duration. Reduces burn damage by 8% per level and burn time by 15% per level.", conflicts: "Protection, Blast Protection, Projectile Protection" },
    { name: "Blast Protection", maxLevel: "IV", target: "Armor", desc: "Reduces explosion damage by 8% per level and explosion knockback by 15% per level.", conflicts: "Protection, Fire Protection, Projectile Protection" },
    { name: "Projectile Protection", maxLevel: "IV", target: "Armor", desc: "Reduces damage from projectiles (Arrows, Shulker Bullets) by 8% per level.", conflicts: "Protection, Fire Protection, Blast Protection" },
    { name: "Feather Falling", maxLevel: "IV", target: "Boots", desc: "Reduces fall damage by 12% per level and ender pearl teleportation damage.", conflicts: "None" },
    { name: "Respiration", maxLevel: "III", target: "Helmet", desc: "Extends underwater breathing time by 15 seconds per level and reduces drowning damage.", conflicts: "None" },
    { name: "Aqua Affinity", maxLevel: "I", target: "Helmet", desc: "Removes the underwater mining speed penalty.", conflicts: "None" },
    { name: "Depth Strider", maxLevel: "III", target: "Boots", desc: "Increases underwater movement speed. Level III matches normal walking speed on land.", conflicts: "Frost Walker" },
    { name: "Frost Walker", maxLevel: "II", target: "Boots", desc: "Creates frosted ice blocks when walking over water and grants immunity to magma block damage.", conflicts: "Depth Strider" },
    { name: "Mending", maxLevel: "I", target: "Any breakable item", desc: "Repairs the item using collected Experience Orbs (2 durability per XP point).", conflicts: "Infinity" },
    { name: "Unbreaking", maxLevel: "III", target: "Any breakable item", desc: "Grants a chance for an item to avoid durability loss when used.", conflicts: "None" },
    { name: "Efficiency", maxLevel: "V", target: "Pickaxe, Shovel, Axe, Hoe, Shears", desc: "Increases mining speed on matching blocks.", conflicts: "None" },
    { name: "Silk Touch", maxLevel: "I", target: "Pickaxe, Shovel, Axe, Hoe", desc: "Causes blocks to drop themselves rather than their usual items (e.g., Ore blocks instead of raw minerals).", conflicts: "Fortune" },
    { name: "Fortune", maxLevel: "III", target: "Pickaxe, Shovel, Axe, Hoe", desc: "Increases the amount or drop rate of block drops.", conflicts: "Silk Touch" },
    { name: "Looting", maxLevel: "III", target: "Sword", desc: "Increases the amount of items dropped by mobs and increases the chance of rare drops.", conflicts: "None" },
    { name: "Fire Aspect", maxLevel: "II", target: "Sword", desc: "Sets target mobs on fire for 4 seconds per level.", conflicts: "None" },
    { name: "Knockback", maxLevel: "II", target: "Sword", desc: "Increases the knockback distance of attack hits.", conflicts: "None" }
];

const villagersData = [
    { name: "Librarian", icon: "📚", workstation: "Lectern", trades: [
        { tier: "Novice", deals: "Paper -> Emerald, Emerald -> Enchanted Book" },
        { tier: "Apprentice", deals: "Book -> Emerald, Emerald -> Lantern" },
        { tier: "Journeyman", deals: "Ink Sac -> Emerald, Emerald -> Glass" },
        { tier: "Expert", deals: "Book & Quill -> Emerald, Emerald -> Name Tag" },
        { tier: "Master", deals: "Emerald -> Enchanted Book, Emerald -> Compass" }
    ]},
    { name: "Armorer", icon: "🛡️", workstation: "Blast Furnace", trades: [
        { tier: "Novice", deals: "Coal -> Emerald, Emerald -> Iron Boots/Helmet" },
        { tier: "Apprentice", deals: "Iron Ingot -> Emerald, Emerald -> Chainmail" },
        { tier: "Journeyman", deals: "Lava Bucket -> Emerald, Emerald -> Shield" },
        { tier: "Expert", deals: "Diamond -> Emerald, Emerald -> Diamond Cuirass" },
        { tier: "Master", deals: "Emerald -> Diamond Helmet/Boots/Leggings" }
    ]},
    { name: "Weaponsmith", icon: "⚔️", workstation: "Grindstone", trades: [
        { tier: "Novice", deals: "Coal -> Emerald, Emerald -> Iron Axe" },
        { tier: "Apprentice", deals: "Iron Ingot -> Emerald, Emerald -> Iron Sword" },
        { tier: "Journeyman", deals: "Flint -> Emerald, Emerald -> Bell" },
        { tier: "Expert", deals: "Diamond -> Emerald, Emerald -> Diamond Axe" },
        { tier: "Master", deals: "Emerald -> Diamond Sword" }
    ]},
    { name: "Toolsmith", icon: "⛏️", workstation: "Smithing Table", trades: [
        { tier: "Novice", deals: "Coal -> Emerald, Emerald -> Stone Pickaxe/Shovel" },
        { tier: "Apprentice", deals: "Iron Ingot -> Emerald, Emerald -> Iron Pickaxe" },
        { tier: "Journeyman", deals: "Flint -> Emerald, Emerald -> Bell" },
        { tier: "Expert", deals: "Diamond -> Emerald, Emerald -> Diamond Shovel/Hoe" },
        { tier: "Master", deals: "Emerald -> Diamond Pickaxe" }
    ]},
    { name: "Fletcher", icon: "🏹", workstation: "Fletching Table", trades: [
        { tier: "Novice", deals: "Stick -> Emerald, Emerald -> Arrow" },
        { tier: "Apprentice", deals: "Flint -> Emerald, Emerald -> Bow" },
        { tier: "Journeyman", deals: "String -> Emerald, Emerald -> Crossbow" },
        { tier: "Expert", deals: "Feather -> Emerald, Emerald -> Tipped Arrow" },
        { tier: "Master", deals: "Emerald -> Bow/Crossbow" }
    ]},
    { name: "Cleric", icon: "🧪", workstation: "Brewing Stand", trades: [
        { tier: "Novice", deals: "Rotten Flesh -> Emerald, Emerald -> Redstone" },
        { tier: "Apprentice", deals: "Gold Ingot -> Emerald, Emerald -> Lapis Lazuli" },
        { tier: "Journeyman", deals: "Rabbit Foot -> Emerald, Emerald -> Glowstone" },
        { tier: "Expert", deals: "Scute -> Emerald, Emerald -> Ender Pearl" },
        { tier: "Master", deals: "Nether Wart -> Emerald, Emerald -> Bottle o' Enchanting" }
    ]},
    { name: "Farmer", icon: "🌾", workstation: "Composter", trades: [
        { tier: "Novice", deals: "Wheat/Potato/Carrot -> Emerald, Emerald -> Bread" },
        { tier: "Apprentice", deals: "Pumpkin -> Emerald, Emerald -> Pumpkin Pie" },
        { tier: "Journeyman", deals: "Melon -> Emerald, Emerald -> Golden Carrot" },
        { tier: "Expert", deals: "Emerald -> Glistering Melon" },
        { tier: "Master", deals: "Emerald -> Golden Apple" }
    ]}
];

let screenshotsData = [];
let replaysData = [];

// Switch Hub Apps
function switchHubApp(appId) {
    currentHubApp = appId;
    document.querySelectorAll('.fugo-sidebar .nav-item').forEach(btn => {
        if (btn.getAttribute('data-app') === appId) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });

    document.querySelectorAll('.fugo-content .hub-app-panel').forEach(panel => {
        panel.classList.add('hidden');
    });

    const activePanel = document.getElementById(`hub-app-${appId}`);
    if (activePanel) {
        activePanel.classList.remove('hidden');
    }

    if (appId === 'huds') {
        renderModsGrid();
    } else if (appId === 'profiles') {
        loadProfilesList();
    } else if (appId === 'recipes') {
        loadRecipesFromMinecraft();
    } else if (appId === 'gallery') {
        loadGalleryItems();
    }
}

// Refresh Dynamic Data
function refreshHubData() {
    // 1. Username and Status
    const userText = document.getElementById('hub-username-text');
    if (window.currentUiState && window.currentUiState.username) {
        if (userText) userText.textContent = window.currentUiState.username;
    } else {
        if (userText) userText.textContent = "Player";
    }

    const serverText = document.getElementById('hub-server-status-text');
    if (serverText) {
        serverText.textContent = currentUiState.inWorld ? "In-Game Server" : "Singleplayer / Lobby";
    }

    // 2. Render lists if not already populated
    renderRecipesList();
    loadGalleryItems();
}
// Global search bar handler
function handleHubSearch() {
    const searchVal = document.getElementById('hub-search-input').value.toLowerCase().trim();
    if (currentHubApp === 'recipes') {
        renderRecipesList(searchVal);
    } else if (currentHubApp === 'huds') {
        modsSearchQuery = searchVal;
        renderModsGrid();
    }
}

// Recipes App Logic
function loadRecipesFromMinecraft() {
    window.MinecraftBridge.send("recipes:get")
        .then(response => {
            if (response && response.recipes) {
                recipesData = response.recipes;
                renderRecipesList();
            }
        })
        .catch(err => {
            console.error("Failed to load recipes from Minecraft:", err);
        });
}

function filterRecipes(category) {
    activeRecipesCategory = category;
    document.querySelectorAll('.recipe-cat-btn').forEach(btn => {
        if (btn.id === `recipe-cat-${category}`) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
    renderRecipesList();
}

function renderRecipesList(searchQuery = '') {
    const container = document.getElementById('recipes-list-container');
    if (!container) return;

    container.innerHTML = '';
    const filtered = recipesData.filter(r => {
        const matchesCat = activeRecipesCategory === 'all' || r.category === activeRecipesCategory;
        const matchesSearch = !searchQuery || r.name.toLowerCase().includes(searchQuery);
        return matchesCat && matchesSearch;
    });

    filtered.forEach(r => {
        const item = document.createElement('div');
        item.className = `recipe-list-item ${selectedRecipe && selectedRecipe.name === r.name ? 'active' : ''}`;
        item.onclick = () => selectRecipeItem(r);
        item.innerHTML = `
            <span class="recipe-item-icon">${mcIcon(r.icon)}</span>
            <span class="recipe-item-name-lbl">${r.name}</span>
        `;
        container.appendChild(item);
    });
}

function selectRecipeItem(recipe) {
    selectedRecipe = recipe;
    renderRecipesList();

    const emptyView = document.getElementById('recipe-detail-empty');
    const contentView = document.getElementById('recipe-detail-content');
    if (emptyView) emptyView.classList.add('hidden');
    if (contentView) contentView.classList.remove('hidden');

    const nameLbl = document.getElementById('recipe-item-name');
    if (nameLbl) nameLbl.textContent = recipe.name;

    // Populate grid cells
    for (let i = 0; i < 9; i++) {
        const cell = document.getElementById(`craft-cell-${i}`);
        if (cell) {
            cell.innerHTML = mcIcon(recipe.grid[i]);
        }
    }

    const resultCell = document.getElementById('craft-result');
    if (resultCell) resultCell.innerHTML = mcIcon(recipe.result);

    const list = document.getElementById('recipe-ingredients-list');
    if (list) {
        list.innerHTML = '';
        recipe.ingredients.forEach(ing => {
            const li = document.createElement('li');
            li.textContent = ing;
            list.appendChild(li);
        });
    }
}

// Brewing App Logic
function renderBrewingList() {
    const container = document.getElementById('brewing-list-container');
    if (!container) return;

    container.innerHTML = '';
    brewingData.forEach(p => {
        const item = document.createElement('div');
        item.className = `brewing-list-item ${selectedPotion && selectedPotion.name === p.name ? 'active' : ''}`;
        item.onclick = () => selectPotionItem(p);
        item.innerHTML = `
            <span class="recipe-item-icon">${p.icon}</span>
            <span class="recipe-item-name-lbl">${p.name}</span>
        `;
        container.appendChild(item);
    });
}

function selectPotionItem(potion) {
    selectedPotion = potion;
    renderBrewingList();

    const emptyView = document.getElementById('brewing-detail-empty');
    const contentView = document.getElementById('brewing-detail-content');
    if (emptyView) emptyView.classList.add('hidden');
    if (contentView) contentView.classList.remove('hidden');

    const nameLbl = document.getElementById('brew-potion-name');
    if (nameLbl) nameLbl.textContent = potion.name;

    const modifier1 = document.getElementById('brew-node-modifier1');
    const modifier2 = document.getElementById('brew-node-modifier2');
    const resultNode = document.getElementById('brew-node-result');
    const effectDesc = document.getElementById('brew-potion-effect-desc');

    if (potion.name === 'Awkward Potion') {
        if (modifier1) modifier1.querySelector('.node-label').textContent = 'Nether Wart';
        if (modifier2) modifier2.style.display = 'none';
        if (resultNode) {
            resultNode.querySelector('.node-icon').textContent = '🧪';
            resultNode.querySelector('.node-label').textContent = 'Awkward Potion';
        }
    } else {
        if (modifier1) modifier1.querySelector('.node-label').textContent = 'Nether Wart';
        if (modifier2) {
            modifier2.style.display = 'flex';
            modifier2.querySelector('.node-label').textContent = potion.ingredient;
        }
        if (resultNode) {
            resultNode.querySelector('.node-icon').textContent = potion.icon;
            resultNode.querySelector('.node-label').textContent = potion.name;
        }
    }

    if (effectDesc) effectDesc.textContent = potion.effect;
}

// Enchantments App Logic
function renderEnchantmentsList(searchQuery = '') {
    const container = document.getElementById('enchant-list-container');
    if (!container) return;

    container.innerHTML = '';
    const filtered = enchantmentsData.filter(e => !searchQuery || e.name.toLowerCase().includes(searchQuery));

    filtered.forEach(e => {
        const item = document.createElement('div');
        item.className = `enchant-item ${selectedEnchantment && selectedEnchantment.name === e.name ? 'active' : ''}`;
        item.onclick = () => selectEnchantmentItem(e);
        item.textContent = e.name;
        container.appendChild(item);
    });
}

function selectEnchantmentItem(e) {
    selectedEnchantment = e;
    renderEnchantmentsList();

    const emptyView = document.getElementById('enchant-detail-empty');
    const contentView = document.getElementById('enchant-detail-content');
    if (emptyView) emptyView.classList.add('hidden');
    if (contentView) contentView.classList.remove('hidden');

    const nameLbl = document.getElementById('enchant-name');
    const maxLevel = document.getElementById('enchant-max-level');
    const target = document.getElementById('enchant-target');
    const desc = document.getElementById('enchant-desc');
    const conflicts = document.getElementById('enchant-conflicts');

    if (nameLbl) nameLbl.textContent = e.name;
    if (maxLevel) maxLevel.textContent = e.maxLevel;
    if (target) target.textContent = e.target;
    if (desc) desc.textContent = e.desc;
    if (conflicts) conflicts.textContent = e.conflicts;
}

// Villagers App Logic
function renderVillagersList(searchQuery = '') {
    const container = document.getElementById('villager-list-container');
    if (!container) return;

    container.innerHTML = '';
    const filtered = villagersData.filter(v => !searchQuery || v.name.toLowerCase().includes(searchQuery));

    filtered.forEach(v => {
        const item = document.createElement('div');
        item.className = `villager-item ${selectedVillager && selectedVillager.name === v.name ? 'active' : ''}`;
        item.onclick = () => selectVillagerItem(v);
        item.innerHTML = `
            <span class="recipe-item-icon">${v.icon}</span>
            <span class="recipe-item-name-lbl">${v.name}</span>
        `;
        container.appendChild(item);
    });
}

function selectVillagerItem(v) {
    selectedVillager = v;
    renderVillagersList();

    const emptyView = document.getElementById('villager-detail-empty');
    const contentView = document.getElementById('villager-detail-content');
    if (emptyView) emptyView.classList.add('hidden');
    if (contentView) contentView.classList.remove('hidden');

    const nameLbl = document.getElementById('villager-prof-name');
    const workstation = document.getElementById('villager-workstation');
    const tierList = document.getElementById('trade-tier-list');

    if (nameLbl) nameLbl.textContent = v.name;
    if (workstation) workstation.textContent = v.workstation;

    if (tierList) {
        tierList.innerHTML = '';
        v.trades.forEach(t => {
            const row = document.createElement('div');
            row.className = 'trade-tier';
            row.innerHTML = `
                <div class="trade-tier-title">${t.tier}</div>
                <div class="trade-deals">${t.deals}</div>
            `;
            tierList.appendChild(row);
        });
    }
}

// Calculator App Logic
let calcHistoryStr = '';
let calcInputStr = '';

function calcInput(val) {
    const display = document.getElementById('calc-display-input');
    if (!display) return;

    if (calcInputStr === '0' && val !== '.') {
        calcInputStr = '';
    }

    calcInputStr += val;
    display.textContent = calcInputStr;
}

function calcAction(action) {
    const display = document.getElementById('calc-display-input');
    const history = document.getElementById('calc-history');
    if (!display) return;

    if (action === 'C') {
        calcInputStr = '0';
        calcHistoryStr = '';
        display.textContent = '0';
        if (history) history.textContent = '';
    } else if (action === 'backspace') {
        if (calcInputStr.length > 0) {
            calcInputStr = calcInputStr.slice(0, -1);
        }
        if (calcInputStr === '') {
            calcInputStr = '0';
        }
        display.textContent = calcInputStr;
    } else {
        if (calcInputStr === '0' && (action === '(' || action === ')')) {
            calcInputStr = '';
        }
        calcInputStr += action;
        display.textContent = calcInputStr;
    }
}

function calcEvaluate() {
    const display = document.getElementById('calc-display-input');
    const history = document.getElementById('calc-history');
    if (!display || !calcInputStr) return;

    try {
        const sanitized = calcInputStr.replace(/[^0-9+\-*/().]/g, '');
        const evalResult = new Function(`return ${sanitized}`)();
        
        if (history) history.textContent = calcInputStr + ' =';
        calcInputStr = String(evalResult);
        display.textContent = calcInputStr;
    } catch (e) {
        display.textContent = 'Error';
        calcInputStr = '';
    }
}

// Gallery App Logic
let currentGalleryTab = 'screenshots';

function switchGalleryTab(tab) {
    currentGalleryTab = tab;
    
    // Toggle active classes on tab buttons
    const sBtn = document.getElementById('gallery-tab-screenshots-btn');
    const rBtn = document.getElementById('gallery-tab-replays-btn');
    if (sBtn) sBtn.classList.toggle('active', tab === 'screenshots');
    if (rBtn) rBtn.classList.toggle('active', tab === 'replays');
    
    // Toggle section visibility
    const sSec = document.getElementById('gallery-sec-screenshots');
    const rSec = document.getElementById('gallery-sec-replays');
    if (sSec) sSec.classList.toggle('hidden', tab !== 'screenshots');
    if (rSec) rSec.classList.toggle('hidden', tab !== 'replays');
    
    renderFullGallery();
}

function openGalleryFolder() {
    window.MinecraftBridge.send('gallery:open_folder', { type: currentGalleryTab === 'screenshots' ? 'screenshot' : 'replay' });
}

function loadGalleryItems() {
    window.MinecraftBridge.send('gallery:get_items')
        .then(resp => {
            if (resp && resp.status === 'success') {
                screenshotsData = resp.screenshots || [];
                replaysData = resp.replays || [];
            }
            renderFullGallery();
            renderHomeGallery();
        })
        .catch(err => {
            console.error("Failed to load gallery items", err);
            renderFullGallery();
            renderHomeGallery();
        });
}

function deleteGalleryItem(type, name) {
    if (confirm(`Are you sure you want to delete this ${type}?`)) {
        window.MinecraftBridge.send('gallery:delete_file', { type: type, name: name })
            .then(() => {
                loadGalleryItems();
            });
    }
}

function openGalleryFile(type, name) {
    window.MinecraftBridge.send('gallery:open_file', { type: type, name: name });
}

function showLightbox(url, name) {
    const lightbox = document.getElementById('gallery-lightbox');
    const img = document.getElementById('lightbox-img');
    const meta = document.getElementById('lightbox-meta');
    if (lightbox && img && meta) {
        img.src = url;
        meta.textContent = name;
        lightbox.classList.remove('hidden');
    }
}

function closeLightbox() {
    const lightbox = document.getElementById('gallery-lightbox');
    if (lightbox) {
        lightbox.classList.add('hidden');
    }
}

function renderHomeGallery() {
    const container = document.getElementById('home-gallery-container');
    if (!container) return;

    container.innerHTML = '';
    if (screenshotsData.length === 0) {
        container.innerHTML = '<div style="grid-column: span 4; text-align: center; opacity: 0.5; padding: 2rem;">No screenshots yet</div>';
        return;
    }
    
    screenshotsData.slice(0, 4).forEach(s => {
        const card = document.createElement('div');
        card.className = 'gallery-preview-card';
        card.onclick = () => {
            switchHubApp('gallery');
            switchGalleryTab('screenshots');
        };
        card.innerHTML = `
            <div class="gallery-preview-img" style="background-image: url('${s.url}');"></div>
            <div class="gallery-preview-meta">
                <div style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${s.name}</div>
                <div style="font-size:0.6rem;opacity:0.7;">${s.date}</div>
            </div>
        `;
        container.appendChild(card);
    });
}

function renderFullGallery() {
    // 1. Populate Screenshots
    const screenshotsContainer = document.getElementById('gallery-grid-screenshots');
    const screenshotsEmpty = document.getElementById('gallery-empty-screenshots');
    
    if (screenshotsContainer) {
        screenshotsContainer.innerHTML = '';
        if (screenshotsData.length === 0) {
            if (screenshotsEmpty) screenshotsEmpty.classList.remove('hidden');
        } else {
            if (screenshotsEmpty) screenshotsEmpty.classList.add('hidden');
            screenshotsData.forEach(s => {
                const card = document.createElement('div');
                card.className = 'gallery-item-card';
                card.onclick = () => showLightbox(s.url, s.name);
                card.innerHTML = `
                    <div class="gallery-item-image" style="background-image: url('${s.url}');"></div>
                    <div class="gallery-item-info">
                        <span class="gallery-item-name" title="${s.name}">${s.name}</span>
                        <div style="display: flex; gap: 0.5rem; align-items: center;">
                            <span style="font-size: 0.65rem; opacity: 0.6;">${s.date}</span>
                            <button class="gallery-item-btn" onclick="event.stopPropagation(); deleteGalleryItem('screenshot', '${s.name}')">🗑️</button>
                        </div>
                    </div>
                `;
                screenshotsContainer.appendChild(card);
            });
        }
    }

    // 2. Populate Replays
    const replaysContainer = document.getElementById('gallery-list-replays');
    const replaysEmpty = document.getElementById('gallery-empty-replays');
    
    if (replaysContainer) {
        replaysContainer.innerHTML = '';
        if (replaysData.length === 0) {
            if (replaysEmpty) replaysEmpty.classList.remove('hidden');
        } else {
            if (replaysEmpty) replaysEmpty.classList.add('hidden');
            replaysData.forEach(r => {
                const item = document.createElement('div');
                item.className = 'gallery-list-item';
                item.onclick = () => openGalleryFile('replay', r.name);
                item.innerHTML = `
                    <div class="gallery-list-item-info">
                        <span class="gallery-list-item-icon">🎬</span>
                        <div class="gallery-list-item-meta">
                            <span class="gallery-list-item-name">${r.name}</span>
                            <span class="gallery-list-item-date">Modified: ${r.date} &bull; Size: ${r.size}</span>
                        </div>
                    </div>
                    <div class="gallery-list-item-actions">
                        <button class="gallery-action-btn" style="padding: 0.3rem 0.6rem; font-size: 0.75rem;" onclick="event.stopPropagation(); openGalleryFile('replay', '${r.name}')">Open File 📂</button>
                        <button class="gallery-item-btn" onclick="event.stopPropagation(); deleteGalleryItem('replay', '${r.name}')">🗑️</button>
                    </div>
                `;
                replaysContainer.appendChild(item);
            });
        }
    }
}

// Ensure init loads properly on startup
window.applyThemeData = function(data) {
    if (!data) return;
    for (const [k, v] of Object.entries(data)) {
        if (k.startsWith('--')) {
            document.documentElement.style.setProperty(k, v);
        }
    }
    // Update fonts
    if (data['--font-body']) {
        document.documentElement.style.setProperty('--hub-font', data['--font-body']);
        document.body.style.fontFamily = data['--font-body'];
    }
    if (data['--font-heading']) {
        document.querySelectorAll('.widget-header, .speed-text, .target-title, .banner-title, .sidebar-title, .panel-section-title, .mountain-logo, .header-logo').forEach(el => {
            el.style.fontFamily = data['--font-heading'];
        });
    }
};

window.addEventListener('DOMContentLoaded', () => {
    // Set initial active state
    switchHubApp('recipes');
    refreshHubData();
    initCustomization();

    // Query Java for saved custom theme
    if (window.MinecraftBridge) {
        window.MinecraftBridge.send('theme:get')
            .then(data => {
                if (data && typeof data === 'object') {
                    window.applyThemeData(data);
                } else if (data && typeof data === 'string') {
                    try {
                        const parsed = JSON.parse(data);
                        window.applyThemeData(parsed);
                    } catch(e) {}
                }
            })
            .catch(err => console.log('[Fugo Theme] No custom theme applied:', err));
    }
});

// ==========================================
// VISUAL CUSTOMIZATION & ACCENT COLOR
// ==========================================
const CUSTOMIZATION_KEY = 'fugo_customization_settings';
let customizationSettings = {
    'block-hitting': '1.7',
    'damage-tilt': 'classic',
    'zoom': 'cinematic',
    'hud-border': 'glass',
    'accent-color': '#ff7a00'
};

function initCustomization() {
    try {
        const saved = localStorage.getItem(CUSTOMIZATION_KEY);
        if (saved) {
            customizationSettings = { ...customizationSettings, ...JSON.parse(saved) };
        }
    } catch (e) {
        console.error("Failed to load customization settings:", e);
    }
    
    // Set UI elements active classes
    applyCustomizationButtons('block-hitting', customizationSettings['block-hitting']);
    applyCustomizationButtons('damage-tilt', customizationSettings['damage-tilt']);
    applyCustomizationButtons('zoom', customizationSettings['zoom']);
    applyCustomizationButtons('hud-border', customizationSettings['hud-border']);
    
    updatePaletteSelection('ui-accent-color-palette', 'ui-accent-hex-input', customizationSettings['accent-color'] || '#ff7a00');
    
    applyCustomizationEffect();
}

function applyCustomizationButtons(type, value) {
    let containerId = '';
    if (type === 'block-hitting') containerId = 'anim-block-hitting';
    else if (type === 'damage-tilt') containerId = 'anim-damage-tilt';
    else if (type === 'zoom') containerId = 'anim-zoom';
    else if (type === 'hud-border') containerId = 'custom-hud-border';
    
    const container = document.getElementById(containerId);
    if (container) {
        container.querySelectorAll('.style-option').forEach(btn => {
            if (btn.getAttribute('data-value') === value) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }
}

function setCustomizationSetting(type, value) {
    customizationSettings[type] = value;
    localStorage.setItem(CUSTOMIZATION_KEY, JSON.stringify(customizationSettings));
    applyCustomizationButtons(type, value);
    applyCustomizationEffect();
    
    // Notify Minecraft Client of visual changes
    if (window.MinecraftBridge) {
        window.MinecraftBridge.send('customization:change', { type: type, value: value })
            .then(res => console.log(`[Customization] Set ${type} to ${value}`, res))
            .catch(err => console.warn(`[Customization] Bridge change failed:`, err));
    }
}

function setHitboxVisibility(mode) {
    customizationSettings['hitbox-visibility'] = mode;
    localStorage.setItem(CUSTOMIZATION_KEY, JSON.stringify(customizationSettings));

    const buttons = document.querySelectorAll('#hitbox-visibility-selector .style-option');
    buttons.forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.querySelector(`#hitbox-visibility-selector .style-option[data-value="${mode}"]`);
    if (activeBtn) activeBtn.classList.add('active');

    if (window.MinecraftBridge) {
        window.MinecraftBridge.send('hitbox:visibility', { mode: mode })
            .then(res => console.log(`[Hitbox] Visibility set to ${mode}`, res))
            .catch(err => console.warn(`[Hitbox] Bridge update failed:`, err));
    }
}

function updateGlobalAccentColor(color) {
    customizationSettings['accent-color'] = color;
    localStorage.setItem(CUSTOMIZATION_KEY, JSON.stringify(customizationSettings));
    applyCustomizationEffect();

    // Notify Minecraft Client of accent color change
    if (window.MinecraftBridge) {
        window.MinecraftBridge.send('customization:accent_color', { color: color })
            .then(res => console.log(`[Customization] Accent color updated: ${color}`, res))
            .catch(err => console.warn(`[Customization] Accent color bridge update failed:`, err));
    }
}

function applyCustomizationEffect() {
    const color = customizationSettings['accent-color'] || '#ff7a00';
    document.documentElement.style.setProperty('--hub-accent', color);
    
    // Generate hover shade (slightly lighter)
    let hoverColor = color;
    try {
        let R = parseInt(color.substring(1, 3), 16);
        let G = parseInt(color.substring(3, 5), 16);
        let B = parseInt(color.substring(5, 7), 16);
        R = Math.min(255, Math.max(0, R + 20));
        G = Math.min(255, Math.max(0, G + 20));
        B = Math.min(255, Math.max(0, B + 20));
        hoverColor = "#" + R.toString(16).padStart(2, '0') + G.toString(16).padStart(2, '0') + B.toString(16).padStart(2, '0');
    } catch(e) {}
    document.documentElement.style.setProperty('--hub-accent-hover', hoverColor);
    
    // Generate glow shade (10% opacity)
    const glowColor = color + '1a';
    document.documentElement.style.setProperty('--hub-accent-glow', glowColor);

    // Generate focus border shade (50% opacity)
    document.documentElement.style.setProperty('--hub-border-focus', color + '80');
    
    // Apply HUD borders to in-game widgets dynamically based on the selected setting
    const borderSetting = customizationSettings['hud-border'];
    const widgets = document.querySelectorAll('.hud-widget');
    
    widgets.forEach(widget => {
        if (borderSetting === 'glass') {
            widget.style.border = '1px solid rgba(255, 255, 255, 0.05)';
            widget.style.boxShadow = '0 8px 32px 0 rgba(0, 0, 0, 0.37)';
            widget.style.background = 'rgba(10, 11, 16, 0.5)';
            widget.style.backdropFilter = 'blur(4px)';
        } else if (borderSetting === 'neon') {
            widget.style.border = `1px solid ${color}`;
            widget.style.boxShadow = `0 0 10px ${color}`;
            widget.style.background = 'rgba(0, 0, 0, 0.7)';
            widget.style.backdropFilter = 'none';
        } else if (borderSetting === 'thin') {
            widget.style.border = '1px solid rgba(255, 255, 255, 0.15)';
            widget.style.boxShadow = 'none';
            widget.style.background = 'rgba(0, 0, 0, 0.8)';
            widget.style.backdropFilter = 'none';
        } else {
            widget.style.border = 'none';
            widget.style.boxShadow = 'none';
            widget.style.background = 'rgba(0, 0, 0, 0.4)';
            widget.style.backdropFilter = 'none';
        }
    });
}

function updatePaletteSelection(paletteId, hexInputId, color) {
    const palette = document.getElementById(paletteId);
    if (palette) {
        palette.querySelectorAll('.color-swatch').forEach(swatch => {
            if (swatch.getAttribute('data-value').toLowerCase() === color.toLowerCase()) {
                swatch.classList.add('active');
            } else {
                swatch.classList.remove('active');
            }
        });
    }
    const hexInput = document.getElementById(hexInputId);
    if (hexInput) {
        hexInput.value = color.startsWith('#') ? color.slice(1).toUpperCase() : color.toUpperCase();
    }
}

function selectCrosshairColor(color) {
    config.crosshair.color = color;
    updatePaletteSelection('crosshair-color-palette', 'crosshair-hex-input', color);
    saveConfig();
}

function updateCrosshairHex(val) {
    const cleanHex = val.replace(/[^0-9A-Fa-f]/g, '');
    if (cleanHex.length === 6) {
        const color = '#' + cleanHex;
        config.crosshair.color = color;
        const palette = document.getElementById('crosshair-color-palette');
        if (palette) {
            palette.querySelectorAll('.color-swatch').forEach(swatch => {
                if (swatch.getAttribute('data-value').toLowerCase() === color.toLowerCase()) {
                    swatch.classList.add('active');
                } else {
                    swatch.classList.remove('active');
                }
            });
        }
        saveConfig();
    }
}

function selectGlobalAccentColor(color) {
    updateGlobalAccentColor(color);
    updatePaletteSelection('ui-accent-color-palette', 'ui-accent-hex-input', color);
}

function updateGlobalAccentHex(val) {
    const cleanHex = val.replace(/[^0-9A-Fa-f]/g, '');
    if (cleanHex.length === 6) {
        const color = '#' + cleanHex;
        updateGlobalAccentColor(color);
        const palette = document.getElementById('ui-accent-color-palette');
        if (palette) {
            palette.querySelectorAll('.color-swatch').forEach(swatch => {
                if (swatch.getAttribute('data-value').toLowerCase() === color.toLowerCase()) {
                    swatch.classList.add('active');
                } else {
                    swatch.classList.remove('active');
                }
            });
        }
    }
}

function syncAutoclickerSettings() {
    if (window.MinecraftBridge && window.MinecraftBridge.send) {
        window.MinecraftBridge.send('update_autoclicker', {
            enabled: config.toggles['toggle-autoclicker'] !== false,
            cps: parseInt(config.autoclickerCps || 12),
            button: config.autoclickerButton || 'left',
            keybind: config.autoclickerKeybind || 'G'
        }).catch(err => console.error("[Fugo] Failed to sync autoclicker settings:", err));
    }
}

function updateAutoclickerCps(val) {
    config.autoclickerCps = parseInt(val);
    const display = document.getElementById('val-autoclicker-cps');
    if (display) display.innerText = val + " CPS";
    saveConfig();
    syncAutoclickerSettings();
}

function setAutoclickerButton(button) {
    config.autoclickerButton = button;
    const container = document.getElementById('opt-autoclicker-button');
    if (container) {
        container.querySelectorAll('.style-option').forEach(btn => {
            if (btn.innerText.toLowerCase().includes(button)) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }
    saveConfig();
    syncAutoclickerSettings();
}

let isRebindingAutoclicker = false;
let autoclickerRebindListener = null;

function startRebindingAutoclicker(event) {
    event.stopPropagation();
    if (isRebindingAutoclicker) return;

    isRebindingAutoclicker = true;
    const badge = document.getElementById('autoclicker-keybind-btn');
    if (badge) {
        badge.innerText = '...';
        badge.classList.add('rebinding');
    }

    autoclickerRebindListener = function(e) {
        e.preventDefault();
        e.stopPropagation();

        let key = e.key.toUpperCase();
        
        // Handle common keys nicely
        if (e.code === 'Space') key = 'SPACE';
        else if (e.code === 'ControlLeft' || e.code === 'ControlRight') key = 'LCONTROL';
        else if (e.code === 'ShiftLeft' || e.code === 'ShiftRight') key = 'LSHIFT';
        else if (e.code === 'AltLeft' || e.code === 'AltRight') key = 'LALT';
        else if (e.code === 'Escape') key = 'ESCAPE';
        else if (e.code === 'Enter') key = 'ENTER';

        // Filter to valid single letters/digits or standard special codes
        const isValid = /^[A-Z0-9]$/.test(key) || ['SPACE', 'LCONTROL', 'LSHIFT', 'LALT', 'ESCAPE', 'ENTER'].includes(key);

        if (isValid) {
            config.autoclickerKeybind = key;
            if (badge) {
                badge.innerText = key;
                badge.classList.remove('rebinding');
            }
            isRebindingAutoclicker = false;
            window.removeEventListener('keydown', autoclickerRebindListener, true);
            saveConfig();
            syncAutoclickerSettings();
        }
    };

    window.addEventListener('keydown', autoclickerRebindListener, true);
}

let quickJoinServers = [];

function loadQuickJoinServers() {
    const saved = localStorage.getItem('quickjoin_servers');
    if (saved) {
        quickJoinServers = JSON.parse(saved);
    } else {
        // Defaults
        quickJoinServers = [
            { name: "Hypixel", ip: "hypixel.net" },
            { name: "Minemen", ip: "minemen.club" }
        ];
        saveQuickJoinServers();
    }
    renderQuickJoinList();
}

function saveQuickJoinServers() {
    localStorage.setItem('quickjoin_servers', JSON.stringify(quickJoinServers));
}

function renderQuickJoinList() {
    const list = document.getElementById('quickjoin-list');
    if (!list) return;
    
    // Clear everything except the Add button
    const addBtn = list.querySelector('.btn-quickjoin-add');
    list.innerHTML = '';
    
    quickJoinServers.forEach((srv, index) => {
        const item = document.createElement('div');
        item.className = 'quickjoin-item';
        item.setAttribute('data-tooltip', `${srv.name} (${srv.ip}) - Right-click to remove`);
        
        // Generate a 1-2 letter abbreviation
        const words = srv.name.trim().split(/\s+/);
        let abbr = '';
        if (words.length > 1 && words[0] && words[1]) {
            abbr = (words[0][0] + words[1][0]).toUpperCase();
        } else if (srv.name.trim()) {
            abbr = srv.name.trim().substring(0, 2).toUpperCase();
        } else {
            abbr = '??';
        }
        item.innerText = abbr;
        
        // Left click to connect
        item.addEventListener('click', () => {
            quickJoinServer(srv.ip);
        });
        
        // Right click to remove
        item.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            removeQuickJoinServer(index);
        });
        
        list.appendChild(item);
    });
    
    if (addBtn) {
        list.appendChild(addBtn);
    }
}

function quickJoinServer(ip) {
    if (window.MinecraftBridge && window.MinecraftBridge.send) {
        window.MinecraftBridge.send('quick_join', { ip: ip })
            .catch(err => console.error("[Fugo] Failed to quick join:", err));
    } else {
        console.log("[Mock] Quick joining server: " + ip);
    }
}

function removeQuickJoinServer(index) {
    quickJoinServers.splice(index, 1);
    saveQuickJoinServers();
    renderQuickJoinList();
}

function showAddServerModal() {
    const modal = document.getElementById('add-server-modal');
    if (modal) {
        modal.classList.remove('hidden');
        document.getElementById('add-server-name').focus();
    }
}

function hideAddServerModal() {
    const modal = document.getElementById('add-server-modal');
    if (modal) {
        modal.classList.add('hidden');
        document.getElementById('add-server-name').value = '';
        document.getElementById('add-server-ip').value = '';
    }
}

function confirmAddServer() {
    const nameInput = document.getElementById('add-server-name');
    const ipInput = document.getElementById('add-server-ip');
    
    if (!nameInput || !ipInput) return;
    
    const name = nameInput.value.trim();
    const ip = ipInput.value.trim();
    
    if (name && ip) {
        quickJoinServers.push({ name, ip });
        saveQuickJoinServers();
        renderQuickJoinList();
        hideAddServerModal();
    }
}

// ─── TIME CONTROL ──────────────────────────────────────────────────────────

function setGameTime(ticks) {
    const hours = Math.floor(((ticks / 1000) + 6) % 24);
    const mins = Math.floor(((ticks % 1000) / 1000) * 60);
    const label = `${String(hours).padStart(2,'0')}:${String(mins).padStart(2,'0')}`;

    // Update slider + label in overlay
    const slider = document.getElementById('overlay-time-slider');
    const sliderLabel = document.getElementById('overlay-time-slider-label');
    if (slider) slider.value = ticks;
    if (sliderLabel) sliderLabel.textContent = label;

    // Send to Java
    window.MinecraftBridge.send('set_time', { ticks })
        .catch(() => {});

    // Show animated toast
    showAnimToast(`⏰ Time set to ${label}`, 'time');
}

function onTimeSliderChange(value) {
    const ticks = parseInt(value);
    const hours = Math.floor(((ticks / 1000) + 6) % 24);
    const mins = Math.floor(((ticks % 1000) / 1000) * 60);
    const label = `${String(hours).padStart(2,'0')}:${String(mins).padStart(2,'0')}`;
    const sliderLabel = document.getElementById('overlay-time-slider-label');
    if (sliderLabel) sliderLabel.textContent = label;

    // Debounce: only send after 200ms of no movement
    clearTimeout(window._timeSliderDebounce);
    window._timeSliderDebounce = setTimeout(() => {
        setGameTime(ticks);
    }, 200);
}

// ─── WEATHER CONTROL ───────────────────────────────────────────────────────

function setGameWeather(type) {
    // Update active button states with animation
    ['clear', 'rain', 'thunder'].forEach(w => {
        const btn = document.getElementById(`overlay-weather-btn-${w}`);
        if (btn) {
            btn.classList.toggle('active', w === type);
        }
    });

    window.MinecraftBridge.send('set_weather', { type })
        .catch(() => {});

    const labels = { clear: '☀️ Clear', rain: '🌧 Rain', thunder: '⛈ Thunder' };
    showAnimToast(`${labels[type] || type} weather applied`, 'weather');
}

// ─── ANIMATION TOAST SYSTEM ───────────────────────────────────────────────

function showAnimToast(message, type = 'info', duration = 2500) {
    const container = document.getElementById('anim-toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `anim-toast anim-toast-${type}`;
    toast.innerHTML = `<span class="toast-msg">${message}</span>`;
    container.appendChild(toast);

    // Trigger entrance animation
    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            toast.classList.add('visible');
        });
    });

    setTimeout(() => {
        toast.classList.remove('visible');
        toast.classList.add('hiding');
        setTimeout(() => toast.remove(), 400);
    }, duration);
}

// Hook into game events to show contextual toast animations
window.MinecraftBridge.on('hud:update_stats', (data) => {
    // Weather change detection
    if (data.utility) {
        const weather = data.utility.weather;
        if (weather && weather !== window._lastWeather) {
            if (window._lastWeather !== undefined) {
                const icons = { Clear: '☀️', Rain: '🌧', Thunder: '⛈' };
                showAnimToast(`${icons[weather] || '🌤'} Weather changed to ${weather}`, 'weather');
                // Sync weather buttons
                ['clear', 'rain', 'thunder'].forEach(w => {
                    const btn = document.getElementById(`weather-btn-${w}`);
                    const match = (weather === 'Thunder' && w === 'thunder') ||
                                  (weather === 'Rain' && w === 'rain') ||
                                  (weather === 'Clear' && w === 'clear');
                    if (btn) btn.classList.toggle('active', match);
                });
            }
            window._lastWeather = weather;
        }

        // Sync time slider from server
        const timeStr = data.utility.time;
        if (timeStr) {
            const [hh, mm] = timeStr.split(':').map(Number);
            const ticks = (((hh - 6 + 24) % 24) * 1000) + Math.round(mm * 1000 / 60);
            const slider = document.getElementById('overlay-time-slider');
            const sliderLabel = document.getElementById('overlay-time-slider-label');
            if (slider && document.activeElement !== slider) {
                slider.value = ticks;
            }
            if (sliderLabel && document.activeElement !== slider) {
                sliderLabel.textContent = timeStr;
            }
        }
    }

    // Health drop animation
    if (data.health !== undefined && window._lastHealth !== undefined) {
        if (data.health < window._lastHealth) {
            showAnimToast(`❤️ ${data.health.toFixed(1)} HP  (−${(window._lastHealth - data.health).toFixed(1)})`, 'damage', 1800);
        }
    }
    if (data.health !== undefined) window._lastHealth = data.health;
});

// ─── TIME & WEATHER CONTROL OVERLAYS ─────────────────────────────────────

function openTimeControlOverlay() {
    // Close hub overlay first
    closeOverlayScreen();
    const overlay = document.getElementById('time-control-overlay');
    if (overlay) overlay.classList.remove('hidden');
}

function closeTimeControlOverlay() {
    const overlay = document.getElementById('time-control-overlay');
    if (overlay) overlay.classList.add('hidden');
}

function openWeatherControlOverlay() {
    closeOverlayScreen();
    const overlay = document.getElementById('weather-control-overlay');
    if (overlay) overlay.classList.remove('hidden');
}

function closeWeatherControlOverlay() {
    const overlay = document.getElementById('weather-control-overlay');
    if (overlay) overlay.classList.add('hidden');
}

// ─── Main Menu Drag & Drop Layout Editor ───
function initMenuDragAndDrop() {
    const selectors = {
        '--menu-logo': '.top-left-logo',
        '--menu-profiles': '.title-profiles-btn',
        '--menu-buttons': '.mid-left-panel',
        '--menu-quickjoin': '.bottom-left-quickjoin',
        '--menu-btn-singleplayer': '.btn-singleplayer',
        '--menu-btn-multiplayer': '.btn-multiplayer',
        '--menu-btn-settings': '.btn-settings',
        '--menu-btn-quit': '.btn-quit'
    };

    Object.entries(selectors).forEach(([varPrefix, selector]) => {
        const el = document.querySelector(selector);
        if (!el) return;

        // Prevent button actions when layout editor is enabled
        el.addEventListener('click', (e) => {
            if (window.parent && window.parent.isDraggingEnabled) {
                e.preventDefault();
                e.stopPropagation();
            }
        }, true);

        // Visual indicator on hover
        el.addEventListener('mouseenter', () => {
            if (window.parent && window.parent.isDraggingEnabled) {
                el.style.cursor = 'grab';
            } else {
                el.style.cursor = '';
            }
        });

        let isDragging = false;
        let startX, startY;
        let origLeft, origTop, origRight, origBottom;
        let origLeftOffset = 0, origTopOffset = 0;

        el.addEventListener('mousedown', (e) => {
            // Check if dragging is enabled in the parent context
            if (!window.parent || !window.parent.isDraggingEnabled) return;
            
            isDragging = true;
            el.style.cursor = 'grabbing';
            e.preventDefault();
            e.stopPropagation(); // prevent bubbling to parent container

            const rect = el.getBoundingClientRect();
            const parentRect = document.documentElement.getBoundingClientRect();

            startX = e.clientX;
            startY = e.clientY;

            origLeft = rect.left;
            origTop = rect.top;
            origRight = parentRect.width - rect.right;
            origBottom = parentRect.height - rect.bottom;

            if (varPrefix.startsWith('--menu-btn-')) {
                origLeftOffset = parseInt(getComputedStyle(el).getPropertyValue(varPrefix + '-left')) || 0;
                origTopOffset = parseInt(getComputedStyle(el).getPropertyValue(varPrefix + '-top')) || 0;
            }

            const onMouseMove = (moveEvent) => {
                if (!isDragging) return;
                const dx = moveEvent.clientX - startX;
                const dy = moveEvent.clientY - startY;

                if (varPrefix.startsWith('--menu-btn-')) {
                    const newTop = origTopOffset + dy;
                    const newLeft = origLeftOffset + dx;
                    document.documentElement.style.setProperty(varPrefix + '-top', newTop + 'px');
                    document.documentElement.style.setProperty(varPrefix + '-left', newLeft + 'px');
                    if (window.parent.updateVar) {
                        window.parent.updateVar(varPrefix + '-top', newTop + 'px');
                        window.parent.updateVar(varPrefix + '-left', newLeft + 'px');
                    }
                } else if (varPrefix === '--menu-profiles') {
                    const newTop = Math.max(0, origTop + dy);
                    const newRight = Math.max(0, origRight - dx);
                    document.documentElement.style.setProperty('--menu-profiles-top', newTop + 'px');
                    document.documentElement.style.setProperty('--menu-profiles-right', newRight + 'px');
                    if (window.parent.updateVar) {
                        window.parent.updateVar('--menu-profiles-top', newTop + 'px');
                        window.parent.updateVar('--menu-profiles-right', newRight + 'px');
                    }
                } else if (varPrefix === '--menu-quickjoin') {
                    const newBottom = Math.max(0, origBottom - dy);
                    const newLeft = Math.max(0, origLeft + dx);
                    document.documentElement.style.setProperty('--menu-quickjoin-bottom', newBottom + 'px');
                    document.documentElement.style.setProperty('--menu-quickjoin-left', newLeft + 'px');
                    if (window.parent.updateVar) {
                        window.parent.updateVar('--menu-quickjoin-bottom', newBottom + 'px');
                        window.parent.updateVar('--menu-quickjoin-left', newLeft + 'px');
                    }
                } else if (varPrefix === '--menu-buttons') {
                    const newTop = Math.max(0, origTop + dy + rect.height/2);
                    const newLeft = Math.max(0, origLeft + dx);
                    document.documentElement.style.setProperty('--menu-buttons-top', newTop + 'px');
                    document.documentElement.style.setProperty('--menu-buttons-left', newLeft + 'px');
                    document.documentElement.style.setProperty('--menu-buttons-transform', 'none');
                    if (window.parent.updateVar) {
                        window.parent.updateVar('--menu-buttons-top', newTop + 'px');
                        window.parent.updateVar('--menu-buttons-left', newLeft + 'px');
                        window.parent.updateVar('--menu-buttons-transform', 'none');
                    }
                } else {
                    const newTop = Math.max(0, origTop + dy);
                    const newLeft = Math.max(0, origLeft + dx);
                    document.documentElement.style.setProperty('--menu-logo-top', newTop + 'px');
                    document.documentElement.style.setProperty('--menu-logo-left', newLeft + 'px');
                    if (window.parent.updateVar) {
                        window.parent.updateVar('--menu-logo-top', newTop + 'px');
                        window.parent.updateVar('--menu-logo-left', newLeft + 'px');
                    }
                }
            };

            const onMouseUp = () => {
                isDragging = false;
                el.style.cursor = (window.parent && window.parent.isDraggingEnabled) ? 'grab' : '';
                document.removeEventListener('mousemove', onMouseMove);
                document.removeEventListener('mouseup', onMouseUp);
            };

            document.addEventListener('mousemove', onMouseMove);
            document.addEventListener('mouseup', onMouseUp);
        });
    });
}

if (window.location.pathname.includes('titlescreen.html')) {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            setTimeout(initMenuDragAndDrop, 100);
        });
    } else {
        setTimeout(initMenuDragAndDrop, 100);
    }
}
