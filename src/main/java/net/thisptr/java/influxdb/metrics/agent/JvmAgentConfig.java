package net.thisptr.java.influxdb.metrics.agent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.management.ObjectName;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;

import net.thisptr.java.influxdb.metrics.agent.parser.Configuration.ResolvedConfiguration;
import net.thisptr.java.influxdb.metrics.agent.parser.ConfigurationException;
import net.thisptr.java.influxdb.metrics.agent.parser.ConfigurationParser;

public class JvmAgentConfig {
	public static final int DEFAULT_PORT = 8086;
	public static final int DEFAULT_INTERVAL = 30;
	public static final String DEFAULT_USER = "root";
	public static final String DEFAULT_PASSWORD = "root";
	public static final String DEFAULT_LOG_LEVEL = "INFO";

	public List<HostAndPort> servers;
	public boolean ssl;
	public int interval;
	public String database;
	public String user;
	public String password;
	public Map<String, String> tags;
	public String retention;

	public String logLevel;
	public String logPath;

	private ResolvedConfiguration config;

	public static class Context {
		private Map<String, String> values;

		public Context(final Map<String, String> values) {
			this.values = new LinkedHashMap<>(values);
		}

		public String getString(final String key) {
			return values.get(key);
		}

		public int getInteger(final String key, final int defval) {
			final String val = values.get(key);
			return val == null ? defval : Integer.parseInt(val);
		}

		public boolean getBoolean(final String key, final boolean defval) {
			final String val = values.get(key);
			return val == null ? defval : Boolean.parseBoolean(val);
		}
		
		public String getString(final String key, final String defval) {
			return values.getOrDefault(key, defval);
		}

		public Map<String, String> getSubProperties(final String key) {
			final Map<String, String> result = new LinkedHashMap<>();
			values.forEach((k, v) -> {
				if (k.startsWith(key))
					result.put(k.substring(key.length()), v);
			});
			return result;
		}
	}

	public Map<String, String> getConfig() {
		return config.properties;
	}

	public Map<String, String> getConfigForMetric(final ObjectName name, final String attribute) {
		final Map<String, String> result = new HashMap<>();
		config.matchers.forEach(matcher -> {
			if (!matcher.pattern.matches(name, attribute))
				return;
			matcher.properties.forEach(property -> {
				result.putIfAbsent(property.key, property.value);
			});
		});
		// default
		config.properties.forEach((k, v) -> {
			result.putIfAbsent(k, v);
		});
		return result;
	}

	private static JvmAgentConfig fromMap(final Map<String, String> values) {
		final Context context = new Context(values);

		final String servers = context.getString("servers");
		if (Strings.isNullOrEmpty(servers))
			throw new ConfigurationException("servers must not be null or empty.");
		final List<HostAndPort> hostAndPorts = StreamSupport.stream(Splitter.on(",").trimResults().split(servers).spliterator(), false)
				.map(server -> HostAndPort.fromString(server).withDefaultPort(DEFAULT_PORT))
				.collect(Collectors.toList());

		final boolean ssl = context.getBoolean("ssl", false);

		final int interval = context.getInteger("interval", DEFAULT_INTERVAL);
		if (interval <= 0)
			throw new ConfigurationException("interval must be positive.");

		
		final String database = context.getString("database");
		if (Strings.isNullOrEmpty(database))
			throw new ConfigurationException("database must not be null or empty.");

		final String user = context.getString("user", DEFAULT_USER);
		if (Strings.isNullOrEmpty(user))
			throw new ConfigurationException("user must not be null or empty.");

		final String password = context.getString("password", DEFAULT_PASSWORD);
		final String retention = context.getString("retention");

		final Map<String, String> tags = context.getSubProperties("tags.");

		final String logLevel = context.getString("log.level", DEFAULT_LOG_LEVEL);
		final String logPath = context.getString("log.path"); // stderr

		final JvmAgentConfig config = new JvmAgentConfig();
		config.servers = hostAndPorts;
		config.ssl = ssl;
		config.interval = interval;
		config.database = database;
		config.user = user;
		config.password = password;
		config.tags = tags;
		config.retention = retention;
		config.logLevel = logLevel;
		config.logPath = logPath;
		return config;
	}

	public static JvmAgentConfig fromString(final String str) {
		final ResolvedConfiguration conf = ConfigurationParser.parse(str).resolve();
		final JvmAgentConfig config = fromMap(conf.properties);
		config.config = conf;
		return config;
	}
}
