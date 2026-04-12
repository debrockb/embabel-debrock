import React, { useState } from 'react';
import './ItineraryCanvas.css';

function ItineraryCanvas({ itinerary }) {
  const [expandedSection, setExpandedSection] = useState('accommodations');
  const [compareMode, setCompareMode] = useState(false);

  const handleDownload = () => {
    const blob = new Blob([JSON.stringify(itinerary, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `matoe-itinerary-${itinerary.destination.replace(/\s+/g, '-').toLowerCase()}-${itinerary.id}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  if (!itinerary) {
    return <div className="itinerary-canvas">No itinerary data</div>;
  }

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
  };

  return (
    <div className="itinerary-canvas">
      <h2>Your Unforgettable Itinerary</h2>

      <div className="itinerary-header">
        <div className="destination-info">
          <h3>{itinerary.destination}</h3>
          <p>
            {formatDate(itinerary.startDate)} → {formatDate(itinerary.endDate)} &bull; {itinerary.guestCount} guest(s)
          </p>
        </div>
        <div className="trip-cost">
          <span className="cost-label">Total Estimated Cost</span>
          <h4>${itinerary.totalEstimatedCost.toFixed(2)}</h4>
        </div>
      </div>

      {itinerary.regionInsights && Object.keys(itinerary.regionInsights).length > 0 && (
        <section className="insights-section">
          <h3>Regional Insights</h3>
          <div className="insights-grid">
            {Object.entries(itinerary.regionInsights).map(([key, value]) => (
              <div key={key} className="insight-card">
                <span className="insight-label">
                  {key.replace(/([A-Z])/g, ' $1').toLowerCase()}
                </span>
                <span className="insight-value">{String(value)}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="accommodations-section">
        <div className="section-header">
          <h3
            onClick={() => setExpandedSection(expandedSection === 'accommodations' ? '' : 'accommodations')}
            className="section-title"
          >
            Accommodations ({itinerary.accommodations.length})
          </h3>
          <button
            className={`compare-btn ${compareMode ? 'active' : ''}`}
            onClick={() => setCompareMode(!compareMode)}
          >
            {compareMode ? 'List View' : 'Compare Tiers'}
          </button>
        </div>

        {expandedSection === 'accommodations' && (
          <div className={`items-grid ${compareMode ? 'compare-grid' : ''}`}>
            {compareMode
              ? ['budget', 'standard', 'luxury'].map((tier) => {
                  const tierItems = itinerary.accommodations.filter(a => a.tier === tier);
                  return tierItems.length > 0 ? (
                    <div key={tier} className="compare-column">
                      <div className={`compare-tier-header ${tier}`}>{tier.toUpperCase()}</div>
                      {tierItems.map((acc) => <AccommodationCard key={acc.id} acc={acc} />)}
                    </div>
                  ) : null;
                })
              : itinerary.accommodations.map((acc) => <AccommodationCard key={acc.id} acc={acc} />)
            }
          </div>
        )}
      </section>

      <section className="transport-section">
        <h3
          onClick={() => setExpandedSection(expandedSection === 'transport' ? '' : 'transport')}
          className="section-title"
        >
          Transport ({itinerary.transport.length})
        </h3>

        {expandedSection === 'transport' && (
          <div className="items-grid">
            {itinerary.transport.map((t) => <TransportCard key={t.id} t={t} />)}
          </div>
        )}
      </section>

      <div className="itinerary-actions">
        <button className="action-btn download-btn" onClick={handleDownload}>
          Download JSON
        </button>
      </div>
    </div>
  );
}

function AccommodationCard({ acc }) {
  return (
    <div className={`option-card ${acc.tier}`}>
      <div className="card-header">
        <h4>{acc.name}</h4>
        <span className="tier-badge">{acc.tier}</span>
      </div>
      <p className="location">{acc.location}</p>
      <div className="details">
        <span className="rating">★ {acc.rating}</span>
        <span className="price">${acc.pricePerNight}/night</span>
      </div>
      <p className="amenities">{acc.amenities && acc.amenities.join(', ')}</p>
      {acc.bookingUrl && (
        <a href={acc.bookingUrl} target="_blank" rel="noopener noreferrer" className="book-btn">
          Book →
        </a>
      )}
    </div>
  );
}

function TransportCard({ t }) {
  const typeIcon = t.type === 'flight' ? '✈' : t.type === 'car' ? '🚗' : '🚌';
  return (
    <div className={`option-card ${t.tier}`}>
      <div className="card-header">
        <h4>{t.provider}</h4>
        <span className="tier-badge">{t.tier}</span>
      </div>
      <p className="type">{typeIcon} {t.type}</p>
      <div className="details">
        <span>{t.departureTime} → {t.arrivalTime}</span>
        <span>Duration: {t.duration}</span>
      </div>
      {t.stops > 0 && <p className="stops">{t.stops} stop(s)</p>}
      <div className="price">${t.price}</div>
      {t.bookingUrl && (
        <a href={t.bookingUrl} target="_blank" rel="noopener noreferrer" className="book-btn">
          Book →
        </a>
      )}
    </div>
  );
}

export default ItineraryCanvas;
