package org.firstinspires.ftc.teamcode;

/**
 * Created by wave on 11/25/2018.
 */

import android.util.Log;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.robotcore.external.tfod.TFObjectDetector;
import org.firstinspires.ftc.robotcore.internal.tfod.TfodParameters;

import java.util.ArrayList;
import java.util.List;

@Autonomous

public class CraterTensorFlow extends LinearOpMode {

    // Declare motors/servos
    private DcMotor leftMotor;
    private DcMotor rightMotor;
    private DcMotor armMotor;
    private Servo handServo;
    private Servo markerServo;
    private static final String TFOD_MODEL_ASSET = "RoverRuckus.tflite";
    private static final String LABEL_GOLD_MINERAL = "Gold Mineral";
    private static final String LABEL_SILVER_MINERAL = "Silver Mineral";

    private static final String VUFORIA_KEY = "AZUaS/D/////AAABmd9bAfIzFEvNp68QYPiUGWod1bqxZ/G6UuphfSOO67letJ25Ep2V5E/VfwlFektkz7sNxqkGiOXlTjCcLqVgj/eUwRxum4kkhFHDXZyjrKRb2U7xZaiv+tXxRLS52MnwFzzsUJZOZ0m9d5z3h0wBxL+yeA0bZHMKkIDdHlol+oxI+oTIlj/HtIJ0lqJMSBx40vrLg5Tx91849XDXFWtY9/CAsJbTUkYmLUniWHyolCF4UJ/mXSuyh0OMfaicPRPT4Ue0b0UKM9Z/PFOrqHeE57zO2e9zMBIG9ihPXbjF68ZZcAGfWIzA6uC3QdLwInO0DxR4iDCKqO6fCV+9EWQx8Xcde3yxdMX/E39+Sr+PpAw5";

    private VuforiaLocalizer vuforia;

    private TFObjectDetector tfod;

    @Override
    public void runOpMode() {

        // Initialize motors/servos
        leftMotor = hardwareMap.get(DcMotor.class, "leftMotor");
        rightMotor = hardwareMap.get(DcMotor.class, "rightMotor");
        armMotor = hardwareMap.get(DcMotor.class, "armMotor");
        handServo = hardwareMap.get(Servo.class, "handServo");
        markerServo = hardwareMap.get(Servo.class, "markerServo");

        initVuforia();

        if (ClassFactory.getInstance().canCreateTFObjectDetector()) {
            initTfod();
        } else {
            telemetry.addData("Sorry!", "This device is not compatible with TFOD");
        }

        telemetry.addData("Status", "Ready to run");
        telemetry.update();

        waitForStart();

        updateTelemetry("Lowering");
        singleMotorEncoder(armMotor, 7265);

        updateTelemetry("Releasing Hand");
        openHand();
        sleep(400);

        // Get gold mineral location from tensor flow
        String goldLocation = getGoldLocation();

        double initialTurn;
        double distanceToElement;
        double turnToWall;
        double distanceToWall;
        boolean finalFix = false;

        switch (goldLocation) { // Logic for left/center/right gold mineral
            case "left":
                initialTurn = 8;
                distanceToElement = 20;
                turnToWall = 5;
                break;
            case "center":
                initialTurn = 0.75;
                distanceToElement = 14;
                turnToWall = 10.84;
                finalFix = true;
                break;
            case "right":
                initialTurn = -8;
                distanceToElement = 20;
                turnToWall = 20;
                break;
            default:
                initialTurn = 0.75;
                distanceToElement = 14;
                turnToWall = 10.84;
                finalFix = true;
                break;
        }

        updateTelemetry("Move to position");
        move(1, 1, -18, -18);

        updateTelemetry("Turn to element");
        move(1, 1, initialTurn, -initialTurn);

        updateTelemetry("Hit element");
        move(1, 1, -distanceToElement, -distanceToElement);

        updateTelemetry("Back into position");
        move(1, 1, distanceToElement, distanceToElement);

        updateTelemetry("Turn to wall");
        move(1, 1, turnToWall, -turnToWall);

        updateTelemetry("Move to wall");
        move(1, 1, -45.25, -45.25);

        updateTelemetry("Turn to depo");
        move(1, 1, 8.725, -8.725);

        updateTelemetry("Move to depo");
        move(1, 1, -47, -47);

        updateTelemetry("Drop marker");
        dropMarker();

        if (finalFix) {
            move(1, 1, 1.4, -1.4);
        }

        updateTelemetry("Move to crater");
        move(1, 1, 80, 80);
        raiseMarker();

        updateTelemetry("Stopping");
        stopMotors();
    }

    /**
     * Prints a message to the telemetry under "Status" and updates it
     *
     * @param msg Message to be printed to telemetry
     */

    private void updateTelemetry(String msg) {
        // Add the data to telemetry
        telemetry.addData("Status", msg);
        // Update telemetry
        telemetry.update();
    }

    /**
     * Runs the robot wheels a specified distance
     *
     * @param leftPower     the power for the left motor (0.0 - 1.0)
     * @param rightPower    the power for the right motor (0.0 - 1.0)
     * @param leftPosition  the position for the left motor to travel to (inches)
     * @param rightPosition the position for the right motor to travel to (inches)
     */

    private void move(int leftPower, int rightPower, double leftPosition, double rightPosition) {
        sleep(50);

        // Converts inches given to ticks
        leftPosition = inchesToTicks(leftPosition);
        rightPosition = inchesToTicks(rightPosition);

        // Reverse left position (because motor is backwards)
        leftPosition = -leftPosition;

        // Reset the tick count on encoders
        leftMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        rightMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

        // Sets mode to RTP (run to position)
        leftMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        rightMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);

        // Set power to given powers
        leftMotor.setPower(1);
        rightMotor.setPower(1);

        // Sets target position
        leftMotor.setTargetPosition((int) leftPosition);
        rightMotor.setTargetPosition((int) rightPosition);

        // Create booleans if running
        boolean leftRunning = true;
        boolean rightRunning = true;

        // While either motor is running and op mode is active
        while (opModeIsActive() && leftRunning && rightRunning) {
            // Add data to the telemetry about right and left motor then update it
            telemetry.addData("Right: position", rightMotor.getCurrentPosition());
            telemetry.addData("Right: target", rightMotor.getTargetPosition());
            telemetry.addData("Right: busy status", rightMotor.isBusy());

            telemetry.addData("Left: position", leftMotor.getCurrentPosition());
            telemetry.addData("Left: target", leftMotor.getTargetPosition());
            telemetry.addData("Left: busy status", leftMotor.isBusy());

            telemetry.update();

            // If left/right motor is at target position
            // Set motor's power to 0 and boolean (leftRunning, rightRunning) to false;
            if (Math.abs(leftMotor.getCurrentPosition()) >= Math.abs(leftPosition)) {
                leftMotor.setPower(0);
                leftRunning = false;
            } else {
                leftMotor.setPower(1);
            }
            if (Math.abs(rightMotor.getCurrentPosition()) >= Math.abs(rightPosition)) {
                rightMotor.setPower(0);
                rightRunning = false;
            } else {
                rightMotor.setPower(1);
            }
        }
        sleep(50);
        // When over set both powers to 0
        leftMotor.setPower(0);
        rightMotor.setPower(0);
        sleep(50);
    }

    /**
     * Runs a single motor to a set amount of ticks
     *
     * @param currentMotor The motor to run
     * @param position     The position (in ticks) to run to
     */

    private void singleMotorEncoder(DcMotor currentMotor, int position) {
        // Reset tick count on encoder and set mode to run to position
        currentMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        currentMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);

        // Set power to 1 and motor's target position to position passed
        currentMotor.setPower(1);
        currentMotor.setTargetPosition(position);

        // While motor is running and op mode is active
        while (opModeIsActive() && currentMotor.isBusy()) {
            // Add data to the telemetry and update
            telemetry.addData("position", currentMotor.getCurrentPosition());
            telemetry.addData("busy status", currentMotor.isBusy());
            telemetry.addData("target", currentMotor.getTargetPosition());
            telemetry.update();
            idle();
        }

        // Update the data after motor is done running
        telemetry.addData("position", currentMotor.getCurrentPosition());
        telemetry.addData("busy status", currentMotor.isBusy());
        telemetry.addData("target", currentMotor.getTargetPosition());
        telemetry.update();

        // Set power to 0
        currentMotor.setPower(0);
    }

    /**
     * Sets The Power Of All Motors (Left, Right, And Arm) To 0
     */

    private void stopMotors() {
        leftMotor.setPower(0);
        rightMotor.setPower(0);
        armMotor.setPower(0);
    }

    /**
     * Lowers the servo the marker is set on
     */

    private void dropMarker() {
        markerServo.setPosition(0.3);
    }

    /**
     * Raise the servo the marker is set on
     */

    private void raiseMarker() {
        markerServo.setPosition(0.9);
    }

    /**
     * Sets the hand servo to the open position
     */

    private void openHand() {
        handServo.setPosition(0.85);
    }

    /**
     * Sets the hand servo to a closed position
     */

    private void closeHand() {
        handServo.setPosition(0.05);
    }

    /**
     * Converts inches to an amount of ticks
     *
     * @param inches Amount of inches to convert to ticks
     * @return Amount of ticks
     */

    private int inchesToTicks(double inches) {
        // Divide inches by 0.0062 and cast to integer
        return (int) (inches / 0.0062);
    }

    private String getGoldLocation() {

        if (tfod != null) {
            tfod.activate();
        }

        sleep(1000);

        while (opModeIsActive()) {
            if (tfod != null) {
                // getUpdatedRecognitions() will return null if no new information is available since
                // the last time that call was made.
                List<Recognition> updatedRecognitions = tfod.getUpdatedRecognitions();
                if (updatedRecognitions != null) {
                    List<Recognition> currentRecognitions = new ArrayList<>();
                    for (Recognition recognition : updatedRecognitions) {
                        Log.w("INFO", String.valueOf(recognition));
                        if (recognition.getTop() < 250) {
                            currentRecognitions.add(recognition);
                        } else {
                            Log.w("INFO", "Removed: " + String.valueOf(recognition));
                        }
                    }
                    telemetry.addData("# Object Detected", currentRecognitions.size());
                    if (currentRecognitions.size() == 3) {
                        if (tfod != null)

                        {
                            tfod.shutdown();
                        }
                        int goldMineralX = -1;
                        int silverMineral1X = -1;
                        int silverMineral2X = -1;
                        for (Recognition recognition : currentRecognitions) {
                            if (recognition.getLabel().equals(LABEL_GOLD_MINERAL)) {
                                goldMineralX = (int) recognition.getLeft();
                            } else if (silverMineral1X == -1) {
                                silverMineral1X = (int) recognition.getLeft();
                            } else {
                                silverMineral2X = (int) recognition.getLeft();
                            }
                        }
                        if (goldMineralX != -1 && silverMineral1X != -1 && silverMineral2X != -1) {
                            if (goldMineralX < silverMineral1X && goldMineralX < silverMineral2X) {
                                telemetry.addData("Gold Mineral Position", "Left");
                                return "left";
                            } else if (goldMineralX > silverMineral1X && goldMineralX > silverMineral2X) {
                                telemetry.addData("Gold Mineral Position", "Right");
                                return "right";
                            } else {
                                telemetry.addData("Gold Mineral Position", "Center");
                                return "center";
                            }
                        }
                    }
                    telemetry.update();
                }
            }
            return "null";
        }

        return "null";
    }

    /**
     * Initialize the Vuforia localization engine.
     */
    private void initVuforia() {
        /*
         * Configure Vuforia by creating a Parameter object, and passing it to the Vuforia engine.
         */
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters();

        parameters.vuforiaLicenseKey = VUFORIA_KEY;
        parameters.cameraName = hardwareMap.get(WebcamName.class, "TensorFlowCamera");

        //  Instantiate the Vuforia engine
        vuforia = ClassFactory.getInstance().createVuforia(parameters);

        // Loading trackables is not necessary for the Tensor Flow Object Detection engine.
    }

    /**
     * Initialize the Tensor Flow Object Detection engine.
     */
    private void initTfod() {
        /*
        TFObjectDetector.Parameters tfodParameters =
        new TFObjectDetector.Parameters(tfodMonitorViewId);
        tfodParameters.minimumConfidence = 0.10;
        tfod = ClassFactory.getInstance().createTFObjectDetector( tfodParameters, vuforia);
         */
        int tfodMonitorViewId = hardwareMap.appContext.getResources().getIdentifier(
                "tfodMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        TFObjectDetector.Parameters tfodParameters = new TFObjectDetector.Parameters(tfodMonitorViewId);
        tfod = ClassFactory.getInstance().createTFObjectDetector(tfodParameters, vuforia);
        tfod.loadModelFromAsset(TFOD_MODEL_ASSET, LABEL_GOLD_MINERAL, LABEL_SILVER_MINERAL);
    }

}
