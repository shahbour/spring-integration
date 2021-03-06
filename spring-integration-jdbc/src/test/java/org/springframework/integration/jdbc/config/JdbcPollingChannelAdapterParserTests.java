/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.integration.jdbc.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Test;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.history.MessageHistory;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.AbstractSqlParameterSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author David Syer
 * @author Gary Russell
 * @since 2.0
 *
 */
// Not transactional because the poller threads need access to the data
// @Transactional
public class JdbcPollingChannelAdapterParserTests {

	private JdbcTemplate jdbcTemplate;

	private MessagingTemplate messagingTemplate;

	private ConfigurableApplicationContext appCtx;

	private PlatformTransactionManager transactionManager;

	@Test
	public void testNoAutoStartupInboundChannelAdapter() {
		setUp("pollingNoAutoStartupJdbcInboundChannelAdapterTest.xml", getClass());
		this.jdbcTemplate.update("insert into item values(1,'',2)");
		Message<?> message = messagingTemplate.receive();
		assertNull("Message found ", message);
	}

	@Test
	public void testSimpleInboundChannelAdapter() {
		setUp("pollingForMapJdbcInboundChannelAdapterTest.xml", getClass());
		this.jdbcTemplate.update("insert into item values(1,'',2)");
		Message<?> message = messagingTemplate.receive();
		assertNotNull("No message found ", message);
		assertTrue("Wrong payload type expected instance of List", message.getPayload() instanceof List<?>);
		MessageHistory history = MessageHistory.read(message);
		assertNotNull(history);
		Properties componentHistoryRecord = TestUtils.locateComponentInHistory(history, "jdbcAdapter", 0);
		assertNotNull(componentHistoryRecord);
		assertEquals("jdbc:inbound-channel-adapter", componentHistoryRecord.get("type"));

	}

	@Test
	public void testSimpleInboundChannelAdapterWithUpdate() {
		setUp("pollingForMapJdbcInboundChannelAdapterWithUpdateTest.xml", getClass());
		this.jdbcTemplate.update("insert into item values(1,'',2)");
		Message<?> message = messagingTemplate.receive();
		assertNotNull(message);
		message = messagingTemplate.receive();
		assertNull(messagingTemplate.receive());
	}

	@Test
	public void testSimpleInboundChannelAdapterWithNestedUpdate() {
		setUp("pollingForMapJdbcInboundChannelAdapterWithNestedUpdateTest.xml", getClass());
		this.jdbcTemplate.update("insert into item values(1,'',2)");
		Message<?> message = messagingTemplate.receive();
		assertNotNull(message);
		message = messagingTemplate.receive();
		assertNull(messagingTemplate.receive());
	}

	@Test
	public void testExtendedInboundChannelAdapter() {
		setUp("pollingWithJdbcOperationsJdbcInboundChannelAdapterTest.xml", getClass());
		this.jdbcTemplate.update("insert into item values(1,'',2)");
		Message<?> message = messagingTemplate.receive();
		assertNotNull(message);
	}

	@Test
	public void testParameterSourceFactoryInboundChannelAdapter() {
		setUp("pollingWithParameterSourceJdbcInboundChannelAdapterTest.xml", getClass());
		this.jdbcTemplate.update("insert into item values(1,'',2)");
		Message<?> message = messagingTemplate.receive();
		assertNotNull(message);
		List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM item WHERE status=1");
		assertEquals(1, list.size());
		assertEquals("BAR", list.get(0).get("NAME"));
	}

	@Test
	public void testSelectParameterSourceFactoryInboundChannelAdapter() {
		setUp("pollingWithSelectParameterSourceJdbcInboundChannelAdapterTest.xml", getClass());
		this.jdbcTemplate.update("insert into item values(1,'',42)");
		Message<?> message = messagingTemplate.receive();
		assertNotNull(message);
		assertEquals(42, ((Map<?, ?>) ((List<?>) message.getPayload()).get(0)).get("STATUS"));
		this.jdbcTemplate.update("insert into item values(2,'',84)");
		this.appCtx.getBean(Status.class).which = 84;
		message = messagingTemplate.receive();
		assertNotNull(message);
		assertEquals(84, ((Map<?, ?>) ((List<?>) message.getPayload()).get(0)).get("STATUS"));
	}

	@Test
	public void testParameterSourceInboundChannelAdapter() {
		setUp("pollingWithParametersForMapJdbcInboundChannelAdapterTest.xml", getClass());
		this.jdbcTemplate.update("insert into item values(1,'',2)");
		Message<?> message = messagingTemplate.receive();
		assertNotNull(message);
	}

	@Test
	public void testMaxRowsInboundChannelAdapter() {
		setUp("pollingWithMaxRowsJdbcInboundChannelAdapterTest.xml", getClass());
		new TransactionTemplate(transactionManager).execute(status -> {
			jdbcTemplate.update("insert into item values(1,'',2)");
			jdbcTemplate.update("insert into item values(2,'',2)");
			jdbcTemplate.update("insert into item values(3,'',2)");
			jdbcTemplate.update("insert into item values(4,'',2)");
			return null;
		});
		int count = 0;
		while (count < 4) {
			@SuppressWarnings("unchecked")
			Message<List<?>> message = (Message<List<?>>) messagingTemplate.receive();
			assertNotNull(message);
			int payloadSize = message.getPayload().size();
			assertTrue(payloadSize <= 2);
			count += payloadSize;
		}
	}

	@Test
	public void testAutoChannel() {
		setUp("autoChannelJdbcPollingChannelAdapterParserTests-context.xml", getClass());
		MessageChannel autoChannel = appCtx.getBean("autoChannel", MessageChannel.class);
		SourcePollingChannelAdapter autoChannelAdapter = appCtx.getBean("autoChannel.adapter", SourcePollingChannelAdapter.class);
		assertSame(autoChannel, TestUtils.getPropertyValue(autoChannelAdapter, "outputChannel"));
	}

	@After
	public void tearDown() {
		if (appCtx != null) {
			appCtx.close();
		}
	}

	public void setUp(String name, Class<?> cls) {
		appCtx = new ClassPathXmlApplicationContext(name, cls);
		setupJdbcTemplate();
		jdbcTemplate.update("delete from item");
		setupTransactionManager();
		setupMessagingTemplate();
	}

	protected void setupMessagingTemplate() {
		PollableChannel pollableChannel = this.appCtx.getBean("target", PollableChannel.class);
		this.messagingTemplate = new MessagingTemplate();
		this.messagingTemplate.setDefaultDestination(pollableChannel);
		this.messagingTemplate.setReceiveTimeout(10000);
	}

	protected void setupJdbcTemplate() {
		this.jdbcTemplate = new JdbcTemplate(this.appCtx.getBean("dataSource", DataSource.class));
	}

	protected void setupTransactionManager() {
		this.transactionManager = this.appCtx.getBean("transactionManager", PlatformTransactionManager.class);
	}

	public static class TestSqlParameterSource extends AbstractSqlParameterSource {

		@Override
		public Object getValue(String paramName) throws IllegalArgumentException {
			return 2;
		}

		@Override
		public boolean hasValue(String paramName) {
			return true;
		}

	}

	public static class Status {

		private int which = 42;

		public int which() {
			return this.which;
		}

	}

}
