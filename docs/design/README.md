# Hippocampus Visual Design Authority

## Status

Frozen for Hippocampus v1 implementation.

Visual direction:

**Contemporary Clinical**

Design philosophy:

**Contemporary Academic Minimalism with Clinical Restraint**

## Authority

`DESIGN.md` is the visual source of truth for Hippocampus v1.

It governs:

- color
- typography
- spacing
- borders
- radius
- elevation
- component visual language
- information density
- visual hierarchy
- responsive visual behavior

The files under `references/` are visual references that demonstrate
the intended application character and layout patterns.

They are not pixel-perfect implementation specifications.

## Conflict Rule

Product, educational, behavioral, security, domain, and architecture
requirements remain governed by the numbered Hippocampus Source-of-Truth
documents and the Implementation Tracker.

If a Stitch screenshot or DESIGN.md example conflicts with those
requirements, the product Source of Truth wins.

Example:

A reference image may contain illustrative mock content.

That mock content does not create a product requirement.

DESIGN.md remains authoritative for how the approved product behavior
should look.

## Implementation Rule

Do not independently redesign Hippocampus unless a tracker task explicitly
requires a design change.

When implementing UI:

1. follow the product/behavior Source of Truth;
2. follow frontend architecture;
3. apply DESIGN.md;
4. use reference screenshots for visual intent;
5. preserve accessibility requirements.

## Reference Screens

- Home
- Topic Workspace
- Study Mission
- Anatomy Learning