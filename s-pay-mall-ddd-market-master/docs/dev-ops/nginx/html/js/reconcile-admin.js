const mallBaseUrl = window.AppConfig?.sPayMallUrl || 'http://127.0.0.1:8070';

const caseBody = document.getElementById('caseBody');
const caseStatus = document.getElementById('caseStatus');
const caseType = document.getElementById('caseType');
const checkAll = document.getElementById('checkAll');
const adminTokenInput = document.getElementById('adminToken');
const adminOperatorInput = document.getElementById('adminOperator');
const logBody = document.getElementById('logBody');
const logTitle = document.getElementById('logTitle');
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
document.getElementById('batchClosedBtn').addEventListener('click', () => batchHandle(3));
document.getElementById('importBillBtn').addEventListener('click', importBill);
checkAll.addEventListener('change', () => {
    document.querySelectorAll('.case-check').forEach(item => item.checked = checkAll.checked);
});
caseBody.addEventListener('click', async event => {
    const target = event.target;
    if (target.classList.contains('replay-btn')) {
        await replayCase(target.dataset.caseNo);
    } else if (target.classList.contains('confirm-btn')) {
        await handleCase(target.dataset.caseNo, 'confirm', '确认处理');
    } else if (target.classList.contains('ignore-btn')) {
        await handleCase(target.dataset.caseNo, 'ignore', '忽略');
    } else if (target.classList.contains('close-btn')) {
        await handleCase(target.dataset.caseNo, 'close', '关闭');
    } else if (target.classList.contains('remark-btn')) {
        await remarkCase(target.dataset.caseNo);
    } else if (target.classList.contains('logs-btn')) {
        await queryLogs(target.dataset.caseNo);
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
            <td>
                <div class="action-group">
                    <button class="row-btn replay-btn" data-case-no="${escapeHtml(item.caseNo)}">重放</button>
                    <button class="row-btn confirm-btn" data-case-no="${escapeHtml(item.caseNo)}">确认</button>
                    <button class="row-btn ignore-btn" data-case-no="${escapeHtml(item.caseNo)}">忽略</button>
                    <button class="row-btn close-btn" data-case-no="${escapeHtml(item.caseNo)}">关闭</button>
                    <button class="row-btn remark-btn" data-case-no="${escapeHtml(item.caseNo)}">备注</button>
                    <button class="row-btn logs-btn" data-case-no="${escapeHtml(item.caseNo)}">日志</button>
                </div>
            </td>
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
            handleNote: status === 1 ? 'batch handled' : (status === 2 ? 'batch ignored' : 'batch closed'),
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
    await queryLogs(caseNo);
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

async function handleCase(caseNo, action, label) {
    const note = prompt(`${label}备注：${caseNo}`, `${label} by ${adminOperator}`);
    if (note === null) return;
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/${action}`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
            caseNo,
            handler: adminOperator,
            handleNote: note || label,
        }),
    });
    const data = await response.json();
    ensureSuccess(data);
    alert(data.data ? `${label}成功` : `${label}失败，可能差错单已终态`);
    await queryCases();
    await queryLogs(caseNo);
}

async function remarkCase(caseNo) {
    const note = prompt(`追加备注：${caseNo}`, '');
    if (!note) return;
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/remark`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
            caseNo,
            handler: adminOperator,
            handleNote: note,
        }),
    });
    const data = await response.json();
    ensureSuccess(data);
    alert(data.data ? '备注成功' : '备注失败');
    await queryCases();
    await queryLogs(caseNo);
}

async function queryLogs(caseNo) {
    const params = new URLSearchParams();
    params.append('bizId', caseNo);
    params.append('pageSize', '50');
    const response = await fetch(`${mallBaseUrl}/api/v1/reconcile/operation_logs?${params.toString()}`, { headers: authHeaders() });
    const data = await response.json();
    ensureSuccess(data);
    renderLogs(caseNo, data.data || []);
}

function renderLogs(caseNo, rows) {
    logTitle.textContent = caseNo;
    if (!rows.length) {
        logBody.innerHTML = '<tr><td colspan="4" class="empty">暂无操作记录</td></tr>';
        return;
    }
    logBody.innerHTML = rows.map(item => `
        <tr>
            <td>${formatTime(item.createTime)}</td>
            <td>${escapeHtml(item.operator || '')}</td>
            <td>${escapeHtml(item.operationType || '')}</td>
            <td>${escapeHtml(item.result || '')}</td>
        </tr>
    `).join('');
}

function statusText(status) {
    if (status === 1) return '已处理';
    if (status === 2) return '已忽略';
    if (status === 3) return '已关闭';
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
