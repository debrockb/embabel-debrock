import React, { useState, useRef, useCallback } from 'react';
import './App.css';
import MissionControl from './components/MissionControl';
import LiveAgentTracker from './components/LiveAgentTracker';
import ItineraryCanvas from './components/ItineraryCanvas';
import AdminDashboard from './components/AdminDashboard';

const INITIAL_AGENTS = [
  { name: 'Orchestrator',       status: 'idle', progress: 0 },
  { name: 'Country Specialist', status: 'idle', progress: 0 },
  { name: 'Weather Agent',      status: 'idle', progress: 0 },
  { name: 'Currency Agent',     status: 'idle', progress: 0 },
  { name: 'Review Agent',       status: 'idle', progress: 0 },
  { name: 'Hotel Agent',        status: 'idle', progress: 0 },
  { name: 'B&B Agent',          status: 'idle', progress: 0 },
  { name: 'Apartment Agent',    status: 'idle', progress: 0 },
  { name: 'Hostel Agent',       status: 'idle', progress: 0 },
  { name: 'Flight Agent',       status: 'idle', progress: 0 },
  { name: 'Car/Bus Agent',      status: 'idle', progress: 0 },
  { name: 'Train Agent',        status: 'idle', progress: 0 },
  { name: 'Ferry Agent',        status: 'idle', progress: 0 },
  { name: 'Attractions Agent',  status: 'idle', progress: 0 },
];

function App() {
  const [itinerary, setItinerary]   = useState(null);
  const [agents, setAgents]         = useState(INITIAL_AGENTS);
  const [isLoading, setIsLoading]   = useState(false);
  const [error, setError]           = useState(null);
  const [activeView, setActiveView] = useState('planner');  // 'planner' | 'admin'
  const eventSourceRef              = useRef(null);

  const updateAgent = useCallback((name, status, progress) => {
    setAgents(prev => prev.map(a =>
      a.name === name ? { ...a, status, progress } : a
    ));
  }, []);

  const closeEventSource = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  };

  const handlePlanTrip = async (travelRequest) => {
    setIsLoading(true);
    setItinerary(null);
    setError(null);
    setAgents(INITIAL_AGENTS);
    closeEventSource();

    const sessionId = `sess-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

    // Open SSE stream first
    const es = new EventSource(`/api/travel/progress/${sessionId}`);
    eventSourceRef.current = es;

    es.addEventListener('agent-progress', (e) => {
      try {
        const evt = JSON.parse(e.data);
        updateAgent(evt.agentName, evt.status, evt.progress);
      } catch (_) {}
    });

    es.addEventListener('complete', () => closeEventSource());
    es.onerror = () => closeEventSource();

    try {
      const response = await fetch(`/api/travel/plan?sessionId=${sessionId}`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify(travelRequest),
      });

      if (!response.ok) {
        const text = await response.text();
        throw new Error(`Backend error ${response.status}: ${text}`);
      }

      const data = await response.json();
      setItinerary(data);
      setAgents(prev => prev.map(a =>
        a.status !== 'completed' ? { ...a, status: 'completed', progress: 100 } : a
      ));
    } catch (err) {
      console.error('Trip planning failed:', err);
      setError(err.message || 'Failed to plan trip. Check backend connectivity.');
      setAgents(prev => prev.map(a =>
        a.status !== 'completed' ? { ...a, status: 'error', progress: a.progress } : a
      ));
    } finally {
      setIsLoading(false);
      closeEventSource();
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>M.A.T.O.E</h1>
        <p>Multi-Agent Travel Orchestration Engine</p>
        <nav className="app-nav">
          <button
            className={activeView === 'planner' ? 'active' : ''}
            onClick={() => setActiveView('planner')}
          >Trip Planner</button>
          <button
            className={activeView === 'admin' ? 'active' : ''}
            onClick={() => setActiveView('admin')}
          >Admin</button>
        </nav>
      </header>

      <main className="app-main">
        {error && (
          <div className="error-banner">
            <strong>Error:</strong> {error}
            <button onClick={() => setError(null)}>Dismiss</button>
          </div>
        )}

        {activeView === 'planner' && (
          <>
            <div className="dashboard">
              <section className="control-panel">
                <MissionControl onPlanTrip={handlePlanTrip} isLoading={isLoading} />
              </section>
              <section className="tracker-panel">
                <LiveAgentTracker agents={agents} isLoading={isLoading} />
              </section>
            </div>

            {itinerary && (
              <section className="itinerary-panel">
                <ItineraryCanvas itinerary={itinerary} />
              </section>
            )}
          </>
        )}

        {activeView === 'admin' && (
          <section className="admin-panel">
            <AdminDashboard />
          </section>
        )}
      </main>
    </div>
  );
}

export default App;
