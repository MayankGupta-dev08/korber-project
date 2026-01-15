package dev.mayank.korber.inventory.service.factory;

import org.springframework.stereotype.Component;

@Component
public class InventoryHandlerFactory {
    private static final String LIFO = "LIFO";
    private static final String FIFO = "FIFO";

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
        return switch (handlerType) {
            case FIFO -> new DefaultInventoryHandler();
            case LIFO -> new LIFOInventoryHandler();
            default -> defaultInventoryHandler;
        };
    }

    /**
     * Get default handler (FIFO)
     * @return Default inventory handler
     */
    public InventoryHandler getDefaultHandler() {
        return defaultInventoryHandler;
    }
}
