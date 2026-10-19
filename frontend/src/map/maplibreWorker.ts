import { setWorkerUrl } from "maplibre-gl";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url";

// MapLibre finds its worker next to its own package files (maplibre-gl-worker.mjs). The dev
// server serves those as they are, but a production build bundles MapLibre into an asset and
// never emits the worker, so every map tile fails with "Worker failed to load". Vite's
// `?worker&url` bundles the worker into one self-contained file and gives its URL instead.
if (import.meta.env.PROD) {
  setWorkerUrl(workerUrl);
}
