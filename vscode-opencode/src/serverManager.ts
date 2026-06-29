import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as path from 'path';
import * as fs from 'fs';

export class ServerManager {
    private childProcess: cp.ChildProcess | null = null;
    private _running = false;
    private statusBarItem: vscode.StatusBarItem;

    get running() { return this._running; }

    constructor() {
        this.statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
        this.updateStatusBar('stopped');
        this.statusBarItem.show();
    }

    async start(): Promise<void> {
        if (this._running) {
            vscode.window.showInformationMessage('OpenCode server is already running');
            return;
        }

        const config = vscode.workspace.getConfiguration('opencode');
        const port = config.get<number>('serverPort', 8080);

        const jarPath = await this.findJar();
        if (!jarPath) {
            vscode.window.showErrorMessage(
                'OpenCode server JAR not found. Build the project first with `mvn package -DskipTests` ' +
                'or set `opencode.serverJar` in settings.'
            );
            return;
        }

        vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: 'Starting OpenCode server...',
        }, () => this.startProcess(jarPath, port));
    }

    private async startProcess(jarPath: string, port: number): Promise<void> {
        return new Promise((resolve) => {
            this.childProcess = cp.spawn('java', [
                '-jar', jarPath,
                '--server.port=' + port
            ], {
                stdio: ['ignore', 'pipe', 'pipe'],
                env: { ...process.env }
            });

            const startTimeout = setTimeout(() => {
                this._running = true;
                this.updateStatusBar('running');
                vscode.window.showInformationMessage(`OpenCode server started on port ${port}`);
                resolve();
            }, 8000);

            this.childProcess.stdout?.on('data', (data: Buffer) => {
                const text = data.toString();
                if (text.includes('Started') || text.includes('Tomcat started')) {
                    clearTimeout(startTimeout);
                    if (!this._running) {
                        this._running = true;
                        this.updateStatusBar('running');
                        vscode.window.showInformationMessage(`OpenCode server started on port ${port}`);
                        resolve();
                    }
                }
            });

            this.childProcess.stderr?.on('data', (data: Buffer) => {
                const text = data.toString();
                if (text.includes('Started') || text.includes('Tomcat started')) {
                    clearTimeout(startTimeout);
                    if (!this._running) {
                        this._running = true;
                        this.updateStatusBar('running');
                        resolve();
                    }
                }
            });

            this.childProcess.on('error', (err) => {
                clearTimeout(startTimeout);
                this._running = false;
                this.updateStatusBar('stopped');
                vscode.window.showErrorMessage(`Failed to start OpenCode server: ${err.message}`);
                resolve();
            });

            this.childProcess.on('exit', (code) => {
                clearTimeout(startTimeout);
                this._running = false;
                this.updateStatusBar('stopped');
                if (code !== 0 && code !== null) {
                    vscode.window.showWarningMessage(`OpenCode server exited with code ${code}`);
                }
                this.childProcess = null;
            });
        });
    }

    async stop(): Promise<void> {
        if (!this._running || !this.childProcess) return;

        this.childProcess.kill('SIGTERM');
        setTimeout(() => {
            if (this.childProcess) {
                this.childProcess.kill('SIGKILL');
            }
        }, 5000);

        this._running = false;
        this.updateStatusBar('stopped');
        this.childProcess = null;
        vscode.window.showInformationMessage('OpenCode server stopped');
    }

    private async findJar(): Promise<string | null> {
        const config = vscode.workspace.getConfiguration('opencode');
        const configured = config.get<string>('serverJar');
        if (configured && fs.existsSync(configured)) {
            return configured;
        }

        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (!workspaceFolders) return null;

        for (const folder of workspaceFolders) {
            const targetDir = path.join(folder.uri.fsPath, 'target');
            if (!fs.existsSync(targetDir)) continue;

            try {
                const files = fs.readdirSync(targetDir);
                const jar = files.find(f => f.endsWith('.jar') && !f.endsWith('-sources.jar') && !f.endsWith('-javadoc.jar'));
                if (jar) {
                    return path.join(targetDir, jar);
                }
            } catch { }
        }
        return null;
    }

    private updateStatusBar(state: 'running' | 'stopped') {
        if (state === 'running') {
            this.statusBarItem.text = '$(debug-start) OpenCode';
            this.statusBarItem.backgroundColor = undefined;
            this.statusBarItem.tooltip = 'OpenCode server is running';
        } else {
            this.statusBarItem.text = '$(debug-disconnect) OpenCode';
            this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
            this.statusBarItem.tooltip = 'OpenCode server is stopped — click to start';
            this.statusBarItem.command = 'opencode.startServer';
        }
    }

    dispose() {
        this.stop();
        this.statusBarItem.dispose();
    }
}
