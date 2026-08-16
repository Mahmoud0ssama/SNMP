import { API } from './api.js';
import { initTheme, toggleTheme, showToast } from './utils.js';

let refreshInterval;

async function loadContainers() {
    try {
        const result = await API.getContainers();
        if (result.success && Array.isArray(result.data)) {
            renderTable(result.data);
            updateStats(result.data);
        } else {
            showToast(result.message || 'Failed to load containers', 'error');
        }
    } catch (e) {
        showToast('Connection error', 'error');
    }
}

function updateStats(containers) {
    document.getElementById('stat-total').textContent = containers.length;
    
    let running = 0;
    let stopped = 0;
    let errors = 0;

    containers.forEach(c => {
        if (c.State === 'running') running++;
        else if (c.State === 'exited') stopped++;
        else errors++;
    });

    document.getElementById('stat-running').textContent = running;
    document.getElementById('stat-stopped').textContent = stopped;
    document.getElementById('stat-error').textContent = errors;
}

function renderTable(containers) {
    const tbody = document.getElementById('container-list');
    tbody.innerHTML = '';
    
    // Sort so running is first, then alphabetical by name
    containers.sort((a, b) => {
        if (a.State === 'running' && b.State !== 'running') return -1;
        if (a.State !== 'running' && b.State === 'running') return 1;
        return a.Names.localeCompare(b.Names);
    });

    containers.forEach(c => {
        const isRunning = c.State === 'running';
        const tr = document.createElement('tr');
        tr.className = 'container-row';
        tr.onclick = () => window.location.href = `detail.html?name=${encodeURIComponent(c.Names)}`;

        let ip = '-';
        if (c.Networks && c.Networks.includes('telecom_net')) {
             // Basic parsing if needed, but 'docker ps' with json might need inspect for exact IP
             // The format string we use for docker ps might not give IP easily. 
             // We'll just show the network name or - for now, detailed inspect gives the exact IP.
        }

        tr.innerHTML = `
            <td>
                <span class="status-dot ${isRunning ? 'running' : 'stopped'}"></span>
                <span class="container-name">${c.Names}</span>
            </td>
            <td>${c.Image.split(':')[0]}</td>
            <td><span class="sev-pill ${isRunning ? 'running' : 'stopped'}">${c.State}</span></td>
            <td>${c.Status}</td>
        `;
        tbody.appendChild(tr);
    });
}

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    
    const themeBtn = document.getElementById('theme-toggle');
    if (themeBtn) {
        themeBtn.addEventListener('click', toggleTheme);
    }
    
    loadContainers();
    refreshInterval = setInterval(loadContainers, 5000);
});
