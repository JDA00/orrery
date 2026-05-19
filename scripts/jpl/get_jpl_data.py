#!/usr/bin/env python3
"""
Get JPL Horizons data using the API.
This script fetches planet positions in the correct reference frame.
"""

import urllib.parse
import urllib.request
import json

def get_jpl_horizons_data(body_id, jd_start, jd_stop):
    """
    Fetch data from JPL Horizons API.
    
    The key is using REF_PLANE='BODY' and REF_SYSTEM='ICRF' 
    to get ICRF equatorial coordinates instead of ecliptic.
    """
    
    base_url = "https://ssd.jpl.nasa.gov/api/horizons.api"
    
    # Parameters for ICRF equatorial coordinates
    params = {
        'format': 'json',
        'COMMAND': str(body_id),
        'EPHEM_TYPE': 'VECTORS',
        'CENTER': "'500@0'",  # Sun center
        'START_TIME': f"'{jd_start}'",
        'STOP_TIME': f"'{jd_stop}'",
        'STEP_SIZE': "'1 d'",
        'REF_PLANE': 'BODY',  # Key: Use body equator
        'REF_SYSTEM': 'ICRF', # Key: ICRF frame
        'OUT_UNITS': 'AU-D',
        'VEC_TABLE': '3',
        'CSV_FORMAT': 'YES'
    }
    
    # Build URL
    query_string = urllib.parse.urlencode(params)
    url = f"{base_url}?{query_string}"
    
    # Make request
    try:
        with urllib.request.urlopen(url) as response:
            data = response.read().decode('utf-8')
            return json.loads(data)
    except Exception as e:
        print(f"Error fetching data for body {body_id}: {e}")
        return None

# Planet IDs
planets = {
    'Mercury': 199,
    'Venus': 299,
    'Earth': 399,
    'Mars': 499,
    'Jupiter': 599,
    'Saturn': 699,
    'Uranus': 799,
    'Neptune': 899
}

# Fetch data for J2000.0
print("Fetching JPL Horizons data for J2000.0...")
print("=" * 60)

jd_j2000 = "2451545.0"  # J2000.0 epoch

for name, body_id in planets.items():
    print(f"Fetching {name} (ID {body_id})...")
    result = get_jpl_horizons_data(body_id, jd_j2000, jd_j2000)
    
    if result and 'result' in result:
        # Parse the result to extract vectors
        lines = result['result'].split('\n')
        
        # Look for the data section
        in_data = False
        for line in lines:
            if '$$SOE' in line:
                in_data = True
                continue
            if '$$EOE' in line:
                break
            if in_data and line.strip() and not line.startswith('*'):
                # This should be our data line
                parts = line.split(',')
                if len(parts) >= 7:
                    # CSV format: JDTDB, Calendar, X, Y, Z, VX, VY, VZ, ...
                    print(f"  JD: {parts[0]}")
                    print(f"  X:  {parts[2]} AU")
                    print(f"  Y:  {parts[3]} AU")
                    print(f"  Z:  {parts[4]} AU")
                    print(f"  VX: {parts[5]} AU/day")
                    print(f"  VY: {parts[6]} AU/day")
                    print(f"  VZ: {parts[7]} AU/day")
                    break
    else:
        print(f"  Failed to get data")
    
    print()