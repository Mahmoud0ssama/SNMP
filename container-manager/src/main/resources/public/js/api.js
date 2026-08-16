const BASE_URL = '/api';

async function fetchJSON(endpoint, options = {}) {
    try {
        const response = await fetch(`${BASE_URL}${endpoint}`, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        });
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

export const API = {
    // Containers
    getContainers: () => fetchJSON('/containers'),
    getContainerInfo: (name) => fetchJSON(`/containers/${name}`),
    startContainer: (name) => fetchJSON(`/containers/${name}/start`, { method: 'POST' }),
    stopContainer: (name) => fetchJSON(`/containers/${name}/stop`, { method: 'POST' }),
    restartContainer: (name) => fetchJSON(`/containers/${name}/restart`, { method: 'POST' }),
    removeContainer: (name) => fetchJSON(`/containers/${name}`, { method: 'DELETE' }),
    getContainerLogs: (name, lines = 100) => fetchJSON(`/containers/${name}/logs?lines=${lines}`),
    execCommand: (name, command) => fetchJSON(`/containers/${name}/exec`, {
        method: 'POST',
        body: JSON.stringify({ command })
    }),

    // Compose
    getComposeServices: () => fetchJSON('/compose/services'),
    getNextIp: () => fetchJSON('/compose/next-ip'),
    addNode: (data) => fetchJSON('/compose/add-node', {
        method: 'POST',
        body: JSON.stringify(data)
    }),
    removeComposeNode: (name) => fetchJSON(`/compose/${name}`, { method: 'DELETE' }),

    // Chaos
    triggerChaos: (type, name) => fetchJSON(`/chaos/${type}/${name}`, { method: 'POST' }),

    getSimulatorMode: (name) => fetchJSON(`/containers/${name}/mode`),
    setSimulatorMode: (name, mode) => fetchJSON(`/containers/${name}/mode`, { method: 'POST', body: JSON.stringify({ mode }) })
};
