export type LogLevel = "DEBUG" | "INFO" | "WARN" | "ERROR";

export interface LogFields {
  [key: string]: string | number | boolean | undefined;
}

export function formatLog(
  service: string,
  level: LogLevel,
  message: string,
  fields: LogFields = {},
): string {
  const payload = {
    ts: new Date().toISOString(),
    service,
    level,
    message,
    ...fields,
  };
  return JSON.stringify(payload);
}
