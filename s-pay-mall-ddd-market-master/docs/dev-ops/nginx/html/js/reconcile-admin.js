const mallBaseUrl = window.AppConfig?.sPayMallUrl || 'http://127.0.0.1:8070';

const caseBody = document.getElementById('caseBody');
const caseStatus = document.getElementById('caseStatus');
const caseType = document.getElementById('caseType');
const checkAll = document.getElementById('checkAll');
const adminTokenInput = document.getElementById('adminToken');
const adminOperatorInput = document.getElementById('adminOperator');
let adminToken = localStorage.getItem('reconcileAdminToken') || 'local-admin-token';
let adminOperator = localStorage.getItem('reconcileAdminOperator') || 'local-admin';

adminTokenInput.value = adminToken;
adminOperatorInput.value = adminOperator;

document.getElementById('scanBtn').addEventListener('click', scanCases);
document.getElementById('queryBtn').addEventListener('click', queryCases);
document.getElementById('saveAuthBtn').addEventListener('click', saveAuth);
document.getElementById('batchReplayBtn').addEventListener('click', batchReplay);
document.getElementById('batchHandledBtn').addEventListener('click', () => batchHandle(1));
document.getElementById('batchIgnoredBtn').addEventListener('click', () => batchHandle(2));
document.getElementById('importBillBtn').addEventListener('click', importBill);
checkAll.addEventListener('change', () => {
    document.querySelectorAll('.case-check').forEach(item => item.checked = checkAll.checked);
});
caseBody.addEventListener('click', async event => {
    const target = event.target;
    if (target.classList.contains('replay-btn')) {
        await replayCase(target.dataset.caseNo);
    }
});

async function scanCases() {
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/scan`, { method: 'POST', headers: authHeaders() });
    const data = await response.json();
    ensureSuccess(data);
    alert(`扫描完成，刷新/新增 ${data.data || 0} 条`);
    await queryCases();
}

async function queryCases() {
    const params = new URLSearchParams();
    if (caseStatus.value !== '') params.append('caseStatus', caseStatus.value);
    if (caseType.value !== '') params.append('caseType', caseType.value);
    params.append('pageSize', '100');
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/case_list?${params.toString()}`, { headers: authHeaders() });
    const data = await response.json();
    ensureSuccess(data);
    renderCases(data.data || []);
}

function renderCases(rows) {
    checkAll.checked = false;
    if (!rows.length) {
        caseBody.innerHTML = '<tr><td colspan="9" class="empty">暂无差错单</td></tr>';
        return;
    }
    caseBody.innerHTML = rows.map(item => `
        <tr>
            <td><input class="case-check" type="checkbox" value="${escapeHtml(item.caseNo)}"></td>
            <td>${escapeHtml(item.caseNo)}</td>
            <td>${escapeHtml(item.caseType)}</td>
            <td class="${escapeHtml(item.severity)}">${escapeHtml(item.severity)}</td>
            <td>${escapeHtml(item.bizId || '')}</td>
            <td>${statusText(item.caseStatus)}</td>
            <td>${escapeHtml(item.summary || '')}</td>
            <td>${formatTime(item.updateTime)}</td>
            <td><button class="row-btn replay-btn" data-case-no="${escapeHtml(item.caseNo)}">重放</button></td>
        </tr>
    `).join('');
}

async function batchHandle(status) {
    const caseNoList = Array.from(document.querySelectorAll('.case-check:checked')).map(item => item.value);
    if (!caseNoList.length) {
        alert('请选择差错单');
        return;
    }
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/batch_handle`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
            caseNoList,
            caseStatus: status,
            handler: adminOperator,
            handleNote: status === 1 ? 'batch handled' : 'batch ignored',
        }),
    });
    const data = await response.json();
    ensureSuccess(data);
    alert(`处理完成 ${data.data || 0} 条`);
    await queryCases();
}

async function replayCase(caseNo) {
    if (!confirm(`确认重放/补偿：${caseNo}`)) return;
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/replay`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ caseNo, operator: adminOperator }),
    });
    const data = await response.json();
    ensureSuccess(data);
    alert(data.data ? '重放成功' : '当前差错单不支持自动重放或重放失败');
    await queryCases();
}

async function batchReplay() {
    const caseNoList = Array.from(document.querySelectorAll('.case-check:checked')).map(item => item.value);
    if (!caseNoList.length) {
        alert('请选择差错单');
        return;
    }
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/batch_replay`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ caseNoList, operator: adminOperator }),
    });
    const data = await response.json();
    ensureSuccess(data);
    alert(`重放完成 ${data.data || 0} 条`);
    await queryCases();
}

async function importBill() {
    const csv = document.getElementById('billCsv').value;
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/import_bill`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'text/plain;charset=UTF-8' }),
        body: csv,
    });
    const data = await response.json();
    ensureSuccess(data);
    alert(`导入账单 ${data.data || 0} 条`);
}

function statusText(status) {
    if (status === 1) return '已处理';
    if (status === 2) return '已忽略';
    return '待处理';
}

function formatTime(value) {
    if (!value) return '';
    return new Date(value).toLocaleString();
}

function escapeHtml(value) {
    return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
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
        if (data && data.code === '0003') {
            localStorage.removeItem('reconcileAdminToken');
            alert('口令无效，请修改后保存');
        }
        throw new Error(data ? data.info : 'request failed');
    }
}

function saveAuth() {
    adminToken = adminTokenInput.value.trim();
    adminOperator = adminOperatorInput.value.trim() || 'local-admin';
    localStorage.setItem('reconcileAdminToken', adminToken);
    localStorage.setItem('reconcileAdminOperator', adminOperator);
    alert('已保存');
}

queryCases();
