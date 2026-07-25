import type { SurfaceViewModel, TeamsSurface } from "../domain/types.js";

const SURFACE_SECTIONS: Record<TeamsSurface, string[]> = {
  "details-tab": ["agenda", "open-tasks", "shared-note", "private-note", "markers"],
  "side-panel": ["markers", "shared-note", "open-tasks", "agenda"],
  "chat-tab": ["markers", "shared-note", "private-note", "open-tasks"],
};

const SURFACE_TITLES: Record<TeamsSurface, string> = {
  "details-tab": "Meeting Details",
  "side-panel": "Meeting Side Panel",
  "chat-tab": "Meeting Chat",
};

export function buildSurfaceViewModel(surface: TeamsSurface, meetingId: string): SurfaceViewModel {
  return {
    surface,
    title: SURFACE_TITLES[surface],
    meetingId,
    sections: SURFACE_SECTIONS[surface],
  };
}

export function renderSurfaceHtml(view: SurfaceViewModel): string {
  const sections = view.sections
    .map((section) => `<section data-section="${section}"><h2>${label(section)}</h2></section>`)
    .join("");
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <title>${view.title} · Actenora</title>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
</head>
<body data-surface="${view.surface}" data-meeting-id="${view.meetingId}">
  <header><h1>${view.title}</h1></header>
  <main>${sections}</main>
</body>
</html>`;
}

function label(section: string): string {
  return section
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}
