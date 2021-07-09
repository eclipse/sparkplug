/*
 * Copyright © 2021 The Eclipse Foundation, Cirrus Link Solutions, and others
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sparkplug.tck;

import javax.validation.constraints.NotNull;

import org.eclipse.sparkplug.tck.test.TCK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.intializer.ClientInitializer;
import com.hivemq.extension.sdk.api.client.ClientContext;
import com.hivemq.extension.sdk.api.client.parameter.InitializerInput;

public class SparkplugHiveMQExtension implements ExtensionMain {

	private static Logger logger = LoggerFactory.getLogger("Sparkplug");
	private TCK aTCK = null;
	
	@Override
	public void extensionStart(final @NotNull ExtensionStartInput extensionStartInput,
			final @NotNull ExtensionStartOutput extensionStartOutput) {

		try {
			logger.info("Starting Sparkplug Extension");
			
			final TCK aTCK = new TCK();
			
			final ConnectInterceptor connectInterceptor = new ConnectInterceptor(aTCK);
			Services.interceptorRegistry().setConnectInboundInterceptorProvider(input -> {
				return connectInterceptor;
			});
			
			final SubscribeInterceptor subscribeInterceptor = new SubscribeInterceptor(aTCK);
			final PublishInterceptor publishInterceptor = new PublishInterceptor(aTCK);
			// create a new client initializer
		    final ClientInitializer clientInitializer = new ClientInitializer() {
		        @Override
		        public void initialize(
		                final @NotNull InitializerInput initializerInput,
		                final @NotNull ClientContext clientContext) {
		            // add the interceptors to the context of the connecting client
		            clientContext.addSubscribeInboundInterceptor(subscribeInterceptor);
		            clientContext.addPublishInboundInterceptor(publishInterceptor);
		        }
		    };

		    // register the client initializer
		    Services.initializerRegistry().setClientInitializer(clientInitializer);
			
		} catch (Exception e) {
			logger.error("Exception thrown at extension start: ", e);
		}
	}

	@Override
	public void extensionStop(@com.hivemq.extension.sdk.api.annotations.NotNull ExtensionStopInput extensionStopInput,
			@com.hivemq.extension.sdk.api.annotations.NotNull ExtensionStopOutput extensionStopOutput) {
		logger.info("Stopping Sparkplug Extension");
	}
}