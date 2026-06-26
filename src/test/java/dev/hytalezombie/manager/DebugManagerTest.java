package dev.hytalezombie.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugManager")
class DebugManagerTest {

    private DebugManager debugManager;

    @BeforeEach
    void setUp() {
        debugManager = new DebugManager();
    }

    @Test
    @DisplayName("should start with all layers inactive")
    void initialState() {
        assertFalse(debugManager.isDebugMode());
        assertFalse(debugManager.isLayerActive(DebugManager.DebugLayer.SPAWNS));
        assertFalse(debugManager.isLayerActive(DebugManager.DebugLayer.BARRIERS));
        assertFalse(debugManager.isLayerActive(DebugManager.DebugLayer.ZONES));
    }

    @Test
    @DisplayName("should toggle all layers on")
    void toggleAll_on() {
        assertTrue(debugManager.toggleAll());
        assertTrue(debugManager.isLayerActive(DebugManager.DebugLayer.SPAWNS));
        assertTrue(debugManager.isLayerActive(DebugManager.DebugLayer.BARRIERS));
        assertTrue(debugManager.isLayerActive(DebugManager.DebugLayer.ZONES));
    }

    @Test
    @DisplayName("should toggle all layers off when any are active")
    void toggleAll_off() {
        debugManager.toggleAll();
        assertFalse(debugManager.toggleAll());
        assertFalse(debugManager.isDebugMode());
    }

    @Test
    @DisplayName("should toggle individual layers")
    void toggleLayer() {
        assertTrue(debugManager.toggleLayer("spawns"));
        assertTrue(debugManager.isLayerActive(DebugManager.DebugLayer.SPAWNS));
        assertFalse(debugManager.isLayerActive(DebugManager.DebugLayer.BARRIERS));

        assertFalse(debugManager.toggleLayer("spawns"));
        assertFalse(debugManager.isLayerActive(DebugManager.DebugLayer.SPAWNS));
    }

    @Test
    @DisplayName("should ignore unknown layer names")
    void toggleLayer_unknown() {
        assertFalse(debugManager.toggleLayer("invalid"));
    }

    @Test
    @DisplayName("should set layer state explicitly")
    void setLayerActive() {
        debugManager.setLayerActive(DebugManager.DebugLayer.ZONES, true);
        assertTrue(debugManager.isLayerActive(DebugManager.DebugLayer.ZONES));
        debugManager.setLayerActive(DebugManager.DebugLayer.ZONES, false);
        assertFalse(debugManager.isLayerActive(DebugManager.DebugLayer.ZONES));
    }
}
