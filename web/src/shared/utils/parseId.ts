export function parsePositiveIntegerParam(value: string | undefined): number | null {
  if (!value || !/^[0-9]+$/.test(value)) {
    return null;
  }

  const parsedValue = Number(value);

  if (!Number.isSafeInteger(parsedValue) || parsedValue <= 0) {
    return null;
  }

  return parsedValue;
}
