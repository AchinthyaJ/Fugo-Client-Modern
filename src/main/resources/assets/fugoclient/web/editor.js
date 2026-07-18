// ─── Fugo Theme Editor Logic ───

const frame = document.getElementById('preview-frame');

// Wait for iframe to load before applying styles
if (frame) {
    frame.addEventListener('load', () => {
        try {
            console.log('Preview frame loaded:', frame.contentDocument?.title);
        } catch(e) {
            console.log('Preview frame loaded (restricted access)');
        }
    });
}

// ─── Tab Switching ───
function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
    document.querySelector(`.tab-btn[data-tab="${tab}"]`).classList.add('active');
    document.getElementById(`panel-${tab}`).classList.add('active');
}

// ─── Preview Switching ───
function switchPreview(btn, view) {
    document.querySelectorAll('.preview-tab').forEach(b => b.classList.remove('active'));
    if (btn) btn.classList.add('active');
    const urls = { hub: 'hud.html', hud: 'hud.html', title: 'titlescreen.html' };
    frame.src = urls[view] || 'hud.html';
    // After load, if hub view, open the settings panel
    frame.onload = () => {
        if (view === 'hub') {
            try {
                const doc = frame.contentDocument;
                const panel = doc.getElementById('overlay-settings-panel');
                if (panel) panel.classList.remove('hidden');
                const overlay = doc.getElementById('overlay-container');
                if (overlay) overlay.classList.add('backdrop-active');
            } catch(e) {}
        }
        reapplyAllVars();
    };
}

// ─── CSS Variable Injection & Debounced Save ───
let currentVars = {};
let saveTimeout = null;

function getFrameDoc() {
    try { return frame.contentDocument || frame.contentWindow.document; } 
    catch(e) { return null; }
}

function pushThemeToServer() {
    clearTimeout(saveTimeout);
    saveTimeout = setTimeout(() => {
        fetch('/api/apply-theme', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(currentVars)
        }).catch(err => console.error("Error saving theme to server:", err));
    }, 150);
}

function updateVar(varName, value) {
    currentVars[varName] = value;
    const doc = getFrameDoc();
    if (doc) doc.documentElement.style.setProperty(varName, value);
    // Update glow variant for accent
    if (varName === '--hub-accent') {
        const r = parseInt(value.slice(1,3),16);
        const g = parseInt(value.slice(3,5),16);
        const b = parseInt(value.slice(5,7),16);
        const glow = `rgba(${r},${g},${b},0.25)`;
        currentVars['--hub-accent-glow'] = glow;
        if (doc) doc.documentElement.style.setProperty('--hub-accent-glow', glow);
    }
    if (varName === '--primary') {
        const r = parseInt(value.slice(1,3),16);
        const g = parseInt(value.slice(3,5),16);
        const b = parseInt(value.slice(5,7),16);
        const glow = `rgba(${r},${g},${b},0.25)`;
        currentVars['--primary-glow'] = glow;
        if (doc) doc.documentElement.style.setProperty('--primary-glow', glow);
    }
    pushThemeToServer();
}

function updateBgVar(varName, hexValue, alpha) {
    const r = parseInt(hexValue.slice(1,3),16);
    const g = parseInt(hexValue.slice(3,5),16);
    const b = parseInt(hexValue.slice(5,7),16);
    const rgba = `rgba(${r},${g},${b},${alpha})`;
    currentVars[varName] = rgba;
    const doc = getFrameDoc();
    if (doc) doc.documentElement.style.setProperty(varName, rgba);
    pushThemeToServer();
}

function updateBarColor(type, value) {
    const doc = getFrameDoc();
    if (!doc) return;
    const bar = doc.querySelector(`.status-bar.${type}`);
    if (bar) bar.style.background = value;
    currentVars[`--bar-${type}`] = value;
    pushThemeToServer();
}

function syncColor(inputId, hexValue) {
    if (!/^#[0-9a-fA-F]{6}$/.test(hexValue)) return;
    document.getElementById(inputId).value = hexValue;
    document.getElementById(inputId).dispatchEvent(new Event('input'));
}

// ─── Typography ───
function updateFont(target, fontFamily) {
    const doc = getFrameDoc();
    if (!doc) return;
    currentVars[`--font-${target}`] = fontFamily;
    if (target === 'body') {
        doc.documentElement.style.setProperty('--hub-font', fontFamily);
        doc.body.style.fontFamily = fontFamily;
    } else {
        doc.querySelectorAll('.widget-header, .speed-text, .target-title, .banner-title, .sidebar-title, .panel-section-title, .mountain-logo, .header-logo').forEach(el => {
            el.style.fontFamily = fontFamily;
        });
    }
    pushThemeToServer();
}

function updateFontSize(val) {
    document.getElementById('val-font-size').textContent = val + 'px';
    const doc = getFrameDoc();
    if (doc) doc.documentElement.style.fontSize = val + 'px';
    currentVars['--font-size'] = val;
    pushThemeToServer();
}

function updateWidgetFont(val) {
    const rem = (val / 100).toFixed(2);
    document.getElementById('val-widget-font').textContent = rem + 'rem';
    const doc = getFrameDoc();
    if (!doc) return;
    doc.querySelectorAll('.hud-widget').forEach(w => { w.style.fontSize = rem + 'rem'; });
    currentVars['--widget-font'] = rem;
    pushThemeToServer();
}

// ─── Layout ───
function updateRadius(val) {
    document.getElementById('val-radius').textContent = val + 'px';
    const doc = getFrameDoc();
    if (!doc) return;
    doc.querySelectorAll('.hud-widget').forEach(w => { w.style.borderRadius = val + 'px'; });
    doc.documentElement.style.setProperty('--hub-radius-lg', (parseInt(val) + 4) + 'px');
    doc.documentElement.style.setProperty('--hub-radius-md', val + 'px');
    doc.documentElement.style.setProperty('--hub-radius-sm', Math.max(4, parseInt(val) - 4) + 'px');
    currentVars['--radius'] = val;
    pushThemeToServer();
}

// Support newer layouts
function updateBorder(val) {
    document.getElementById('val-border').textContent = val + 'px';
    const doc = getFrameDoc();
    if (!doc) return;
    doc.querySelectorAll('.hud-widget').forEach(w => {
        w.style.borderWidth = val + 'px';
        w.style.borderStyle = 'solid';
    });
    currentVars['--border-width'] = val;
    pushThemeToServer();
}

function updateLeftAccent(val) {
    document.getElementById('val-left-accent').textContent = val + 'px';
    const doc = getFrameDoc();
    if (!doc) return;
    doc.querySelectorAll('.hud-widget').forEach(w => {
        w.style.borderLeftWidth = val + 'px';
    });
    currentVars['--left-accent'] = val;
    pushThemeToServer();
}

function updatePadding(val) {
    const rem = (val / 10).toFixed(1);
    document.getElementById('val-padding').textContent = rem + 'rem';
    const doc = getFrameDoc();
    if (!doc) return;
    doc.querySelectorAll('.hud-widget').forEach(w => { w.style.padding = rem + 'rem'; });
    currentVars['--widget-padding'] = rem;
    pushThemeToServer();
}

function updateOpacity(val) {
    document.getElementById('val-opacity').textContent = val + '%';
    const doc = getFrameDoc();
    if (!doc) return;
    const alpha = (val / 100).toFixed(2);
    doc.querySelectorAll('.hud-widget').forEach(w => {
        w.style.background = `rgba(35,35,38,${alpha})`;
    });
    currentVars['--widget-opacity'] = val;
    pushThemeToServer();
}

function updateBorderOpacity(val) {
    document.getElementById('val-border-opacity').textContent = val + '%';
    const doc = getFrameDoc();
    if (!doc) return;
    const alpha = (val / 100).toFixed(2);
    doc.querySelectorAll('.hud-widget').forEach(w => {
        w.style.borderColor = `rgba(255,255,255,${alpha})`;
    });
    currentVars['--border-opacity'] = val;
    pushThemeToServer();
}

// ─── Widgets Toggle ───
function toggleWidget(id, show) {
    const doc = getFrameDoc();
    if (!doc) return;
    const el = doc.getElementById(id);
    if (!el) return;
    if (show) {
        el.style.display = '';
        el.style.opacity = '1';
        el.style.visibility = 'visible';
    } else {
        el.style.display = 'none';
    }
}

// ─── Preview Scale ───
function updatePreviewScale(val) {
    const el = document.getElementById('preview-scale-val');
    if (el) el.textContent = val + '%';
    if (frame) frame.style.transform = `scale(${val / 100})`;
}

// ─── Reapply all vars after iframe navigation ───
function reapplyAllVars() {
    const doc = getFrameDoc();
    if (!doc) return;
    for (const [k, v] of Object.entries(currentVars)) {
        if (k.startsWith('--')) {
            doc.documentElement.style.setProperty(k, v);
        }
    }
}

// ─── Presets ───
const PRESETS = {
    default: {
        '--hub-accent': '#ff7a00', '--hub-accent-hover': '#ff9533', '--primary': '#0a84ff',
        '--hub-bg': 'rgba(12,13,20,0.98)', '--hub-bg-sidebar': 'rgba(8,9,13,0.99)',
        '--hub-text': '#f3f4f6', '--hub-text-muted': '#9ca3af',
        '--text-main': '#f3f4f6', '--text-muted': '#9ca3af',
    },
    midnight: {
        '--hub-accent': '#7c3aed', '--hub-accent-hover': '#8b5cf6', '--primary': '#2563eb',
        '--hub-bg': 'rgba(10,10,25,0.98)', '--hub-bg-sidebar': 'rgba(6,6,18,0.99)',
        '--hub-text': '#e0e7ff', '--hub-text-muted': '#818cf8',
        '--text-main': '#e0e7ff', '--text-muted': '#818cf8',
    },
    emerald: {
        '--hub-accent': '#10b981', '--hub-accent-hover': '#34d399', '--primary': '#059669',
        '--hub-bg': 'rgba(8,16,12,0.98)', '--hub-bg-sidebar': 'rgba(4,10,8,0.99)',
        '--hub-text': '#d1fae5', '--hub-text-muted': '#6ee7b7',
        '--text-main': '#d1fae5', '--text-muted': '#6ee7b7',
    },
    crimson: {
        '--hub-accent': '#ef4444', '--hub-accent-hover': '#f87171', '--primary': '#dc2626',
        '--hub-bg': 'rgba(18,8,8,0.98)', '--hub-bg-sidebar': 'rgba(12,4,4,0.99)',
        '--hub-text': '#fecaca', '--hub-text-muted': '#fca5a5',
        '--text-main': '#fecaca', '--text-muted': '#fca5a5',
    },
    sakura: {
        '--hub-accent': '#ec4899', '--hub-accent-hover': '#f472b6', '--primary': '#db2777',
        '--hub-bg': 'rgba(18,8,14,0.98)', '--hub-bg-sidebar': 'rgba(12,4,10,0.99)',
        '--hub-text': '#fce7f3', '--hub-text-muted': '#f9a8d4',
        '--text-main': '#fce7f3', '--text-muted': '#f9a8d4',
    },
    arctic: {
        '--hub-accent': '#06b6d4', '--hub-accent-hover': '#22d3ee', '--primary': '#0891b2',
        '--hub-bg': 'rgba(8,12,18,0.98)', '--hub-bg-sidebar': 'rgba(4,8,14,0.99)',
        '--hub-text': '#cffafe', '--hub-text-muted': '#67e8f9',
        '--text-main': '#cffafe', '--text-muted': '#67e8f9',
    },
    gold: {
        '--hub-accent': '#f59e0b', '--hub-accent-hover': '#fbbf24', '--primary': '#d97706',
        '--hub-bg': 'rgba(16,12,6,0.98)', '--hub-bg-sidebar': 'rgba(10,8,3,0.99)',
        '--hub-text': '#fef3c7', '--hub-text-muted': '#fcd34d',
        '--text-main': '#fef3c7', '--text-muted': '#fcd34d',
    },
    neon: {
        '--hub-accent': '#a855f7', '--hub-accent-hover': '#c084fc', '--primary': '#22d3ee',
        '--hub-bg': 'rgba(8,6,18,0.98)', '--hub-bg-sidebar': 'rgba(4,2,12,0.99)',
        '--hub-text': '#f3e8ff', '--hub-text-muted': '#c4b5fd',
        '--text-main': '#f3e8ff', '--text-muted': '#c4b5fd',
    }
};

function applyPreset(name) {
    const preset = PRESETS[name];
    if (!preset) return;
    for (const [k, v] of Object.entries(preset)) {
        currentVars[k] = v;
        const doc = getFrameDoc();
        if (doc) {
            try {
                doc.documentElement.style.setProperty(k, v);
            } catch(e) {}
        }
    }
    // Also update glow
    if (preset['--hub-accent']) {
        const hex = preset['--hub-accent'];
        updateVar('--hub-accent', hex);
        const el = document.getElementById('c-accent');
        if (el) el.value = hex;
        const elHex = document.getElementById('c-accent-hex');
        if (elHex) elHex.value = hex;
    }
    if (preset['--primary']) {
        const el = document.getElementById('c-primary');
        if (el) el.value = preset['--primary'];
        const elHex = document.getElementById('c-primary-hex');
        if (elHex) elHex.value = preset['--primary'];
    }
    showToast(`Applied "${name}" theme`);
    pushThemeToServer();
}

// ─── Export / Import ───
function exportTheme() {
    const data = JSON.stringify(currentVars, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `fugo-theme-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('Theme exported!');
}

function importTheme(event) {
    const file = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
        try {
            const vars = JSON.parse(e.target.result);
            currentVars = vars;
            reapplyAllVars();
            syncInputsFromVars();
            showToast('Theme imported!');
            pushThemeToServer();
        } catch(err) {
            showToast('Invalid theme file');
        }
    };
    reader.readAsText(file);
}

function copyCSS() {
    let css = ':root {\n';
    for (const [k, v] of Object.entries(currentVars)) {
        if (k.startsWith('--')) css += `    ${k}: ${v};\n`;
    }
    css += '}';
    navigator.clipboard.writeText(css).then(() => showToast('CSS copied to clipboard!'));
}

// ─── Toast ───
function showToast(msg) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 2000);
}

// ─── Input Syncing ───
function syncInputsFromVars() {
    function rgbaToHex(rgba) {
        if (!rgba) return '#000000';
        if (rgba.startsWith('#')) return rgba;
        const m = rgba.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)$/);
        if (!m) return '#000000';
        const r = parseInt(m[1]).toString(16).padStart(2, '0');
        const g = parseInt(m[2]).toString(16).padStart(2, '0');
        const b = parseInt(m[3]).toString(16).padStart(2, '0');
        return `#${r}${g}${b}`;
    }

    const setVal = (id, val) => {
        const el = document.getElementById(id);
        if (el) el.value = val;
    };
    const setTxt = (id, val) => {
        const el = document.getElementById(id);
        if (el) el.textContent = val;
    };

    if (currentVars['--hub-accent']) {
        const val = currentVars['--hub-accent'];
        setVal('c-accent', val);
        setVal('c-accent-hex', val);
    }
    if (currentVars['--hub-accent-hover']) {
        const val = currentVars['--hub-accent-hover'];
        setVal('c-accent-hover', val);
        setVal('c-accent-hover-hex', val);
    }
    if (currentVars['--primary']) {
        const val = currentVars['--primary'];
        setVal('c-primary', val);
        setVal('c-primary-hex', val);
    }
    if (currentVars['--hub-bg']) {
        const hex = rgbaToHex(currentVars['--hub-bg']);
        setVal('c-hub-bg', hex);
        setVal('c-hub-bg-hex', hex);
    }
    if (currentVars['--hub-bg-sidebar']) {
        const hex = rgbaToHex(currentVars['--hub-bg-sidebar']);
        setVal('c-sidebar-bg', hex);
        setVal('c-sidebar-bg-hex', hex);
    }
    if (currentVars['--bg-dark']) {
        const hex = rgbaToHex(currentVars['--bg-dark']);
        setVal('c-widget-bg', hex);
        setVal('c-widget-bg-hex', hex);
    }
    if (currentVars['--hub-text']) {
        const val = currentVars['--hub-text'];
        setVal('c-text', val);
        setVal('c-text-hex', val);
    }
    if (currentVars['--hub-text-muted']) {
        const val = currentVars['--hub-text-muted'];
        setVal('c-text-muted', val);
        setVal('c-text-muted-hex', val);
    }
    if (currentVars['--bar-health']) {
        setVal('c-health', currentVars['--bar-health']);
        setVal('c-health-hex', currentVars['--bar-health']);
    }
    if (currentVars['--bar-hunger']) {
        setVal('c-hunger', currentVars['--bar-hunger']);
        setVal('c-hunger-hex', currentVars['--bar-hunger']);
    }
    if (currentVars['--bar-xp']) {
        setVal('c-xp', currentVars['--bar-xp']);
        setVal('c-xp-hex', currentVars['--bar-xp']);
    }
    if (currentVars['--font-body']) {
        setVal('font-body', currentVars['--font-body']);
    }
    if (currentVars['--font-heading']) {
        setVal('font-heading', currentVars['--font-heading']);
    }
    if (currentVars['--font-size']) {
        const val = currentVars['--font-size'];
        setVal('font-size-slider', val);
        setTxt('val-font-size', val + 'px');
    }
    if (currentVars['--widget-font']) {
        const val = Math.round(parseFloat(currentVars['--widget-font']) * 100);
        setVal('widget-font-slider', val);
        setTxt('val-widget-font', currentVars['--widget-font'] + 'rem');
    }
    if (currentVars['--radius']) {
        const val = currentVars['--radius'];
        setVal('radius-slider', val);
        setTxt('val-radius', val + 'px');
    }
    if (currentVars['--border-width']) {
        const val = currentVars['--border-width'];
        setVal('border-slider', val);
        setTxt('val-border', val + 'px');
    }
    if (currentVars['--left-accent']) {
        const val = currentVars['--left-accent'];
        setVal('left-accent-slider', val);
        setTxt('val-left-accent', val + 'px');
    }
    if (currentVars['--widget-padding']) {
        const val = Math.round(parseFloat(currentVars['--widget-padding']) * 10);
        setVal('padding-slider', val);
        setTxt('val-padding', currentVars['--widget-padding'] + 'rem');
    }
    if (currentVars['--widget-opacity']) {
        const val = currentVars['--widget-opacity'];
        setVal('opacity-slider', val);
        setTxt('val-opacity', val + '%');
    }
    if (currentVars['--border-opacity']) {
        const val = currentVars['--border-opacity'];
        setVal('border-opacity-slider', val);
        setTxt('val-border-opacity', val + '%');
    }
    if (currentVars['--menu-bg-url']) {
        let url = currentVars['--menu-bg-url'];
        if (url.startsWith('url(')) {
            url = url.slice(5, -2); // slice url("...")
            if (url.endsWith('"') || url.endsWith("'")) url = url.slice(0, -1);
        }
        setVal('menu-bg-url', url);
    }
}

function loadThemeFromServer() {
    fetch('/api/get-theme')
        .then(res => res.json())
        .then(data => {
            if (data && Object.keys(data).length > 0) {
                currentVars = data;
                reapplyAllVars();
                syncInputsFromVars();
                showToast("Theme loaded from game!");
            }
        })
        .catch(err => console.warn("Standalone mode: could not connect to game server."));
}

// ─── Init ───
frame.addEventListener('load', () => {
    setTimeout(() => {
        try {
            const doc = frame.contentDocument;
            const panel = doc.getElementById('overlay-settings-panel');
            if (panel) panel.classList.remove('hidden');
            const overlay = doc.getElementById('overlay-container');
            if (overlay) overlay.classList.add('backdrop-active');
        } catch(e) {}
        reapplyAllVars();
    }, 200);
});

// Load current theme at startup
loadThemeFromServer();

// ─── Main Menu Drag & Drop Layout Controls ───
window.isDraggingEnabled = false;

function toggleMenuDragging(checked) {
    window.isDraggingEnabled = checked;
    showToast(checked ? "Drag & Drop Enabled! Drag elements in preview." : "Drag & Drop Disabled.");
}

function updateMenuBg(val) {
    if (val.trim() === '') {
        delete currentVars['--menu-bg-url'];
        const doc = getFrameDoc();
        if (doc) doc.documentElement.style.removeProperty('--menu-bg-url');
    } else {
        let formatted = val;
        if (!val.startsWith('url(')) {
            formatted = `url("${val}")`;
        }
        currentVars['--menu-bg-url'] = formatted;
        const doc = getFrameDoc();
        if (doc) doc.documentElement.style.setProperty('--menu-bg-url', formatted);
    }
    pushThemeToServer();
}

function resetMenuBg() {
    const input = document.getElementById('menu-bg-url');
    if (input) input.value = '';
    updateMenuBg('');
    showToast("Background reset to default");
}

function resetMenuPositions() {
    const coords = [
        '--menu-logo-top', '--menu-logo-left',
        '--menu-profiles-top', '--menu-profiles-right',
        '--menu-buttons-top', '--menu-buttons-left', '--menu-buttons-transform',
        '--menu-quickjoin-bottom', '--menu-quickjoin-left',
        '--menu-btn-singleplayer-top', '--menu-btn-singleplayer-left',
        '--menu-btn-multiplayer-top', '--menu-btn-multiplayer-left',
        '--menu-btn-settings-top', '--menu-btn-settings-left',
        '--menu-btn-quit-top', '--menu-btn-quit-left'
    ];
    const doc = getFrameDoc();
    coords.forEach(c => {
        delete currentVars[c];
        if (doc) {
            try {
                doc.documentElement.style.removeProperty(c);
            } catch(e) {}
        }
    });
    
    const dragToggle = document.getElementById('menu-drag-toggle');
    if (dragToggle) dragToggle.checked = false;
    window.isDraggingEnabled = false;
    
    showToast("Menu positions reset!");
    pushThemeToServer();
}
