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
 * This is a utility to simulate a Sparkplug edge node and device for the Sparkplug TCK,
 * to help test a Sparkplug Host Application.
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

import org.eclipse.tahu.SparkplugException;
import org.eclipse.tahu.SparkplugInvalidTypeException;
import org.eclipse.tahu.message.SparkplugBPayloadDecoder;
import org.eclipse.tahu.message.SparkplugBPayloadEncoder;
import org.eclipse.tahu.message.model.*;
import org.eclipse.tahu.message.model.DataSet.DataSetBuilder;
import org.eclipse.tahu.message.model.Metric.MetricBuilder;
import org.eclipse.tahu.message.model.PropertySet.PropertySetBuilder;
import org.eclipse.tahu.message.model.Row.RowBuilder;
import org.eclipse.tahu.message.model.SparkplugBPayload.SparkplugBPayloadBuilder;
import org.eclipse.tahu.message.model.Template.TemplateBuilder;
import org.eclipse.tahu.util.CompressionAlgorithm;
import org.eclipse.tahu.util.PayloadUtil;
import static org.eclipse.tahu.message.model.MetricDataType.*;

import org.eclipse.sparkplug.tck.sparkplug.Sections;
import org.jboss.test.audit.annotations.SpecAssertion;
import org.jboss.test.audit.annotations.SpecVersion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.math.BigInteger;

@SpecVersion(
		spec = "sparkplug",
		version = "3.0.0-SNAPSHOT")
public class Device {
	
	private static final boolean USING_COMPRESSION = false;
	private static final CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.GZIP;

	private String state = null;
	
	private static final String HW_VERSION = "Emulated Hardware";
	private static final String SW_VERSION = "v1.0.0";
	private String namespace = "spBv1.0";
	private String group_id = "SparkplugTCK";
	private String brokerURI = "tcp://localhost:1883";
	private String log_topic_name = "SPARKPLUG_TCK/LOG";
	
	private String controlId = "Sparkplug TCK device utility"; 
	private MqttClient control = null;
	private MqttTopic log_topic = null;
	private MessageListener control_listener = null;
	
	private MqttClient edge = null;
	private MqttTopic edge_topic = null;
	private MqttTopic device_topic = null;
	private MessageListener edge_listener = null;
	
	private int bdSeq = 0;
	private int seq = 0;

	public void log(String message) {
		try {
			MqttMessage mqttmessage = new MqttMessage(message.getBytes());
			log_topic.publish(mqttmessage);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {	
		new Device().run(args);
	}
	
	public void run(String[] args) {
		System.out.println("*** Sparkplug TCK Device and Edge Node Utility ***");
		try {
			control = new MqttClient(brokerURI, controlId);
		    control_listener = new MessageListener();
		    control.setCallback(control_listener);
			log_topic = control.getTopic(log_topic_name);
			control.connect();
			log("starting");
			control.subscribe("SPARKPLUG_TCK/DEVICE_CONTROL");
			while (true) {
				MqttMessage msg = control_listener.getNextMessage();			
				if (msg != null) {
					String[] words = msg.toString().split(" ");
					if (words.length == 5 && words[0].toUpperCase().equals("NEW") && words[1].toUpperCase().equals("DEVICE")) {
						edgeCreate(words[2], words[3]);
						deviceCreate(words[3], words[4]);
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
	
	public void edgeCreate(String host_application_id, String edge_node_id) throws Exception {
		if (edge != null) {
			log("Edge node already created");
			return;
		}
		log("Creating new edge node \""+edge_node_id+"\"");
		edge = new MqttClient(brokerURI, "Sparkplug TCK edge "+edge_node_id);
	    edge_listener = new MessageListener();
	    edge.setCallback(edge_listener);
	    
	    //TODO: set edge death message as MQTT will
		
		edge.connect();
		
		edge.subscribe("STATE/"+host_application_id); /* look for status of the host application we are to use */
		
		/* wait for retained message indicating state of host application under test */
		int count = 0;
		while (true) {
			MqttMessage msg = edge_listener.getNextMessage();
			
			if (msg != null) {
				if (msg.toString().equals("ONLINE")) {
					break;
				} else {
					log("Error: host application not online");
					return;
				}
			}
			Thread.sleep(100);
			if (++count >= 5) {
				log("Error: no host application state");
				return;
			}
		}
		
		// subscribe to NCMD topic
		edge.subscribe(namespace+"/"+group_id+"/NCMD/"+edge_node_id); 
		
		// issue NBIRTH for the edge node
		byte[] payload = createNodeBirthPayload();
		MqttMessage mqttmessage = new MqttMessage(payload);
		edge_topic = edge.getTopic(namespace+"/"+group_id+"/NBIRTH/"+edge_node_id);
		edge_topic.publish(mqttmessage);
		log("Edge node "+edge_node_id+" successfully created");
	}
	
	public void deviceCreate(String edge_node_id, String device_id) throws Exception {
		if (edge == null) {
			log("No edge node");		
			return;
		}
		
		log("Creating new device \""+device_id+"\"");
		// Publish device birth message
		byte[] payload = createDeviceBirthPayload();
		MqttMessage mqttmessage = new MqttMessage(payload);
		device_topic = edge.getTopic(namespace+"/"+group_id+"/DBIRTH/"+edge_node_id+"/"+device_id);
		device_topic.publish(mqttmessage);
		
		log("Device "+device_id+" successfully created");
	}
	
	private String newUUID() {
		return java.util.UUID.randomUUID().toString();
	}
	
	// Used to add the sequence number
	private long getSeqNum() throws Exception {
		System.out.println("seq: " + seq);
		if (seq == 256) {
			seq = 0;
		}
		return seq++;
	}

	private byte[] createNodeBirthPayload() throws Exception {
		// Reset the sequence number
		seq = 0;

		// Create the BIRTH payload and set the position and other metrics
		SparkplugBPayload payload =
				new SparkplugBPayload(new Date(), new ArrayList<Metric>(), getSeqNum(), newUUID(), null);

		payload.addMetric(new MetricBuilder("bdSeq", Int64, (long) bdSeq).createMetric());
		payload.addMetric(new MetricBuilder("Node Control/Rebirth", Boolean, false).createMetric());

		PropertySet propertySet = new PropertySetBuilder()
				.addProperty("engUnit", new PropertyValue(PropertyDataType.String, "My Units"))
				.addProperty("engLow", new PropertyValue(PropertyDataType.Double, 1.0))
				.addProperty("engHigh", new PropertyValue(PropertyDataType.Double, 10.0))
				/*
				 * .addProperty("CustA", new PropertyValue(PropertyDataType.String, "Custom A"))
				 * .addProperty("CustB", new PropertyValue(PropertyDataType.Double, 10.0)) .addProperty("CustC",
				 * new PropertyValue(PropertyDataType.Int32, 100))
				 */
				.createPropertySet();
		payload.addMetric(
				new MetricBuilder("MyMetric", String, "My Value").properties(propertySet).createMetric());
		
		payload.setTimestamp(new Date());
		SparkplugBPayloadEncoder encoder = new SparkplugBPayloadEncoder();

		// Compress payload (optional)
		byte[] bytes = null;
		if (USING_COMPRESSION) {
			bytes = encoder.getBytes(PayloadUtil.compress(payload, compressionAlgorithm));
		} else {
			bytes = encoder.getBytes(payload);
		}
		
		return bytes;
	}
	
	private byte[] createDeviceBirthPayload() throws Exception {
		// Create the payload and add some metrics
		SparkplugBPayload payload =
				new SparkplugBPayload(new Date(), newMetrics(true), getSeqNum(), newUUID(), null);

		payload.addMetric(new MetricBuilder("Device Control/Rebirth", Boolean, false).createMetric());

		// Only do this once to set up the inputs and outputs
		payload.addMetric(new MetricBuilder("Inputs/0", Boolean, true).createMetric());
		payload.addMetric(new MetricBuilder("Inputs/1", Int32, 0).createMetric());
		payload.addMetric(new MetricBuilder("Inputs/2", Double, 1.23d).createMetric());
		payload.addMetric(new MetricBuilder("Outputs/0", Boolean, true).createMetric());
		payload.addMetric(new MetricBuilder("Outputs/1", Int32, 0).createMetric());
		payload.addMetric(new MetricBuilder("Outputs/2", Double, 1.23d).createMetric());

		// payload.addMetric(new MetricBuilder("New_1", Int32, 0).createMetric());
		// payload.addMetric(new MetricBuilder("New_2", Double, 1.23d).createMetric());

		// Add some properties
		payload.addMetric(new MetricBuilder("Properties/hw_version", String, HW_VERSION).createMetric());
		payload.addMetric(new MetricBuilder("Properties/sw_version", String, SW_VERSION).createMetric());

		PropertySet propertySet = new PropertySetBuilder()
				.addProperty("engUnit", new PropertyValue(PropertyDataType.String, "My Units"))
				.addProperty("engLow", new PropertyValue(PropertyDataType.Double, 1.0))
				.addProperty("engHigh", new PropertyValue(PropertyDataType.Double, 10.0))
				/*
				 * .addProperty("CustA", new PropertyValue(PropertyDataType.String, "Custom A"))
				 * .addProperty("CustB", new PropertyValue(PropertyDataType.Double, 10.0)) .addProperty("CustC",
				 * new PropertyValue(PropertyDataType.Int32, 100))
				 */
				.createPropertySet();
		payload.addMetric(
				new MetricBuilder("MyMetric", String, "My Value").properties(propertySet).createMetric());

		SparkplugBPayloadEncoder encoder = new SparkplugBPayloadEncoder();

		// Compress payload (optional)
		byte[] bytes = null;
		if (USING_COMPRESSION) {
			bytes = encoder.getBytes(PayloadUtil.compress(payload, compressionAlgorithm));
		} else {
			bytes = encoder.getBytes(payload);
		}
		
		return bytes;
	}
	
	private List<Metric> newMetrics(boolean isBirth) throws SparkplugException {
		Random random = new Random();
		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(new MetricBuilder("Int8", Int8, (byte) random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("Int16", Int16, (short) random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("Int32", Int32, random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("Int64", Int64, random.nextLong()).createMetric());
		metrics.add(new MetricBuilder("UInt8", UInt8, (short) random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("UInt16", UInt16, random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("UInt32", UInt32, random.nextLong()).createMetric());
		metrics.add(new MetricBuilder("UInt64", UInt64, BigInteger.valueOf(random.nextLong())).createMetric());
		metrics.add(new MetricBuilder("Float", Float, random.nextFloat()).createMetric());
		metrics.add(new MetricBuilder("Double", Double, random.nextDouble()).createMetric());
		metrics.add(new MetricBuilder("Boolean", Boolean, random.nextBoolean()).createMetric());
		metrics.add(new MetricBuilder("String", String, newUUID()).createMetric());
		metrics.add(new MetricBuilder("DateTime", DateTime, new Date()).createMetric());
		metrics.add(new MetricBuilder("Text", Text, newUUID()).createMetric());
		metrics.add(new MetricBuilder("UUID", UUID, newUUID()).createMetric());
		// metrics.add(new MetricBuilder("Bytes", Bytes, randomBytes(20)).createMetric());
		// metrics.add(new MetricBuilder("File", File, null).createMetric());

		// DataSet
		metrics.add(new MetricBuilder("DataSet", DataSet, newDataSet()).createMetric());
		if (isBirth) {
			metrics.add(new MetricBuilder("TemplateDef", Template, newTemplate(true, null)).createMetric());
		}

		// Template
		metrics.add(new MetricBuilder("TemplateInst", Template, newTemplate(false, "TemplateDef")).createMetric());

		// Complex Template
		metrics.addAll(newComplexTemplate(isBirth));

		// Metrics with properties
		metrics.add(new MetricBuilder("IntWithProps", Int32, random.nextInt()).properties(
				new PropertySetBuilder().addProperty("engUnit", new PropertyValue(PropertyDataType.String, "My Units"))
						.addProperty("engHigh", new PropertyValue(PropertyDataType.Int32, Integer.MAX_VALUE))
						.addProperty("engLow", new PropertyValue(PropertyDataType.Int32, Integer.MIN_VALUE))
						.createPropertySet())
				.createMetric());

		// Aliased metric
		// The name and alias will be specified in a NBIRTH/DBIRTH message.
		// Only the alias will be specified in a NDATA/DDATA message.
		Long alias = 1111L;
		if (isBirth) {
			metrics.add(new MetricBuilder("AliasedString", String, newUUID()).alias(alias).createMetric());
		} else {
			metrics.add(new MetricBuilder(alias, String, newUUID()).createMetric());
		}

		return metrics;
	}
	
	private List<Metric> newComplexTemplate(boolean withTemplateDefs) throws SparkplugInvalidTypeException {
		ArrayList<Metric> metrics = new ArrayList<Metric>();
		if (withTemplateDefs) {

			// Add a new template "subType" definition with two primitive members
			metrics.add(new MetricBuilder("subType", Template,
					new TemplateBuilder().definition(true)
							.addMetric(new MetricBuilder("StringMember", String, "value").createMetric())
							.addMetric(new MetricBuilder("IntegerMember", Int32, 0).createMetric()).createTemplate())
									.createMetric());
			// Add new template "newType" definition that contains an instance of "subType" as a member
			metrics.add(new MetricBuilder("newType", Template,
					new TemplateBuilder().definition(true).addMetric(new MetricBuilder("mySubType", Template,
							new TemplateBuilder().definition(false).templateRef("subType")
									.addMetric(new MetricBuilder("StringMember", String, "value").createMetric())
									.addMetric(new MetricBuilder("IntegerMember", Int32, 0).createMetric())
									.createTemplate()).createMetric())
							.createTemplate()).createMetric());
		}

		// Add an instance of "newType
		metrics.add(new MetricBuilder("myNewType", Template,
				new TemplateBuilder().definition(false).templateRef("newType")
						.addMetric(new MetricBuilder("mySubType", Template,
								new TemplateBuilder().definition(false).templateRef("subType")
										.addMetric(new MetricBuilder("StringMember", String, "myValue").createMetric())
										.addMetric(new MetricBuilder("IntegerMember", Int32, 1).createMetric())
										.createTemplate()).createMetric())
						.createTemplate()).createMetric());

		return metrics;

	}
	
	private List<Parameter> newParams() throws SparkplugException {
		Random random = new Random();
		List<Parameter> params = new ArrayList<Parameter>();
		params.add(new Parameter("ParamInt32", ParameterDataType.Int32, random.nextInt()));
		params.add(new Parameter("ParamFloat", ParameterDataType.Float, random.nextFloat()));
		params.add(new Parameter("ParamDouble", ParameterDataType.Double, random.nextDouble()));
		params.add(new Parameter("ParamBoolean", ParameterDataType.Boolean, random.nextBoolean()));
		params.add(new Parameter("ParamString", ParameterDataType.String, newUUID()));
		return params;
	}
	
	private Template newTemplate(boolean isDef, String templatRef) throws SparkplugException {
		Random random = new Random();
		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(new MetricBuilder("MyInt8", Int8, (byte) random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("MyInt16", Int16, (short) random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("MyInt32", Int32, random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("MyInt64", Int64, random.nextLong()).createMetric());
		metrics.add(new MetricBuilder("MyUInt8", UInt8, (short) random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("MyUInt16", UInt16, random.nextInt()).createMetric());
		metrics.add(new MetricBuilder("MyUInt32", UInt32, random.nextLong()).createMetric());
		metrics.add(new MetricBuilder("MyUInt64", UInt64, BigInteger.valueOf(random.nextLong())).createMetric());
		metrics.add(new MetricBuilder("MyFloat", Float, random.nextFloat()).createMetric());
		metrics.add(new MetricBuilder("MyDouble", Double, random.nextDouble()).createMetric());
		metrics.add(new MetricBuilder("MyBoolean", Boolean, random.nextBoolean()).createMetric());
		metrics.add(new MetricBuilder("MyString", String, newUUID()).createMetric());
		metrics.add(new MetricBuilder("MyDateTime", DateTime, new Date()).createMetric());
		metrics.add(new MetricBuilder("MyText", Text, newUUID()).createMetric());
		metrics.add(new MetricBuilder("MyUUID", UUID, newUUID()).createMetric());

		return new TemplateBuilder().version("v1.0").templateRef(templatRef).definition(isDef)
				.addParameters(newParams()).addMetrics(metrics).createTemplate();
	}
	
	private DataSet newDataSet() throws SparkplugException {
		Random random = new Random();
		return new DataSetBuilder(14).addColumnName("Int8s").addColumnName("Int16s").addColumnName("Int32s")
				.addColumnName("Int64s").addColumnName("UInt8s").addColumnName("UInt16s").addColumnName("UInt32s")
				.addColumnName("UInt64s").addColumnName("Floats").addColumnName("Doubles").addColumnName("Booleans")
				.addColumnName("Strings").addColumnName("Dates").addColumnName("Texts").addType(DataSetDataType.Int8)
				.addType(DataSetDataType.Int16).addType(DataSetDataType.Int32).addType(DataSetDataType.Int64)
				.addType(DataSetDataType.UInt8).addType(DataSetDataType.UInt16).addType(DataSetDataType.UInt32)
				.addType(DataSetDataType.UInt64).addType(DataSetDataType.Float).addType(DataSetDataType.Double)
				.addType(DataSetDataType.Boolean).addType(DataSetDataType.String).addType(DataSetDataType.DateTime)
				.addType(DataSetDataType.Text)
				.addRow(new RowBuilder().addValue(new Value<Byte>(DataSetDataType.Int8, (byte) random.nextInt()))
						.addValue(new Value<Short>(DataSetDataType.Int16, (short) random.nextInt()))
						.addValue(new Value<Integer>(DataSetDataType.Int32, random.nextInt()))
						.addValue(new Value<Long>(DataSetDataType.Int64, random.nextLong()))
						.addValue(new Value<Short>(DataSetDataType.UInt8, (short) random.nextInt()))
						.addValue(new Value<Integer>(DataSetDataType.UInt16, random.nextInt()))
						.addValue(new Value<Long>(DataSetDataType.UInt32, random.nextLong()))
						.addValue(new Value<BigInteger>(DataSetDataType.UInt64, BigInteger.valueOf(random.nextLong())))
						.addValue(new Value<Float>(DataSetDataType.Float, random.nextFloat()))
						.addValue(new Value<Double>(DataSetDataType.Double, random.nextDouble()))
						.addValue(new Value<Boolean>(DataSetDataType.Boolean, random.nextBoolean()))
						.addValue(new Value<String>(DataSetDataType.String, newUUID()))
						.addValue(new Value<Date>(DataSetDataType.DateTime, new Date()))
						.addValue(new Value<String>(DataSetDataType.Text, newUUID())).createRow())
				.addRow(new RowBuilder().addValue(new Value<Byte>(DataSetDataType.Int8, (byte) random.nextInt()))
						.addValue(new Value<Short>(DataSetDataType.Int16, (short) random.nextInt()))
						.addValue(new Value<Integer>(DataSetDataType.Int32, random.nextInt()))
						.addValue(new Value<Long>(DataSetDataType.Int64, random.nextLong()))
						.addValue(new Value<Short>(DataSetDataType.UInt8, (short) random.nextInt()))
						.addValue(new Value<Integer>(DataSetDataType.UInt16, random.nextInt()))
						.addValue(new Value<Long>(DataSetDataType.UInt32, random.nextLong()))
						.addValue(new Value<BigInteger>(DataSetDataType.UInt64, BigInteger.valueOf(random.nextLong())))
						.addValue(new Value<Float>(DataSetDataType.Float, random.nextFloat()))
						.addValue(new Value<Double>(DataSetDataType.Double, random.nextDouble()))
						.addValue(new Value<Boolean>(DataSetDataType.Boolean, random.nextBoolean()))
						.addValue(new Value<String>(DataSetDataType.String, newUUID()))
						.addValue(new Value<Date>(DataSetDataType.DateTime, new Date()))
						.addValue(new Value<String>(DataSetDataType.Text, newUUID())).createRow())
				.createDataSet();
	}
	
	public void deviceDestroy() throws MqttException {
		edge.disconnect();
		edge.close();
		edge = null;
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
