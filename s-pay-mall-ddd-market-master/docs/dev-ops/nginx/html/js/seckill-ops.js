const messageBody = document.getElementById('messageBody');
const checkAll = document.getElementById('checkAll');
const adminTokenInput = document.getElementById('adminToken');
const adminOperatorInput = document.getElementById('adminOperator');

let adminToken = localStorage.getItem('seckillAdminToken') || 'local-admin-token';
let adminOperator = localStorage.getItem('seckillAdminOperator') || 'local-admin';

adminTokenInput.value = adminToken;
adminOperatorInput.value = adminOperator;

document.getElementById('saveAuthBtn').addEventListener('click', saveAuth);
document.getElementById('queryBtn').addEventListener('click', queryMessages);
document.getElementById('replaySelectedBtn').addEventListener('click', replaySelected);
document.getElementById('replayTopBtn').addEventListener('click', () => replayMessages([], 20));
checkAll.addEventListener('change', () => {
    document.querySelectorAll('.message-check').forEach(item => item.checked = checkAll.checked);
});

async function queryMessages() {
    const response = await fetch(`${AppConfig.groupBuyMarketUrl}/api/v1/gbm/seckill/ops/manual_messages?limit=100`, {
        headers: authHeaders(),
    });
    const data = await response.json();
    ensureSuccess(data);
    renderMessages(data.data || []);
}

function renderMessages(rows) {
    checkAll.checked = false;
    if (!rows.length) {
        messageBody.innerHTML = '<tr><td colspan="7" class="empty">暂无人工补偿消息</td></tr>';
        return;
    }
    messageBody.innerHTML = rows.map(item => `
        <tr>
            <td><input class="message-check" type="checkbox" value="${escapeHtml(item.id)}"></td>
            <td>${escapeHtml(item.id)}</td>
            <td>${escapeHtml(item.originalStreamKey || '')}</td>
            <td>${escapeHtml(item.originalMessageId || '')}</td>
            <td>${escapeHtml(item.retryCount || 0)}</td>
            <td>${escapeHtml(item.error || '')}</td>
            <td>${escapeHtml(item.body || '')}</td>
        </tr>
    `).join('');
}

async function replaySelected() {
    const messageIds = Array.from(document.querySelectorAll('.message-check:checked')).map(item => item.value);
    if (!messageIds.length) {
        alert('请选择补偿消息');
        return;
    }
    await replayMessages(messageIds, messageIds.length);
}

async function replayMessages(messageIds, limit) {
    const response = await fetch(`${AppConfig.groupBuyMarketUrl}/api/v1/gbm/seckill/ops/replay_manual`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ messageIds, limit }),
    });
    const data = await response.json();
    ensureSuccess(data);
    alert(`已重放 ${data.data || 0} 条`);
    await queryMessages();
}

function authHeaders(extra = {}) {
    return {
        'x-admin-token': adminToken,
        'x-admin-operator': adminOperator,
        ...extra,
    };
}

function ensureSuccess(data) {
    if (!data || data.code !== '0000') {
        alert(data ? data.info : '请求失败');
        throw new Error(data ? data.info : 'request failed');
    }
}

function saveAuth() {
    adminToken = adminTokenInput.value.trim();
    adminOperator = adminOperatorInput.value.trim() || 'local-admin';
    localStorage.setItem('seckillAdminToken', adminToken);
    localStorage.setItem('seckillAdminOperator', adminOperator);
    alert('已保存');
}

function escapeHtml(value) {
    return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

queryMessages();
