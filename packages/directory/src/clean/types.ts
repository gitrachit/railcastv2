// Normalized directory records. Localized-name columns are carried from day
// one (empty in P1) so adding a language is a data fill, not a schema change
// (packages/directory/CLAUDE.md).
export const LOCALE_COLUMNS = [
  "hi", "bn", "ta", "te", "mr", "ml", "kn", "pa", "or", "as", "gu",
] as const;
export type LocaleColumn = (typeof LOCALE_COLUMNS)[number];

export interface StationRecord {
  code: string;
  name: string;
  city: string;
  state: string;
  lat: number | null;
  lng: number | null;
  names: Record<LocaleColumn, string>; // native-script names, empty in P1
}

export interface TrainRecord {
  number: string;
  name: string;
  fromCode: string;
  toCode: string;
  names: Record<LocaleColumn, string>;
}

export function emptyNames(): Record<LocaleColumn, string> {
  return Object.fromEntries(LOCALE_COLUMNS.map((c) => [c, ""])) as Record<LocaleColumn, string>;
}
