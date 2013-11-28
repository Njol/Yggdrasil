package ch.njol.yggdrasil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ch.njol.yggdrasil.YggdrasilContext.FieldContext; // required - wtf

final class YggdrasilContext implements Iterable<FieldContext> {
	
	private final static class PrimitiveContainer {
		Object value;
		
		PrimitiveContainer(final Object value) {
			this.value = value;
		}
	}
	
	public final static class FieldContext {
		final Field field;
		private Object value;
		
		FieldContext(final Field field) {
			this.field = field;
		}
		
		public boolean isPrimitive() {
			return value instanceof PrimitiveContainer;
		}
		
		public void setValue(final Object o) throws IllegalArgumentException {
			try {
				field.setAccessible(true);
				if (field.getType().isPrimitive())
					value = new PrimitiveContainer(field.get(o));
				else
					value = field.get(o);
			} catch (final IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		
		public void setField(final Object o) throws IllegalArgumentException {
			try {
				field.setAccessible(true);
				if (field.getType().isPrimitive())
					field.set(o, getPrimitive());
				else
					field.set(o, getObject());
			} catch (final IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		
		public Object getObject() {
			if (value instanceof PrimitiveContainer)
				throw new IllegalArgumentException("field " + field + " is a primitive");
			return value;
		}
		
		public Object getPrimitive() {
			if (!(value instanceof PrimitiveContainer))
				throw new IllegalArgumentException("field " + field + " is not a primitive");
			return ((PrimitiveContainer) value).value;
		}
	}
	
	private final Map<String, FieldContext> fields = new HashMap<>();
	
//	private final Class<?> c;
	
	public YggdrasilContext(final Class<?> c) {
//		this.c = c;
		for (Class<?> sc = c; sc != null; sc = sc.getSuperclass()) {
			
			final Field[] fs = sc.getDeclaredFields();
			for (final Field f : fs) {
				final int m = f.getModifiers();
				if (Modifier.isStatic(m) || Modifier.isTransient(m))
					continue;
				f.setAccessible(true);
				fields.put(f.getName(), new FieldContext(f));
			}
		}
		if (fields.size() > Short.MAX_VALUE)
			throw new YggdrasilException("class " + c.getCanonicalName() + " has too many fields");
	}
	
	/**
	 * Stores the value of the given Object's fields in this YggdrasilContext
	 */
	public void setValues(final Object o) {
		for (final FieldContext c : this)
			c.setValue(o);
	}
	
	/**
	 * Sets all fields of the given Object to the values stored in this YggdrasilContext
	 */
	public void setFields(final Object o) {
		for (final FieldContext c : this)
			c.setField(o);
	}
	
	public int numFields() {
		return fields.size();
	}
	
	public void putObject(final String field, final Object value) {
		final FieldContext c = fields.get(field);
		if (c == null)
			throw new IllegalArgumentException("Nonexistent field " + field);
		c.value = value;
	}
	
	public void putPrimitive(final String field, final Object value) {
		final FieldContext c = fields.get(field);
		if (c == null)
			throw new IllegalArgumentException("Nonexistent field " + field);
		c.value = new PrimitiveContainer(value);
	}
	
	public boolean contains(String field) {
		return fields.containsKey(field);
	}
	
	public Object getObject(final String field) {
		final FieldContext c = fields.get(field);
		if (c == null)
			throw new IllegalArgumentException("Nonexistent field " + field);
		return c.getObject();
	}
	
	public Object getPrimitive(final String field) {
		final FieldContext c = fields.get(field);
		if (c == null)
			throw new IllegalArgumentException("Nonexistent field " + field);
		return c.getPrimitive();
	}
	
	@Override
	public Iterator<FieldContext> iterator() {
		return fields.values().iterator();
	}
	
}
