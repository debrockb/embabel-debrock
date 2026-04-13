import React, { useState } from 'react';
import { getModelsForRole, groupByProvider } from '../modelConfig';
import './MissionControl.css';

function MissionControl({ onPlanTrip, isLoading }) {
  const [formData, setFormData] = useState({
    destination: '',
    originCity: '',
    startDate: '',
    endDate: '',
    guestCount: 1,
    budgetMin: 1000,
    budgetMax: 5000,
    travelStyle: 'standard',
    accommodationTypes: ['hotel', 'bb', 'apartment'],
    transportTypes: ['flight', 'car'],
    interestTags: [],
    orchestratorModel: 'lmstudio/qwen3.5:9b',
    extractorModel: 'lmstudio/nemotron-3-nano:4b',
  });

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;

    if (type === 'checkbox') {
      const fieldName = name.replace('_', '');
      setFormData((prev) => ({
        ...prev,
        [fieldName]: checked
          ? [...prev[fieldName], value]
          : prev[fieldName].filter((item) => item !== value),
      }));
    } else {
      setFormData((prev) => ({
        ...prev,
        [name]: value,
      }));
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onPlanTrip(formData);
  };

  return (
    <div className="mission-control">
      <h2>🎯 Mission Control Dashboard</h2>

      <form onSubmit={handleSubmit} className="control-form">
        <div className="form-row">
          <div className="form-group">
            <label>Destination</label>
            <input
              type="text"
              name="destination"
              value={formData.destination}
              onChange={handleInputChange}
              placeholder="e.g., Tokyo, Paris, New York"
              required
            />
          </div>
          <div className="form-group">
            <label>Origin City</label>
            <input
              type="text"
              name="originCity"
              value={formData.originCity}
              onChange={handleInputChange}
              placeholder="e.g., London, Amsterdam"
            />
            <span className="field-hint">For transport routing</span>
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Start Date</label>
            <input
              type="date"
              name="startDate"
              value={formData.startDate}
              onChange={handleInputChange}
              required
            />
          </div>
          <div className="form-group">
            <label>End Date</label>
            <input
              type="date"
              name="endDate"
              value={formData.endDate}
              onChange={handleInputChange}
              required
            />
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Guest Count</label>
            <input
              type="number"
              name="guestCount"
              value={formData.guestCount}
              onChange={handleInputChange}
              min="1"
              max="20"
            />
          </div>
          <div className="form-group">
            <label>Travel Style</label>
            <select
              name="travelStyle"
              value={formData.travelStyle}
              onChange={handleInputChange}
            >
              <option value="budget">Budget</option>
              <option value="standard">Standard</option>
              <option value="luxury">Luxury</option>
            </select>
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Budget Min (USD)</label>
            <input
              type="number"
              name="budgetMin"
              value={formData.budgetMin}
              onChange={handleInputChange}
              min="0"
            />
          </div>
          <div className="form-group">
            <label>Budget Max (USD)</label>
            <input
              type="number"
              name="budgetMax"
              value={formData.budgetMax}
              onChange={handleInputChange}
              min="0"
            />
          </div>
        </div>

        <div className="form-group">
          <label>Accommodations</label>
          <div className="checkbox-group">
            <label>
              <input
                type="checkbox"
                name="accommodationTypes_"
                value="hotel"
                checked={formData.accommodationTypes.includes('hotel')}
                onChange={handleInputChange}
              />
              Hotels
            </label>
            <label>
              <input
                type="checkbox"
                name="accommodationTypes_"
                value="bb"
                checked={formData.accommodationTypes.includes('bb')}
                onChange={handleInputChange}
              />
              B&Bs
            </label>
            <label>
              <input
                type="checkbox"
                name="accommodationTypes_"
                value="apartment"
                checked={formData.accommodationTypes.includes('apartment')}
                onChange={handleInputChange}
              />
              Apartments
            </label>
            <label>
              <input
                type="checkbox"
                name="accommodationTypes_"
                value="hostel"
                checked={formData.accommodationTypes.includes('hostel')}
                onChange={handleInputChange}
              />
              Hostels
            </label>
          </div>
        </div>

        <div className="form-group">
          <label>Transport</label>
          <div className="checkbox-group">
            <label>
              <input
                type="checkbox"
                name="transportTypes_"
                value="flight"
                checked={formData.transportTypes.includes('flight')}
                onChange={handleInputChange}
              />
              Flights
            </label>
            <label>
              <input
                type="checkbox"
                name="transportTypes_"
                value="car"
                checked={formData.transportTypes.includes('car')}
                onChange={handleInputChange}
              />
              Car Rental
            </label>
            <label>
              <input
                type="checkbox"
                name="transportTypes_"
                value="bus"
                checked={formData.transportTypes.includes('bus')}
                onChange={handleInputChange}
              />
              Bus
            </label>
            <label>
              <input
                type="checkbox"
                name="transportTypes_"
                value="train"
                checked={formData.transportTypes.includes('train')}
                onChange={handleInputChange}
              />
              Train
            </label>
            <label>
              <input
                type="checkbox"
                name="transportTypes_"
                value="ferry"
                checked={formData.transportTypes.includes('ferry')}
                onChange={handleInputChange}
              />
              Ferry
            </label>
          </div>
        </div>

        <div className="form-group">
          <label>Interests</label>
          <div className="checkbox-group">
            {['food', 'history', 'nature', 'nightlife', 'art', 'adventure', 'shopping', 'relaxation'].map((tag) => (
              <label key={tag}>
                <input
                  type="checkbox"
                  name="interestTags_"
                  value={tag}
                  checked={formData.interestTags.includes(tag)}
                  onChange={handleInputChange}
                />
                {tag.charAt(0).toUpperCase() + tag.slice(1)}
              </label>
            ))}
          </div>
        </div>

        <div className="form-group llm-config">
          <label className="section-label">LLM Configuration</label>

          <div className="form-row">
            <div className="form-group">
              <label>Orchestrator Model</label>
              <ModelSelect
                name="orchestratorModel"
                value={formData.orchestratorModel}
                onChange={handleInputChange}
                role="orchestrator"
              />
              <span className="field-hint">Used for synthesis and high-reasoning tasks</span>
            </div>

            <div className="form-group">
              <label>Extractor Model</label>
              <ModelSelect
                name="extractorModel"
                value={formData.extractorModel}
                onChange={handleInputChange}
                role="extractor"
              />
              <span className="field-hint">Used for data extraction — local models save cost</span>
            </div>
          </div>
        </div>

        <button type="submit" disabled={isLoading} className="submit-btn">
          {isLoading ? 'Planning Trip...' : 'Plan My Trip'}
        </button>
      </form>
    </div>
  );
}

/** Dynamic model dropdown — reads from localStorage-backed modelConfig. */
function ModelSelect({ name, value, onChange, role }) {
  const models = getModelsForRole(role);
  const grouped = groupByProvider(models);
  return (
    <select name={name} value={value} onChange={onChange}>
      {Object.entries(grouped).map(([provider, items]) => (
        <optgroup key={provider} label={provider}>
          {items.map((m) => (
            <option key={m.modelId} value={m.modelId}>
              {m.displayName}
            </option>
          ))}
        </optgroup>
      ))}
    </select>
  );
}

export default MissionControl;
