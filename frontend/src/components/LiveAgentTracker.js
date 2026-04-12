import React from 'react';
import './LiveAgentTracker.css';

function LiveAgentTracker({ agents, isLoading }) {
  const getStatusEmoji = (status) => {
    switch (status) {
      case 'idle':
        return '💤';
      case 'deployed':
        return '🚀';
      case 'searching':
        return '🔍';
      case 'analyzing':
        return '🧠';
      case 'completed':
        return '✅';
      case 'error':
        return '❌';
      default:
        return '⏳';
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'idle':
        return '#555';
      case 'deployed':
        return '#ffc107';
      case 'searching':
        return '#2196f3';
      case 'analyzing':
        return '#9c27b0';
      case 'completed':
        return '#4caf50';
      case 'error':
        return '#f44336';
      default:
        return '#999';
    }
  };

  return (
    <div className="live-tracker">
      <h2>📊 Live Agent Tracker</h2>

      {!isLoading && agents.length === 0 ? (
        <div className="tracker-empty">
          <p>Agents will appear here when you plan a trip...</p>
        </div>
      ) : (
        <div className="agents-list">
          {agents.map((agent, idx) => (
            <div key={idx} className="agent-card">
              <div className="agent-header">
                <span className="agent-emoji">{getStatusEmoji(agent.status)}</span>
                <h3>{agent.name}</h3>
                <span className="agent-status" style={{ color: getStatusColor(agent.status) }}>
                  {agent.status}
                </span>
              </div>
              <div className="progress-bar">
                <div
                  className="progress-fill"
                  style={{
                    width: `${agent.progress}%`,
                    backgroundColor: getStatusColor(agent.status),
                  }}
                ></div>
              </div>
              <span className="progress-label">{agent.progress}%</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default LiveAgentTracker;
