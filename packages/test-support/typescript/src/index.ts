export function assertDefined<T>(value: T | null | undefined, label = "value"): T {
  if (value === null || value === undefined) {
    throw new Error(`${label} must be defined`);
  }
  return value;
}
