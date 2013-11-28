package ch.njol.yggdrasil;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Handles common JRE-related incompatible field types. This handler should be the last one to be called.
 * 
 * @author Peter Güttinger
 */
public class JREFieldHandler implements FieldHandler {
	
	/**
	 * Not used
	 */
	@Override
	public boolean missingField(final Object o, final String name, final Object value) {
		return false;
	}
	
	/**
	 * Converts collection types and non-primitive arrays
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Override
	public boolean incompatibleFieldType(final Object o, final Field f, Object value) {
		if (value instanceof Object[])
			value = Arrays.asList(value);
		if (value instanceof Collection) {
			final Collection v = (Collection) value;
			try {
				if (Collection.class.isAssignableFrom(f.getType())) {
					final Collection c = (Collection) f.get(o);
					if (c != null) {
						c.clear();
						c.addAll(v);
						return true;
					}
				} else if (Object[].class.isAssignableFrom(f.getType())) {
					Object[] array = (Object[]) f.get(o);
					if (array != null) {
						if (array.length < v.size())
							return false;
						Class<?> ct = array.getClass().getComponentType();
						for (Object x : v) {
							if (!ct.isInstance(x))
								return false;
						}
					} else {
						array = (Object[]) Array.newInstance(f.getType().getComponentType(), v.size());
						f.set(o, array);
					}
					final int l = array.length;
					int i = 0;
					for (final Object x : v)
						array[i++] = x;
					while (i < l)
						array[i++] = null;
				}
			} catch (IllegalArgumentException | IllegalAccessException | UnsupportedOperationException | ClassCastException | NullPointerException | IllegalStateException e) {
				throw new YggdrasilException(e);
			}
		} else if (value instanceof Map) {
			if (!Map.class.isAssignableFrom(f.getType()))
				return false;
			try {
				final Map m = (Map) f.get(o);
				if (m != null) {
					m.clear();
					m.putAll((Map) value);
					return true;
				}
			} catch (IllegalArgumentException | IllegalAccessException | UnsupportedOperationException | ClassCastException | NullPointerException e) {
				throw new YggdrasilException(e);
			}
		}
		return false;
	}
	
}
