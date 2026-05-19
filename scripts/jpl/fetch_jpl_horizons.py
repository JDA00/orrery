#!/usr/bin/env python3
"""
Fetch JPL Horizons ephemeris data for test validation.

Frame: J2000 ecliptic, solar system barycentric.
This matches the output of VSOP87E in the live code path, so the resulting
CSV can be compared directly against FramedState positions in the
JPLHorizonsValidationTest suite.
"""

import requests
import re

def get_horizons_vector(body_id, jd_tdb):
    """
    Fetch position/velocity vectors from JPL Horizons API.
    
    Args:
        body_id: Horizons ID (e.g., '399' for Earth)
        jd_tdb: Julian Date in TDB
    
    Returns:
        dict with x, y, z, vx, vy, vz in AU and AU/day
    """
    url = "https://ssd.jpl.nasa.gov/api/horizons.api"
    
    params = {
        'format': 'text',
        'COMMAND': f"'{body_id}'",
        'OBJ_DATA': 'NO',
        'MAKE_EPHEM': 'YES',
        'EPHEM_TYPE': 'VECTORS',
        'CENTER': '@0',            # Solar System Barycenter — matches VSOP87E
        'START_TIME': f'JD{jd_tdb}',
        'STOP_TIME': f'JD{jd_tdb + 1.0}',  # API requires STOP > START; parser picks first match
        'STEP_SIZE': '1d',
        'REF_PLANE': 'ECLIPTIC',   # J2000 ecliptic — matches VSOP87E output frame
        'REF_SYSTEM': 'ICRF',
        'OUT_UNITS': 'AU-D',
        'VEC_TABLE': '2',
        'VEC_CORR': 'NONE',
        'CSV_FORMAT': 'NO'
    }

    response = requests.get(url, params=params)
    text = response.text

    # Sanity-check the returned frame
    if "Ecliptic" in text:
        print(f"✓ Got ecliptic coordinates for body {body_id}")
    elif "Earth mean equator" in text:
        print(f"⚠️  WARNING: Got equatorial coordinates for body {body_id}, not ecliptic!")
    
    # Parse the vector data
    # Look for pattern: X = number Y = number Z = number
    x_match = re.search(r'X\s*=\s*([-\d.E+]+)', text)
    y_match = re.search(r'Y\s*=\s*([-\d.E+]+)', text)
    z_match = re.search(r'Z\s*=\s*([-\d.E+]+)', text)
    vx_match = re.search(r'VX\s*=\s*([-\d.E+]+)', text)
    vy_match = re.search(r'VY\s*=\s*([-\d.E+]+)', text)
    vz_match = re.search(r'VZ\s*=\s*([-\d.E+]+)', text)
    
    if not all([x_match, y_match, z_match]):
        # Try alternative format
        lines = text.split('\n')
        for i, line in enumerate(lines):
            if str(jd_tdb) in line:
                # Found the data line, next lines have X Y Z and VX VY VZ
                if i+1 < len(lines):
                    pos_line = lines[i+1].strip()
                    pos_parts = pos_line.split()
                    if len(pos_parts) >= 6 and pos_parts[0] == 'X':
                        x = float(pos_parts[2])
                        y = float(pos_parts[5])
                        if i+2 < len(lines):
                            z_line = lines[i+2].strip()
                            z_parts = z_line.split()
                            if len(z_parts) >= 3 and z_parts[0] == 'Z':
                                z = float(z_parts[2])
                                
                                # Get velocities
                                if i+3 < len(lines):
                                    vx_line = lines[i+3].strip()
                                    vx_parts = vx_line.split()
                                    if len(vx_parts) >= 6 and vx_parts[0] == 'VX':
                                        vx = float(vx_parts[2])
                                        vy = float(vx_parts[5])
                                        if i+4 < len(lines):
                                            vz_line = lines[i+4].strip()
                                            vz_parts = vz_line.split()
                                            if len(vz_parts) >= 3 and vz_parts[0] == 'VZ':
                                                vz = float(vz_parts[2])
                                                
                                                return {
                                                    'x': x, 'y': y, 'z': z,
                                                    'vx': vx, 'vy': vy, 'vz': vz
                                                }
    
    if x_match and y_match and z_match:
        result = {
            'x': float(x_match.group(1)),
            'y': float(y_match.group(1)),
            'z': float(z_match.group(1))
        }
        
        if vx_match and vy_match and vz_match:
            result.update({
                'vx': float(vx_match.group(1)),
                'vy': float(vy_match.group(1)),
                'vz': float(vz_match.group(1))
            })
        else:
            result.update({'vx': 0, 'vy': 0, 'vz': 0})
        
        return result
    
    print(f"Failed to parse data for body {body_id}")
    print("Response preview:")
    print(text[:2000])
    return None

def main():
    # Planet IDs in Horizons
    planets = {
        'Mercury': '199',
        'Venus': '299',
        'Earth': '399',
        'Mars': '499',
        'Jupiter': '599',
        'Saturn': '699',
        'Uranus': '799',
        'Neptune': '899'
    }
    
    # Test epochs
    epochs = {
        'J2000.0': 2451545.0,
        'Mars Opposition 2020': 2459136.47694,
        'Apollo 11': 2440423.06389
    }
    
    print("Fetching JPL Horizons data...")
    print("=" * 60)
    
    results = {}
    
    for epoch_name, jd in epochs.items():
        print(f"\nEpoch: {epoch_name} (JD {jd})")
        print("-" * 40)
        
        # For J2000, get all planets. For others, just Earth and Mars
        if epoch_name == 'J2000.0':
            bodies = planets.items()
        elif 'Mars' in epoch_name:
            bodies = [('Earth', '399'), ('Mars', '499')]
        else:
            bodies = [('Earth', '399')]
        
        for planet_name, planet_id in bodies:
            print(f"Fetching {planet_name}...")
            data = get_horizons_vector(planet_id, jd)
            
            if data:
                key = f"{planet_name},{jd}"
                results[key] = data
                print(f"  X = {data['x']:.15E} AU")
                print(f"  Y = {data['y']:.15E} AU")
                print(f"  Z = {data['z']:.15E} AU")
                print(f"  VX = {data['vx']:.15E} AU/day")
                print(f"  VY = {data['vy']:.15E} AU/day")
                print(f"  VZ = {data['vz']:.15E} AU/day")
    
    # Generate CSV output
    print("\n" + "=" * 60)
    print("CSV Output for jpl-horizons-reference.csv:")
    print("=" * 60)
    print("# JPL Horizons Reference Data for Test Validation")
    print("# Generated from: https://ssd.jpl.nasa.gov/horizons/")
    print("# Ephemeris: DE441")
    print("# Reference Frame: J2000 Ecliptic, solar system barycentric (matches VSOP87E)")
    print("# Time Scale: TDB (Barycentric Dynamical Time)")
    print("# Units: AU for position, AU/day for velocity")
    print("#")
    print("# Format: Body,JD_TDB,X,Y,Z,VX,VY,VZ")
    print("#")
    
    # J2000.0 data
    print("# Test Case 1: J2000.0 Epoch (2000-Jan-01 12:00:00.0000 TDB)")
    for planet_name, planet_id in planets.items():
        key = f"{planet_name},{epochs['J2000.0']}"
        if key in results:
            d = results[key]
            print(f"{planet_name},{epochs['J2000.0']},{d['x']:.15E},{d['y']:.15E},{d['z']:.15E},{d['vx']:.15E},{d['vy']:.15E},{d['vz']:.15E}")
    
    # Mars opposition data
    if f"Earth,{epochs['Mars Opposition 2020']}" in results:
        print("#")
        print("# Test Case 2: Mars Opposition (2020-Oct-13 23:26:00.0000 UTC)")
        print(f"# JD_TDB = {epochs['Mars Opposition 2020']}")
        for planet in ['Earth', 'Mars']:
            key = f"{planet},{epochs['Mars Opposition 2020']}"
            if key in results:
                d = results[key]
                print(f"{planet},{epochs['Mars Opposition 2020']},{d['x']:.15E},{d['y']:.15E},{d['z']:.15E},{d['vx']:.15E},{d['vy']:.15E},{d['vz']:.15E}")

if __name__ == "__main__":
    main()