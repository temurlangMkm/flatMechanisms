import { loadMechanismConfig } from "./core/configLoader";
import { clearCanvas, MechanismCanvasRenderer } from "./core/renderer";
import { MechanismSimulation } from "./core/simulation";
import type { MechanismConfig } from "./core/types";
import { PanelSwapper } from "./core/panelSwap";
import type { PanelPosition, ViewType } from "./core/viewManager";
import { ViewManager } from "./core/viewManager";
import { radial8Json, sampleJson } from "./fixtures/templates";
import "./styles.css";

const planZoomFactor = 1.15;
const minPlanZoom = 0.2;
const maxPlanZoom = 8;

const viewMeta: Record<ViewType, { title: string; description: string; canvasId: string }> = {
  mechanism: {
    title: "Чертеж механизма",
    description: "Колесо мыши - масштаб, перетаскивание - сдвиг",
    canvasId: "mechanismCanvas",
  },
  velocity: {
    title: "План скоростей",
    description: "Отдельный масштаб и сдвиг сохраняются",
    canvasId: "velocityCanvas",
  },
  acceleration: {
    title: "План ускорений",
    description: "Отдельный масштаб и сдвиг сохраняются",
    canvasId: "accelerationCanvas",
  },
};

const positionLabels: Record<PanelPosition, string> = {
  center: "Главное окно",
  sideTop: "Верхнее дополнительное",
  sideBottom: "Нижнее дополнительное",
};

const viewManager = new ViewManager();
let panelSwapper: PanelSwapper;
let renderer: MechanismCanvasRenderer | null = null;
let simulation: MechanismSimulation | null = null;
let currentConfig: MechanismConfig | null = null;
let currentJsonText: string | null = null;
let running = false;
let frameHandle = 0;
let lastFrame = 0;
let updatingAngleSlider = false;
let activePlanDrag: { viewType: "velocity" | "acceleration"; x: number; y: number } | null = null;

let jsonInput: HTMLTextAreaElement;
let fileInput: HTMLInputElement;
let templateSelect: HTMLSelectElement;
let runButton: HTMLButtonElement;
let restartButton: HTMLButtonElement;
let angleSlider: HTMLInputElement;
let angleValue: HTMLOutputElement;
let statusBox: HTMLElement;
let mechanismCanvas: HTMLCanvasElement;
let velocityCanvas: HTMLCanvasElement;
let accelerationCanvas: HTMLCanvasElement;

function createVisualPanel(viewType: ViewType, position: PanelPosition): HTMLElement {
  const meta = viewMeta[viewType];
  const panel = document.createElement("section");
  panel.className = `panel visual-panel ${position === "center" ? "main-panel active-mobile" : "side-panel"}`;
  panel.dataset.panel = viewType;
  panel.dataset.panelPosition = position;
  panel.innerHTML = `
    <div class="panel-header">
      <div>
        <span class="panel-kicker">${positionLabels[position]}</span>
        <h3>${meta.title}</h3>
      </div>
      <div class="panel-actions">
        ${createPanelActions(position)}
      </div>
    </div>
    <canvas id="${meta.canvasId}" aria-label="${meta.title}"></canvas>
    <div class="panel-hint">${meta.description}</div>
  `;
  return panel;
}

function createPanelActions(position: PanelPosition): string {
  if (position === "center") {
    return `
      <button class="icon-button swap-to-sideTop" type="button" title="Поменять с верхним дополнительным окном" aria-label="Поменять с верхним дополнительным окном">↗</button>
      <button class="icon-button swap-to-sideBottom" type="button" title="Поменять с нижним дополнительным окном" aria-label="Поменять с нижним дополнительным окном">↘</button>
    `;
  }

  return `
    <button class="promote-button swap-to-center" type="button" title="Показать в главном окне" aria-label="Показать в главном окне">
      <span>В главное</span>
      <strong>↔</strong>
    </button>
  `;
}

function buildMainHTML(): void {
  const app = document.querySelector<HTMLDivElement>("#app");
  if (!app) return;

  app.innerHTML = `
    <main class="app-shell">
      <header class="toolbar">
        <div class="toolbar-title">
          <strong>MechIsCool</strong>
          <span>кинематический расчет механизма</span>
        </div>
        <label class="file-button">
          <input id="fileInput" type="file" accept="application/json,.json" />
          Загрузить JSON
        </label>
        <select id="templateSelect" aria-label="Шаблон">
          <option value="">Шаблон</option>
          <option value="sample">Sample</option>
          <option value="radial8">Radial 8</option>
        </select>
        <button id="runButton" class="primary-button" type="button">Запуск</button>
        <button id="restartButton" type="button">Сброс</button>
        <label class="angle-control">
          <span>Угол кривошипа</span>
          <input id="angleSlider" type="range" min="0" max="360" step="1" value="0" />
        </label>
        <output id="angleValue">0 deg</output>
      </header>

      <details class="config-drawer">
        <summary>JSON конфигурация</summary>
        <textarea id="jsonInput" spellcheck="false" aria-label="JSON конфигурация"></textarea>
      </details>

      <nav class="mobile-tabs" aria-label="Окна">
        <button class="tab-button active" data-tab="mechanism" type="button">Чертеж</button>
        <button class="tab-button" data-tab="velocity" type="button">Скорости</button>
        <button class="tab-button" data-tab="acceleration" type="button">Ускорения</button>
      </nav>

      <section id="status" class="status" hidden></section>
      <section id="workspace" class="workspace"></section>
    </main>
  `;

  const workspace = document.getElementById("workspace")!;
  for (const view of viewManager.getAllViews()) {
    workspace.appendChild(createVisualPanel(view.type, view.position));
  }
}

function rebuildLayout(): void {
  buildMainHTML();
  reconnectEventHandlers();
  resizeCanvases();
  redrawCurrentState();
}

function reconnectEventHandlers(): void {
  jsonInput = element<HTMLTextAreaElement>("jsonInput");
  fileInput = element<HTMLInputElement>("fileInput");
  templateSelect = element<HTMLSelectElement>("templateSelect");
  runButton = element<HTMLButtonElement>("runButton");
  restartButton = element<HTMLButtonElement>("restartButton");
  angleSlider = element<HTMLInputElement>("angleSlider");
  angleValue = element<HTMLOutputElement>("angleValue");
  statusBox = element<HTMLElement>("status");
  mechanismCanvas = element<HTMLCanvasElement>("mechanismCanvas");
  velocityCanvas = element<HTMLCanvasElement>("velocityCanvas");
  accelerationCanvas = element<HTMLCanvasElement>("accelerationCanvas");

  if (!renderer) renderer = new MechanismCanvasRenderer();
  renderer.initializeControls(mechanismCanvas, drawRunState);
  attachPlanCanvasControls(velocityCanvas, "velocity");
  attachPlanCanvasControls(accelerationCanvas, "acceleration");

  fileInput.addEventListener("change", async () => {
    const file = fileInput.files?.[0];
    if (!file) return;
    jsonInput.value = await file.text();
    stopAnimation();
    simulation = null;
    currentConfig = null;
    currentJsonText = null;
    templateSelect.value = "";
    showStatus(`Файл загружен: ${file.name}`);
    loadCurrentJson(false);
  });

  templateSelect.addEventListener("change", () => {
    if (templateSelect.value === "sample") jsonInput.value = sampleJson;
    else if (templateSelect.value === "radial8") jsonInput.value = radial8Json;
    else return;
    stopAnimation();
    simulation = null;
    currentConfig = null;
    currentJsonText = null;
    loadCurrentJson(false);
  });

  runButton.addEventListener("click", () => {
    if (running) {
      stopAnimation();
      return;
    }
    if (loadCurrentJson(true)) {
      drawRunState();
      startAnimation();
    }
  });

  restartButton.addEventListener("click", () => {
    stopAnimation();
    if (simulation && currentConfig) {
      simulation.reset();
      updateAngleSlider(simulation.getPhaseDegrees());
      drawRunState();
    } else {
      clearAll();
    }
  });

  angleSlider.addEventListener("input", () => {
    const degrees = Number(angleSlider.value);
    updateAngleLabel(degrees);
    if (updatingAngleSlider || !simulation || !currentConfig) return;
    if (currentJsonText !== jsonInput.value && !loadCurrentJson(true)) return;
    stopAnimation();
    simulation.setPhaseDegrees(degrees);
    drawRunState();
  });

  attachSwapButtonHandlers();
  attachMobileTabs();
  jsonInput.value = currentJsonText || sampleJson;
}

function attachPlanCanvasControls(canvas: HTMLCanvasElement, viewType: "velocity" | "acceleration"): void {
  canvas.addEventListener("pointerdown", (event) => {
    canvas.setPointerCapture(event.pointerId);
    activePlanDrag = { viewType, x: event.offsetX, y: event.offsetY };
  });

  canvas.addEventListener("pointermove", (event) => {
    if (!activePlanDrag || activePlanDrag.viewType !== viewType) return;
    const state = viewManager.getViewState(viewType);
    if (!state) return;
    viewManager.updateViewState(viewType, {
      panX: state.panX + event.offsetX - activePlanDrag.x,
      panY: state.panY + event.offsetY - activePlanDrag.y,
    });
    activePlanDrag = { viewType, x: event.offsetX, y: event.offsetY };
    updateDiagrams();
  });

  const endDrag = () => {
    if (activePlanDrag?.viewType === viewType) activePlanDrag = null;
  };
  canvas.addEventListener("pointerup", endDrag);
  canvas.addEventListener("pointercancel", endDrag);
  canvas.addEventListener(
    "wheel",
    (event) => {
      event.preventDefault();
      const state = viewManager.getViewState(viewType);
      if (!state) return;
      const zoomFactor = event.deltaY < 0 ? planZoomFactor : 1 / planZoomFactor;
      viewManager.updateViewState(viewType, {
        zoom: clamp(state.zoom * zoomFactor, minPlanZoom, maxPlanZoom),
      });
      updateDiagrams();
    },
    { passive: false },
  );
}

function attachSwapButtonHandlers(): void {
  for (const button of document.querySelectorAll<HTMLButtonElement>(".swap-to-center, .swap-to-sideTop, .swap-to-sideBottom")) {
    button.addEventListener("click", handleSwapClick);
  }
}

async function handleSwapClick(event: Event): Promise<void> {
  const button = event.currentTarget as HTMLButtonElement;
  const panel = button.closest<HTMLElement>("[data-panel-position]");
  if (!panel) return;

  const fromPosition = (panel.dataset.panelPosition || "center") as PanelPosition;
  const toPosition = button.classList.contains("swap-to-sideTop")
    ? "sideTop"
    : button.classList.contains("swap-to-sideBottom")
      ? "sideBottom"
      : "center";

  const success = await panelSwapper.swapPanels(fromPosition, toPosition);
  if (success) rebuildLayout();
}

function attachMobileTabs(): void {
  for (const button of document.querySelectorAll<HTMLButtonElement>(".tab-button")) {
    button.addEventListener("click", () => {
      document.querySelectorAll(".tab-button").forEach((item) => item.classList.toggle("active", item === button));
      document.querySelectorAll<HTMLElement>("[data-panel]").forEach((panel) =>
        panel.classList.toggle("active-mobile", panel.dataset.panel === button.dataset.tab),
      );
      resizeCanvases();
      redrawCurrentState();
    });
  }
}

function initialize(): void {
  buildMainHTML();
  panelSwapper = new PanelSwapper({
    viewManager,
    containerId: "workspace",
    onBeforeSwap: () => stopAnimation(),
  });
  reconnectEventHandlers();
  jsonInput.value = sampleJson;
  resizeCanvases();
  clearAll();
  loadCurrentJson(false);

  window.addEventListener("resize", () => {
    resizeCanvases();
    redrawCurrentState();
  });
}

function loadCurrentJson(showErrors: boolean): boolean {
  try {
    const config = loadMechanismConfig(jsonInput.value);
    currentConfig = config;
    currentJsonText = jsonInput.value;
    simulation = new MechanismSimulation(config);
    updateAngleSlider(simulation.getPhaseDegrees());
    drawRunState();
    hideStatus();
    return true;
  } catch (error) {
    if (showErrors) showError(error instanceof Error ? error.message : String(error));
    return false;
  }
}

function drawRunState(): void {
  if (!simulation || !currentConfig || !renderer) return;
  renderer.render(mechanismCanvas, currentConfig, simulation.getPositions());
  updateDiagrams();
}

function updateDiagrams(): void {
  if (!simulation || !currentConfig || !renderer) return;

  const velocityState = viewManager.getViewState("velocity");
  const accelerationState = viewManager.getViewState("acceleration");

  renderer.renderVelocityPlan(
    velocityCanvas,
    currentConfig,
    simulation.getPositions(),
    simulation.getVelocities(),
    simulation.getMaxVelocity(),
    velocityState?.zoom || 1,
    velocityState?.panX || 0,
    velocityState?.panY || 0,
  );

  renderer.renderAccelerationPlan(
    accelerationCanvas,
    currentConfig,
    simulation.getPositions(),
    simulation.getVelocities(),
    simulation.getAccelerations(),
    simulation.getMaxAcceleration(),
    accelerationState?.zoom || 1,
    accelerationState?.panX || 0,
    accelerationState?.panY || 0,
  );
}

function startAnimation(): void {
  lastFrame = 0;
  running = true;
  runButton.textContent = "Пауза";
  const frame = (time: number) => {
    if (!running) return;
    if (lastFrame === 0) lastFrame = time;
    const deltaSeconds = Math.min((time - lastFrame) / 1000, 0.05);
    lastFrame = time;
    if (simulation && currentConfig) {
      simulation.step(deltaSeconds);
      updateAngleSlider(simulation.getPhaseDegrees());
      drawRunState();
    }
    frameHandle = requestAnimationFrame(frame);
  };
  frameHandle = requestAnimationFrame(frame);
}

function stopAnimation(): void {
  running = false;
  lastFrame = 0;
  if (runButton) runButton.textContent = "Запуск";
  cancelAnimationFrame(frameHandle);
}

function updateAngleSlider(degrees: number): void {
  updatingAngleSlider = true;
  angleSlider.value = String(degrees);
  updatingAngleSlider = false;
  updateAngleLabel(degrees);
}

function updateAngleLabel(degrees: number): void {
  angleValue.value = `${degrees.toFixed(0)} deg`;
}

function clearAll(): void {
  if (!renderer) return;
  clearCanvas(mechanismCanvas, "Механизм");
  clearCanvas(velocityCanvas, "План скоростей");
  clearCanvas(accelerationCanvas, "План ускорений");
}

function redrawCurrentState(): void {
  if (simulation && currentConfig) drawRunState();
  else clearAll();
}

function resizeCanvases(): void {
  resizeCanvasToDisplay(mechanismCanvas);
  resizeCanvasToDisplay(velocityCanvas);
  resizeCanvasToDisplay(accelerationCanvas);
}

function resizeCanvasToDisplay(canvas: HTMLCanvasElement): void {
  const rect = canvas.getBoundingClientRect();
  canvas.width = Math.max(1, Math.floor(rect.width));
  canvas.height = Math.max(1, Math.floor(rect.height));
}

function showError(message: string): void {
  stopAnimation();
  statusBox.hidden = false;
  statusBox.className = "status error";
  statusBox.textContent = message;
}

function showStatus(message: string): void {
  statusBox.hidden = false;
  statusBox.className = "status";
  statusBox.textContent = message;
}

function hideStatus(): void {
  statusBox.hidden = true;
}

function element<T extends HTMLElement>(id: string): T {
  const item = document.getElementById(id);
  if (!item) throw new Error(`Missing element #${id}`);
  return item as T;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

initialize();
