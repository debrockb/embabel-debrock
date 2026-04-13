import React, { useState, useEffect, useCallback } from 'react';
import { getModels, saveModels, resetModels, newModelId } from '../modelConfig';
import './AdminDashboard.css';

function AdminDashboard() {
  const [activeTab, setActiveTab] = useState('prompts');
  const [prompts, setPrompts] = useState([]);
  const [costs, setCosts] = useState(null);
  const [targets, setTargets] = useState([]);
  const [editingPrompt, setEditingPrompt] = useState(null);
  const [editText, setEditText] = useState('');
  const [loading, setLoading] = useState(false);
  const [adminToken, setAdminToken] = useState(localStorage.getItem('matoe-admin-token') || '');

  const authHeaders = (extra = {}) => ({
    'Content-Type': 'application/json',
    ...(adminToken ? { 'X-Admin-Token': adminToken } : {}),
    ...extra,
  });

  const handleTokenChange = (e) => {
    const val = e.target.value;
    setAdminToken(val);
    localStorage.setItem('matoe-admin-token', val);
  };

  const fetchPrompts = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/prompts', { headers: authHeaders() });
      if (!res.ok) { console.warn('Prompts fetch returned', res.status); return; }
      const data = await res.json();
      setPrompts(data.prompts || []);
    } catch (err) { console.error('Failed to fetch prompts:', err); }
  }, [adminToken]); // eslint-disable-line react-hooks/exhaustive-deps

  const fetchCosts = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/costs?hours=24', { headers: authHeaders() });
      if (!res.ok) { console.warn('Costs fetch returned', res.status); return; }
      setCosts(await res.json());
    } catch (err) { console.error('Failed to fetch costs:', err); }
  }, [adminToken]); // eslint-disable-line react-hooks/exhaustive-deps

  const fetchTargets = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/search-targets', { headers: authHeaders() });
      if (!res.ok) { console.warn('Targets fetch returned', res.status); return; }
      setTargets(await res.json());
    } catch (err) { console.error('Failed to fetch targets:', err); }
  }, [adminToken]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    fetchPrompts();
    fetchCosts();
    fetchTargets();
  }, [fetchPrompts, fetchCosts, fetchTargets]);

  const handleSavePrompt = async (agentName) => {
    setLoading(true);
    try {
      await fetch(`/api/admin/prompts/${agentName}`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ promptText: editText, author: 'admin' }),
      });
      setEditingPrompt(null);
      setEditText('');
      fetchPrompts();
    } catch (err) { console.error('Failed to save prompt:', err); }
    setLoading(false);
  };

  const handleToggleTarget = async (target) => {
    try {
      await fetch(`/api/admin/search-targets/${target.id}`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify({ ...target, enabled: !target.enabled }),
      });
      fetchTargets();
    } catch (err) { console.error('Failed to toggle target:', err); }
  };

  return (
    <div className="admin-dashboard">
      <h2>Admin Dashboard</h2>

      <div className="admin-token-bar">
        <label>Admin Token:</label>
        <input
          type="password"
          value={adminToken}
          onChange={handleTokenChange}
          placeholder="Enter MATOE_ADMIN_TOKEN (leave blank if not configured)"
        />
      </div>

      <div className="admin-tabs">
        <button className={activeTab === 'prompts' ? 'active' : ''} onClick={() => setActiveTab('prompts')}>
          Prompts
        </button>
        <button className={activeTab === 'costs' ? 'active' : ''} onClick={() => setActiveTab('costs')}>
          LLM Costs
        </button>
        <button className={activeTab === 'targets' ? 'active' : ''} onClick={() => setActiveTab('targets')}>
          Search Targets
        </button>
        <button className={activeTab === 'models' ? 'active' : ''} onClick={() => setActiveTab('models')}>
          Models
        </button>
        <button className={activeTab === 'agents' ? 'active' : ''} onClick={() => setActiveTab('agents')}>
          Agents
        </button>
      </div>

      {activeTab === 'prompts' && (
        <div className="admin-section">
          <h3>Agent Prompts</h3>
          <p className="admin-hint">Edit prompts at runtime. Changes take effect immediately. Version history is preserved.</p>
          {prompts.map((p) => (
            <div key={p.agentName} className="prompt-card">
              <div className="prompt-header">
                <strong>{p.agentName}</strong>
                <span className="version-badge">{p.historyCount} version(s)</span>
                <button className="edit-btn" onClick={() => {
                  setEditingPrompt(p.agentName);
                  setEditText(p.activePrompt);
                }}>Edit</button>
              </div>
              {editingPrompt === p.agentName ? (
                <div className="prompt-editor">
                  <textarea
                    value={editText}
                    onChange={(e) => setEditText(e.target.value)}
                    rows={8}
                  />
                  <div className="editor-actions">
                    <button className="save-btn" onClick={() => handleSavePrompt(p.agentName)} disabled={loading}>
                      {loading ? 'Saving...' : 'Save New Version'}
                    </button>
                    <button className="cancel-btn" onClick={() => setEditingPrompt(null)}>Cancel</button>
                  </div>
                </div>
              ) : (
                <pre className="prompt-preview">{p.activePrompt.substring(0, 200)}...</pre>
              )}
            </div>
          ))}
        </div>
      )}

      {activeTab === 'costs' && costs && (
        <div className="admin-section">
          <h3>LLM Cost Monitoring (Last {costs.periodHours}h)</h3>
          <div className="cost-summary">
            <div className="cost-card total">
              <span className="cost-label">Total Cost</span>
              <span className="cost-value">${(costs.totalCostUsd || 0).toFixed(4)}</span>
            </div>
          </div>

          {costs.byAgent && costs.byAgent.length > 0 && (
            <>
              <h4>By Agent</h4>
              <table className="admin-table">
                <thead><tr><th>Agent</th><th>Calls</th><th>Cost</th></tr></thead>
                <tbody>
                  {costs.byAgent.map((row, i) => (
                    <tr key={i}>
                      <td>{row.agent}</td>
                      <td>{row.calls}</td>
                      <td>${(row.cost || 0).toFixed(4)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          {costs.byModel && costs.byModel.length > 0 && (
            <>
              <h4>By Model</h4>
              <table className="admin-table">
                <thead><tr><th>Model</th><th>Calls</th><th>Input Tokens</th><th>Output Tokens</th><th>Cost</th></tr></thead>
                <tbody>
                  {costs.byModel.map((row, i) => (
                    <tr key={i}>
                      <td>{row.model}</td>
                      <td>{row.calls}</td>
                      <td>{(row.inputTokens || 0).toLocaleString()}</td>
                      <td>{(row.outputTokens || 0).toLocaleString()}</td>
                      <td>${(row.cost || 0).toFixed(4)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          <button className="refresh-btn" onClick={fetchCosts}>Refresh</button>
        </div>
      )}

      {activeTab === 'targets' && (
        <div className="admin-section">
          <h3>Search Targets</h3>
          <p className="admin-hint">Enable/disable search sites per agent. Priority determines search order.</p>
          <table className="admin-table">
            <thead><tr><th>Agent</th><th>Site</th><th>Priority</th><th>Rate Limit</th><th>Enabled</th></tr></thead>
            <tbody>
              {targets.map((t) => (
                <tr key={t.id} className={t.enabled ? '' : 'disabled-row'}>
                  <td>{t.agentName}</td>
                  <td>{t.siteUrl}</td>
                  <td>{t.priority}</td>
                  <td>{t.rateLimitRpm} rpm</td>
                  <td>
                    <button
                      className={`toggle-btn ${t.enabled ? 'on' : 'off'}`}
                      onClick={() => handleToggleTarget(t)}
                    >
                      {t.enabled ? 'ON' : 'OFF'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {activeTab === 'models' && <ModelsTab />}
      {activeTab === 'agents' && <AgentsTab authHeaders={authHeaders} />}
    </div>
  );
}

/** Admin tab for managing the LLM model dropdown list (localStorage-backed). */
function ModelsTab() {
  const [models, setModels] = useState(getModels);
  const [adding, setAdding] = useState(false);
  const [editId, setEditId] = useState(null);
  const [form, setForm] = useState({ provider: '', modelId: '', displayName: '', roles: ['orchestrator', 'extractor'] });

  const persist = (next) => { setModels(next); saveModels(next); };

  const handleToggle = (id) => {
    persist(models.map((m) => m.id === id ? { ...m, enabled: !m.enabled } : m));
  };

  const handleDelete = (id) => {
    if (window.confirm('Remove this model from the list?')) {
      persist(models.filter((m) => m.id !== id));
    }
  };

  const handleReset = () => {
    if (window.confirm('Reset model list to factory defaults? Custom models will be lost.')) {
      const defaults = resetModels();
      setModels(defaults);
    }
  };

  const handleRoleToggle = (role) => {
    setForm((f) => ({
      ...f,
      roles: f.roles.includes(role) ? f.roles.filter((r) => r !== role) : [...f.roles, role],
    }));
  };

  const startAdd = () => {
    setForm({ provider: 'Local (LM Studio)', modelId: '', displayName: '', roles: ['orchestrator', 'extractor'] });
    setAdding(true);
    setEditId(null);
  };

  const startEdit = (m) => {
    setForm({ provider: m.provider, modelId: m.modelId, displayName: m.displayName, roles: [...m.roles] });
    setEditId(m.id);
    setAdding(false);
  };

  const handleSave = () => {
    if (!form.modelId.trim() || !form.displayName.trim()) return;
    if (editId) {
      persist(models.map((m) => m.id === editId ? { ...m, ...form } : m));
      setEditId(null);
    } else {
      persist([...models, { ...form, id: newModelId(), enabled: true }]);
      setAdding(false);
    }
    setForm({ provider: '', modelId: '', displayName: '', roles: ['orchestrator', 'extractor'] });
  };

  const handleCancel = () => { setAdding(false); setEditId(null); };

  const providers = [...new Set(models.map((m) => m.provider))];

  return (
    <div className="admin-section">
      <h3>LLM Model Configuration</h3>
      <p className="admin-hint">
        Add, edit, or disable models shown in the trip planner dropdowns.
        Changes are saved in your browser and take effect immediately.
      </p>

      <div className="model-actions-bar">
        <button className="save-btn" onClick={startAdd}>+ Add Model</button>
        <button className="cancel-btn" onClick={handleReset}>Reset to Defaults</button>
      </div>

      {(adding || editId) && (
        <div className="model-form">
          <div className="form-row">
            <label>Provider Group
              <input
                list="provider-suggestions"
                value={form.provider}
                onChange={(e) => setForm({ ...form, provider: e.target.value })}
                placeholder="e.g. Local (LM Studio)"
              />
              <datalist id="provider-suggestions">
                {providers.map((p) => <option key={p} value={p} />)}
                <option value="Local (LM Studio)" />
                <option value="Local (Ollama)" />
                <option value="Anthropic" />
                <option value="OpenRouter" />
              </datalist>
            </label>
            <label>Model ID (sent to backend)
              <input
                value={form.modelId}
                onChange={(e) => setForm({ ...form, modelId: e.target.value })}
                placeholder="e.g. lmstudio/qwen3.5:9b"
              />
            </label>
            <label>Display Name
              <input
                value={form.displayName}
                onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                placeholder="e.g. Qwen 3.5 9B (LM Studio)"
              />
            </label>
          </div>
          <div className="form-row">
            <label className="role-check">
              <input type="checkbox" checked={form.roles.includes('orchestrator')} onChange={() => handleRoleToggle('orchestrator')} />
              Orchestrator
            </label>
            <label className="role-check">
              <input type="checkbox" checked={form.roles.includes('extractor')} onChange={() => handleRoleToggle('extractor')} />
              Extractor
            </label>
            <button className="save-btn" onClick={handleSave}>{editId ? 'Update' : 'Add'}</button>
            <button className="cancel-btn" onClick={handleCancel}>Cancel</button>
          </div>
        </div>
      )}

      <table className="admin-table">
        <thead>
          <tr><th>Provider</th><th>Display Name</th><th>Model ID</th><th>Roles</th><th>Enabled</th><th></th></tr>
        </thead>
        <tbody>
          {models.map((m) => (
            <tr key={m.id} className={m.enabled ? '' : 'disabled-row'}>
              <td>{m.provider}</td>
              <td>{m.displayName}</td>
              <td><code>{m.modelId}</code></td>
              <td>{m.roles.join(', ')}</td>
              <td>
                <button className={`toggle-btn ${m.enabled ? 'on' : 'off'}`} onClick={() => handleToggle(m.id)}>
                  {m.enabled ? 'ON' : 'OFF'}
                </button>
              </td>
              <td>
                <button className="edit-btn" onClick={() => startEdit(m)}>Edit</button>{' '}
                <button className="cancel-btn" onClick={() => handleDelete(m.id)}>Del</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Admin tab for viewing/editing/creating agent configurations (DB-backed). */
function AgentsTab({ authHeaders }) {
  const [agents, setAgents] = useState([]);
  const [editId, setEditId] = useState(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    agentName: '', displayName: '', description: '', promptTemplate: '',
    searchSites: '', resultType: 'accommodation', modelRole: 'extractor',
    resultSchema: '', enabled: true,
  });
  const [loading, setLoading] = useState(false);

  const fetchAgents = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/agents', { headers: authHeaders() });
      if (res.ok) setAgents(await res.json());
    } catch (err) { console.error('Failed to fetch agents:', err); }
  }, [authHeaders]);

  useEffect(() => { fetchAgents(); }, [fetchAgents]);

  const startCreate = () => {
    setForm({
      agentName: '', displayName: '', description: '', promptTemplate: '',
      searchSites: '', resultType: 'accommodation', modelRole: 'extractor',
      resultSchema: '', enabled: true,
    });
    setCreating(true);
    setEditId(null);
  };

  const startEdit = (a) => {
    setForm({
      agentName: a.agentName, displayName: a.displayName || '', description: a.description || '',
      promptTemplate: a.promptTemplate || '', searchSites: a.searchSites || '',
      resultType: a.resultType || 'accommodation', modelRole: a.modelRole || 'extractor',
      resultSchema: a.resultSchema || '', enabled: a.enabled,
    });
    setEditId(a.id);
    setCreating(false);
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      if (editId) {
        await fetch(`/api/admin/agents/${editId}`, {
          method: 'PUT', headers: authHeaders(), body: JSON.stringify(form),
        });
      } else {
        await fetch('/api/admin/agents', {
          method: 'POST', headers: authHeaders(), body: JSON.stringify(form),
        });
      }
      setEditId(null);
      setCreating(false);
      fetchAgents();
    } catch (err) { console.error('Failed to save agent:', err); }
    setLoading(false);
  };

  const handleToggle = async (agent) => {
    await fetch(`/api/admin/agents/${agent.id}`, {
      method: 'PUT', headers: authHeaders(),
      body: JSON.stringify({ ...agent, enabled: !agent.enabled }),
    });
    fetchAgents();
  };

  const handleDelete = async (agent) => {
    if (!window.confirm(`Delete custom agent "${agent.displayName}"?`)) return;
    await fetch(`/api/admin/agents/${agent.id}`, {
      method: 'DELETE', headers: authHeaders(),
    });
    fetchAgents();
  };

  const resultTypes = ['accommodation', 'transport', 'attraction', 'intelligence'];
  const modelRoles = ['orchestrator', 'extractor'];

  return (
    <div className="admin-section">
      <h3>Agent Configuration</h3>
      <p className="admin-hint">
        View and edit all agents. Built-in agents can be configured but not deleted.
        Create custom agents that run the same browser-first / LLM-fallback pipeline.
      </p>

      <div className="model-actions-bar">
        <button className="save-btn" onClick={startCreate}>+ New Custom Agent</button>
      </div>

      {(creating || editId) && (
        <div className="model-form">
          <div className="form-row">
            <label>Agent Name (unique key)
              <input value={form.agentName} disabled={!!editId}
                onChange={(e) => setForm({ ...form, agentName: e.target.value })}
                placeholder="e.g. spa-agent" />
            </label>
            <label>Display Name
              <input value={form.displayName}
                onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                placeholder="e.g. Spa & Wellness Agent" />
            </label>
          </div>
          <div className="form-row">
            <label>Description
              <input value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                placeholder="What this agent searches for" />
            </label>
            <label>Result Type
              <select value={form.resultType}
                onChange={(e) => setForm({ ...form, resultType: e.target.value })}>
                {resultTypes.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </label>
            <label>Model Role
              <select value={form.modelRole}
                onChange={(e) => setForm({ ...form, modelRole: e.target.value })}>
                {modelRoles.map((r) => <option key={r} value={r}>{r}</option>)}
              </select>
            </label>
          </div>
          <div className="form-row">
            <label style={{flex: 2}}>Search Sites (comma-separated)
              <input value={form.searchSites}
                onChange={(e) => setForm({ ...form, searchSites: e.target.value })}
                placeholder="e.g. tripadvisor.com,yelp.com" />
            </label>
          </div>
          <label>Prompt Template (use &#123;&#123;destination&#125;&#125;, &#123;&#123;startDate&#125;&#125;, &#123;&#123;adults&#125;&#125;, etc.)
            <textarea rows={6} value={form.promptTemplate}
              onChange={(e) => setForm({ ...form, promptTemplate: e.target.value })}
              placeholder="You are a travel expert. Find..." style={{width:'100%', fontFamily:'monospace', fontSize:'0.85rem'}} />
          </label>
          <label>Result Schema (JSON hint for browser extraction)
            <textarea rows={3} value={form.resultSchema}
              onChange={(e) => setForm({ ...form, resultSchema: e.target.value })}
              placeholder='e.g. [{"name":"string","price":"number","rating":"number","bookingUrl":"string"}]'
              style={{width:'100%', fontFamily:'monospace', fontSize:'0.85rem'}} />
          </label>
          <div className="form-row" style={{marginTop:'0.75rem'}}>
            <button className="save-btn" onClick={handleSave} disabled={loading}>
              {loading ? 'Saving...' : editId ? 'Update Agent' : 'Create Agent'}
            </button>
            <button className="cancel-btn" onClick={() => { setEditId(null); setCreating(false); }}>Cancel</button>
          </div>
        </div>
      )}

      <div className="agent-grid">
        {agents.map((a) => (
          <div key={a.id} className={`agent-card ${a.enabled ? '' : 'disabled-agent'}`}>
            <div className="agent-card-header">
              <strong>{a.displayName || a.agentName}</strong>
              <span className={`agent-type-badge ${a.resultType}`}>{a.resultType}</span>
              {a.builtIn && <span className="built-in-badge">built-in</span>}
            </div>
            <p className="agent-desc">{a.description}</p>
            <div className="agent-meta">
              <span>Model: {a.modelRole}</span>
              <span>Sites: {a.searchSites ? a.searchSites.split(',').length : 0}</span>
            </div>
            {a.promptTemplate && (
              <pre className="prompt-preview">{a.promptTemplate.substring(0, 120)}...</pre>
            )}
            <div className="agent-card-actions">
              <button className={`toggle-btn ${a.enabled ? 'on' : 'off'}`} onClick={() => handleToggle(a)}>
                {a.enabled ? 'ON' : 'OFF'}
              </button>
              <button className="edit-btn" onClick={() => startEdit(a)}>Edit</button>
              {!a.builtIn && (
                <button className="cancel-btn" onClick={() => handleDelete(a)}>Delete</button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default AdminDashboard;
