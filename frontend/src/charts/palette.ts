/**
 * Chart colors. One hue for every single-series chart (the dashboard has
 * no multi-series chart, so nothing needs a categorical palette). The
 * steps come from the dataviz reference palette and were validated
 * against this app's real card surfaces with the skill's validator:
 *
 *   node validate_palette.js "#2a78d6" --mode light --surface "#ffffff"
 *     -> lightness band, chroma floor, contrast vs surface: ALL CHECKS PASS
 *   node validate_palette.js "#3987e5" --mode dark  --surface "#171e27"
 *     -> ALL CHECKS PASS
 *   node validate_palette.js "#86b6ef,#5598e7,#256abf,#104281" --ordinal --mode light
 *     -> lightness monotone, adjacent gaps, light-end contrast: ALL CHECKS PASS
 *
 * Text on charts always uses the app's text tokens, never the series color.
 */
export const SERIES = {
  light: '#2a78d6',
  dark: '#3987e5',
};

/** One-hue ordinal ramp, light -> dark, for "how long has this waited". */
export const ORDINAL = ['#86b6ef', '#5598e7', '#256abf', '#104281'];

export function seriesColor(): string {
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
    return SERIES.dark;
  }
  return SERIES.light;
}
