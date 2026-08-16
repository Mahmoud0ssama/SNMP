import { API } from './api.js';
import { initTheme, toggleTheme, showToast } from './utils.js';

let containerName = '';

async function loadDetail() {
    try {
        const result = await API.getContainerInfo(containerName);
        if (result.success && result.data.inspect && result.data.inspect.length > 0) {
            const info = result.data.inspect[0];
            const stats = result.data.stats;
            renderInfo(info, stats);
        } else {
            showToast('Failed to load container details', 'error');
        }
    } catch (e) {
        showToast('Failed to load container data', 'error');
    }

    // Load current mode
    try {
        const modeRes = await API.getSimulatorMode(containerName);
        if (modeRes.success) {
            updateModeBadge(modeRes.data);
        }
    } catch (e) {
        console.error('Failed to load mode', e);
    }
}

function updateModeBadge(mode) {
    const badge = document.getElementById('current-mode-badge');
    badge.textContent = `Current Mode: ${mode.toUpperCase()}`;
    if (mode === 'sensor') {
        badge.style.background = 'rgba(46, 204, 113, 0.2)';
        badge.style.color = 'var(--success)';
    } else {
        badge.style.background = 'rgba(52, 152, 219, 0.2)';
        badge.style.color = 'var(--primary)';
    }
}

function renderInfo(info, stats) {
    document.getElementById('c-title').textContent = info.Name.replace('/', '');
    
    const isRunning = info.State.Running;
    document.getElementById('c-status').className = `sev-pill ${isRunning ? 'running' : 'stopped'}`;
    document.getElementById('c-status').textContent = isRunning ? 'Running' : 'Stopped';

    let ip = '-';
    if (info.NetworkSettings && info.NetworkSettings.Networks) {
        const networks = Object.values(info.NetworkSettings.Networks);
        if (networks.length > 0) {
            ip = networks[0].IPAddress;
        }
    }
    document.getElementById('info-ip').textContent = ip;
    document.getElementById('info-image').textContent = info.Config.Image;
    
    const startedAt = new Date(info.State.StartedAt);
    document.getElementById('info-uptime').textContent = isRunning ? startedAt.toLocaleString() : '-';

    if (stats) {
        document.getElementById('info-cpu').textContent = stats.CPUPerc || '-';
        document.getElementById('info-mem').textContent = stats.MemUsage || '-';
    }

    const envs = info.Config.Env || [];
    let envHtml = '';
    envs.forEach(e => {
        if (e.startsWith('NODE_')) {
            envHtml += `<div><span class="info-label">${e.split('=')[0]}:</span> <span class="info-val">${e.split('=')[1]}</span></div>`;
        }
    });
    document.getElementById('info-env').innerHTML = envHtml;
}

async function handleAction(action) {
    try {
        let res;
        if (action === 'start') res = await API.startContainer(containerName);
        else if (action === 'stop') res = await API.stopContainer(containerName);
        else if (action === 'restart') res = await API.restartContainer(containerName);
        else if (action === 'remove') {
            if (confirm(`Are you sure you want to completely remove node ${containerName}? This will delete the container and remove it from docker-compose.yml.`)) {
                res = await API.removeComposeNode(containerName);
                if (res.success) window.location.href = 'index.html';
            }
            return;
        }

        if (res && res.success) {
            showToast(`Successfully ${action}ed container`, 'success');
            loadDetail();
        } else {
            showToast(res.message || 'Action failed', 'error');
        }
    } catch(e) {
        showToast('Error executing action', 'error');
    }
}

async function handleChaos(type) {
    try {
        showToast(`Triggering ${type}...`, 'info');
        const res = await API.triggerChaos(type, containerName);
        if (res.success) {
            showToast(`Chaos ${type} successful!`, 'success');
        } else {
            showToast(`Chaos failed: ${res.message}`, 'error');
        }
    } catch(e) {
        showToast('Error executing chaos script', 'error');
    }
}

async function loadLogs() {
    try {
        const res = await API.getContainerLogs(containerName);
        if (res.success) {
            document.getElementById('log-viewer').textContent = res.data;
            document.getElementById('log-viewer').scrollTop = document.getElementById('log-viewer').scrollHeight;
        }
    } catch(e) {}
}

async function runCommand() {
    const input = document.getElementById('exec-input');
    const cmd = input.value.trim();
    if (!cmd) return;

    const outputWindow = document.getElementById('exec-output');
    outputWindow.textContent += `\n$ ${cmd}\n`;
    input.value = '';

    try {
        const res = await API.execCommand(containerName, cmd);
        if (res.success) {
            outputWindow.textContent += res.data + '\n';
        } else {
            outputWindow.textContent += `Error: ${res.message}\n`;
        }
        outputWindow.scrollTop = outputWindow.scrollHeight;
    } catch(e) {
        outputWindow.textContent += `Error executing command\n`;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    document.getElementById('theme-toggle')?.addEventListener('click', toggleTheme);

    const params = new URLSearchParams(window.location.search);
    containerName = params.get('name');
    if (!containerName) {
        window.location.href = 'index.html';
        return;
    }

    loadDetail();
    loadLogs();
    setInterval(loadLogs, 10000); // refresh logs every 10s

    // Bind action buttons
    document.getElementById('btn-start').onclick = () => handleAction('start');
    document.getElementById('btn-stop').onclick = () => handleAction('stop');
    document.getElementById('btn-restart').onclick = () => handleAction('restart');
    document.getElementById('btn-remove').onclick = () => handleAction('remove');

    // Bind mode buttons
    document.getElementById('btn-mode-sensor').onclick = async () => {
        const res = await API.setSimulatorMode(containerName, 'sensor');
        showToast(res.success ? 'Mode set to Sensor' : res.message, res.success ? 'success' : 'error');
        if (res.success) updateModeBadge('sensor');
    };
    document.getElementById('btn-mode-file').onclick = async () => {
        const res = await API.setSimulatorMode(containerName, 'file');
        showToast(res.success ? 'Mode set to File' : res.message, res.success ? 'success' : 'error');
        if (res.success) updateModeBadge('file');
    };

    // Bind chaos buttons
    document.getElementById('btn-disk').onclick = () => handleChaos('disk_full');
    document.getElementById('btn-temp').onclick = () => handleChaos('high_temp');
    document.getElementById('btn-cong').onclick = () => handleChaos('congestion');
    document.getElementById('btn-linkdown').onclick = () => handleChaos('link_down');
    
    // Bind fix buttons
    document.getElementById('btn-fix-disk').onclick = () => handleChaos('fix_disk_full');
    document.getElementById('btn-fix-temp').onclick = () => handleChaos('fix_high_temp');
    document.getElementById('btn-fix-cong').onclick = () => handleChaos('fix_congestion');
    document.getElementById('btn-linkup').onclick = () => handleChaos('recover_link');

    // Bind exec
    document.getElementById('btn-run').onclick = runCommand;
    document.getElementById('exec-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') runCommand();
    });
    
    document.getElementById('btn-refresh-logs').onclick = loadLogs;
});
