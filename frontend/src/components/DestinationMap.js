import React, { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';

// Fix default marker icon (Leaflet + webpack/CRA bundler issue)
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

/**
 * Geocodes a destination name to lat/lng using OpenStreetMap Nominatim
 * (free, no API key required, respects usage policy with single request).
 */
async function geocode(destination) {
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(destination)}&format=json&limit=1`,
      { headers: { 'User-Agent': 'MATOE-TravelPlanner/0.1' } }
    );
    const data = await res.json();
    if (data.length > 0) {
      return { lat: parseFloat(data[0].lat), lng: parseFloat(data[0].lon), name: data[0].display_name };
    }
  } catch (e) {
    console.warn('Geocoding failed:', e);
  }
  return null;
}

function DestinationMap({ destination, attractions }) {
  const [coords, setCoords] = useState(null);

  useEffect(() => {
    if (destination) {
      geocode(destination).then(setCoords);
    }
  }, [destination]);

  if (!coords) return null;

  return (
    <div className="destination-map" style={{ height: '300px', borderRadius: '12px', overflow: 'hidden', margin: '1rem 0' }}>
      <MapContainer
        center={[coords.lat, coords.lng]}
        zoom={12}
        style={{ height: '100%', width: '100%' }}
        scrollWheelZoom={false}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <Marker position={[coords.lat, coords.lng]}>
          <Popup>{destination}</Popup>
        </Marker>
        {attractions && attractions.map((attr, i) => {
          // If attractions have location coords, show them too
          if (attr.latitude && attr.longitude) {
            return (
              <Marker key={i} position={[attr.latitude, attr.longitude]}>
                <Popup>{attr.name}</Popup>
              </Marker>
            );
          }
          return null;
        })}
      </MapContainer>
    </div>
  );
}

export default DestinationMap;
