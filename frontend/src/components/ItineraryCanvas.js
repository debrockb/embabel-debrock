import React, { useState } from 'react';
import './ItineraryCanvas.css';
import DestinationMap from './DestinationMap';

function ItineraryCanvas({ itinerary }) {
  const [expandedSection, setExpandedSection] = useState('variants');
  const [compareMode, setCompareMode] = useState(false);
  const [selectedVariant, setSelectedVariant] = useState(null);

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

  // PDF export — calls the backend endpoint so that rendering (layout, fonts,
  // synthetic warnings) stays consistent across clients. The backend serves
  // application/pdf with Content-Disposition: attachment, so we just navigate
  // the browser to the URL and let it download.
  const handleDownloadPdf = () => {
    if (!itinerary.id) {
      alert('PDF download requires a saved itinerary (with an id).');
      return;
    }
    const url = `/api/travel/itineraries/${encodeURIComponent(itinerary.id)}/pdf`;
    const a = document.createElement('a');
    a.href = url;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  if (!itinerary) return <div className="itinerary-canvas">No itinerary data</div>;

  const formatDate = (dateString) => {
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
    } catch { return dateString; }
  };

  const hasVariants = itinerary.variants && itinerary.variants.length > 0;
  const hasAttractions = itinerary.attractions && itinerary.attractions.length > 0;
  const hasWeather = itinerary.weatherForecast && Object.keys(itinerary.weatherForecast).length > 0;
  const hasCurrency = itinerary.currencyInfo && Object.keys(itinerary.currencyInfo).length > 0;

  return (
    <div className="itinerary-canvas">
      <h2>Your Unforgettable Itinerary</h2>

      {/* Header */}
      <div className="itinerary-header">
        <div className="destination-info">
          <h3>{itinerary.destination}</h3>
          <p>{formatDate(itinerary.startDate)} &rarr; {formatDate(itinerary.endDate)} &bull; {itinerary.guestCount} guest(s)</p>
        </div>
        <div className="trip-cost">
          <span className="cost-label">Estimated Cost (Standard)</span>
          <h4>&euro;{itinerary.totalEstimatedCost.toFixed(0)}</h4>
        </div>
      </div>

      {/* Destination Map */}
      <DestinationMap destination={itinerary.destination} attractions={itinerary.attractions} accommodations={itinerary.accommodations} />

      {/* Variants (Budget / Standard / Luxury) */}
      {hasVariants && (
        <section className="variants-section">
          <h3 className="section-title" onClick={() => setExpandedSection(expandedSection === 'variants' ? '' : 'variants')}>
            Itinerary Variants ({itinerary.variants.length})
          </h3>
          {expandedSection === 'variants' && (
            <div className="variants-grid">
              {itinerary.variants.map((v, i) => (
                <div
                  key={i}
                  className={`variant-card ${v.tier} ${selectedVariant === i ? 'selected' : ''}`}
                  onClick={() => setSelectedVariant(selectedVariant === i ? null : i)}
                >
                  <div className={`variant-header ${v.tier}`}>
                    {v.tier.toUpperCase()}
                  </div>
                  <div className="variant-cost">&euro;{(v.totalEstimatedCost || 0).toFixed(0)}</div>
                  {v.highlights && v.highlights.length > 0 && (
                    <ul className="variant-highlights">
                      {v.highlights.map((h, j) => <li key={j}>{h}</li>)}
                    </ul>
                  )}
                  {v.tradeoffs && <p className="variant-tradeoffs">{v.tradeoffs}</p>}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* Day-by-Day (for selected variant) */}
      {selectedVariant !== null && hasVariants && itinerary.variants[selectedVariant]?.dayByDay?.length > 0 && (
        <section className="daybyday-section">
          <h3 className="section-title">
            Day-by-Day: {itinerary.variants[selectedVariant].tier.toUpperCase()} Variant
          </h3>
          <div className="days-timeline">
            {itinerary.variants[selectedVariant].dayByDay.map((day, i) => (
              <div key={i} className="day-card">
                <div className="day-number">Day {day.dayNumber}</div>
                <div className="day-content">
                  <h4>{day.title}</h4>
                  {day.date && <span className="day-date">{formatDate(day.date)}</span>}
                  {day.summary && <p className="day-summary">{day.summary}</p>}
                  {day.morningActivities?.length > 0 && (
                    <div className="day-block">
                      <strong>Morning</strong>
                      <ul>{day.morningActivities.map((a, j) => <li key={j}>{a}</li>)}</ul>
                    </div>
                  )}
                  {day.afternoonActivities?.length > 0 && (
                    <div className="day-block">
                      <strong>Afternoon</strong>
                      <ul>{day.afternoonActivities.map((a, j) => <li key={j}>{a}</li>)}</ul>
                    </div>
                  )}
                  {day.eveningActivities?.length > 0 && (
                    <div className="day-block">
                      <strong>Evening</strong>
                      <ul>{day.eveningActivities.map((a, j) => <li key={j}>{a}</li>)}</ul>
                    </div>
                  )}
                  {day.meals?.length > 0 && (
                    <div className="day-block">
                      <strong>Meals</strong>
                      <ul>{day.meals.map((m, j) => <li key={j}>{m}</li>)}</ul>
                    </div>
                  )}
                  {day.transportNotes && <p className="day-transport">{day.transportNotes}</p>}
                  {day.estimatedDayCost > 0 && (
                    <span className="day-cost">&euro;{day.estimatedDayCost.toFixed(0)}/day</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Regional Insights */}
      {itinerary.regionInsights && Object.keys(itinerary.regionInsights).length > 0 && (
        <section className="insights-section">
          <h3 className="section-title" onClick={() => setExpandedSection(expandedSection === 'insights' ? '' : 'insights')}>
            Regional Insights
          </h3>
          {expandedSection === 'insights' && (
            <div className="insights-grid">
              {Object.entries(itinerary.regionInsights)
                .filter(([k]) => k !== 'reviewSummary')
                .map(([key, value]) => (
                <div key={key} className="insight-card">
                  <span className="insight-label">{key.replace(/([A-Z])/g, ' $1').toLowerCase()}</span>
                  <span className="insight-value">{typeof value === 'object' ? JSON.stringify(value) : String(value)}</span>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* Weather & Currency */}
      {(hasWeather || hasCurrency) && (
        <section className="info-section">
          <h3 className="section-title" onClick={() => setExpandedSection(expandedSection === 'info' ? '' : 'info')}>
            Weather & Currency
          </h3>
          {expandedSection === 'info' && (
            <div className="info-grid">
              {hasWeather && (
                <div className="info-card">
                  <h4>Weather</h4>
                  {Object.entries(itinerary.weatherForecast).map(([k, v]) => (
                    <div key={k} className="info-row">
                      <span className="info-label">{k.replace(/([A-Z])/g, ' $1')}</span>
                      <span>{Array.isArray(v) ? v.join(', ') : String(v)}</span>
                    </div>
                  ))}
                </div>
              )}
              {hasCurrency && (
                <div className="info-card">
                  <h4>Currency</h4>
                  {Object.entries(itinerary.currencyInfo).map(([k, v]) => (
                    <div key={k} className="info-row">
                      <span className="info-label">{k.replace(/([A-Z])/g, ' $1')}</span>
                      <span>{String(v)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </section>
      )}

      {/* Attractions */}
      {hasAttractions && (
        <section className="attractions-section">
          <h3 className="section-title" onClick={() => setExpandedSection(expandedSection === 'attractions' ? '' : 'attractions')}>
            Attractions & Experiences ({itinerary.attractions.length})
          </h3>
          {expandedSection === 'attractions' && (
            <div className="items-grid">
              {itinerary.attractions.map((attr) => (
                <div key={attr.id} className={`option-card ${attr.tier}`}>
                  <div className="card-header">
                    <h4>{attr.name}</h4>
                    <span className="tier-badge">{attr.tier}</span>
                  </div>
                  <p className="type">{attr.category}</p>
                  {attr.description && <p className="description">{attr.description}</p>}
                  <div className="details">
                    <span className="rating">{attr.rating > 0 ? `★ ${attr.rating}` : ''}</span>
                    <span className="price">&euro;{attr.price}</span>
                    <span>{attr.duration}</span>
                  </div>
                  {attr.tags?.length > 0 && (
                    <div className="tags">{attr.tags.map((t, i) => <span key={i} className="tag">{t}</span>)}</div>
                  )}
                  {attr.source === 'llm' && <span className="synthetic-warning">AI estimate</span>}
                  <a
                    href={attr.bookingUrl || `https://www.viator.com/searchResults/all?text=${encodeURIComponent((attr.name || '') + ' ' + (itinerary.destination || ''))}`}
                    target="_blank" rel="noopener noreferrer" className="book-btn"
                  >{attr.source === 'llm' ? 'Search' : 'Book'} &rarr;</a>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* Accommodations */}
      <section className="accommodations-section">
        <div className="section-header">
          <h3 className="section-title" onClick={() => setExpandedSection(expandedSection === 'accommodations' ? '' : 'accommodations')}>
            Accommodations ({itinerary.accommodations.length})
          </h3>
          <button className={`compare-btn ${compareMode ? 'active' : ''}`} onClick={() => setCompareMode(!compareMode)}>
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

      {/* Transport */}
      <section className="transport-section">
        <h3 className="section-title" onClick={() => setExpandedSection(expandedSection === 'transport' ? '' : 'transport')}>
          Transport ({itinerary.transport.length})
        </h3>
        {expandedSection === 'transport' && (
          <div className="items-grid">
            {itinerary.transport.map((t) => <TransportCard key={t.id} t={t} />)}
          </div>
        )}
      </section>

      {/* Actions */}
      <div className="itinerary-actions">
        <button className="action-btn download-btn" onClick={handleDownload}>Download JSON</button>
        <button className="action-btn download-btn" onClick={handleDownloadPdf}>Download PDF</button>
      </div>
    </div>
  );
}

function AccommodationCard({ acc }) {
  // Always show a booking link — use the LLM-provided URL if available,
  // otherwise generate a fallback search URL for the accommodation name + location.
  const searchUrl = acc.bookingUrl ||
    `https://www.booking.com/searchresults.html?ss=${encodeURIComponent((acc.name || '') + ' ' + (acc.location || ''))}`;
  const isLlm = acc.source === 'llm';

  return (
    <div className={`option-card ${acc.tier}`}>
      <div className="card-header">
        <h4>{acc.name}</h4>
        <span className="tier-badge">{acc.tier}</span>
      </div>
      <p className="location">{acc.location}</p>
      <div className="details">
        {acc.rating > 0 && <span className="rating">★ {acc.rating}</span>}
        <span className="price">&euro;{acc.pricePerNight}/night</span>
      </div>
      {acc.totalPrice > 0 && <p className="total-price">Total: &euro;{acc.totalPrice}</p>}
      <p className="amenities">{acc.amenities && acc.amenities.join(', ')}</p>
      <div className="card-footer">
        {acc.source && <span className="source-badge">{acc.source}</span>}
        {isLlm && <span className="synthetic-warning">AI estimate — verify price</span>}
        <a href={searchUrl} target="_blank" rel="noopener noreferrer" className="book-btn">
          {isLlm ? 'Search on Booking.com' : 'Book'} &rarr;
        </a>
      </div>
    </div>
  );
}

function TransportCard({ t }) {
  const icons = { flight: '✈', car: '🚗', 'own-car': '🚙', bus: '🚌', train: '🚆', ferry: '⛴' };
  // Generate a fallback search URL based on transport type
  const fallbackUrls = {
    flight: `https://www.skyscanner.com/transport/flights/${encodeURIComponent(t.origin || '')}/${encodeURIComponent(t.destination || '')}/`,
    train: `https://www.thetrainline.com/`,
    bus: `https://www.flixbus.com/`,
    car: `https://www.rentalcars.com/`,
    ferry: `https://www.directferries.com/`,
  };
  const searchUrl = t.bookingUrl || fallbackUrls[t.type] || '#';
  const isLlm = t.source === 'llm';

  return (
    <div className={`option-card ${t.tier}`}>
      <div className="card-header">
        <h4>{t.provider}</h4>
        <span className="tier-badge">{t.tier}</span>
      </div>
      <p className="type">{icons[t.type] || '🚗'} {t.type}</p>
      <div className="details">
        <span>{t.departureTime} &rarr; {t.arrivalTime}</span>
        {t.duration && <span>Duration: {t.duration}</span>}
      </div>
      {t.origin && t.destination && <p className="route">{t.origin} &rarr; {t.destination}</p>}
      {t.stops > 0 && <p className="stops">{t.stops} stop(s)</p>}
      <div className="price">&euro;{t.price}/person</div>
      <div className="card-footer">
        {t.source && <span className="source-badge">{t.source}</span>}
        {isLlm && <span className="synthetic-warning">AI estimate — verify price</span>}
        <a href={searchUrl} target="_blank" rel="noopener noreferrer" className="book-btn">
          {isLlm ? 'Search' : 'Book'} &rarr;
        </a>
      </div>
    </div>
  );
}

export default ItineraryCanvas;
