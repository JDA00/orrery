#!/usr/bin/env python3
import math

# JPL Horizons data for all planets at J2000.0 (in ECLIPTIC coordinates)
# Retrieved from API calls - these are the actual JPL DE441 ephemeris values
planets_ecliptic = {
    'Mercury': (-1.304629583338261E-01, -4.462625062911310E-01, 2.445033847571385E-02,
                2.148515067295426E-02, -7.224389696967179E-03, -4.571637734652582E-03),
    'Venus': (-7.183159874878110E-01, -3.267235967269601E-02, 4.100877055606787E-02,
              7.979641905835706E-04, -2.029489372555312E-02, -3.234191916742176E-04),
    'Earth': (-1.842722784343177E-01, 9.644456892709630E-01, 2.022132246085051E-04,
              -1.720224660838933E-02, -3.166189060532839E-03, 1.064592514002072E-08),
    'Mars': (1.390718590458134E+00, -1.341351842925283E-02, -3.446774662832197E-02,
             6.713866033660028E-04, 1.518719274907438E-02, 3.016476126774044E-04),
    'Jupiter': (3.997060389610827E+00, 2.931858652447588E+00, -1.017928019328259E-01,
                -4.560231480479256E-03, 6.437907477988055E-03, 7.539379778838846E-05),
    'Saturn': (6.401971129097850E+00, 6.568635679089425E+00, -3.691712061844283E-01,
               -4.285449745593642E-03, 3.883240038900173E-03, 1.027062223636116E-04),
    'Uranus': (1.442965770943040E+01, -1.374638162862473E+01, -2.381765362454692E-01,
               2.683612192618285E-03, 2.665503512402032E-03, -2.485098853116098E-05),
    'Neptune': (1.681073584815167E+01, -2.499201199848000E+01, 1.273840375481025E-01,
                2.584499957625341E-03, 1.768238617783849E-03, -9.620531719117447E-05)
}

# Obliquity at J2000.0 (IAU 1976/80): 84381.448 arcseconds = 23.4392911 degrees
epsilon = math.radians(84381.448 / 3600.0)
cos_e = math.cos(epsilon)
sin_e = math.sin(epsilon)

print("# JPL Horizons Reference Data for Test Validation")
print("# Generated from: https://ssd.jpl.nasa.gov/horizons/")
print("# Ephemeris: DE441")
print("# Reference Frame: ICRF/J2000.0 (converted from ecliptic)")
print("# Time Scale: TDB (Barycentric Dynamical Time)")
print("# Units: AU for position, AU/day for velocity")
print("#")
print("# Format: Body,JD_TDB,X,Y,Z,VX,VY,VZ")
print("#")
print("# Test Case 1: J2000.0 Epoch (2000-Jan-01 12:00:00.0000 TDB)")

for name, (x_ecl, y_ecl, z_ecl, vx_ecl, vy_ecl, vz_ecl) in planets_ecliptic.items():
    # Transform from ecliptic to equatorial
    x_eq = x_ecl
    y_eq = cos_e * y_ecl - sin_e * z_ecl
    z_eq = sin_e * y_ecl + cos_e * z_ecl
    
    vx_eq = vx_ecl
    vy_eq = cos_e * vy_ecl - sin_e * vz_ecl
    vz_eq = sin_e * vy_ecl + cos_e * vz_ecl
    
    print(f"{name},2451545.0,{x_eq:.15E},{y_eq:.15E},{z_eq:.15E},{vx_eq:.15E},{vy_eq:.15E},{vz_eq:.15E}")