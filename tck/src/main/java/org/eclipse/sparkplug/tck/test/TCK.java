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

package org.eclipse.sparkplug.tck.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.sparkplug.tck.test.TCKTest;
import com.hivemq.extension.sdk.api.packets.connect.ConnectPacket;
import com.hivemq.extension.sdk.api.packets.subscribe.SubscribePacket;
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket;

import java.lang.reflect.Constructor;

public class TCK {

	private static Logger logger = LoggerFactory.getLogger("Sparkplug");
	
	private TCKTest current = null;
	
	public void newTest(String profile, String test, String[] parms) {
		
		logger.info("Test requested "+profile+" "+test);

		try {
			Class testClass = Class.forName("org.eclipse.sparkplug.tck.test."+profile+"."+test);
			Class[] types = {this.getClass(), String[].class};
			Constructor constructor = testClass.getConstructor(types);

			Object[] parameters = {this, parms};
			current = (TCKTest)constructor.newInstance(parameters);
		}
		catch (Exception e) {
			logger.info("Could not find or set test class "+profile+"."+test);
		}
	}
	
	public void endTest() {
		
		if (current != null) { 
			logger.info("Test end requested for "+current.getName());
			current.endTest();
			current = null;
		} else {
			logger.info("Test end requested but no test active");
		}
			
	}
	
	public void connect(String clientId, ConnectPacket packet) {
		if (current != null) {
			current.connect(clientId, packet);
		}
	}
	
	public void subscribe(String clientId, SubscribePacket packet) {
		if (current != null) {
			current.subscribe(clientId, packet);
		}
	}
	
	public void publish(String clientId, PublishPacket packet) {
		if (current != null) {
			current.publish(clientId, packet);
		}
	}
	
}