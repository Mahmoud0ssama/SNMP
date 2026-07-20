package com.snmp.manager.snmp.parser;

import com.snmp.manager.snmp.model.TrapEvent;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts SNMP4J {@link CommandResponderEvent} objects into the application's
 * {@link TrapEvent} domain model.
 *
 * <p>This class only performs mapping. It performs no database access and no
 * business logic.</p>
 */
public class TrapParser {

    private static final OID SNMP_TRAP_OID = new OID("1.3.6.1.6.3.1.1.4.1.0");

    /**
     * Parses a raw SNMP4J event into a {@link TrapEvent}.
     *
     * @param event the SNMP4J command responder event, must not be {@code null}
     * @return the parsed trap event
     */
    public TrapEvent parse(CommandResponderEvent<?> event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        PDU pdu = event.getPDU();
        String sourceIp = String.valueOf(event.getPeerAddress());
        String community = decodeCommunity(event.getSecurityName());
        String version = resolveVersion(event.getMessageProcessingModel());
        String trapOid = resolveTrapOid(pdu);
        Map<String, String> variableBindings = extractVariableBindings(pdu);

        return new TrapEvent(sourceIp, trapOid, Instant.now(), community, version, variableBindings);
    }

    private String decodeCommunity(byte[] securityName) {
        if (securityName == null) {
            return "";
        }
        return new String(securityName, StandardCharsets.UTF_8);
    }

    private String resolveVersion(int messageProcessingModel) {
        if (messageProcessingModel == SnmpConstants.version3) {
            return "SNMPv3";
        }
        if (messageProcessingModel == SnmpConstants.version1) {
            return "SNMPv1";
        }
        return "SNMPv2c";
    }

    private String resolveTrapOid(PDU pdu) {
        if (pdu == null) {
            return "";
        }
        for (VariableBinding vb : pdu.getVariableBindings()) {
            if (SNMP_TRAP_OID.equals(vb.getOid())) {
                return String.valueOf(vb.getVariable());
            }
        }
        List<? extends VariableBinding> bindings = pdu.getVariableBindings();
        if (!bindings.isEmpty()) {
            return String.valueOf(bindings.get(0).getOid());
        }
        return "";
    }

    private Map<String, String> extractVariableBindings(PDU pdu) {
        Map<String, String> bindings = new LinkedHashMap<>();
        if (pdu == null) {
            return bindings;
        }
        for (VariableBinding vb : pdu.getVariableBindings()) {
            bindings.put(String.valueOf(vb.getOid()), String.valueOf(vb.getVariable()));
        }
        return bindings;
    }
}
