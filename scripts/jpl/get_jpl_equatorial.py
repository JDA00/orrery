#!/usr/bin/env python3
"""
Get JPL Horizons data in ICRF/J2000 equatorial coordinates.
"""

import subprocess
import re

def get_jpl_vector(body_id, body_name, jd="2451545.0"):
    """
    Get position/velocity from JPL Horizons in ICRF equatorial frame.
    """
    
    # Use REF_PLANE='FRAME' to get ICRF equatorial coordinates
    cmd = [
        'curl', '-s',
        'https://ssd.jpl.nasa.gov/api/horizons.api',
        '-G',
        '--data-urlencode', f"format=text",
        '--data-urlencode', f"COMMAND='{body_id}'",
        '--data-urlencode', f"EPHEM_TYPE=VECTORS",
        '--data-urlencode', f"CENTER='500@10'",  # Sun center
        '--data-urlencode', f"START_TIME='2000-01-01T12:00'",
        '--data-urlencode', f"STOP_TIME='2000-01-01T12:01'",
        '--data-urlencode', f"STEP_SIZE='1m'",
        '--data-urlencode', f"REF_PLANE=FRAME",  # Key: Use FRAME for ICRF
        '--data-urlencode', f"REF_SYSTEM=ICRF",
        '--data-urlencode', f"OUT_UNITS=AU-D",
        '--data-urlencode', f"VEC_TABLE=2"
    ]
    
    result = subprocess.run(cmd, capture_output=True, text=True)
    output = result.stdout
    
    # Check we got ICRF
    if "Reference frame : ICRF" not in output:
        print(f"WARNING: Did not get ICRF frame for {body_name}")
        if "Reference frame : Ecliptic" in output:
            print(f"  Got ecliptic instead!")
        return None
    
    # Extract the vector data
    # Look for line with JD 2451545.000000000
    lines = output.split('\n')
    for i, line in enumerate(lines):
        if '2451545.000000000' in line:
            # Next line has X Y Z
            if i+1 < len(lines):
                xyz_line = lines[i+1]
                # Parse: X = value Y = value Z = value
                x_match = re.search(r'X\s*=\s*([-\d.E+]+)', xyz_line)
                y_match = re.search(r'Y\s*=\s*([-\d.E+]+)', xyz_line)
                z_match = re.search(r'Z\s*=\s*([-\d.E+]+)', xyz_line)
                
                # Next line has VX VY VZ
                if i+2 < len(lines):
                    v_line = lines[i+2]
                    vx_match = re.search(r'VX\s*=\s*([-\d.E+]+)', v_line)
                    vy_match = re.search(r'VY\s*=\s*([-\d.E+]+)', v_line)
                    vz_match = re.search(r'VZ\s*=\s*([-\d.E+]+)', v_line)
                    
                    if all([x_match, y_match, z_match, vx_match, vy_match, vz_match]):
                        return {
                            'x': float(x_match.group(1)),
                            'y': float(y_match.group(1)),
                            'z': float(z_match.group(1)),
                            'vx': float(vx_match.group(1)),
                            'vy': float(vy_match.group(1)),
                            'vz': float(vz_match.group(1))
                        }
    
    print(f"Failed to parse data for {body_name}")
    return None

# Planet IDs
planets = [
    (199, 'Mercury'),
    (299, 'Venus'),
    (399, 'Earth'),
    (499, 'Mars'),
    (599, 'Jupiter'),
    (699, 'Saturn'),
    (799, 'Uranus'),
    (899, 'Neptune')
]

print("# JPL Horizons Reference Data for Test Validation")
print("# Generated from: https://ssd.jpl.nasa.gov/horizons/")
print("# Ephemeris: DE441")
print("# Reference Frame: ICRF/J2000.0 (equatorial)")
print("# Time Scale: TDB (Barycentric Dynamical Time)")
print("# Units: AU for position, AU/day for velocity")
print("#")
print("# Format: Body,JD_TDB,X,Y,Z,VX,VY,VZ")
print("#")
print("# Test Case 1: J2000.0 Epoch (2000-Jan-01 12:00:00.0000 TDB)")

for body_id, name in planets:
    print(f"Fetching {name}...", end='', flush=True)
    data = get_jpl_vector(body_id, name)
    
    if data:
        print(f" OK")
        print(f"{name},2451545.0,{data['x']:.15E},{data['y']:.15E},{data['z']:.15E},{data['vx']:.15E},{data['vy']:.15E},{data['vz']:.15E}")
    else:
        print(f" FAILED")