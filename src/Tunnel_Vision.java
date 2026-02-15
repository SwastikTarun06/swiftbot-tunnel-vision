import swiftbot.Button;
import swiftbot.SwiftBotAPI;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import swiftbot.ImageSize;
import java.util.ArrayList;
import java.util.HashMap;

public class Tunnel_Vision {
    
	// Create an instance of the SwiftBot API
    static SwiftBotAPI API = new SwiftBotAPI(); 
    static boolean running = true;
    static long startTime;
    static double totalDistance = 0; // Total distance travelled
    static double speedPercentage = 40; // Default speed at 40%
    static int obstacleCount = 0;  // Counter for detected obstacles
    static int totalTunnels = 0; // Counter for tunnels detected
    
 // Lists to store tunnel data
    static ArrayList<Double> tunnelLengths = new ArrayList<>();
    static ArrayList<Double> tunnelLightIntensities = new ArrayList<>(); 
    static ArrayList<Double> tunnelGaps = new ArrayList<>();
    static ArrayList<Double> tunnelDistances = new ArrayList<>();

 // Obstacle detection variables
    static boolean obstacleDetected = false;
    static String obstaclePhotoPath = "None";  
    static long executionStartTime = 0; 
    
 // Tunnel detection flags and timers
    static boolean insideTunnel = false;
    static boolean measuringGap = false;
    static long tunnelStartTime;
    static long lastTunnelEndTime;
    
    // Speed calibration map
    static HashMap<Integer, Double> speedToCmPerSec = new HashMap<>();
    
    // Light intensity thresholds for tunnel entry and exit
    static final int ENTRY_THRESHOLD = 110;  
    static final int EXIT_THRESHOLD = 140;   

 // Displays the user interface instructions
    public static void displayUI() {
        System.out.println("\n****************************************************");
        System.out.println("*                                                   *");
        System.out.println("*     WELCOME TO SWIFTBOT TUNNEL VISION        	*");
        System.out.println("*                                                   *");
        System.out.println("****************************************************\n");

        System.out.println("Place the SwiftBot at the entrance of the tunnel.");
        System.out.println("Then, press the 'Y' button to start.\n");
        System.out.println("Then, press the 'X' button to stop .\n");
        System.out.println("----------------------------------------------------\n");
    }

 // Assign functions to button presses
    public static void main(String[] args) {
        setupSpeedCalibration(); 
        displayUI();

        API.enableButton(Button.Y, () -> {
            System.out.println("Button Y has been pressed - Starting SwiftBot.");
            API.disableButton(Button.Y);
            executionStartTime = System.currentTimeMillis();
            startBot();
        });

        API.enableButton(Button.X, () -> {
            System.out.println("Button X pressed - Stopping SwiftBot.");
            API.disableButton(Button.X);
            stopBot();
            logExecutionData();
        });

     // This keeps the program running
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    // Initialises the speed calibration table
    public static void setupSpeedCalibration() {
        speedToCmPerSec.put(20, 5.0); 
        speedToCmPerSec.put(40, 10.0);  
        speedToCmPerSec.put(60, 15.0);
        speedToCmPerSec.put(80, 20.0); 
        speedToCmPerSec.put(100, 25.0); 
    }

 // This starts the SwiftBot's movement and detection system
    public static void startBot() {
    							// R   G  B
        int[] colourToLightUp = { 255, 0, 0 };// Set under lights to red
        try {
            API.fillUnderlights(colourToLightUp);
        } catch (Exception e) {
            e.printStackTrace();
        }

        API.startMove(40, 40); 
        System.out.println("SwiftBot is moving forward at 40% speed.");

        startTime = System.currentTimeMillis();
        long lastImageTime = 0; 
        int previousLightIntensity = 0;  // To store the light intensity of the previous image
        ArrayList<Integer> lightIntensitiesInTunnel = new ArrayList<>();  // List to store light intensities inside the tunnel

        while (running) {
            long currentTime = System.currentTimeMillis();
            double elapsedTime = (currentTime - startTime) / 1000.0;
            totalDistance = calculateDistance(elapsedTime);
            
            // Capture image every second to check light intensity
            if (currentTime - lastImageTime >= 1000) {
                BufferedImage img = captureGrayscaleImage();
                if (img != null) {
                    int currentLightIntensity = calculateAverageIntensity(img);
                    System.out.println("Average Light Intensity: " + currentLightIntensity);
                    lastImageTime = currentTime;
                    
                    try {
                        Thread.sleep(2000); // Allow a little delay between images
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Capture a second image after some delay for better accuracy
                    if (currentTime - lastImageTime >= 3000) {
                        BufferedImage img1 = captureGrayscaleImage();
                        if (img1 != null) {
                            int currentLightIntensity1 = calculateAverageIntensity(img1);
                            System.out.println("Average Light Intensity: " + currentLightIntensity1);
                            lastImageTime = currentTime;

                            // Debug: Check the light intensity values for entry condition
                            System.out.println("Previous Light Intensity: " + previousLightIntensity);
                            System.out.println("Current Light Intensity: " + currentLightIntensity1);

                            // Tunnel entry logic: when light intensity dips below threshold (110)
                            if (!insideTunnel && currentLightIntensity1 < ENTRY_THRESHOLD && currentLightIntensity1 < previousLightIntensity) {
                                insideTunnel = true;
                                measuringGap = false;
                                tunnelStartTime = System.currentTimeMillis();
                                totalTunnels++;  // Increment tunnel count when entering the tunnel
                                System.out.println("Entering tunnel #" + totalTunnels + "...");
                                System.out.println("Total tunnels detected: " + totalTunnels);

                                // Reset light intensities for the new tunnel
                                lightIntensitiesInTunnel.clear();
                            }

                            // Inside tunnel logic: continue recording light intensity values below threshold (110)
                            if (insideTunnel) {
                                lightIntensitiesInTunnel.add(currentLightIntensity1);
                            }

                            // Tunnel exit logic: when light intensity spikes above threshold (110)
                            if (insideTunnel && currentLightIntensity1 > EXIT_THRESHOLD && currentLightIntensity1 > previousLightIntensity) {
                                insideTunnel = false;
                                measuringGap = true;
                                long tunnelEndTime = System.currentTimeMillis();
                                double tunnelElapsedTime = (tunnelEndTime - tunnelStartTime) / 1000.0;
                                double tunnelDistance = calculateDistance(tunnelElapsedTime);

                                // Log the tunnel's length and total distance
                                tunnelLengths.add(tunnelDistance);
                                totalDistance += tunnelDistance;
                                tunnelDistances.add(tunnelDistance);
                                System.out.println("Exiting the tunnel. Tunnel Distance: " + tunnelDistance + " cm");

                                // Calculate the average light intensity for the tunnel
                                if (!lightIntensitiesInTunnel.isEmpty()) {
                                    int sumLightIntensity = 0;
                                    for (int intensity : lightIntensitiesInTunnel) {
                                        sumLightIntensity += intensity;
                                    }
                                    double averageLightIntensity = sumLightIntensity / (double) lightIntensitiesInTunnel.size();
                                    tunnelLightIntensities.add(averageLightIntensity);
                                    System.out.println("Average Light Intensity in Tunnel #" + totalTunnels + ": " + averageLightIntensity);
                                }

                                lastTunnelEndTime = tunnelEndTime;
                            }

                            // Measure the gap between tunnels
                            if (measuringGap) {
                                long gapStartTime = lastTunnelEndTime;
                                long gapEndTime = System.currentTimeMillis();
                                double gapElapsedTime = (gapEndTime - gapStartTime) / 1000.0;
                                double gapDistance = calculateDistance(gapElapsedTime);
                                tunnelGaps.add(gapDistance);
                                System.out.println("Gap between tunnels: " + gapDistance + " cm");

                                measuringGap = false;
                            }

                            // Update previous light intensity for the next loop
                            previousLightIntensity = currentLightIntensity1;
                        }
                    }
                }
            }

            // Check for obstacles and stop if detected
            if (isObstacleDetected()) {
                stopBot();
                captureObstaclePhoto();
                logExecutionData();
                break;
            }
        }
    }
    
    // Makes the SwiftBot stop
    public static void stopBot() {
        API.stopMove(); 
        API.disableUnderlights();
        System.out.println("SwiftBot has stopped.");
        running = false;
        
        logExecutionData();
        reassignYButtonForLog();
    }
    
    // Makes 'Y' the button to press to access logs
    public static void reassignYButtonForLog() {
        System.out.println("\nButton Y is now assigned to view the execution log.");
        API.enableButton(Button.Y, () -> {
            System.out.println("\nDisplaying Execution Log:");
            displayLog();
        });

        logExecutionData();
        reassignButtonsForLogPrompt(); 
        
        System.out.println("Press 'Y' to view the log or 'X' to exit.");
    }

    public static BufferedImage captureGrayscaleImage() {
        try {
            long timestamp = System.currentTimeMillis();
            String fileName = "/data/home/pi/tunnel_image_" + timestamp + ".jpg";  
            
         // Capture the gray scale image
            BufferedImage img = API.takeGrayscaleStill(ImageSize.SQUARE_720x720);
            
         // Save the image to a file
            File outputFile = new File(fileName);
            ImageIO.write(img, "jpg", outputFile);
            
            System.out.println("Image captured and saved to: " + outputFile.getPath());
            
            return img;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Calculates the average light intensity from a gray scale image.
    public static int calculateAverageIntensity(BufferedImage img) {
        long sumIntensity = 0;
        int width = img.getWidth();
        int height = img.getHeight();
        int totalPixels = width * height;

     // Loop through each pixel and calculate its brightness
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = img.getRGB(x, y);  
                int intensity = (pixel >> 16) & 0xFF;  
                sumIntensity += intensity; 
            }
        }
        
        return (int) (sumIntensity / totalPixels);
    }

    public static boolean isObstacleDetected() {
        try {
            double distance = API.useUltrasound(); 
            System.out.println("Ultrasound Sensor Distance: " + distance + " cm");

            if (distance < 40) { 
                obstacleCount++; 
                obstacleDetected = true; 
                return true; 
            }
            return false; 
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR: Ultrasound sensor failed.");
            return false;
        }
    }

    public static void captureObstaclePhoto() {
        System.out.println("Obstacle detected! Capturing photo.");
        String filePath = "/data/home/pi/obstacle_image.jpg";
        System.out.println("Photo saved at: " + filePath);
    }

    public static double calculateDistance(double elapsedTime) {  
        double speedInCmPerSec = speedToCmPerSec.getOrDefault((int) speedPercentage, 0.0);
        return speedInCmPerSec * elapsedTime;
    }
    
    public static void logExecutionData() {
        long executionTime = (System.currentTimeMillis() - executionStartTime) / 1000;   
        double totalTunnelDistance = tunnelLengths.stream().mapToDouble(Double::doubleValue).sum(); 

        // Create log data as a string
        StringBuilder logData = new StringBuilder("Execution Log:\n");
        logData.append("Total tunnels detected: ").append(totalTunnels).append("\n");  
        logData.append("Tunnel lengths (cm): ").append(tunnelLengths).append("\n");  
        logData.append("Average light intensity inside tunnels: ").append(tunnelLightIntensities).append("\n"); 
        logData.append("Distances between tunnels (cm): ").append(tunnelGaps).append("\n");  
        logData.append("Total distance traveled in tunnels (cm): ").append(totalTunnelDistance).append("\n");  
        logData.append("Execution duration (s): ").append(executionTime).append("\n"); 
        logData.append("Obstacles detected: ").append(obstacleCount).append("\n");  
        logData.append("Obstacle photo path: ").append(obstaclePhotoPath).append("\n");

     // Save log data to a file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("/data/home/pi/execution_log.txt"))) {
            writer.write(logData.toString());
            System.out.println("Execution log saved at: /data/home/pi/execution_log.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Prompts the user to view the execution log after stopping the SwiftBot.
    public static void reassignButtonsForLogPrompt() {
        System.out.println("\nDo you want to view the execution log?");
        System.out.println("Press 'Y' to display the log on screen.");
        System.out.println("Press 'X' to exit without displaying the log.");

        // Assign button functions
        API.enableButton(Button.Y, () -> {
            System.out.println("\nDisplaying Execution Log:");
            displayLog();
        });

        API.enableButton(Button.X, () -> { 
            System.out.println("\nExiting without displaying the log.");
            System.out.println("Log has been saved at: /data/home/pi/execution_log.txt");
            System.exit(2000);
        });

     // Keep checking for input
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void displayLog() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/data/home/pi/execution_log.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println("ERROR: Unable to read the log file.");
            e.printStackTrace();
        }

        System.exit(5000);
    }
}
