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

package org.eclipse.sparkplug.tck.test.host;

/*
 * This is the primary host Sparkplug session establishment, and re-establishment test.
 * 
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.extension.sdk.api.packets.connect.ConnectPacket;
import com.hivemq.extension.sdk.api.packets.subscribe.SubscribePacket;
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket;
import com.hivemq.extension.sdk.api.packets.connect.WillPublishPacket;
import com.hivemq.extension.sdk.api.packets.general.Qos;

import org.eclipse.sparkplug.tck.sparkplug.Sections;
import org.eclipse.sparkplug.tck.test.TCK;
import org.eclipse.sparkplug.tck.test.TCKTest;
import org.jboss.test.audit.annotations.SpecAssertion;
import org.jboss.test.audit.annotations.SpecVersion;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.nio.ByteBuffer;

@SpecVersion(
        spec = "sparkplug",
        version = "3.0.0-SNAPSHOT")
public class SessionEstablishmentTest extends TCKTest {

    private static Logger logger = LoggerFactory.getLogger("Sparkplug");
    private HashMap testResults = new HashMap<String, String>();
    String[] testIds = {
    	"host-topic-phid-birth-payload",
    	"host-topic-phid-death-payload", 
    	"host-topic-phid-death-qos",
    	"host-topic-phid-death-retain",
    	"primary-application-connect",
    	"primary-application-death-cert",
    	"primary-application-subscribe",
    	"primary-application-state-publish",
    	"components-ph-state"
    };
    private String myClientId = null;
    private String state = null;
    private TCK theTCK = null;
    private String host_application_id = null;
    
    public SessionEstablishmentTest(TCK aTCK, String[] parms) {
        logger.info("Primary host session establishment test. Parameter: host_application_id");
        theTCK = aTCK;
         
        testResults = new HashMap<String, String>();
        
        for (int i = 0; i < testIds.length; ++i) {
            testResults.put(testIds[i], "");
        }
        
        host_application_id = parms[0];
        logger.info("Host application id is "+host_application_id);
    }
    
    public void endTest() {
    	state = null;
    	myClientId = null;
    	reportResults(testResults);
        for (int i = 0; i < testIds.length; ++i) {
            testResults.put(testIds[i], "");
        }
    }
    
    public String getName() {
    	return "SessionEstablishment";
    }
    
    public String[] getTestIds() {
    	return testIds;
    }
    
    public HashMap<String, String> getResults() {
    	return testResults;
    }
    
    @SpecAssertion(
    		section = Sections.TOPICS_DEATH_MESSAGE_STATE,
    		id = "host-topic-phid-death-payload")
    @SpecAssertion(
    		section = Sections.TOPICS_DEATH_MESSAGE_STATE,
    		id = "host-topic-phid-death-qos")
    @SpecAssertion(
    		section = Sections.TOPICS_DEATH_MESSAGE_STATE,
    		id = "host-topic-phid-death-retain")
    public Optional<WillPublishPacket> checkWillMessage(ConnectPacket packet) {
    	Optional<WillPublishPacket> willPublishPacketOptional = packet.getWillPublish();
    	if (willPublishPacketOptional.isPresent()) {
    		WillPublishPacket willPublishPacket = willPublishPacketOptional.get();

    		String result = "FAIL";
    		ByteBuffer payload = willPublishPacket.getPayload().orElseGet(null);
    		if (payload != null && "OFFLINE".equals(StandardCharsets.UTF_8.decode(payload).toString())) {
    			result = "PASS";
    		} 
    		testResults.put("host-topic-phid-death-payload", result);
    		
    		result = "FAIL";
    		if (willPublishPacket.getQos() == Qos.AT_LEAST_ONCE) {
    			result = "PASS";
    		} 
    		testResults.put("host-topic-phid-death-qos", result);

    		result = "FAIL";
    		if (willPublishPacket.getRetain()) {
    			result = "PASS";
    		}
    		testResults.put("host-topic-phid-death-retain", result);
    	}
    	return willPublishPacketOptional;
    }

    @Test
    @SpecAssertion(
            section = Sections.OPERATIONAL_BEHAVIOR_PRIMARY_HOST_APPLICATION_SESSION_ESTABLISHMENT,
            id = "primary-application-connect")
    @SpecAssertion(
            section = Sections.OPERATIONAL_BEHAVIOR_PRIMARY_HOST_APPLICATION_SESSION_ESTABLISHMENT,
            id = "primary-application-death-cert")          
    public void connect(String clientId, ConnectPacket packet) {
        logger.info("Primary host session establishment test - connect");
        
        String result = "FAIL";
        Optional<WillPublishPacket> willPublishPacketOptional = null;
        try
        {
        	willPublishPacketOptional = checkWillMessage(packet);
        	if (willPublishPacketOptional != null) {
        		result = "PASS";
        	}
        	testResults.put("primary-application-death-cert", result);
        } catch (Exception e) {
        	logger.info("Exception", e);
        }

        try {
        	if (willPublishPacketOptional == null)
        		throw new Exception("Will message is needed");
        	if (packet.getCleanStart() == false)
        		throw new Exception("Clean start should be true");
        	// TODO: what else do we need to check?
        	result = "PASS";
        	myClientId = clientId;
        	state = "CONNECTED";
        } catch (Exception e) {
        	logger.info("Test failed "+e.getMessage());
        	result = "FAIL "+e.getMessage();
        }
        testResults.put("primary-application-connect", result);
    }
    
    @Test
    @SpecAssertion(
            section = Sections.OPERATIONAL_BEHAVIOR_PRIMARY_HOST_APPLICATION_SESSION_ESTABLISHMENT,
            id = "primary-application-subscribe")
    public void subscribe(String clientId, SubscribePacket packet) {
        logger.info("Primary host session establishment test - subscribe");

        if (myClientId.equals(clientId)) {
        	String result = "FAIL";
        	try {
        		if (!state.equals("CONNECTED"))
        			throw new Exception("State should be connected");
        		if (!packet.getSubscriptions().get(0).getTopicFilter().equals("spAv1.0/#"))
        			throw new Exception("Topic string wrong");
        		// TODO: what else do we need to check?
        		result = "PASS";
         		state = "SUBSCRIBED";
        	} catch (Exception e) {
        		result = "FAIL "+e.getMessage();
        	}
        	testResults.put("primary-application-subscribe", result);
        }
    }

    @Test
    @SpecAssertion(
    		section = Sections.TOPICS_PRIMARY_HOST,
    		id = "host-topic-phid-birth-payload")
    @SpecAssertion(
            section = Sections.OPERATIONAL_BEHAVIOR_PRIMARY_HOST_APPLICATION_SESSION_ESTABLISHMENT,
            id = "primary-application-state-publish")
    @SpecAssertion(
            section = Sections.COMPONENTS_PRIMARY_HOST_APPLICATION,
            id = "components-ph-state")
    public void publish(String clientId, PublishPacket packet) {
        logger.info("Primary host session establishment test - publish");
        
        if (myClientId.equals(clientId)) {
        	String result = "FAIL";
        	try {
        		if (!state.equals("SUBSCRIBED"))
        			throw new Exception("State should be subscribed");
        		
        		String topic = packet.getTopic();
        		if (!topic.equals("STATE/"+host_application_id))
        			throw new Exception("Topic should be STATE/host_application_id");
        		
    			String payload = null;
    			ByteBuffer bpayload = packet.getPayload().orElseGet(null);
    			if (bpayload != null) {
    				payload = StandardCharsets.UTF_8.decode(bpayload).toString();
    			}	
        		if (!payload.equals("ONLINE"))
        			throw new Exception("Payload should be ONLINE");
        		
        		// TODO: what else do we need to check?
        		result = "PASS";
         		state = "PUBLISHED";
        	} catch (Exception e) {
        		result = "FAIL " + e.getMessage();
        	}
        	testResults.put("primary-application-state-publish", result);
        	testResults.put("host-topic-phid-birth-payload", result);
        	testResults.put("components-ph-state", result);
        }
        
        // TODO: now we can disconnnect the client and allow it to reconnect and go throught the
        // session re-establishment phases.  It would be nice to be able to do this at after a 
        // short arbitrary interval, but I haven't worked out a good way of doing that yet (assuming
        // that a sleep here is not a good idea).  Using a PING interceptor could be one way but
        // we probably can't rely on any particular keepalive interval values.
        
        theTCK.endTest();
        
    }

}