import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

export interface ToolCallTrace {
  toolName: string;
  query: string;
  explanation?: string;
  rowCount: number;
  executionTimeMs: number;
  success: boolean;
  subTable?: Record<string, any>[];
  message?: string;
  showDetails?: boolean;
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'agent';
  text: string;
  timestamp: Date;
  loading?: boolean;
  toolCalls?: ToolCallTrace[];
  error?: string;
  showAudit?: boolean; // Toggles the optional raw SQL & sub-table grounding view
}

export interface DbStatus {
  connected: boolean;
  host?: string;
  port?: number;
  database?: string;
  user?: string;
  totalProducts?: number;
  totalStockUnits?: number;
  tablesExist?: boolean;
  tableCount?: number;
  message?: string;
  error?: string;
}

export interface ConfigStatus {
  hasApiKey: boolean;
  maskedApiKey: string;
  model: string;
  serverPort: number;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private http = inject(HttpClient);
  readonly backendUrl = 'http://localhost:8080';

  // State Signals
  messages = signal<ChatMessage[]>([]);
  userInput = signal<string>('');
  isThinking = signal<boolean>(false);
  serverOnline = signal<boolean>(false);
  dbStatus = signal<DbStatus>({ connected: false });
  configStatus = signal<ConfigStatus>({ hasApiKey: false, maskedApiKey: '', model: 'gemini-3-flash-preview', serverPort: 8080 });

  // Modals & Panels
  showSettingsModal = signal<boolean>(false);
  showDbModal = signal<boolean>(false);
  showSchemaDrawer = signal<boolean>(false);
  dbTesting = signal<boolean>(false);
  dbFeedback = signal<{ success: boolean; message: string; data?: any } | null>(null);

  // Form bindings
  apiKeyInput = '';
  selectedModel = 'gemini-3-flash-preview';
  dbHostInput = 'localhost';
  dbPortInput = 3306;
  dbNameInput = 'inventory_db';
  dbUserInput = 'root';
  dbPasswordInput = '';

  // Schema data
  schemaSummary = signal<string>('');
  sampleInventory = signal<any[]>([]);

  // Sample prompt chips
  samplePrompts = [
    'Analyze product demand and tell me which items are likely to succeed or fail',
    'Which products are below reorder level in Chicago?',
    'What is the total value of inventory across all warehouses?',
    'List all Apple devices with prices and stock count',
    'Who supplies Cisco equipment and what is their contact info?',
    'Compare monitor stocks between West Coast and East Coast hubs'
  ];

  ngOnInit() {
    this.checkStatus();
    // Add initial welcoming message
    this.messages.set([
      {
        id: 'welcome',
        sender: 'agent',
        text: `### Welcome to Inventory Intelligence\n\nI am your enterprise **Inventory & Supply Chain Analyst**.\n\nYou can ask me **any dynamic business question** in natural language. I will analyze live database records from **MySQL**, detect stock anomalies or demand trends, and provide you with actionable executive insights.\n\n*All analyses are grounded on real database tables, with an optional audit inspector available if you wish to verify the underlying queries.*`,
        timestamp: new Date()
      }
    ]);
  }

  checkStatus() {
    // Check Core Java Server health
    this.http.get<any>(`${this.backendUrl}/api/health`).subscribe({
      next: () => this.serverOnline.set(true),
      error: () => this.serverOnline.set(false)
    });

    // Check DB status
    this.http.get<DbStatus>(`${this.backendUrl}/api/db/status`).subscribe({
      next: (status) => {
        this.dbStatus.set(status);
        if (status.host) this.dbHostInput = status.host;
        if (status.port) this.dbPortInput = status.port;
        if (status.database) this.dbNameInput = status.database;
        if (status.user) this.dbUserInput = status.user;
      },
      error: (err) => this.dbStatus.set({ connected: false, error: err.message })
    });

    // Check Config status
    this.http.get<ConfigStatus>(`${this.backendUrl}/api/config`).subscribe({
      next: (config) => {
        this.configStatus.set(config);
        this.selectedModel = config.model;
      },
      error: () => {}
    });
  }

  sendMessage(customPrompt?: string) {
    const promptToSend = (customPrompt || this.userInput()).trim();
    if (!promptToSend || this.isThinking()) return;

    const userMsgId = 'u-' + Date.now();
    const agentMsgId = 'a-' + Date.now();

    // Add user message
    this.messages.update(msgs => [
      ...msgs,
      { id: userMsgId, sender: 'user', text: promptToSend, timestamp: new Date() },
      { id: agentMsgId, sender: 'agent', text: '', timestamp: new Date(), loading: true }
    ]);

    this.userInput.set('');
    this.isThinking.set(true);

    const payload: any = { prompt: promptToSend };
    if (this.apiKeyInput.trim()) {
      payload.apiKey = this.apiKeyInput.trim();
    }

    this.http.post<any>(`${this.backendUrl}/api/chat`, payload).subscribe({
      next: (res) => {
        this.isThinking.set(false);
        const tools: ToolCallTrace[] = (res.toolCalls || []).map((t: any) => ({
          ...t,
          showDetails: false // Hidden by default for clean business presentation
        }));

        this.messages.update(msgs =>
          msgs.map(m => m.id === agentMsgId
            ? { ...m, text: res.answer || 'Analysis complete.', loading: false, toolCalls: tools, showAudit: false }
            : m
          )
        );
        this.scrollToBottom();
      },
      error: (err) => {
        this.isThinking.set(false);
        const errorDetail = err.error?.error || err.message || 'Unknown backend error';
        this.messages.update(msgs =>
          msgs.map(m => m.id === agentMsgId
            ? {
                ...m,
                loading: false,
                text: `### ⚠️ Agent Notice\n\n${errorDetail}\n\n*Please ensure MySQL is running on port 3306 and your Gemini API key is configured.*`,
                error: errorDetail
              }
            : m
          )
        );
        this.scrollToBottom();
      }
    });

    this.scrollToBottom();
  }

  toggleAuditView(msg: ChatMessage) {
    msg.showAudit = !msg.showAudit;
  }

  toggleToolDetails(trace: ToolCallTrace) {
    trace.showDetails = !trace.showDetails;
  }

  getTableKeys(row: Record<string, any>): string[] {
    return row ? Object.keys(row) : [];
  }

  saveApiKey() {
    if (!this.apiKeyInput.trim()) return;
    this.http.post<any>(`${this.backendUrl}/api/config`, {
      apiKey: this.apiKeyInput.trim(),
      model: this.selectedModel
    }).subscribe({
      next: (config) => {
        this.configStatus.set(config);
        this.showSettingsModal.set(false);
        this.apiKeyInput = '';
      },
      error: (err) => alert('Failed to save API Key: ' + err.message)
    });
  }

  openDbModal() {
    this.dbFeedback.set(null);
    this.dbTesting.set(false);
    this.showDbModal.set(true);
    // Refresh live DB status when opening the modal
    this.http.get<DbStatus>(`${this.backendUrl}/api/db/status`).subscribe({
      next: (status) => {
        this.dbStatus.set(status);
        if (status.host) this.dbHostInput = status.host;
        if (status.port) this.dbPortInput = status.port;
        if (status.database) this.dbNameInput = status.database;
        if (status.user) this.dbUserInput = status.user;
      }
    });
  }

  saveDbConfig() {
    this.dbTesting.set(true);
    this.dbFeedback.set(null);

    const payload = {
      host: this.dbHostInput.trim(),
      port: Number(this.dbPortInput),
      database: this.dbNameInput.trim(),
      user: this.dbUserInput.trim(),
      password: this.dbPasswordInput
    };

    this.http.post<DbStatus>(`${this.backendUrl}/api/db/config`, payload).subscribe({
      next: (status) => {
        this.dbTesting.set(false);
        this.dbStatus.set(status);

        if (status.connected) {
          const detail = status.tablesExist
            ? `Verified live database: ${status.totalProducts ?? 0} products, ${status.totalStockUnits ?? 0} stock units across ${status.tableCount ?? 0} tables.`
            : `Connected to MySQL successfully, but database '${status.database}' contains 0 tables. Run db/schema.sql in MySQL Workbench.`;
          this.dbFeedback.set({
            success: true,
            message: detail,
            data: status
          });
        } else {
          this.dbFeedback.set({
            success: false,
            message: status.error || 'Connection failed: Unable to establish connection to MySQL server.',
            data: status
          });
        }
      },
      error: (err) => {
        this.dbTesting.set(false);
        const errMsg = err.error?.error || err.message || 'Core Java Server connection error';
        this.dbFeedback.set({
          success: false,
          message: 'Server Error: ' + errMsg
        });
      }
    });
  }

  openSchemaDrawer() {
    this.showSchemaDrawer.set(true);
    this.http.get<any>(`${this.backendUrl}/api/inventory/schema`).subscribe({
      next: (res) => this.schemaSummary.set(res.schema),
      error: () => this.schemaSummary.set('Schema details unavailable.')
    });

    this.http.get<any>(`${this.backendUrl}/api/inventory/sample`).subscribe({
      next: (res) => {
        if (res.sampleInventory) this.sampleInventory.set(res.sampleInventory);
      },
      error: () => {}
    });
  }

  copyToClipboard(text: string) {
    navigator.clipboard.writeText(text);
  }

  clearChat() {
    this.messages.set([
      {
        id: 'welcome-new',
        sender: 'agent',
        text: '### Chat Cleared\n\nWhat business question would you like to explore across our inventory next?',
        timestamp: new Date()
      }
    ]);
  }

  /**
   * Formats raw markdown from Gemini into clean, styled executive HTML.
   */
  formatMarkdown(raw: string): string {
    if (!raw) return '';
    let html = raw;

    // Headers
    html = html.replace(/^### (.*$)/gim, '<h3 class="analysis-h3">$1</h3>');
    html = html.replace(/^## (.*$)/gim, '<h2 class="analysis-h2">$1</h2>');
    html = html.replace(/^# (.*$)/gim, '<h1 class="analysis-h1">$1</h1>');

    // Bold & Italic
    html = html.replace(/\*\*\*(.*?)\*\*\*/g, '<strong><em>$1</em></strong>');
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');

    // Inline Code
    html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');

    // Horizontal Rules
    html = html.replace(/^---$/gim, '<hr class="analysis-hr" />');

    // Bullet points
    html = html.replace(/^\s*[\*\-]\s+(.*$)/gim, '<li class="analysis-li">$1</li>');

    // Line breaks for paragraphs
    html = html.replace(/\n\n/g, '<div class="paragraph-gap"></div>');
    html = html.replace(/\n/g, '<br />');

    return html;
  }

  private scrollToBottom() {
    setTimeout(() => {
      const el = document.getElementById('chat-scroll-anchor');
      if (el) el.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  }
}
