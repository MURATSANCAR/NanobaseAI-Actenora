import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { formatLog } from "@actenora/observability";
import "./styles.css";

console.log(formatLog("web-portal", "INFO", "boot"));

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
