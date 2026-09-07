# Schema Comparison Specification

## Algorithm
1. Retrieve metadata for Source and Target schemas.
2. Index columns by case-insensitive name.
3. Compare column data types, sizes, nullable constraints, and default values.
4. Compare Primary Keys by column sequence.
5. Generate symmetric differences: Added, Removed, Modified.
