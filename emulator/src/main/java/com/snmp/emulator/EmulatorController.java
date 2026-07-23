package com.snmp.emulator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class EmulatorController {

    // These variables link directly to the fx:id attributes in the dashboard.fxml file
    @FXML
    private TextField nodeNameField;
    @FXML
    private TextField nodeIpField;
    @FXML
    private ComboBox<String> nodeTypeDropdown;
    @FXML
    private ComboBox<String> alarmTypeDropdown;
    @FXML
    private TextField ipField;
    @FXML
    private TextField portField;
    @FXML
    private TextField detailsField;
    @FXML
    private TextArea consoleOutput;
    @FXML
    private VBox rootPane;
    @FXML
    private Button themeButton;

    private boolean isDarkMode = true;

    // This method runs automatically the moment the GUI appears on screen
    @FXML
    public void initialize() {
        ipField.setText("127.0.0.1");
        portField.setText("1162");
        nodeIpField.setText("10.0.0.5");

        nodeTypeDropdown.getItems().addAll("BTS", "BSC", "MSC", "HLR", "VLR", "SGSN", "GGSN");
        nodeTypeDropdown.setValue("BTS");

        for (AlarmType type : AlarmType.values()) {
            alarmTypeDropdown.getItems().add(type.getDisplayName());
        }
        alarmTypeDropdown.setValue(AlarmType.DISK_FULL.getDisplayName());

        logToConsole("System Initialized. Ready to send traps.");
    }

    @FXML
    public void onSendButtonClicked() {
        String nodeName = nodeNameField.getText();
        String nodeType = nodeTypeDropdown.getValue();
        String nodeIp = nodeIpField.getText();
        String alarmTypeDisplay = alarmTypeDropdown.getValue();
        String details = detailsField.getText();
        String targetIp = ipField.getText();
        int targetPort;

        if (nodeName == null || nodeName.trim().isEmpty()) {
            logToConsole("Error: Node Name cannot be empty.");
            return;
        }

        String ipRegex = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$";
        if (nodeIp == null || !nodeIp.matches(ipRegex)) {
            logToConsole("Error: Simulated IP must be a valid IPv4 address.");
            return;
        }

        if (alarmTypeDisplay == null || alarmTypeDisplay.trim().isEmpty()) {
            logToConsole("Error: Alarm Type must be selected.");
            return;
        }

        try {
            targetPort = Integer.parseInt(portField.getText());
            if (targetPort < 1 || targetPort > 65535) {
                logToConsole("Error: Port must be between 1 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            logToConsole("Error: Port must be a valid numeric value.");
            return;
        }

        if (!targetIp.matches(ipRegex)) {
            logToConsole("Error: Target IP must be a valid IPv4 address.");
            return;
        }

        try {
            AlarmType alarmType = AlarmType.fromDisplayName(alarmTypeDisplay);

            logToConsole("Transmitting " + nodeName 
                    + " (" + nodeType + " at " + nodeIp + ") [" + alarmType.getDisplayName() + "] trap to " + targetIp 
                    + ":" + targetPort + "...");
            
            SnmpService snmp = new SnmpService();
            snmp.sendTrap(nodeName, nodeType, nodeIp, alarmType, details, targetIp, targetPort);

            logToConsole("SUCCESS: Trap sent to network interface.");

            detailsField.clear();

        } catch (Exception e) {
            logToConsole("FATAL: Failed to send trap. " + e.getMessage());
        }
    }

    @FXML
    public void onThemeToggleClicked() {
        isDarkMode = !isDarkMode;
        rootPane.getStylesheets().clear();

        if (isDarkMode) {
            rootPane.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
            themeButton.setText("☀️ Light Mode");
        } else {
            rootPane.getStylesheets().add(getClass().getResource("/light-theme.css").toExternalForm());
            themeButton.setText("🌙 Dark Mode");
        }
    }

    // Helper method to write messages to the GUI console box
    private void logToConsole(String logMessage) {
        consoleOutput.appendText(logMessage + "\n");
    }
}
