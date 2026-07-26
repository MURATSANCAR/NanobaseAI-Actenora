import { useEffect, useRef, useState } from "react";
import type { DesignComponent } from "@/types/template";
import {
  A4_PAGE_HEIGHT_PX,
  A4_PAGE_WIDTH_PX,
  TemplateDocumentContent,
} from "@/components/template/TemplateDocumentContent";
import { useI18n } from "@/i18n";

const STAGE_PAD_X = 32;
/** Prefer filling stage width; allow modest upscale so the page reads larger on wide screens. */
const MAX_SCALE = 1.15;

/** Isolated fullscreen A4 preview — scaled to fit stage width, content never clipped. */
export function TemplateDocumentPreviewStage({ components }: { components: DesignComponent[] }) {
  const { t } = useI18n();
  const stageRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;

    const update = () => {
      const w = stage.clientWidth - STAGE_PAD_X;
      if (w <= 0) return;
      // Fill stage width; page may scroll vertically inside the stage.
      const fit = w / A4_PAGE_WIDTH_PX;
      setScale(Math.max(0.35, Math.min(fit, MAX_SCALE)));
    };

    update();
    const ro = new ResizeObserver(update);
    ro.observe(stage);
    window.addEventListener("resize", update);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", update);
    };
  }, [components.length]);

  const scaledW = A4_PAGE_WIDTH_PX * scale;
  const scaledH = A4_PAGE_HEIGHT_PX * scale;

  return (
    <section
      className="flex min-h-[calc(100dvh-11rem)] flex-col overflow-hidden rounded-2xl border border-slate-300/80 bg-slate-400/30 shadow-inner"
      aria-label={t("templates.preview.title")}
    >
      <header className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-300/60 bg-slate-900/90 px-4 py-2.5 text-white backdrop-blur">
        <div>
          <h3 className="text-sm font-bold tracking-tight">{t("templates.preview.title")}</h3>
          <p className="text-[11px] text-white/60">{t("templates.preview.hint")}</p>
        </div>
        <span className="rounded-full bg-white/10 px-3 py-1 text-[10px] font-semibold uppercase tracking-wider text-white/80 ring-1 ring-white/20">
          A4 · {Math.round(scale * 100)}%
        </span>
      </header>

      <div
        ref={stageRef}
        className="relative flex min-h-0 flex-1 items-start justify-center overflow-auto p-4 sm:p-5"
      >
        <div
          className="shrink-0"
          style={{ width: scaledW, height: scaledH }}
        >
          <div
            className="origin-top-left overflow-hidden rounded-sm bg-white shadow-2xl ring-1 ring-slate-900/10"
            style={{
              width: A4_PAGE_WIDTH_PX,
              height: A4_PAGE_HEIGHT_PX,
              transform: `scale(${scale})`,
            }}
          >
            <div className="h-full overflow-hidden p-7">
              <TemplateDocumentContent components={components} density="normal" />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
