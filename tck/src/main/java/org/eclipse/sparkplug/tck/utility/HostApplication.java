/*******************************************************************************
 * Copyright (c) 2021 Ian Craggs
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Ian Craggs - initial implementation and documentation
 *******************************************************************************/

package org.eclipse.sparkplug.tck.utility;

/*
 * This the Sparkplug Host Application utility.
 * 
 * It mimics the behavior of a Host Application for use in Device/Edge node tests.
 * 
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;

import org.eclipse.sparkplug.tck.sparkplug.Sections;
import org.jboss.test.audit.annotations.SpecAssertion;
import org.jboss.test.audit.annotations.SpecVersion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.nio.ByteBuffer;

@SpecVersion(
		spec = "sparkplug",
		version = "3.0.0-SNAPSHOT")
public class HostApplication {

	private String state = null;
	
	private String namespace = "spBv1.0";
	private String group_id = "SparkplugTCK";
	private String brokerURI = "tcp://localhost:1883";
	private String log_topic_name = "SPARKPLUG_TCK/LOG";
	
	private String controlId = "Sparkplug TCK host application utility"; 
	private MqttClient control = null;
	private MqttTopic log_topic = null;
	private MessageListener control_listener = null;
	
	private MqttClient host = null;
	private MqttTopic state_topic = null;
	private MessageListener host_listener = null;

	public void log(String message) {
		try {
			MqttMessage mqttmessage = new MqttMessage(message.getBytes());
			log_topic.publish(mqttmessage);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {	
		new HostApplication().run(args);
	}
	
	public void run(String[] args) {
		System.out.println("*** Sparkplug TCK Host Application Utility ***");
		try {
			control = new MqttClient(brokerURI, controlId);
		    control_listener = new MessageListener();
		    control.setCallback(control_listener);
			log_topic = control.getTopic(log_topic_name);
			control.connect();
			log("starting");
			control.subscribe("SPARKPLUG_TCK/HOST_CONTROL");
			while (true) {
				MqttMessage msg = control_listener.getNextMessage();			
				if (msg != null) {
					String[] words = msg.toString().split(" ");
					if (words.length == 3 && words[0].toUpperCase().equals("NEW") && words[1].toUpperCase().equals("HOST")) {
						//log(msg.toString());
						hostCreate(words[2]);
					}
					else {
						log("Command not understood: "+msg);
					}
				}
				Thread.sleep(100);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void hostCreate(String host_application_id) throws Exception {
		if (host != null) {
			log("host application in use");
			return;
		}
		log("Creating new host \""+host_application_id+"\"");
		host = new MqttClient(brokerURI, "Sparkplug TCK host "+host_application_id);
	    host_listener = new MessageListener();
	    host.setCallback(host_listener);
	    
	    state_topic = host.getTopic("STATE/"+host_application_id);
	    
	    MqttConnectOptions connectOptions = new MqttConnectOptions(); 
	    connectOptions.setWill(state_topic, "OFFLINE".getBytes(), 1, true);
		host.connect(connectOptions);
		
		// subscribe to topic namespace
		host.subscribe(namespace+"/#"); 
		
		// send ONLINE state message 
		String payload = "ONLINE";
		MqttMessage online = new MqttMessage(payload.getBytes());
		online.setQos(1);
		online.setRetained(true);
		state_topic.publish(online);
		log("Host "+host_application_id+" successfully created");
	}

	
	public void hostDestroy() throws MqttException {
		// send ONLINE state message 
		String payload = "OFFLINE";
		MqttMessage mqttmessage = new MqttMessage(payload.getBytes());
		state_topic.publish(mqttmessage);
		host.disconnect();
		host.close();
		host = null;
	}
	
	class MessageListener implements MqttCallback {
		ArrayList<MqttMessage> messages;

		public MessageListener() {
			messages = new ArrayList<MqttMessage>();
		}

		public MqttMessage getNextMessage() {
			synchronized (messages) {
				if (messages.size() == 0) {
					return null;
				}
				return messages.remove(0);
			}
		}

		public void connectionLost(Throwable cause) {
			log("connection lost: " + cause.getMessage());
		}

		public void deliveryComplete(IMqttDeliveryToken token) {
			
		}

		public void messageArrived(String topic, MqttMessage message) throws Exception {
			//log("message arrived: " + new String(message.getPayload()));

			synchronized (messages) {
				messages.add(message);
				messages.notifyAll();
			}
		}
	}

}
