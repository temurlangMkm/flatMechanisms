/**
 * Panel Swap Logic
 * Handles the mechanics of swapping panel content while preserving state
 */

import { ViewManager, type ViewType, type PanelPosition } from "./viewManager";

export interface SwapConfig {
  viewManager: ViewManager;
  containerId: string;
  onBeforeSwap?: () => void;
  onAfterSwap?: () => void;
}

/**
 * Manages panel swapping operations
 */
export class PanelSwapper {
  private config: SwapConfig;
  private isSwapping = false;

  constructor(config: SwapConfig) {
    this.config = config;
  }

  /**
   * Swap two panels and rebuild the layout
   * This will preserve the ViewState for both panels
   */
  async swapPanels(
    fromPosition: PanelPosition,
    toPosition: PanelPosition
  ): Promise<boolean> {
    if (this.isSwapping) return false;

    try {
      this.isSwapping = true;

      // Execute before-swap callback
      this.config.onBeforeSwap?.();

      // Perform the swap in view manager
      const swapped = this.config.viewManager.swapViews(
        fromPosition,
        toPosition
      );
      if (!swapped) return false;

      // Apply animation class and wait for transition to complete
      const container = document.getElementById(this.config.containerId);
      if (container) {
        container.classList.add("panel-swap-transition");
        await new Promise((resolve) => setTimeout(resolve, 300));
        container.classList.remove("panel-swap-transition");
      }

      // Execute after-swap callback
      this.config.onAfterSwap?.();

      return true;
    } finally {
      this.isSwapping = false;
    }
  }

  /**
   * Check if a swap is currently in progress
   */
  isSwappingInProgress(): boolean {
    return this.isSwapping;
  }

  /**
   * Get the view type that should be at a specific position
   */
  getViewAtPosition(position: PanelPosition): ViewType | null {
    const view = this.config.viewManager.getViewAtPosition(position);
    return view?.type || null;
  }

  /**
   * Get the position of a specific view type
   */
  getPositionOfView(viewType: ViewType): PanelPosition | null {
    return this.config.viewManager.getPositionOfView(viewType);
  }

  /**
   * Get all current views
   */
  getAllViews() {
    return this.config.viewManager.getAllViews();
  }

  /**
   * Update view state (zoom, pan, etc.)
   */
  updateViewState(viewType: ViewType, state: any): void {
    this.config.viewManager.updateViewState(viewType, state);
  }

  /**
   * Get view state
   */
  getViewState(viewType: ViewType) {
    return this.config.viewManager.getViewState(viewType);
  }
}

/**
 * Simplified function to swap and trigger UI rebuild
 * Returns true if successful
 */
export function performPanelSwap(
  swapper: PanelSwapper,
  from: PanelPosition,
  to: PanelPosition
): Promise<boolean> {
  return swapper.swapPanels(from, to);
}
