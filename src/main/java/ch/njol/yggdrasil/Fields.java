/*
 *   This file is part of Yggdrasil, a data format to store object graphs.
 *
 *  Yggdrasil is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Yggdrasil is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2013 Peter Güttinger
 * 
 */

package ch.njol.yggdrasil;

import java.io.NotSerializableException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import ch.njol.yggdrasil.Fields.FieldContext; // required - wtf
import ch.njol.yggdrasil.YggdrasilSerializable.YggdrasilRobustSerializable;

public final class Fields implements Iterable<FieldContext> {
	
	/**
	 * Holds a field's name and value, and throws errors if primitives and objects are used incorrectly.
	 * 
	 * @author Peter Güttinger
	 */
	public final static class FieldContext {
		final String name;
		private Object value;
		private boolean isPrimitiveValue;
		
		FieldContext(final String name) {
			this.name = name;
		}
		
		private FieldContext(final Field f, final Object o) throws IllegalArgumentException, IllegalAccessException {
			name = f.getName();
			value = f.get(o);
			isPrimitiveValue = f.getType().isPrimitive();
		}
		
		public String getName() {
			return name;
		}
		
		public boolean isPrimitive() {
			return isPrimitiveValue;
		}
		
		public Class<?> getType() {
			if (value == null)
				return null;
			return isPrimitiveValue ? Tag.getPrimitiveFromWrapper(value.getClass()).c : value.getClass();
		}
		
		public Object getObject() throws StreamCorruptedException {
			if (isPrimitiveValue)
				throw new StreamCorruptedException("field " + name + " is a primitive");
			return value;
		}
		
		public Object getPrimitive() throws StreamCorruptedException {
			if (!isPrimitiveValue)
				throw new StreamCorruptedException("field " + name + " is not a primitive");
			return value;
		}
		
		public void setObject(final Object value) {
			this.value = value;
			isPrimitiveValue = false;
		}
		
		public void setPrimitive(final Object value) {
			assert value != null && Tag.getPrimitiveFromWrapper(value.getClass()) != null;
			this.value = value;
			isPrimitiveValue = true;
		}
		
		public void setField(final Object o, final Field f, final Yggdrasil y) throws StreamCorruptedException {
			if (Modifier.isStatic(f.getModifiers()))
				throw new YggdrasilException("The field " + name + " of " + f.getDeclaringClass() + " is static");
			if (f.getType().isPrimitive() != isPrimitiveValue)
				throw new YggdrasilException("The field " + name + " of " + f.getDeclaringClass() + " is not primitive");
			try {
				f.setAccessible(true);
				f.set(o, value);
			} catch (final IllegalArgumentException e) {
				if (!(o instanceof YggdrasilRobustSerializable) || !((YggdrasilRobustSerializable) o).incompatibleFieldType(f, this))
					y.incompatibleFieldType(o, f, this);
			} catch (final IllegalAccessException e) {
				assert false;
			}
		}
		
		@Override
		public int hashCode() {
			return name.hashCode();
		}
		
		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof FieldContext))
				return false;
			final FieldContext other = (FieldContext) obj;
			return name.equals(other.name);
		}
		
	}
	
	private final Map<String, FieldContext> fields = new HashMap<String, FieldContext>();
	
	/**
	 * Creates an empty Fields object.
	 */
	public Fields() {}
	
	/**
	 * Creates a fields object and initialises it with all non-transient and non-static fields of the given class and its superclasses.
	 * 
	 * @param c Some class
	 * @throws NotSerializableException If a field occurs more than once (i.e. if a class has a field with the same name as a field in one of its superclasses)
	 */
	public Fields(final Class<?> c) throws NotSerializableException {
		for (final Field f : getFields(c)) {
			fields.put(f.getName(), new FieldContext(f.getName()));
		}
	}
	
	/**
	 * Creates a fields object and initialises it with all non-transient and non-static fields of the given object.
	 * 
	 * @param o Some object
	 * @throws NotSerializableException If a field occurs more than once (i.e. if a class has a field with the same name as a field in one of its superclasses)
	 */
	public Fields(final Object o) throws NotSerializableException {
		for (final Field f : getFields(o.getClass())) {
			try {
				fields.put(f.getName(), new FieldContext(f, o));
			} catch (final IllegalArgumentException e) {
				assert false;
			} catch (final IllegalAccessException e) {
				assert false;
			}
		}
	}
	
	private final static Map<Class<?>, Collection<Field>> cache = new HashMap<Class<?>, Collection<Field>>();
	
	/**
	 * Gets all serialisable fields of the provided class.
	 * 
	 * @param c The class to get the fields of
	 * @return All non-static and non-transient fields of the given class and its superclasses
	 * @throws NotSerializableException If a field occurs more than once (i.e. if a class has a field with the same name as a field in one of its superclasses)
	 */
	public final static Collection<Field> getFields(final Class<?> c) throws NotSerializableException {
		Collection<Field> fields = cache.get(c);
		if (fields != null)
			return fields;
		fields = new ArrayList<Field>();
		for (Class<?> sc = c; sc != null; sc = sc.getSuperclass()) {
			final Field[] fs = sc.getDeclaredFields();
			for (final Field f : fs) {
				final int m = f.getModifiers();
				if (Modifier.isStatic(m) || Modifier.isTransient(m))
					continue;
				if (fields.contains(f))
					throw new NotSerializableException(c.getName() + ": duplicate field " + f.getName());
				f.setAccessible(true);
				fields.add(f);
			}
		}
		fields = Collections.unmodifiableCollection(fields);
		cache.put(c, fields);
		return fields;
	}
	
	/**
	 * Sets all fields of the given Object to the values stored in this Fields object.
	 * <p>
	 * This also sets transient fields.
	 * 
	 * @param o The object whose fields should be set
	 * @param y A reference to the Yggdrasil object used for loading - this is required for incompatible or missing fields. You can use <tt>null</tt> if your class implements
	 *            {@link YggdrasilRobustSerializable} and handles <i>all</i> fields, or if you are sure that <tt>fields</tt> only contains existing fields. A
	 *            {@link NullPointerException} may be thrown otherwise.
	 * @throws StreamCorruptedException
	 * @throws NotSerializableException
	 */
	public void setFields(final Object o, final Yggdrasil y) throws StreamCorruptedException, NotSerializableException {
		final Set<FieldContext> missing = new HashSet<FieldContext>(fields.values());
		for (final Field f : getFields(o.getClass())) {
			final FieldContext c = fields.get(f.getName());
			if (c == null)
				throw new StreamCorruptedException("Missing field " + f.getName() + " of " + o.getClass() + " in stream");
			c.setField(o, f, y);
			missing.remove(c);
		}
		for (final FieldContext f : missing) {
			if (!(o instanceof YggdrasilRobustSerializable) || !((YggdrasilRobustSerializable) o).missingField(f))
				y.missingField(o, f);
		}
	}
	
	/**
	 * @return The number of fields defined
	 */
	public int size() {
		return fields.size();
	}
	
	public void putObject(final String field, final Object value) {
		FieldContext c = fields.get(field);
		if (c == null)
			fields.put(field, c = new FieldContext(field));
		c.setObject(value);
	}
	
	public void putPrimitive(final String field, final Object value) {
		FieldContext c = fields.get(field);
		if (c == null)
			fields.put(field, c = new FieldContext(field));
		c.setPrimitive(value);
	}
	
	/**
	 * @param field A field's name
	 * @return Whether the field is defined
	 */
	public boolean contains(final String field) {
		return fields.containsKey(field);
	}
	
	public Object getObject(final String field) throws StreamCorruptedException {
		final FieldContext c = fields.get(field);
		if (c == null)
			throw new StreamCorruptedException("Nonexistent field " + field);
		return c.getObject();
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getObject(final String field, final Class<T> expectedType) throws StreamCorruptedException {
		assert !expectedType.isPrimitive();
		final FieldContext c = fields.get(field);
		if (c == null)
			throw new StreamCorruptedException("Nonexistent field " + field);
		final Object o = c.getObject();
		if (o != null && !expectedType.isInstance(o))
			throw new StreamCorruptedException("Field " + field + " of " + o.getClass() + " but expected " + expectedType);
		return (T) o;
	}
	
	public Object getPrimitive(final String field) throws StreamCorruptedException {
		final FieldContext c = fields.get(field);
		if (c == null)
			throw new StreamCorruptedException("Nonexistent field " + field);
		return c.getPrimitive();
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getPrimitive(final String field, final Class<T> expectedType) throws StreamCorruptedException {
		assert expectedType.isPrimitive() || Tag.getPrimitiveFromWrapper(expectedType) != null;
		final FieldContext c = fields.get(field);
		if (c == null)
			throw new StreamCorruptedException("Nonexistent field " + field);
		final Object o = c.getPrimitive();
		if (o != null && !(expectedType.isPrimitive() ? Tag.getType(expectedType).getWrapper().c.isInstance(o) : expectedType.isInstance(o)))
			throw new StreamCorruptedException("Field " + field + " of " + o.getClass() + " but expected " + expectedType);
		return (T) o;
	}
	
	public <T> T getAndRemoveObject(final String field, final Class<T> expectedType) throws StreamCorruptedException {
		final T t = getObject(field, expectedType);
		removeField(field);
		return t;
	}
	
	public <T> T getAndRemovePrimitive(final String field, final Class<T> expectedType) throws StreamCorruptedException {
		final T t = getPrimitive(field, expectedType);
		removeField(field);
		return t;
	}
	
	/**
	 * Removes a field and its value from this Fields object.
	 * 
	 * @param field The name of the field to remove
	 * @return Whether a field with the given name was actually defined
	 */
	public boolean removeField(final String field) {
		return fields.remove(field) != null;
	}
	
	@Override
	public Iterator<FieldContext> iterator() {
		return fields.values().iterator();
	}
	
}
