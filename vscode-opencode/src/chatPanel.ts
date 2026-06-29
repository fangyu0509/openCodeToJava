import * as vscode from 'vscode';

export class ChatPanel {
    public static currentPanel: ChatPanel | undefined;
    private readonly panel: vscode.WebviewPanel;
    private disposables: vscode.Disposable[] = [];
    private port: number;

    private constructor(panel: vscode.WebviewPanel, port: number) {
        this.panel = panel;
        this.port = port;
        this.panel.webview.html = this.getHtml();
        this.panel.onDidDispose(() => this.dispose(), null, this.disposables);
    }

    static show(port: number) {
        const column = vscode.window.activeTextEditor
            ? vscode.window.activeTextEditor.viewColumn
            : undefined;

        if (ChatPanel.currentPanel) {
            ChatPanel.currentPanel.panel.reveal(column);
            return;
        }

        const panel = vscode.window.createWebviewPanel(
            'opencodeChat',
            'OpenCode Chat',
            column || vscode.ViewColumn.Beside,
            { enableScripts: true, retainContextWhenHidden: true }
        );

        ChatPanel.currentPanel = new ChatPanel(panel, port);
    }

    private getHtml(): string {
        const port = this.port;
        return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>OpenCode</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: var(--vscode-font-family); height: 100vh; display: flex; flex-direction: column; background: var(--vscode-editor-background); color: var(--vscode-editor-foreground); font-size: var(--vscode-font-size); }
  .header { padding: 8px 12px; border-bottom: 1px solid var(--vscode-panel-border); display: flex; gap: 8px; align-items: center; }
  .header select { flex: 1; background: var(--vscode-dropdown-background); color: var(--vscode-dropdown-foreground); border: 1px solid var(--vscode-dropdown-border); padding: 4px 8px; border-radius: 2px; }
  .messages { flex: 1; overflow-y: auto; padding: 12px; }
  .msg { margin-bottom: 12px; padding: 8px 12px; border-radius: 4px; max-width: 90%; line-height: 1.5; white-space: pre-wrap; word-wrap: break-word; }
  .msg.user { background: var(--vscode-textBlockQuote-background); margin-left: auto; }
  .msg.assistant { background: var(--vscode-editor-inactiveSelectionBackground); }
  .msg.tool_call { background: var(--vscode-inputValidation-infoBackground); font-size: 11px; max-width: 100%; }
  .msg.tool_result { background: var(--vscode-inputValidation-warningBackground); font-size: 11px; max-width: 100%; }
  .msg.error { background: var(--vscode-inputValidation-errorBackground); }
  .msg .meta { font-size: 10px; opacity: 0.5; margin-top: 4px; }
  .msg code { background: var(--vscode-textPreformat-background); padding: 1px 4px; border-radius: 2px; font-size: 11px; }
  .msg pre { background: var(--vscode-textPreformat-background); padding: 8px; border-radius: 4px; overflow-x: auto; margin: 6px 0; }
  .input-area { padding: 8px 12px; border-top: 1px solid var(--vscode-panel-border); display: flex; gap: 6px; }
  .input-area input { flex: 1; background: var(--vscode-input-background); color: var(--vscode-input-foreground); border: 1px solid var(--vscode-input-border); padding: 6px 10px; border-radius: 2px; }
  .input-area button { background: var(--vscode-button-background); color: var(--vscode-button-foreground); border: none; padding: 6px 14px; border-radius: 2px; cursor: pointer; }
  .input-area button:disabled { opacity: 0.5; cursor: default; }
  .status-bar { padding: 4px 12px; font-size: 11px; opacity: 0.6; text-align: center; border-top: 1px solid var(--vscode-panel-border); }
</style>
</head>
<body>
<div class="header">
  <select id="agentSelect"><option value="build">Build</option><option value="plan">Plan</option><option value="architect">Architect</option></select>
  <button id="newSessionBtn">New</button>
</div>
<div class="messages" id="messages"></div>
<div class="status-bar" id="status">Starting server...</div>
<div class="input-area">
  <input type="text" id="input" placeholder="Type a message..." disabled>
  <button id="sendBtn" disabled>Send</button>
</div>
<script>
const API = 'http://localhost:${port}/api';
let sessionId = null;
let eventSource = null;
let currentDiv = null;
let currentText = '';
let connected = false;

const messagesEl = document.getElementById('messages');
const inputEl = document.getElementById('input');
const sendBtn = document.getElementById('sendBtn');
const statusEl = document.getElementById('status');
const agentSelect = document.getElementById('agentSelect');
const newSessionBtn = document.getElementById('newSessionBtn');

async function checkServer() {
  try {
    const res = await fetch(API + '/providers');
    if (res.ok) {
      connected = true;
      statusEl.textContent = 'Connected';
      enableInput(true);
      return true;
    }
  } catch(e) {}
  statusEl.textContent = 'Server not reachable on port ${port}';
  setTimeout(checkServer, 2000);
  return false;
}

async function loadProviders() {
  const res = await fetch(API + '/providers');
  if (!res.ok) return;
  const providers = await res.json();
  statusEl.textContent = 'Ready (' + providers.join(', ') + ')';
}

async function newSession() {
  if (eventSource) { eventSource.close(); eventSource = null; }
  messagesEl.innerHTML = '';
  currentDiv = null; currentText = '';
  statusEl.textContent = 'Creating session...';

  const agent = agentSelect.value;
  const res = await fetch(API + '/session', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({agent})
  });
  const data = await res.json();
  sessionId = data.id;

  eventSource = new EventSource(API + '/session/' + sessionId + '/stream');
  eventSource.addEventListener('token', e => onToken(e.data));
  eventSource.addEventListener('done', e => { finalize(); statusEl.textContent = 'Done'; enableInput(true); });
  eventSource.addEventListener('error', e => { finalize(); handleError(e.data); enableInput(true); });
  eventSource.addEventListener('tool_call', e => { finalize(); onToolCall(e.data); });
  eventSource.addEventListener('tool_result', e => addRaw('tool_result', e.data));
  eventSource.addEventListener('message', e => onSSEMessage(e.data));

  statusEl.textContent = 'Ready';
  enableInput(true);
}

function onToken(data) {
  try { const p = JSON.parse(data); if (p.delta) {
    if (!currentDiv) { currentDiv = document.createElement('div'); currentDiv.className = 'msg assistant'; messagesEl.appendChild(currentDiv); }
    currentText += p.delta;
    currentDiv.textContent = currentText;
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }} catch(e) {}
}

function onToolCall(data) {
  try { const p = JSON.parse(data); addRaw('tool_call', 'Tool: ' + p.tool); } catch(e) { addRaw('tool_call', data); }
}

function onSSEMessage(data) {
  try { const p = JSON.parse(data); if (p.role === 'user') addRaw('user', p.text); } catch(e) {}
  finalize();
}

function handleError(data) {
  try { const p = JSON.parse(data); addRaw('error', p.message || 'Error'); } catch(e) { addRaw('error', data); }
}

function finalize() { currentDiv = null; currentText = ''; }

function addRaw(cls, text) {
  const div = document.createElement('div'); div.className = 'msg ' + cls; div.textContent = text;
  messagesEl.appendChild(div); messagesEl.scrollTop = messagesEl.scrollHeight;
}

async function sendMessage() {
  const text = inputEl.value.trim();
  if (!text || !sessionId) return;
  inputEl.value = ''; enableInput(false);
  statusEl.textContent = 'Sending...';
  addRaw('user', text);
  try {
    await fetch(API + '/session/' + sessionId + '/message', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({text}) });
  } catch(e) { statusEl.textContent = 'Error: ' + e.message; enableInput(true); }
}

function enableInput(enabled) {
  sendBtn.disabled = !enabled; inputEl.disabled = !enabled;
  if (enabled) inputEl.focus();
}

inputEl.addEventListener('keydown', e => { if (e.key === 'Enter') sendMessage(); });
newSessionBtn.addEventListener('click', newSession);

checkServer().then(ok => { if (ok) { loadProviders(); newSession(); } });
</script>
</body>
</html>`;
    }

    dispose() {
        ChatPanel.currentPanel = undefined;
        this.panel.dispose();
        while (this.disposables.length) {
            this.disposables.pop()?.dispose();
        }
    }
}
