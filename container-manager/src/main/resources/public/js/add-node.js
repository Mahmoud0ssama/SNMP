import { API } from './api.js';
import { initTheme, toggleTheme, showToast } from './utils.js';

let nextIp = '';

async function fetchNextIp() {
    try {
        const res = await API.getNextIp();
        if (res.success) {
            nextIp = res.data;
            document.getElementById('auto-ip-hint').textContent = `→ Next available: ${nextIp}`;
        } else {
            document.getElementById('auto-ip-hint').textContent = `→ Error finding IP`;
        }
    } catch(e) {}
}

function handleRadioChange() {
    const isAuto = document.getElementById('ip-auto').checked;
    const manualInput = document.getElementById('manual-ip-input');
    if (isAuto) {
        manualInput.disabled = true;
        manualInput.value = '';
        manualInput.classList.remove('error');
        document.getElementById('ip-error').classList.remove('active');
    } else {
        manualInput.disabled = false;
        manualInput.focus();
    }
}

function validateManualIp() {
    const manualInput = document.getElementById('manual-ip-input');
    const ip = manualInput.value.trim();
    const errorMsg = document.getElementById('ip-error');
    
    if (document.getElementById('ip-auto').checked) return true;
    
    if (!ip.startsWith('172.25.0.')) {
        manualInput.classList.add('error');
        errorMsg.textContent = 'IP must be in 172.25.0.0/24 subnet';
        errorMsg.classList.add('active');
        return false;
    }
    
    try {
        const octet = parseInt(ip.split('.')[3]);
        if (isNaN(octet) || octet < 2 || octet > 254) {
            manualInput.classList.add('error');
            errorMsg.textContent = 'Last octet must be between 2 and 254';
            errorMsg.classList.add('active');
            return false;
        }
    } catch(e) {
        manualInput.classList.add('error');
        errorMsg.classList.add('active');
        return false;
    }
    
    manualInput.classList.remove('error');
    errorMsg.classList.remove('active');
    return true;
}

async function submitForm(e) {
    e.preventDefault();
    
    const isAuto = document.getElementById('ip-auto').checked;
    if (!isAuto && !validateManualIp()) {
        return;
    }

    const data = {
        serviceName: document.getElementById('serviceName').value.trim(),
        nodeName: document.getElementById('nodeName').value.trim(),
        nodeType: document.getElementById('nodeType').value,
        region: document.getElementById('region').value,
        vendor: document.getElementById('vendor').value.trim(),
        ip: isAuto ? nextIp : document.getElementById('manual-ip-input').value.trim()
    };

    if (!data.serviceName || !data.nodeName || !data.ip) {
        showToast('Please fill all required fields', 'error');
        return;
    }

    const btn = document.getElementById('submit-btn');
    btn.disabled = true;
    btn.textContent = 'Creating...';

    try {
        const res = await API.addNode(data);
        if (res.success) {
            showToast('Node created and started successfully!', 'success');
            setTimeout(() => window.location.href = 'index.html', 1500);
        } else {
            showToast(`Error: ${res.message}`, 'error');
            btn.disabled = false;
            btn.textContent = 'Create Node';
        }
    } catch(err) {
        showToast('Connection error', 'error');
        btn.disabled = false;
        btn.textContent = 'Create Node';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    document.getElementById('theme-toggle')?.addEventListener('click', toggleTheme);

    fetchNextIp();

    document.getElementById('ip-auto').addEventListener('change', handleRadioChange);
    document.getElementById('ip-manual').addEventListener('change', handleRadioChange);
    document.getElementById('manual-ip-input').addEventListener('input', validateManualIp);
    
    document.getElementById('add-node-form').addEventListener('submit', submitForm);
});
