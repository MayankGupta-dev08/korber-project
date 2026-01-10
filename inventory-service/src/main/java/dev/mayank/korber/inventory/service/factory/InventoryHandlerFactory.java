package dev.mayank.korber.inventory.service.factory;

import org.springframework.stereotype.Component;

@Component
public class InventoryHandlerFactory {
    private final DefaultInventoryHandler defaultInventoryHandler;

    public InventoryHandlerFactory(DefaultInventoryHandler defaultInventoryHandler) {
        this.defaultInventoryHandler = defaultInventoryHandler;
    }

    /**
     * Get handler based on type
     * Can be extended to support:
     * - "FIFO" -> DefaultInventoryHandler
     * - "LIFO" -> LifoInventoryHandler
     * - "PRIORITY" -> PriorityInventoryHandler
     * - "WAREHOUSE" -> WarehouseSpecificHandler
     *
     * @param handlerType Type of handler
     * @return Appropriate inventory handler
     */
    public InventoryHandler getInventoryHandler(String handlerType) {
        InventoryHandler handler  = switch (handlerType) {
            default -> defaultInventoryHandler;
        };
        return handler;
    }

    /**
     * Get default handler (FIFO)
     * @return Default inventory handler
     */
    public InventoryHandler getDefaultHandler() {
        return defaultInventoryHandler;
    }
}
