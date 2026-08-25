---
name: Contemporary Clinical
colors:
  surface: '#FFFFFF'
  surface-dim: '#d1dce2'
  surface-bright: '#f4faff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#ebf5fc'
  surface-container: '#e5eff6'
  surface-container-high: '#dfeaf1'
  surface-container-highest: '#d9e4eb'
  on-surface: '#131d22'
  on-surface-variant: '#3f484a'
  inverse-surface: '#283237'
  inverse-on-surface: '#e8f2f9'
  outline: '#70797a'
  outline-variant: '#bfc8ca'
  surface-tint: '#256770'
  primary: '#00383f'
  on-primary: '#ffffff'
  primary-container: '#00515a'
  on-primary-container: '#84c2cc'
  inverse-primary: '#93d1db'
  secondary: '#4d5a9c'
  on-secondary: '#ffffff'
  secondary-container: '#a8b5fd'
  on-secondary-container: '#374485'
  tertiary: '#452554'
  on-tertiary: '#ffffff'
  tertiary-container: '#5d3c6c'
  on-tertiary-container: '#d3aae2'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#aeedf8'
  primary-fixed-dim: '#93d1db'
  on-primary-fixed: '#001f24'
  on-primary-fixed-variant: '#004f57'
  secondary-fixed: '#dee1ff'
  secondary-fixed-dim: '#b9c3ff'
  on-secondary-fixed: '#021356'
  on-secondary-fixed-variant: '#354282'
  tertiary-fixed: '#f7d9ff'
  tertiary-fixed-dim: '#e1b8f1'
  on-tertiary-fixed: '#2c0d3b'
  on-tertiary-fixed-variant: '#5b3a6a'
  background: '#F8FAFB'
  on-background: '#131d22'
  surface-variant: '#d9e4eb'
  surface-secondary: '#F1F5F6'
  border-subtle: '#E2E8F0'
  soft-teal: '#E5F3F4'
  soft-indigo: '#ECEEFA'
  text-secondary: '#5C6970'
typography:
  display-lg:
    fontFamily: Hanken Grotesk
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 30px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  body-sm:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
  label-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 24px
  margin-desktop: 40px
  margin-mobile: 20px
  section-gap: 64px
  content-gap: 24px
  max-width: 1120px
---

## Brand & Style

The brand personality is defined as **Contemporary Academic Minimalism with Clinical Restraint**. It is engineered for high-stakes medical and scientific education, functioning as a "Precise Mentor"—sophisticated, intellectually honest, and profoundly calm.

The visual style is a deliberate hybrid of **Minimalism** and **Corporate Modernism**. It prioritizes extreme legibility and cognitive ease through expansive whitespace and a reductionist approach to containment. By stripping away traditional gamification elements like streaks, coins, or XP, the UI shifts focus from extrinsic rewards to **evidence-based learning progress**. This creates a premium, distraction-free workspace that reduces visual harshness while preserving strong readability, respecting the user's time and mental energy.

## Colors

The palette is designed for long-duration study, minimizing glare while maintaining a clear semantic hierarchy.

- **Primary Teal (#00515a):** The lead clinical color, used for core actions, navigation, and "Strong" evidence indicators.
- **Secondary Indigo (#4d5a9c):** Used for structural categorization and "Developing" evidence markers.
- **Tertiary Violet (#5d3c6c):** An accent for advanced metadata or "Needs Attention" qualitative states.
- **Neutral Foundation:** The background uses a cool-tinted white to reduce visual harshness. Surfaces are strictly white to create separation through tonal layering rather than heavy shadows.

## Typography

This system utilizes **Hanken Grotesk**, a humanist sans-serif that balances technical precision with approachable geometry. 

- **Clinical Legibility:** Body text uses a generous line-height (approx 1.6x) to prevent line-skipping during dense medical reading.
- **Primary Reading:** The `body-lg` role is the default for core study content.
- **Metadata & Provenance:** Labels and source citations use `label-sm`. In medical contexts, these may be set in uppercase to distinguish evidence from curriculum content.

## Layout & Spacing

The layout follows a **Fixed Grid** philosophy on desktop to maintain ideal line lengths (65-75 characters) for academic retention.

- **Grid:** A 12-column grid with a 1120px max-width to emphasize whitespace on large displays.
- **Reflow:** On mobile, margins reduce to 20px and columns collapse to a single vertical stack.
- **Rhythm:** Use `section-gap` (64px) to separate logical modules rather than nested boxes. Density is kept low to maintain "Clinical Calm." Subtle 1px dividers are preferred over complex container nesting for related information.

## Elevation & Depth

Hierarchy is established via **Tonal Layers** and **Low-contrast Outlines**. This minimizes the visual noise associated with traditional shadow-heavy interfaces.

- **Stacking:** White surfaces sit on the cool-tinted background. Separation is achieved through 1px solid borders using `border-subtle` (#E2E8F0).
- **Shadows:** Strictly reserved for temporary overlays (modals/dropdowns). These must be ultra-diffused and low-opacity: `0 8px 32px rgba(23, 33, 38, 0.04)`.
- **Interactivity:** Elements do not "lift" on hover. Interaction is signaled through subtle border color transitions to Primary Teal or background shifts to Surface Secondary.

## Shapes

The shape language is **Soft**, utilizing a 4px base radius. This reflects a professional, structured environment suitable for surgical and clinical terminology.

- **Standard Elements:** 4px radius for buttons, inputs, and small markers.
- **Large Containers:** 8px (`rounded-lg`) for primary cards.
- **Constraint:** Pill-shaped buttons are avoided to maintain a grounded, serious aesthetic.

## Components

- **Buttons:**
  - **Primary:** Solid Primary Teal, white text, 4px radius.
  - **Secondary:** Soft Teal surface with Primary Teal text.
  - **Tertiary:** Ghost style with `border-subtle` and Primary Teal text.
- **Evidence-Based Learning Progress:** Numerical progress bars and percentages are removed. Use restrained visual markers:
  - **Strong:** Primary Teal dot or label.
  - **Developing:** Secondary Indigo dot or label.
  - **Needs Attention:** Tertiary Violet dot or label.
  - **Insufficient Evidence:** Neutral/Muted border dot.
- **Source Provenance:** A specialized medical citation component featuring a left-hand 2px Primary Teal vertical rule, `label-sm` text, and a hyperlink style for clinical references.
- **Cards:** White surface with a 1px `border-subtle`. Internal padding must be at least 24px.
- **Input Fields:** 1px `border-subtle` that transitions to Primary Teal on focus. No shadows or glow effects.