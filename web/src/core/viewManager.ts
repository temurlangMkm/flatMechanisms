/**
 * View Management System
 * Handles the state and positioning of visual views
 * Preserves zoom, pan, and other canvas-specific state across panel swaps
 */

export type ViewType = "mechanism" | "velocity" | "acceleration";
export type PanelPosition = "center" | "sideTop" | "sideBottom";

/**
 * State of a view (zoom level, pan offset, etc.)
 */
export interface ViewState {
  type: ViewType;
  zoom: number;
  panX: number;
  panY: number;
  timestamp: number; // For debugging/logging
}

/**
 * Complete view with its current position and state
 */
export interface View {
  type: ViewType;
  position: PanelPosition;
  state: ViewState;
}

/**
 * Manages all views and their states
 */
export class ViewManager {
  private views: Map<ViewType, ViewState> = new Map();
  private positionMap: Map<PanelPosition, ViewType> = new Map();

  constructor() {
    // Initialize default positions and states
    this.views.set("mechanism", {
      type: "mechanism",
      zoom: 1,
      panX: 0,
      panY: 0,
      timestamp: Date.now(),
    });

    this.views.set("velocity", {
      type: "velocity",
      zoom: 1,
      panX: 0,
      panY: 0,
      timestamp: Date.now(),
    });

    this.views.set("acceleration", {
      type: "acceleration",
      zoom: 1,
      panX: 0,
      panY: 0,
      timestamp: Date.now(),
    });

    // Default positions
    this.positionMap.set("center", "mechanism");
    this.positionMap.set("sideTop", "velocity");
    this.positionMap.set("sideBottom", "acceleration");
  }

  /**
   * Get the view at a specific position
   */
  getViewAtPosition(position: PanelPosition): View | null {
    const viewType = this.positionMap.get(position);
    if (!viewType) return null;

    const state = this.views.get(viewType);
    if (!state) return null;

    return { type: viewType, position, state };
  }

  /**
   * Get the position of a specific view type
   */
  getPositionOfView(viewType: ViewType): PanelPosition | null {
    for (const [pos, type] of this.positionMap) {
      if (type === viewType) return pos;
    }
    return null;
  }

  /**
   * Update the state of a view
   */
  updateViewState(viewType: ViewType, state: Partial<ViewState>): void {
    const current = this.views.get(viewType);
    if (!current) return;

    this.views.set(viewType, {
      ...current,
      ...state,
      type: viewType,
      timestamp: Date.now(),
    });
  }

  /**
   * Get the current state of a view
   */
  getViewState(viewType: ViewType): ViewState | null {
    return this.views.get(viewType) || null;
  }

  /**
   * Swap two views at different positions
   * Returns true if swap was successful, false otherwise
   */
  swapViews(
    position1: PanelPosition,
    position2: PanelPosition
  ): boolean {
    const view1 = this.positionMap.get(position1);
    const view2 = this.positionMap.get(position2);

    if (!view1 || !view2) return false;
    if (view1 === view2) return false;

    // Swap positions
    this.positionMap.set(position1, view2);
    this.positionMap.set(position2, view1);

    return true;
  }

  /**
   * Get all current views with their positions
   */
  getAllViews(): View[] {
    const result: View[] = [];
    for (const [position, viewType] of this.positionMap) {
      const state = this.views.get(viewType);
      if (state) {
        result.push({ type: viewType, position, state });
      }
    }
    return result;
  }

  /**
   * Reset zoom and pan for a specific view
   */
  resetViewState(viewType: ViewType): void {
    this.updateViewState(viewType, {
      zoom: 1,
      panX: 0,
      panY: 0,
    });
  }

  /**
   * Reset all views to default states
   */
  resetAllViewStates(): void {
    for (const viewType of this.views.keys()) {
      this.resetViewState(viewType as ViewType);
    }
  }
}
