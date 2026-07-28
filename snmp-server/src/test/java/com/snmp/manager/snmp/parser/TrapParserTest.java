package com.snmp.manager.snmp.parser;

import com.snmp.manager.snmp.model.TrapEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;

import static org.junit.jupiter.api.Assertions.*;

class TrapParserTest {

    private final TrapParser parser = new TrapParser();

    @SuppressWarnings("unchecked")
    private CommandResponderEvent<Address> mockEvent(String source, byte[] securityName, int msgId, PDU pdu) {
        CommandResponderEvent<Address> event = Mockito.mock(CommandResponderEvent.class);
        Address address = Mockito.mock(Address.class);
        Mockito.when(address.toString()).thenReturn(source);
        Mockito.when(event.getPeerAddress()).thenReturn(address);
        Mockito.when(event.getSecurityName()).thenReturn(securityName);
        Mockito.when(event.getMessageProcessingModel()).thenReturn(msgId);
        Mockito.when(event.getPDU()).thenReturn(pdu);
        return event;
    }

    @Test
    void parse_nullEvent_throws() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void parse_setsBasicFields() {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID("1.3.6.1.6.3.1.1.4.1.0"), new OID("1.3.6.1.6.3.1.1.5.1")));

        CommandResponderEvent<Address> event = mockEvent("192.168.1.10/162", new byte[]{'p','u','b'}, 1, pdu);

        TrapEvent result = parser.parse(event);

        assertNotNull(result);
        assertEquals("192.168.1.10/162", result.getSourceIp());
        assertEquals("1.3.6.1.6.3.1.1.5.1", result.getTrapOid());
        assertEquals("pub", result.getCommunity());
        assertEquals("SNMPv2c", result.getVersion());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void parse_snmpTrapOidFallbackToFirstVarbind() {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.1.3.0"), new OctetString("uptime")));

        CommandResponderEvent<Address> event = mockEvent("10.0.0.1/1162", new byte[]{'p','u','b'}, 3, pdu);

        TrapEvent result = parser.parse(event);

        assertEquals("SNMPv3", result.getVersion());
        assertTrue(result.getVariableBindings().containsKey("1.3.6.1.2.1.1.3.0"));
    }
}
