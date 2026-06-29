import * as vscode from 'vscode';
import { ServerManager } from './serverManager';
import { ChatPanel } from './chatPanel';

let serverManager: ServerManager | undefined;

export function activate(context: vscode.ExtensionContext) {
    serverManager = new ServerManager();

    context.subscriptions.push(
        vscode.commands.registerCommand('opencode.chat', () => {
            if (!serverManager?.running) {
                vscode.window.showWarningMessage('OpenCode server is not running. Start it first with "OpenCode: Start Server".');
                return;
            }
            const config = vscode.workspace.getConfiguration('opencode');
            const port = config.get<number>('serverPort', 8080);
            ChatPanel.show(port);
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('opencode.startServer', async () => {
            await serverManager?.start();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('opencode.stopServer', async () => {
            await serverManager?.stop();
        })
    );

    const config = vscode.workspace.getConfiguration('opencode');
    if (config.get<boolean>('autoStartServer', true)) {
        setTimeout(() => serverManager?.start(), 1000);
    }
}

export function deactivate() {
    serverManager?.dispose();
}
