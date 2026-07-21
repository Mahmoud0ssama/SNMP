package com.snmp.emulator;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class EmulatorGUI extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load the UI layout from the resources folder
            Parent root = FXMLLoader.load(getClass().getResource("/dashboard.fxml"));
            
            // Apply the layout to the main window
            Scene scene = new Scene(root);
            primaryStage.setTitle("Telecom Node Emulator");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Fatal Error: Could not load the JavaFX dashboard.fxml file.");
        }
    }
}