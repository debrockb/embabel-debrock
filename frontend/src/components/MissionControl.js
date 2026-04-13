import React, { useState } from 'react';
import { getModelsForRole, groupByProvider } from '../modelConfig';
import './MissionControl.css';

function MissionControl({ onPlanTrip, isLoading }) {
  const [formData, setFormData] = useState({
    destination: '',
    originCity: '',
    startDate: '',
    endDate: '',
    adults: 2,
    children: 0,
    childrenAges: [],
    rooms: 1,
    budgetMin: 1000,
    budgetMax: 5000,
    travelStyle: 'standard',
    mealPlan: 'breakfast',
    accommodationTypes: ['hotel', 'bb', 'apartment'],
    transportTypes: ['flight', 'car'],
    interestTags: [],
    orchestratorModel: 'lmstudio/nemotron-3-nano:4b',
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

  const handleChildrenChange = (e) => {
    const count = Math.max(0, Math.min(10, parseInt(e.target.value) || 0));
    setFormData((prev) => {
      const ages = [...prev.childrenAges];
      // Grow or shrink the ages array to match the new count
      while (ages.length < count) ages.push(5);
      while (ages.length > count) ages.pop();
      return { ...prev, children: count, childrenAges: ages };
    });
  };

  const handleChildAgeChange = (index, value) => {
    setFormData((prev) => {
      const ages = [...prev.childrenAges];
      ages[index] = Math.max(0, Math.min(17, parseInt(value) || 0));
      return { ...prev, childrenAges: ages };
    });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // Compute guestCount for backwards compat
    const guestCount = (parseInt(formData.adults) || 1) + (parseInt(formData.children) || 0);
    onPlanTrip({ ...formData, guestCount });
  };

  return (
    <div className="mission-control">
      <h2>Plan Your Trip</h2>

      <form onSubmit={handleSubmit} className="control-form">
        <div className="form-row">
          <div className="form-group">
            <label>Destination</label>
            <input
              type="text"
              name="destination"
              value={formData.destination}
              onChange={handleInputChange}
              placeholder="e.g., Tokyo, Tuscany, the Greek Islands"
              required
            />
            <span className="field-hint">City, region, country, or area</span>
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

        {/* ── Travellers & Rooms ────────────────────────────────────────── */}
        <div className="form-group">
          <label className="section-label">Travellers & Rooms</label>
          <div className="form-row">
            <div className="form-group">
              <label>Adults (18+)</label>
              <input
                type="number"
                name="adults"
                value={formData.adults}
                onChange={handleInputChange}
                min="1"
                max="20"
              />
            </div>
            <div className="form-group">
              <label>Children (0-17)</label>
              <input
                type="number"
                value={formData.children}
                onChange={handleChildrenChange}
                min="0"
                max="10"
              />
            </div>
            <div className="form-group">
              <label>Rooms</label>
              <input
                type="number"
                name="rooms"
                value={formData.rooms}
                onChange={handleInputChange}
                min="1"
                max="10"
              />
            </div>
          </div>

          {formData.children > 0 && (
            <div className="children-ages">
              <label>Children's Ages</label>
              <div className="ages-row">
                {formData.childrenAges.map((age, i) => (
                  <div key={i} className="age-input">
                    <label>Child {i + 1}</label>
                    <input
                      type="number"
                      value={age}
                      onChange={(e) => handleChildAgeChange(i, e.target.value)}
                      min="0"
                      max="17"
                    />
                  </div>
                ))}
              </div>
              <span className="field-hint">Ages affect pricing — children under 2 often fly free</span>
            </div>
          )}
        </div>

        <div className="form-row">
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
          <div className="form-group">
            <label>Meal Plan</label>
            <select
              name="mealPlan"
              value={formData.mealPlan}
              onChange={handleInputChange}
            >
              <option value="room-only">Room Only</option>
              <option value="breakfast">Bed & Breakfast</option>
              <option value="half-board">Half Board (breakfast + dinner)</option>
              <option value="full-board">Full Board (all meals)</option>
              <option value="all-inclusive">All-Inclusive</option>
              <option value="self-catering">Self-Catering</option>
            </select>
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Budget Min (EUR)</label>
            <input
              type="number"
              name="budgetMin"
              value={formData.budgetMin}
              onChange={handleInputChange}
              min="0"
            />
          </div>
          <div className="form-group">
            <label>Budget Max (EUR)</label>
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
            {[
              ['hotel', 'Hotels'], ['bb', 'B&Bs'], ['apartment', 'Apartments'], ['hostel', 'Hostels'],
            ].map(([val, label]) => (
              <label key={val}>
                <input
                  type="checkbox"
                  name="accommodationTypes_"
                  value={val}
                  checked={formData.accommodationTypes.includes(val)}
                  onChange={handleInputChange}
                />
                {label}
              </label>
            ))}
          </div>
        </div>

        <div className="form-group">
          <label>Transport</label>
          <div className="checkbox-group">
            {[
              ['own-car', 'Own Car'], ['flight', 'Flights'], ['car', 'Car Rental'],
              ['bus', 'Bus'], ['train', 'Train'], ['ferry', 'Ferry'],
            ].map(([val, label]) => (
              <label key={val}>
                <input
                  type="checkbox"
                  name="transportTypes_"
                  value={val}
                  checked={formData.transportTypes.includes(val)}
                  onChange={handleInputChange}
                />
                {label}
              </label>
            ))}
          </div>
        </div>

        <div className="form-group">
          <label>Interests</label>
          <div className="checkbox-group">
            {['food', 'history', 'nature', 'nightlife', 'art', 'adventure', 'shopping', 'relaxation', 'family', 'romance'].map((tag) => (
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
