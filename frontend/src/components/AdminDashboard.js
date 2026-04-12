import React, { useState, useEffect, useCallback } from 'react';
import './AdminDashboard.css';

function AdminDashboard() {
  const [activeTab, setActiveTab] = useState('prompts');
  const [prompts, setPrompts] = useState([]);
  const [costs, setCosts] = useState(null);
  const [targets, setTargets] = useState([]);
  const [editingPrompt, setEditingPrompt] = useState(null);
  const [editText, setEditText] = useState('');
  const [loading, setLoading] = useState(false);

  const fetchPrompts = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/prompts');
      const data = await res.json();
      setPrompts(data.prompts || []);
    } catch (err) { console.error('Failed to fetch prompts:', err); }
  }, []);

  const fetchCosts = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/costs?hours=24');
      setCosts(await res.json());
    } catch (err) { console.error('Failed to fetch costs:', err); }
  }, []);

  const fetchTargets = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/search-targets');
      setTargets(await res.json());
    } catch (err) { console.error('Failed to fetch targets:', err); }
  }, []);

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
        headers: { 'Content-Type': 'application/json' },
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
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...target, enabled: !target.enabled }),
      });
      fetchTargets();
    } catch (err) { console.error('Failed to toggle target:', err); }
  };

  return (
    <div className="admin-dashboard">
      <h2>Admin Dashboard</h2>

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
    </div>
  );
}

export default AdminDashboard;
