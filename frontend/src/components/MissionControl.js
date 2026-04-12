import React, { useState } from 'react';
import './MissionControl.css';

function MissionControl({ onPlanTrip, isLoading }) {
  const [formData, setFormData] = useState({
    destination: '',
    startDate: '',
    endDate: '',
    guestCount: 1,
    budgetMin: 1000,
    budgetMax: 5000,
    travelStyle: 'standard',
    accommodationTypes: ['hotel', 'bb', 'apartment'],
    transportTypes: ['flight', 'car'],
    orchestratorModel: 'anthropic/claude-3-5-sonnet',
    extractorModel: 'lmstudio/llama-3-8b',
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
          </div>
        </div>

        <div className="form-group llm-config">
          <label className="section-label">LLM Configuration</label>

          <div className="form-row">
            <div className="form-group">
              <label>Orchestrator Model</label>
              <select
                name="orchestratorModel"
                value={formData.orchestratorModel}
                onChange={handleInputChange}
              >
                <optgroup label="Anthropic">
                  <option value="anthropic/claude-opus-4-6">Claude Opus 4.6 (most capable)</option>
                  <option value="anthropic/claude-sonnet-4-6">Claude Sonnet 4.6 (balanced)</option>
                  <option value="anthropic/claude-haiku-4-5">Claude Haiku 4.5 (fast)</option>
                  <option value="anthropic/claude-3-5-sonnet-20241022">Claude 3.5 Sonnet</option>
                </optgroup>
                <optgroup label="OpenRouter">
                  <option value="openrouter/openai/gpt-4o">GPT-4o (via OpenRouter)</option>
                  <option value="openrouter/google/gemini-pro-1.5">Gemini Pro 1.5 (via OpenRouter)</option>
                  <option value="openrouter/meta-llama/llama-3.3-70b-instruct">Llama 3.3 70B (via OpenRouter)</option>
                </optgroup>
                <optgroup label="Local (LM Studio)">
                  <option value="lmstudio/llama-3-8b">Llama 3 8B (LM Studio)</option>
                  <option value="lmstudio/mistral-7b">Mistral 7B (LM Studio)</option>
                  <option value="lmstudio/phi-3-mini">Phi-3 Mini (LM Studio)</option>
                </optgroup>
                <optgroup label="Local (Ollama)">
                  <option value="ollama/llama3">Llama 3 (Ollama)</option>
                  <option value="ollama/mistral">Mistral (Ollama)</option>
                  <option value="ollama/gemma2">Gemma 2 (Ollama)</option>
                </optgroup>
              </select>
              <span className="field-hint">Used for synthesis and high-reasoning tasks</span>
            </div>

            <div className="form-group">
              <label>Extractor Model</label>
              <select
                name="extractorModel"
                value={formData.extractorModel}
                onChange={handleInputChange}
              >
                <optgroup label="Local (LM Studio) — Recommended">
                  <option value="lmstudio/llama-3-8b">Llama 3 8B (LM Studio)</option>
                  <option value="lmstudio/mistral-7b">Mistral 7B (LM Studio)</option>
                  <option value="lmstudio/phi-3-mini">Phi-3 Mini (LM Studio)</option>
                </optgroup>
                <optgroup label="Local (Ollama)">
                  <option value="ollama/llama3">Llama 3 (Ollama)</option>
                  <option value="ollama/mistral">Mistral (Ollama)</option>
                </optgroup>
                <optgroup label="Anthropic">
                  <option value="anthropic/claude-haiku-4-5">Claude Haiku 4.5 (fast + cheap)</option>
                  <option value="anthropic/claude-sonnet-4-6">Claude Sonnet 4.6</option>
                </optgroup>
                <optgroup label="OpenRouter">
                  <option value="openrouter/meta-llama/llama-3.3-70b-instruct">Llama 3.3 70B (via OpenRouter)</option>
                </optgroup>
              </select>
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

export default MissionControl;
