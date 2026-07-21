package com.snmp.emulator;

/**
 * Predefined telecom alarm types, each mapped to a unique SNMP Trap OID.
 *
 * <p>OIDs follow the enterprise notification branch convention:
 * {@code 1.3.6.1.4.1.99999.0.<id>}</p>
 */
public enum AlarmType {

    DISK_FULL("Disk Full", "1.3.6.1.4.1.99999.0.1"),
    POWER_FAILURE("Power Failure", "1.3.6.1.4.1.99999.0.2"),
    LINK_DOWN("Link Down", "1.3.6.1.4.1.99999.0.3"),
    CONGESTION("Congestion", "1.3.6.1.4.1.99999.0.4"),
    HIGH_TEMPERATURE("High Temperature", "1.3.6.1.4.1.99999.0.5"),
    MEMORY_EXHAUSTION("Memory Exhaustion", "1.3.6.1.4.1.99999.0.6"),
    CONFIG_ERROR("Configuration Error", "1.3.6.1.4.1.99999.0.7");

    private final String displayName;
    private final String oid;

    AlarmType(String displayName, String oid) {
        this.displayName = displayName;
        this.oid = oid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getOid() {
        return oid;
    }

    /**
     * Finds an AlarmType by its display name.
     *
     * @param displayName the display name to search for
     * @return the matching AlarmType
     * @throws IllegalArgumentException if no match is found
     */
    public static AlarmType fromDisplayName(String displayName) {
        for (AlarmType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown alarm type: " + displayName);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
