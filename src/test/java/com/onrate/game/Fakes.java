package com.onrate.game;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Stand-ins for RuneLite's interfaces (the game client, a widget, a player) in tests: each answers the
 * few calls a test sets up, and anything else with nothing (0, false or null), as a client that isn't
 * in that state would.
 */
public final class Fakes
{
	private Fakes()
	{
	}

	/** The answers of one stand-in, by method name. An answer gets the call's arguments. */
	public static final class Answers
	{
		private final Map<String, Function<Object[], Object>> byMethod = new HashMap<>();

		public Answers on(String method, Function<Object[], Object> answer)
		{
			byMethod.put(method, answer);
			return this;
		}

		public Answers on(String method, Object value)
		{
			return on(method, args -> value);
		}
	}

	public static <T> T of(Class<T> type, Answers answers)
	{
		InvocationHandler handler = (proxy, method, args) ->
		{
			Function<Object[], Object> answer = answers.byMethod.get(method.getName());
			if (answer != null)
			{
				return answer.apply(args == null ? new Object[0] : args);
			}
			switch (method.getName())
			{
				case "toString":
					return "fake " + type.getSimpleName();
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				default:
					break;
			}
			Class<?> r = method.getReturnType();
			if (r == boolean.class)
			{
				return false;
			}
			if (r == int.class)
			{
				return 0;
			}
			if (r == long.class)
			{
				return 0L;
			}
			if (r == double.class)
			{
				return 0.0;
			}
			if (r == float.class)
			{
				return 0f;
			}
			if (r == short.class)
			{
				return (short) 0;
			}
			if (r == byte.class)
			{
				return (byte) 0;
			}
			if (r == char.class)
			{
				return (char) 0;
			}
			return null;
		};
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
