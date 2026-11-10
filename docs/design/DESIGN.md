# PulsePass Design System - "Pulse Neon"

Dark, energetic UI for a mega-event ticketing platform (concert and festival mood).
All interface text is in Brazilian Portuguese (pt-BR). Code and token names are in English.

## 1. Principles
- High contrast, clean spacing, one strong accent (violet).
- Information first: during a flash sale the user must read seat state and countdown at a glance.
- Never use color alone to convey state (see Seat states).
- Meet WCAG AA contrast for all text.

## 2. Color tokens

| Token | Hex | Use |
|---|---|---|
| `color.bg` | #0B0B14 | Page background |
| `color.surface` | #151526 | Cards, panels, navigation bar |
| `color.border` | #2A2A44 | 1px borders, dividers |
| `color.text` | #F2F2FA | Primary text |
| `color.text-muted` | #9A9AB5 | Secondary text, labels (min 14px) |
| `color.primary` | #7C4DFF | Buttons, links, selected state, focus ring |
| `color.on-primary` | #FFFFFF | Text on primary |
| `color.seat-free` | #00E5A0 | Available seat, success states |
| `color.seat-reserved` | #FFB020 | Reserved by someone else, warnings, timer near expiry |
| `color.seat-taken` | #FF4D6D | Sold seat, errors, invalid ticket |

Glow: primary buttons and selected items use `0 0 24px rgba(124, 77, 255, 0.45)`.
Page backgrounds may use a subtle radial violet gradient from `#2A1670` fading to `#0B0B14`.

## 3. Typography
- Headings, numbers, countdown, KPIs: **Space Grotesk** (500, 700).
- Body, forms, tables: **Inter** (400, 600).
- Scale: 12 / 14 / 16 / 20 / 24 / 32 / 48 px. Body 16px (14px in dense tables).
- Countdown timer: Space Grotesk 700, 32-48px, color `color.primary` (amber below 2 minutes, pink-red at 30 seconds).

## 4. Spacing, shape, elevation
- Spacing scale (px): 4, 8, 12, 16, 24, 32, 48.
- Corner radius: 12px for cards and buttons, 6px for seats, 999px for chips.
- Borders: 1px solid `color.border`. No heavy shadows; use glow only for primary and selected items.

## 5. Components

**Navigation bar:** `color.surface`, 64px high, logo "PulsePass" in violet (Space Grotesk 700) on the left; links "Eventos", "Meus ingressos" in the center-left; user menu on the right. Role-specific areas (Organizador, Operador, Admin) appear as a left sidebar.

**Button primary:** background `color.primary`, text `color.on-primary`, radius 12px, height 44px, glow. Hover: brighten 8%. Focus: 2px outline `#FFFFFF` with 2px offset. Disabled: 40% opacity, no glow.
**Button secondary:** transparent, 1.5px border `color.primary`, text `color.primary`.
**Button destructive:** border and text `color.seat-taken`.

**Card:** `color.surface`, 1px border, radius 12px, padding 16-24px.

**Input:** `color.bg` fill, 1px border `color.border`, radius 8px, height 44px; focus border `color.primary` with glow; error border `color.seat-taken` with message below.

**Chip / status badge:** pill, 12px text, tinted background (16% of the status color) with solid text in the status color: "Vendas abertas" (free/mint), "Em breve" (primary), "Esgotado" (taken/pink-red), "Reservado" (amber).

**Table:** header text `color.text-muted` uppercase 12px, row divider `color.border`, row hover `color.surface` lightened 4%.

**Charts:** series colors in order: primary violet, mint, amber, pink-red. Gridlines `color.border`. Always include a legend or direct labels.

**Live indicator:** small pulsing mint dot with text "Ao vivo" or "Atualizado em tempo real".

## 6. Seat states (critical component)
Seats are 28-36px squares (radius 6px). Each state has a distinct shape cue in addition to color.

| State | Label (pt-BR) | Visual |
|---|---|---|
| Free | Livre | Transparent fill, 2px outline `color.seat-free` |
| Selected (mine) | Selecionado | Filled `color.primary` with white check icon, glow |
| Reserved by others | Reservado | Amber diagonal stripes pattern, 2px amber outline, not clickable |
| Taken | Ocupado | Filled `color.seat-taken` with dark X icon, not clickable |

Seat maps always show a legend with all four states, a "PALCO" bar at the top, and section/row labels (A-J). Selected seats are listed in the order summary panel.

## 7. Key patterns
- **Reservation countdown:** always visible on seat selection and checkout; progress bar under the timer; message "Sua reserva expira em MM:SS".
- **Order status timeline:** steps Pedido criado, Assentos reservados, Pagamento confirmado, Ingresso emitido; the current step has a pulsing violet dot.
- **Ticket card:** concert-ticket style with perforated edge, event, date, seat, QR code, status chip, button "Baixar PDF".
- **Validation result (operator):** large state card, valid = mint check "Ingresso válido", invalid = pink-red X "Ingresso inválido ou já utilizado".

## 8. Accessibility
- Minimum touch target 44x44px.
- Visible focus ring on every interactive element.
- Do not rely on color alone (icons, patterns, labels).
- Respect `prefers-reduced-motion`: disable pulse and glow animations.

## 9. Do and don't
- Do keep one primary button per view.
- Do use mint only for "available/success" and pink-red only for "taken/error".
- Don't use more than two font families.
- Don't use pure white backgrounds or light themes.
- Don't use English text in the interface.
