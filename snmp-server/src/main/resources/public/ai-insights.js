document.addEventListener('DOMContentLoaded', () => {
    let token = localStorage.getItem('snmp_token');
    if (!token) {
        window.location.href = 'index.html';
        return;
    }
    generateInsights();
});

async function generateInsights() {
    const contentBox = document.getElementById('aiContentBox');
    
    contentBox.innerHTML = `
        <div class="loading">
            <div class="spinner"></div>
            <div>Analyzing recent network traps with Gemini AI...</div>
        </div>
    `;

    try {
        let token = localStorage.getItem('snmp_token');
        const response = await fetch('/api/ai/insights', {
            method: 'GET',
            headers: { 'Authorization': 'Bearer ' + token }
        });

        if (response.status === 401 || response.status === 403) {
            localStorage.removeItem('snmp_token');
            window.location.href = 'index.html';
            return;
        }

        const data = await response.json();
        
        if (data.markdown) {
            contentBox.innerHTML = marked.parse(data.markdown);
        } else {
            contentBox.innerHTML = `<div class="error-msg">Failed to parse AI response.</div>`;
        }
    } catch (err) {
        contentBox.innerHTML = `<div class="error-msg">Error contacting the AI service: ${err.message}</div>`;
    }
}
