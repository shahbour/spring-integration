/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.adapter.event;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.integration.channel.MessageChannel;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.message.MessageExchangeTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * A message source for passing Spring
 * {@link ApplicationEvent ApplicationEvents} within messages.
 * 
 * @author Mark Fisher
 */
public class ApplicationEventSource implements ApplicationListener {

	private final MessageChannel channel;

	private List<Class<? extends ApplicationEvent>> eventTypes = new ArrayList<Class<? extends ApplicationEvent>>();

	private final MessageExchangeTemplate messageExchangeTemplate = new MessageExchangeTemplate();


	public ApplicationEventSource(MessageChannel channel) {
		Assert.notNull(channel, "channel must not be null");
		this.channel = channel;
	}


	/**
	 * Set the list of event types (classes that extend ApplicationEvent) that
	 * this adapter should send to the message channel. By default, all event
	 * types will be sent.
	 */
	public void setEventTypes(List<Class<? extends ApplicationEvent>> eventTypes) {
		Assert.notEmpty(eventTypes, "at least one event type is required");
		this.eventTypes = eventTypes;
	}

	public void onApplicationEvent(ApplicationEvent event) {
		if (CollectionUtils.isEmpty(this.eventTypes)) {
			this.sendMessage(event);
			return;
		}
		for (Class<? extends ApplicationEvent> eventType : this.eventTypes) {
			if (eventType.isAssignableFrom(event.getClass())) {
				this.sendMessage(event);
				return;
			}
		}
	}

	private boolean sendMessage(ApplicationEvent event) {
		return this.messageExchangeTemplate.send(
				new GenericMessage<ApplicationEvent>(event), this.channel);
	}

}
