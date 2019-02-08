/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package doorsensormqtt;

/**
 *
 * @author Dug Threewitt
 * Program to send MQTT open/close depending on state of magnetic sensor attached to piZeroW
 * Based on Sample programs from Eclipse.Paho.MQTT and pi4j
 */
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.wiringpi.Gpio;
import com.pi4j.wiringpi.GpioUtil;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.util.CommandArgumentParser;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.nio.file.Files;




public class DoorSensorMQTT implements MqttCallback {
  
    // Private instance variables
	private MqttClient 			client;
	private String 				brokerUrl;
	private boolean 			quietMode;
	private MqttConnectOptions              conOpt;
	private boolean 			clean;
	private String password;
	private String userName;
        String tmpDir = System.getProperty("java.io.tmpdir");
    	MqttDefaultFilePersistence dataStore = new MqttDefaultFilePersistence(tmpDir);
        private boolean doorOpen = false;
       
    public static void main(String[] args) {
    
        // Default settings:
        boolean quietMode 	= false;
        String action 		= "publish";
	String topic 		= "";
	String message          = "OPEN";
	int qos 		= 2;
	String broker 		= "192.168.1.10";
	int port 		= 1883;
	String clientId 	= "doorSensorClient";
	String subTopic		= "garage/door";
	String pubTopic 	= "garage/sensor";
	boolean cleanSession = true;	// Non durable subscriptions
	//boolean ssl = false;
	String password = null;
	String userName = null;
        String protocol = "tcp://";
        String url = protocol + broker + ":" + port;
        
        System.out.println("running....");
        
        // create gpio controller
        final GpioController gpio = GpioFactory.getInstance();
        
        //Provsion pin for input
        Pin sensorPin = CommandArgumentParser.getPin(
    		RaspiPin.class,
    		RaspiPin.GPIO_04,
    		args);
        
        // by default gpio pin will use pullup
        PinPullResistance pull = CommandArgumentParser.getPinPullResistance(
            PinPullResistance.PULL_UP,  // default pin pull resistance if no pull argument found
            args);                      // argument array to search in   
        
        // Set sensorPin as an input pin with its internal pull resistor set to UP or DOWN
         final GpioPinDigitalInput doorSensor = gpio.provisionDigitalInputPin(sensorPin, pull); 
    
        // unexport the GPIO pin when program exits
        doorSensor.setShutdownOptions(true);


        //add a gpio listener to the gpio pin
        doorSensor.addListener(new GpioPinListenerDigital() {
            @Override
            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                
                try {
                     // Create an instance of this class
                    DoorSensorMQTT sampleClient = new DoorSensorMQTT(url, clientId, cleanSession, quietMode,userName,password);
                
                    if(doorSensor.isHigh()) {
                        sampleClient.publish(pubTopic,2, "OPEN".getBytes());
                        System.out.println("Open");
                    }
                    else {
                        sampleClient.publish(pubTopic,2,"CLOSED".getBytes());
                        System.out.println("Closed");
                    }
                } catch(MqttException me) {
                    // Display which exception was thrown
                    System.out.println("publish exception");
                }
                
                // display pin state on console uncomment for debugging
                // System.out.println(" --> Door Sensor PIN STATE CHANGE: " + event.getPin() + " = " +
                //      event.getState());
            }
        });

        //run wait method to keep process alive
        try {
            DoorSensorMQTT waiter = new DoorSensorMQTT("tcp://192.168.1.10:1883", "Test", true, true,null,null);
           
            waiter.waitMethod();
        } 
        catch(MqttException me) {
            // Display which exception was thrown
            System.out.println("wait exception");
        }   
    }
    
/*******************************************************
**Method to cause program to run indefinitely          
*******************************************************/
    private synchronized void waitMethod() {
        while (true) {
            try {
                wait(500);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    /* Utility method to handle logging.
     * @param message the message to log
     */
    private void log(String message) {
    	//if (!quietMode) {
    		System.out.println(message);
    	//}
    }
    
    // Constructor for a DoorSensorMQTT Object
    public DoorSensorMQTT(String brokerUrl, String clientId, boolean cleanSession, boolean quietMode, String userName, String password) throws MqttException {
    	this.brokerUrl  = brokerUrl;
    	this.quietMode  = quietMode;
    	this.clean      = cleanSession;
    	this.password   = password;
    	this.userName   = userName;
    	//This sample stores in a temporary directory... where messages temporarily
    	// stored until the message has been delivered to the server.
    	String tmpDir = ("/home/MQTTtemp/messageTmp");
    	MqttDefaultFilePersistence dataStore = new MqttDefaultFilePersistence(tmpDir);

    	try {
            // Construct the connection options object that contains connection parameters
            conOpt = new MqttConnectOptions();
	    conOpt.setCleanSession(clean);
	    if(password != null ) {
	    	conOpt.setPassword(this.password.toCharArray());
	    }
	    if(userName != null) {
                conOpt.setUserName(this.userName);
	    }

            // Construct an MQTT blocking mode client
            client = new MqttClient(this.brokerUrl,clientId, dataStore);

            // Set this wrapper as the callback handler
	    client.setCallback(this);

            } 
        catch (MqttException e) {
            e.printStackTrace();
            log("Unable to set up client...exiting");
		System.exit(1);
            }
    }
    
    /***************************************************************
     * Method to publish to MQTT
     ***************************************************************/
    public void publish(String topicName, int qos, byte[] payload) throws MqttException {

    	// Connect to the MQTT server
    	log("Connecting to "+brokerUrl + " with client ID "+client.getClientId());
    	client.connect(conOpt);
    	log("Connected");

    	String time = new Timestamp(System.currentTimeMillis()).toString();
    	log("Publishing at: "+time+ " to topic \""+topicName+"\" qos "+qos);

    	// Create and configure a message
   	MqttMessage message = new MqttMessage(payload);
    	message.setQos(qos);

    	// Send the message to the server, control is not returned until
    	// it has been delivered to the server meeting the specified
    	// quality of service.
    	client.publish(topicName, message);

    	// Disconnect the client
    	client.disconnect();
    	//log("Disconnected");
    }
    
    /**
     * Subscribe to a topic on an MQTT server
     * Once subscribed this method waits for the messages to arrive from the server
     * that match the subscription. It continues listening for messages until the enter key is
     * pressed. Not used in this implementation.
     * @param topicName to subscribe to (can be wild carded)
     * @param qos the maximum quality of service to receive messages at for this subscription
     * @throws MqttException
     */
    public void subscribe(String topicName, int qos) throws MqttException {

    	// Connect to the MQTT server
    	client.connect(conOpt);
    	log("Connected to "+brokerUrl+" with client ID "+client.getClientId());

    	// Subscribe to the requested topic
    	// The QoS specified is the maximum level that messages will be sent to the client at.
    	// For instance if QoS 1 is specified, any messages originally published at QoS 2 will
    	// be downgraded to 1 when delivering to the client but messages published at 1 and 0
    	// will be received at the same level they were published at.
    	log("Subscribing to topic \""+topicName+"\" qos "+qos);
    	client.subscribe(topicName, qos);

    	// Continue waiting for messages until the Enter is pressed
    	log("Press <Enter> to exit");
		try {
			System.in.read();
		} catch (IOException e) {
			//If we can't read we'll just exit
		}

		// Disconnect the client from the server
		client.disconnect();
		//log("Disconnected");
    }

    
    /****************************************************************/
	/* Methods to implement the MqttCallback interface              */
	/****************************************************************/

    /**
     * @see MqttCallback#connectionLost(Throwable)
     */
	public void connectionLost(Throwable cause) {
		// Called when the connection to the server has been lost.
		// An application may choose to implement reconnection
		// logic at this point. This sample simply exits.
		log("Connection to lost!");
		System.exit(1);
	}

    /**
     * @see MqttCallback#deliveryComplete(IMqttDeliveryToken)
     */
	public void deliveryComplete(IMqttDeliveryToken token) {
		// Called when a message has been delivered to the
		// server. The token passed in here is the same one
		// that was passed to or returned from the original call to publish.
		// This allows applications to perform asynchronous
		// delivery without blocking until delivery completes.
		//
		// This sample demonstrates asynchronous deliver and
		// uses the token.waitForCompletion() call in the main thread which
		// blocks until the delivery has completed.
		// Additionally the deliveryComplete method will be called if
		// the callback is set on the client
		//
		// If the connection to the server breaks before delivery has completed
		// delivery of a message will complete after the client has re-connected.
		// The getPendingTokens method will provide tokens for any messages
		// that are still to be delivered.
	}

    /**
     * @see MqttCallback#messageArrived(String, MqttMessage)
     */
	public void messageArrived(String topic, MqttMessage message) throws MqttException {
		// Called when a message arrives from the server that matches any
		// subscription made by the client. Not used in this implementation
		//String time = new Timestamp(System.currentTimeMillis()).toString();
                Date time = new Date();
		System.out.println("Time:\t" +time +
                           "  Topic:\t" + topic +
                           "  Message:\t" + new String(message.getPayload()) +
                           "  QoS:\t" + message.getQos());
	}

	/****************************************************************/
	/* End of MqttCallback methods                                  */
	/****************************************************************/

}
