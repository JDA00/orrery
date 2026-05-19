#!/bin/bash

# Fetch JPL Horizons data for test validation
# Gets positions in J2000.0 equatorial (ICRF) coordinates

echo "# JPL Horizons Reference Data for Test Validation"
echo "# Generated from: https://ssd.jpl.nasa.gov/horizons/"
echo "# Ephemeris: DE441"
echo "# Reference Frame: ICRF/J2000.0 (Earth mean equator)"
echo "# Time Scale: TDB (Barycentric Dynamical Time)"
echo "# Units: AU for position, AU/day for velocity"
echo "#"
echo "# Format: Body,JD_TDB,X,Y,Z,VX,VY,VZ"
echo "#"

# Function to fetch data for a specific body and time
fetch_planet() {
    local body_id=$1
    local body_name=$2
    local jd=$3
    
    # Use Julian Date directly
    local response=$(curl -s "https://ssd.jpl.nasa.gov/api/horizons.api?format=text&COMMAND='${body_id}'&OBJ_DATA='NO'&MAKE_EPHEM='YES'&EPHEM_TYPE='VECTORS'&CENTER='@0'&START_TIME='JD ${jd}'&STOP_TIME='JD ${jd}'&REF_PLANE='FRAME'&OUT_UNITS='AU-D'&VEC_TABLE='2'")
    
    # Extract vectors from response
    echo "$response" | awk -v name="$body_name" -v jd="$jd" '
    /^\$\$SOE/ { flag=1; next }
    /^\$\$EOE/ { flag=0 }
    flag && /X =/ {
        # Parse X = value Y = value format
        gsub(/X =/, "", $0)
        gsub(/Y =/, "", $0) 
        gsub(/Z =/, "", $0)
        x = $1; y = $2; z = $3
        getline
        gsub(/VX=/, "", $0)
        gsub(/VY=/, "", $0)
        gsub(/VZ=/, "", $0)
        vx = $1; vy = $2; vz = $3
        printf "%s,%.10f,%s,%s,%s,%s,%s,%s\n", name, jd, x, y, z, vx, vy, vz
    }'
}

echo "# Test Case 1: J2000.0 Epoch (2000-Jan-01 12:00:00.0000 TDB)"

# All planets at J2000.0
fetch_planet "199" "Mercury" "2451545.0"
fetch_planet "299" "Venus" "2451545.0"
fetch_planet "399" "Earth" "2451545.0"
fetch_planet "499" "Mars" "2451545.0"
fetch_planet "599" "Jupiter" "2451545.0"
fetch_planet "699" "Saturn" "2451545.0"
fetch_planet "799" "Uranus" "2451545.0"
fetch_planet "899" "Neptune" "2451545.0"

echo "#"
echo "# Test Case 2: Mars Opposition (2020-Oct-13 23:26:47.6160 TDB)"
echo "# JD_TDB = 2459136.47694"

fetch_planet "399" "Earth" "2459136.47694"
fetch_planet "499" "Mars" "2459136.47694"