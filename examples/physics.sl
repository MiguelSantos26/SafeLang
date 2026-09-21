## multi-line comment
   SI base units
##

# independent SI base dimensions
# type <name> [<unit-name>,<suffix-name>] : <numeric-type>
type Length [meter,m]: real; # <name> [<unity>,<suffix>]
type Time [second,s]: real;
#type Mass [gram,g]: real; # NOTE: SI unit is kg and not gram!
#type Temperature [kelvin,K]: real;
#type ElectricCurrent [ampere,A]: real;
#type AmountOfSubstance [mole,mol]: real;
#type LuminousIntensity [candela,cd]: real;

# alternative Length unit:

# unit <dimension-name> [<unit-name>,<suffix-name>]
unit Length [inch,in] := 0.0254*meter;
#unit Length [inch,in] := 0.0254 m; # alternative definition using suffix (not a minimum requirement)

# dependant dimensions:

type Velocity := Length/Time: real;
type Acceleration := Velocity/Time: real;
type Area := Length*Length: real;
#type Volume := Length*Length*Length: real;
#type Force [newton, N] := Mass*Acceleration: real;


