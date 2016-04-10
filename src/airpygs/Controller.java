package airpygs;

import airpygs.aplink.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.stage.DirectoryChooser;
import jssc.SerialPortException;
import jssc.SerialPortList;

import org.apache.commons.io.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

class FileTypesFilter implements FileFilter {

    String[] types;

    FileTypesFilter(String[] types) {
        this.types = types;
    }

    public boolean accept(File f) {
        if (f.isDirectory()) {
            if (f.getName().startsWith("."))
                return false;
            else
                return true;
        }
        for (String type : types) {
            if (f.getName().endsWith(type)) return true;
        }
        return false;
    }
}

public class Controller implements Initializable {

    serialHandler cli;
    RxDecoder rxdec = null;
    TxEncoder txenc = null;
    ApBuffer buffer;
    boolean cliConnected = false;
    boolean isRxCalibrating = false;
    boolean isImuCalibrating = false;
    File airPyDestinationFolder = null;
    File airPySourceFolder = null;
    String[] serialPorts = null;
    Rotate rxBox = new Rotate(0, 0, 0, 0, Rotate.X_AXIS);
    Rotate ryBox = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS);
    Rotate rzBox = new Rotate(0, 0, 0, 0, Rotate.Z_AXIS);
    PhongMaterial blueMaterial = new PhongMaterial(Color.BLUE);
    PhongMaterial redMaterial = new PhongMaterial(Color.RED);
    PhongMaterial greenMaterial = new PhongMaterial(Color.GREEN);
    PhongMaterial grayMaterial = new PhongMaterial(Color.GRAY);
    boolean toggleFlag = false;
    XYChart.Series pitchSeries;
    XYChart.Series rollSeries;
    XYChart.Series motor1Series;
    XYChart.Series motor2Series;
    XYChart.Series motor3Series;
    XYChart.Series motor4Series;
    int xAxisDefault = 50;
    int xAxisSamplesCount = 0;

    //Rc Calibration Specific
    int minValCh1 = 2047;
    int minValCh2 = 2047;
    int minValCh3 = 2047;
    int minValCh4 = 2047;
    int maxValCh1 = 0;
    int maxValCh2 = 0;
    int maxValCh3 = 0;
    int maxValCh4 = 0;
    int centerValCh1 = 1023;
    int centerValCh2 = 1023;
    int centerValCh3 = 1023;
    int centerValCh4 = 1023;
    SimpleStringProperty sMinValCh1 = new SimpleStringProperty("");
    SimpleStringProperty sMinValCh2 = new SimpleStringProperty("");
    SimpleStringProperty sMinValCh3 = new SimpleStringProperty("");
    SimpleStringProperty sMinValCh4 = new SimpleStringProperty("");
    SimpleStringProperty sCenterValCh1 = new SimpleStringProperty("");
    SimpleStringProperty sCenterValCh2 = new SimpleStringProperty("");
    SimpleStringProperty sCenterValCh3 = new SimpleStringProperty("");
    SimpleStringProperty sCenterValCh4 = new SimpleStringProperty("");
    SimpleStringProperty sMaxValCh1 = new SimpleStringProperty("");
    SimpleStringProperty sMaxValCh2 = new SimpleStringProperty("");
    SimpleStringProperty sMaxValCh3 = new SimpleStringProperty("");
    SimpleStringProperty sMaxValCh4 = new SimpleStringProperty("");


    @FXML
    private LineChart chartAttitude, chartMotors;

    @FXML
    private Sphere ledIndicator;

    @FXML
    private Button buttonEscCalibration, buttonImu, buttonCalibration, bConnect, bUpdate, buttonSavePIDs;

    @FXML
    private Label lbCh1Min, lbCh2Min, lbCh3Min, lbCh4Min, lbCh5Min;

    @FXML
    private Label lbCh1Max, lbCh2Max, lbCh3Max, lbCh4Max, lbCh5Max;

    @FXML
    private Label lbCh1Center, lbCh2Center, lbCh3Center, lbCh4Center, lbCh5Center;

    @FXML
    private Box imuBox;

    @FXML
    private Label labelPitch, labelRoll, labelYaw;

    @FXML
    private TabPane apTabPane;

    @FXML
    private Tab imuTab;

    @FXML
    private ProgressBar pbCh1, pbCh2, pbCh3, pbCh4, pbCh5;

    @FXML
    private TextArea cliConsole;

    @FXML
    private TextField txtKp, txtKd, txtKi, txtMaxIncrement;

    @FXML
    private ChoiceBox serialCombo, baudRateCombo;

    @FXML
    private CheckBox checkBoxPitch, checkBoxRoll;

    @FXML
    private CheckBox checkBoxMotor1, checkBoxMotor2, checkBoxMotor3, checkBoxMotor4;

    @FXML
    private void handleButtonSavePIDs(final  ActionEvent event) {
        float[] pids = {Float.parseFloat(txtKp.getText()),
                        Float.parseFloat(txtKd.getText()),
                        Float.parseFloat(txtKi.getText()),
                        Float.parseFloat(txtMaxIncrement.getText())
        };


        txenc.savePidSettings(pids);

        /*for (int i= 0; i < pids.length; i++) {
            System.out.println(pids[i]);
        }*/
    }

    @FXML
    private void handleSetSourceAction(final ActionEvent event)
    {
        DirectoryChooser folderChooser = new DirectoryChooser();
        folderChooser.setInitialDirectory(airPyDestinationFolder);
        airPySourceFolder = folderChooser.showDialog(null);
        updateButtons();
    }

    @FXML
    private void handleSetDestinationAction(final ActionEvent event)
    {
        DirectoryChooser folderChooser = new DirectoryChooser();
        folderChooser.setInitialDirectory(airPyDestinationFolder);
        airPyDestinationFolder = folderChooser.showDialog(null);
        updateButtons();
    }

    @FXML
    private void handleConnectButtonAction(final ActionEvent event)
    {
        if (!cliConnected) {
            connect();
        } else {
            disconnect();
        }
    }

    @FXML
    private void handleUpdateButtonAction(final ActionEvent event)
    {
        try {
            String[] types = {"py"};
            FileFilter filter = new FileTypesFilter(types);
            FileUtils.copyDirectory(airPySourceFolder, airPyDestinationFolder, filter);
        } catch (IOException e) {
            e.printStackTrace();
        }
        cliConsole.setText("Code updated\n\n");
    }

    @FXML
    private void handleRefreshButtonAction(final ActionEvent event)
    {
        updateComPortList();
        updateButtons();
    }

    @FXML
    private void handleCalibrationButtonAction(final ActionEvent event) {
        if (cliConnected & !isRxCalibrating) {
            txenc.enableMessage(ApLinkParams.AP_MESSAGE_RC_INFO);
            buttonCalibration.setText("Stop Calibration");
            isRxCalibrating = true;
        } else if(cliConnected & isRxCalibrating) {
            txenc.disableMessage(ApLinkParams.AP_MESSAGE_RC_INFO);
            buttonCalibration.setText("Rx Calibration");
            isRxCalibrating = false;
        }

    }

    @FXML
    private void handleImuButtonAction(final ActionEvent event) {

        if (cliConnected & !isImuCalibrating) {
            txenc.enableMessage(ApLinkParams.AP_MESSAGE_IMU_STATUS);
            buttonImu.setText("Stop IMU Calibration");
            isImuCalibrating = true;
        } else if(cliConnected & isImuCalibrating) {
            txenc.disableMessage(ApLinkParams.AP_MESSAGE_IMU_STATUS);
            buttonImu.setText("Start IMU Calibration");
            isImuCalibrating = false;
        }

    }

    @FXML
    private void handleEnableEscCalibrationAction(final ActionEvent event) {
        if (cliConnected & !isRxCalibrating) {
            txenc.enableEscCalibration();
        }

    }

    @FXML
    private void handleCheckBoxPitch(final ActionEvent event) {

        /*if (!checkBoxPitch.isSelected()) {

            chartAttitude.getData().remove(0);
            checkBoxPitch.setSelected(false);

        } else {

            chartAttitude.getData().add(0,pitchSeries);
            checkBoxPitch.setSelected(true);

        }*/

        System.out.println("Checbox Pitch:" + checkBoxPitch.isSelected());
    }

    @FXML
    private void handleCheckBoxRoll(final ActionEvent event) {

    }

    @FXML
    private void handleCheckBoxMotor1(final ActionEvent event) {

    }

    @FXML
    private void handleCheckBoxMotor2(final ActionEvent event) {

    }

    @FXML
    private void handleCheckBoxMotor3(final ActionEvent event) {

    }

    @FXML
    private void handleCheckBoxMotor4(final ActionEvent event) {

    }

    public Label getLabelPitch(){

        return labelPitch;
    }
    public Label getLabelRoll(){

        return labelRoll;
    }
    public Label getLabelYaw(){
        return labelYaw;
    }

    public void setConnectLed(ConnectLed led) {

        switch (led) {

            case ON:        ledIndicator.setMaterial(greenMaterial);
                            break;

            case OFF:       //ledIndicator.setMaterial(redMaterial);
                            ledIndicator.setMaterial(grayMaterial);
                            break;

            case TOGGLE:    if (toggleFlag) {
                ledIndicator.setMaterial(greenMaterial);
                toggleFlag = false;
            } else {
                //ledIndicator.setMaterial(redMaterial);
                ledIndicator.setMaterial(grayMaterial);
                toggleFlag = true;
            }
                break;
        }
    }

    private void disconnect() {
        try {
            rxdec.stopRxDecoder();
            txenc = null;
            cli.getSerial().closePort();
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
        cliConnected = false;
        //ledIndicator.setMaterial(redMaterial);
        ledIndicator.setMaterial(grayMaterial);
        updateButtons();
    }

    private void connect() {
        //cliConsole.setText("Cli Started\n\n");
        buffer = new ApBuffer();
        cli = new serialHandler(cliConsole,(String)serialCombo.getValue(),(String)baudRateCombo.getValue(),buffer);
        cliConsole.textProperty().bind(cli.readString);
        cliConnected = true;

        //Initialize Rx Chain
        rxdec = new RxDecoder(buffer,this);
        rxdec.start();
        rxdec.startRxDecoder();

        //Initialize Tx Chain
        txenc = new TxEncoder(cli);

        ledIndicator.setMaterial(greenMaterial);
        updateButtons();
    }

    private void updateButtons() {
        if (airPySourceFolder != null && airPyDestinationFolder != null)
            bUpdate.setDisable(false);
        else
            bUpdate.setDisable(true);
        if (serialPorts != null && serialPorts.length > 0) {
            bConnect.setDisable(false);
            if (cliConnected)
                bConnect.setText("Disconnect");
            else
                bConnect.setText("Connect");
        }
        else
            bConnect.setDisable(true);
    }

    private void updateComPortList() {
        serialPorts = SerialPortList.getPortNames();
        serialCombo.setItems(FXCollections.observableArrayList(serialPorts));
        serialCombo.getSelectionModel().select(0);
    }

    public void updateRcBars(int[] channels) {

       // if (apTabPane.getSelectionModel().getSelectedItem().getId() == "rcSetupTab") {
                    for (int i = 0; i < channels.length; i++) {
                        switch (i) {
                            case 0:
                                pbCh1.progressProperty().set(channels[i] / ApLinkParams.MAX_RC_VALUE);
                                if (channels[i] < minValCh1) {
                                    minValCh1 = channels[i];
                                }
                                if (channels[i] > maxValCh1) {
                                    maxValCh1 = channels[i];
                                }
                                centerValCh1 = channels[i];
                                sMaxValCh1.set(String.valueOf(maxValCh1));
                                sMinValCh1.set(String.valueOf(minValCh1));
                                sCenterValCh1.set(String.valueOf(centerValCh1));
                                break;

                            case 1:
                                pbCh2.progressProperty().set(channels[i] / ApLinkParams.MAX_RC_VALUE);
                                if (channels[i] < minValCh2) {
                                    minValCh2 = channels[i];
                                }
                                if (channels[i] > maxValCh2) {
                                    maxValCh2 = channels[i];
                                }
                                centerValCh2 = channels[i];
                                sMaxValCh2.set(String.valueOf(maxValCh2));
                                sMinValCh2.set(String.valueOf(minValCh2));
                                sCenterValCh2.set(String.valueOf(centerValCh2));
                                break;

                            case 2:
                                pbCh3.progressProperty().set(channels[i] / ApLinkParams.MAX_RC_VALUE);
                                if (channels[i] < minValCh3) {
                                    minValCh3 = channels[i];
                                }
                                if (channels[i] > maxValCh3) {
                                    maxValCh3 = channels[i];
                                }
                                centerValCh3 = channels[i];
                                sMaxValCh3.set(String.valueOf(maxValCh3));
                                sMinValCh3.set(String.valueOf(minValCh3));
                                sCenterValCh3.set(String.valueOf(centerValCh3));
                                break;

                            case 3:
                                pbCh4.progressProperty().set(channels[i] / ApLinkParams.MAX_RC_VALUE);
                                if (channels[i] < minValCh4) {
                                    minValCh4 = channels[i];
                                }
                                if (channels[i] > maxValCh4) {
                                    maxValCh4 = channels[i];
                                }
                                centerValCh4 = channels[i];
                                sMaxValCh4.set(String.valueOf(maxValCh4));
                                sMinValCh4.set(String.valueOf(minValCh4));
                                sCenterValCh4.set(String.valueOf(centerValCh4));
                                break;
                        }
                    }
        //}

    }


    public void updateModelRotations(float[] angles) {

        //Update the rotation of the 3D object
        rxBox.setAngle(angles[0]);
        ryBox.setAngle(angles[2]);
        rzBox.setAngle(angles[1]);


    }

    public void updateAttitudeChart(float[] angles) {

        if (xAxisSamplesCount > 100) {

            //pitchSeries.getData().remove(0);
            //rollSeries.getData().remove(0);

            NumberAxis xAxis = (NumberAxis) chartAttitude.getXAxis();
            xAxis.setForceZeroInRange(false);
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(xAxisSamplesCount - 100);
            xAxis.setUpperBound(xAxisSamplesCount);

        }

        if (checkBoxPitch.isSelected()) {
            pitchSeries.getData().add(new XYChart.Data(xAxisSamplesCount, angles[0]));
        }

        if (checkBoxRoll.isSelected()) {
            rollSeries.getData().add(new XYChart.Data(xAxisSamplesCount, angles[1]));
        }

    }

    public void updateMotorChart(short[] motors) {
        if (xAxisSamplesCount > 100) {


            NumberAxis xAxis = (NumberAxis) chartMotors.getXAxis();
            xAxis.setForceZeroInRange(false);
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(xAxisSamplesCount - 100);
            xAxis.setUpperBound(xAxisSamplesCount);

        }

        if (checkBoxMotor1.isSelected()) {
            motor1Series.getData().add(new XYChart.Data(xAxisSamplesCount, motors[0]));
        }
        if (checkBoxMotor2.isSelected()) {
            motor2Series.getData().add(new XYChart.Data(xAxisSamplesCount, motors[1]));
        }
        if (checkBoxMotor3.isSelected()) {
            motor3Series.getData().add(new XYChart.Data(xAxisSamplesCount, motors[2]));
        }
        if (checkBoxMotor4.isSelected()) {
            motor4Series.getData().add(new XYChart.Data(xAxisSamplesCount, motors[3]));
        }
        //TODO: Move in a static class variable related to the chart
        xAxisSamplesCount++;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        assert cliConsole != null : "fx:id=\"cliConsole\" was not injected: check your FMXL";
        assert bConnect != null : "fx:id=\"bConnect\" was not injected: check your FMXL";
        assert bUpdate != null : "fx:id=\"bUpdate\" was not injected: check your FMXL";
        assert buttonCalibration != null: "fx:id=\"buttonCalibration\" was not injected: check your FMXL";
        assert serialCombo != null : "fx:id=\"serialCombo\" was not injected: check your FMXL";
        assert pbCh1 != null : "fx:id=\"pbCh1\" was not injected: check your FMXL";


        //TODO: move the allowed baudrate in a property file
        ObservableList baudRates = FXCollections.observableArrayList("9600","14400","38400","57600","115200");
        baudRateCombo.setItems(baudRates);
        baudRateCombo.getSelectionModel().select(baudRates.size()-1);
        updateComPortList();
        updateButtons();

        //Initialization of 3D cube TODO: loading of custom 3d model
        blueMaterial.setSpecularColor(Color.LIGHTBLUE);
        imuBox.setMaterial(blueMaterial);
        imuBox.getTransforms().addAll(rxBox, ryBox, rzBox);

        //Initialization of 3D sphere
        redMaterial.setSpecularColor(Color.LIGHTCORAL);
        //ledIndicator.setMaterial(redMaterial);
        ledIndicator.setMaterial(grayMaterial);
        greenMaterial.setSpecularColor(Color.LIGHTGREEN);
        grayMaterial.setSpecularColor(Color.LIGHTGRAY);

        //Initialize RC labels
        lbCh1Min.textProperty().bind(sMinValCh1);
        lbCh2Min.textProperty().bind(sMinValCh2);
        lbCh3Min.textProperty().bind(sMinValCh3);
        lbCh4Min.textProperty().bind(sMinValCh4);
        lbCh1Center.textProperty().bind(sCenterValCh1);
        lbCh2Center.textProperty().bind(sCenterValCh2);
        lbCh3Center.textProperty().bind(sCenterValCh3);
        lbCh4Center.textProperty().bind(sCenterValCh4);
        lbCh1Max.textProperty().bind(sMaxValCh1);
        lbCh2Max.textProperty().bind(sMaxValCh2);
        lbCh3Max.textProperty().bind(sMaxValCh3);
        lbCh4Max.textProperty().bind(sMaxValCh4);

        //Initialize Charts
        pitchSeries = new XYChart.Series();
        pitchSeries.setName("Pitch angle");
        rollSeries = new XYChart.Series();
        rollSeries.setName("Roll angle");
        motor1Series = new XYChart.Series();
        motor1Series.setName("Motor 1");
        motor2Series = new XYChart.Series();
        motor2Series.setName("Motor 2");
        motor3Series = new XYChart.Series();
        motor3Series.setName("Motor 3");
        motor4Series = new XYChart.Series();
        motor4Series.setName("Motor 4");


        //populating the all the series with default data
        /*for (int i = 0; i < xAxisDefault; i++){
            pitchSeries.getData().add(new XYChart.Data(i, 0));
            rollSeries.getData().add(new XYChart.Data(i, 0));
            motor1Series.getData().add(new XYChart.Data(i, 0));
            motor2Series.getData().add(new XYChart.Data(i, 0));
            motor3Series.getData().add(new XYChart.Data(i, 0));
            motor4Series.getData().add(new XYChart.Data(i, 0));

        }*/

        chartAttitude.getData().add(pitchSeries);
        chartAttitude.getData().add(rollSeries);
        chartMotors.getData().add(motor1Series);
        chartMotors.getData().add(motor2Series);
        chartMotors.getData().add(motor3Series);
        chartMotors.getData().add(motor4Series);

        checkBoxPitch.setSelected(true);



    }
}
