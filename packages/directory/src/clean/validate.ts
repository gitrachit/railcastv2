// Code validation for the clean stage: train 5 digits, station 2–5 uppercase letters.
export function isValidTrainNo(value: string): boolean {
  return /^\d{5}$/.test(value);
}

export function isValidStationCode(value: string): boolean {
  return /^[A-Z]{2,5}$/.test(value);
}
