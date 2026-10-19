import { MapView } from "./map/MapView";
import { SidePanel } from "./panel/SidePanel";
import { TimeBar } from "./timebar/TimeBar";

export function App() {
  return (
    <div className="app">
      <MapView />
      <SidePanel />
      <TimeBar />
    </div>
  );
}
