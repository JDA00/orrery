#!/bin/bash

# Get JPL Horizons data in ecliptic coordinates and convert to equatorial

echo "# Fetching JPL Horizons data at J2000.0 epoch"
echo "# ==========================================="

# Function to get planet data
get_planet() {
    local id=$1
    local name=$2
    
    # Get data from JPL (this returns ecliptic)
    result=$(curl -s "https://ssd.jpl.nasa.gov/api/horizons.api?format=text&COMMAND='${id}'&EPHEM_TYPE='VECTORS'&CENTER='@0'&START_TIME='2000-01-01T12:00'&STOP_TIME='2000-01-01T12:00'&OUT_UNITS='AU-D'&VEC_TABLE='2'" 2>&1)
    
    # Extract position and velocity
    x=$(echo "$result" | grep -A1 "2451545.000" | tail -1 | sed 's/.*X =//' | awk '{print $1}')
    y=$(echo "$result" | grep -A1 "2451545.000" | tail -1 | sed 's/.*Y =//' | awk '{print $2}')
    z=$(echo "$result" | grep -A1 "2451545.000" | tail -1 | sed 's/.*Z =//' | awk '{print $3}')
    
    vx=$(echo "$result" | grep -A2 "2451545.000" | tail -1 | sed 's/.*VX=//' | awk '{print $1}')
    vy=$(echo "$result" | grep -A2 "2451545.000" | tail -1 | sed 's/.*VY=//' | awk '{print $2}')
    vz=$(echo "$result" | grep -A2 "2451545.000" | tail -1 | sed 's/.*VZ=//' | awk '{print $3}')
    
    if [ -n "$x" ]; then
        echo "${name} (ecliptic): $x $y $z $vx $vy $vz"
        
        # Convert from ecliptic to equatorial using Python
        python3 -c "
import math

# Ecliptic coordinates from JPL
x_ecl = $x
y_ecl = $y
z_ecl = $z
vx_ecl = $vx
vy_ecl = $vy
vz_ecl = $vz

# Obliquity at J2000.0 (IAU 1976/80): 84381.448 arcseconds = 23.4392911 degrees
epsilon = math.radians(84381.448 / 3600.0)
cos_e = math.cos(epsilon)
sin_e = math.sin(epsilon)

# Transform from ecliptic to equatorial
# Rotation matrix about X-axis by -epsilon
x_eq = x_ecl
y_eq = cos_e * y_ecl - sin_e * z_ecl
z_eq = sin_e * y_ecl + cos_e * z_ecl

vx_eq = vx_ecl
vy_eq = cos_e * vy_ecl - sin_e * vz_ecl
vz_eq = sin_e * vy_ecl + cos_e * vz_ecl

print(f'{name},2451545.0,{x_eq:.15E},{y_eq:.15E},{z_eq:.15E},{vx_eq:.15E},{vy_eq:.15E},{vz_eq:.15E}')
"
    else
        echo "Failed to get data for $name"
    fi
}

# Get all planets
echo "# JPL Horizons Reference Data (converted from ecliptic to equatorial)"
echo "# Format: Body,JD_TDB,X,Y,Z,VX,VY,VZ"
echo "#"

get_planet 199 Mercury
get_planet 299 Venus
get_planet 399 Earth
get_planet 499 Mars
get_planet 599 Jupiter
get_planet 699 Saturn
get_planet 799 Uranus
get_planet 899 Neptune