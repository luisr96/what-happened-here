import { useId, useState } from "react";

export function SidePanel() {
  const [open, setOpen] = useState(true);
  const contentId = useId();

  return (
    <aside className="side-panel" aria-label="Filters and layers" data-open={open}>
      <div id={contentId} className="side-panel__content" hidden={!open}>
        <section>
          <h2>Filters</h2>
        </section>
        <section>
          <h2>Layers</h2>
        </section>
      </div>
      <button
        type="button"
        className="side-panel__toggle"
        aria-expanded={open}
        aria-controls={contentId}
        aria-label="Filters and layers panel"
        onClick={() => setOpen(!open)}
      >
        {open ? "‹" : "›"}
      </button>
    </aside>
  );
}
