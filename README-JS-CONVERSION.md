# Supreme J/s Display Conversion

Extract this archive into the root of the Supreme-Legacy repository.

This update leaves all internal Slimefun energy values unchanged and converts only player-facing rates:

- displayed J/s = internal J/tick × 20
- 20 J/tick displays as 400 J/s
- 100 J/tick displays as 2,000 J/s
- 300 J/tick displays as 6,000 J/s
- 2,000 J/tick displays as 40,000 J/s

The conversion applies to item lore, quarry status, generator menus, and Tech Generator insufficient-power messages.
