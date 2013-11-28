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

import static ch.njol.yggdrasil.Tag.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import ch.njol.yggdrasil.YggdrasilSerializable.YggdrasilExtendedSerializable;
import ch.njol.yggdrasil.YggdrasilSerializable.YggdrasilRobustEnum;

public abstract class YggdrasilInputStream implements Closeable {
	
	protected final Yggdrasil y;
	
	protected YggdrasilInputStream(final Yggdrasil y) {
		this.y = y;
	}
	
	// Tag
	
	protected abstract Tag readTag() throws IOException;
	
	// Primitives
	
	protected abstract Object readPrimitive(Tag type) throws IOException;
	
	protected abstract Object readPrimitive_(Tag type) throws IOException;
	
	// String
	
	protected abstract String readString() throws IOException;
	
	// Array
	
	protected abstract Class<?> readArrayContentType() throws IOException;
	
	protected abstract int readArrayLength() throws IOException;
	
	private final void readArrayContents(final Object array) throws IOException {
		if (array.getClass().getComponentType().isPrimitive()) {
			final int length = Array.getLength(array);
			final Tag type = getType(array.getClass().getComponentType());
			for (int i = 0; i < length; i++) {
				Array.set(array, i, readPrimitive_(type));
			}
		} else {
			for (int i = 0; i < ((Object[]) array).length; i++) {
				((Object[]) array)[i] = readObject();
			}
		}
	}
	
	// Enum
	
	protected abstract Class<?> readEnumType() throws IOException;
	
	protected abstract String readEnumName() throws IOException;
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	private final Enum<?> readEnum() throws IOException {
		final Class<?> c = readEnumType();
		if (!Enum.class.isAssignableFrom(c))
			throw new StreamCorruptedException(c + " is not an enum type");
		final String name = readEnumName();
		try {
			return Enum.valueOf((Class<Enum>) c, name);
		} catch (final IllegalArgumentException e) {
			if (YggdrasilRobustEnum.class.isAssignableFrom(c)) {
				final Object[] cs = c.getEnumConstants();
				if (cs.length == 0)
					throw new StreamCorruptedException(c + " does not have any enum constants");
				return ((YggdrasilRobustEnum) cs[0]).missingConstant(name);
			} else {
				throw new StreamCorruptedException("Enum constant " + name + " does not exist in " + c);
			}
		}
	}
	
	// Class
	
	protected abstract Class<?> readClass() throws IOException;
	
	// Reference
	
	protected abstract int readReference() throws IOException;
	
	// generic Object
	
	protected abstract Class<?> readObjectType() throws IOException;
	
	protected abstract short readNumFields() throws IOException;
	
	protected abstract String readFieldName() throws IOException;
	
	private final Fields readFields() throws IOException {
		final Fields fields = new Fields();
		final short numFields = readNumFields();
		for (int i = 0; i < numFields; i++) {
			final String s = readFieldName();
			final Tag t = readTag();
			if (t.isPrimitive())
				fields.putPrimitive(s, readPrimitive(t));
			else
				fields.putObject(s, readObject(t));
		}
		return fields;
	}
	
	// any Objects
	
	private final List<Object> readObjects = new ArrayList<Object>();
	
	public final Object readObject() throws IOException {
		final Tag t = readTag();
		return readObject(t);
	}
	
	@SuppressWarnings("unchecked")
	public final <T> T readObject(final Class<T> expectedType) throws IOException {
		final Tag t = readTag();
		final Object o = readObject(t);
		if (o != null && !expectedType.isInstance(o))
			throw new StreamCorruptedException("Object " + o + " of " + o.getClass() + " but expected " + expectedType);
		return (T) o;
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	private final Object readObject(final Tag t) throws IOException {
		if (t == T_NULL)
			return null;
		if (t == T_REFERENCE) {
			final int ref = readReference();
			if (ref < 0 || ref >= readObjects.size())
				throw new StreamCorruptedException();
			return readObjects.get(ref);
		}
		final Object o;
		switch (t) {
			case T_ARRAY: {
				final Class<?> c = readArrayContentType();
				o = Array.newInstance(c, readArrayLength());
				readObjects.add(o);
				readArrayContents(o);
				return o;
			}
			case T_CLASS:
				o = readClass();
				break;
			case T_ENUM:
				o = readEnum();
				break;
			case T_STRING:
				o = readString();
				break;
			case T_OBJECT: {
				final Class<?> c = readObjectType();
				final YggdrasilSerializer s = y.getSerializer(c);
				if (s != null && !s.canBeInstantiated(c)) {
					final Fields fields = readFields();
					o = s.deserialize(c, fields);
					if (o == null)
						throw new StreamCorruptedException("YggdrasilSerializer " + s + " returned null from deserialize(" + c + "," + fields + ")");
				} else {
					o = y.newInstance(c);
					readObjects.add(o);
					final Fields fields = readFields();
					if (s != null) {
						s.deserialize(o, fields);
					} else if (o instanceof YggdrasilExtendedSerializable) {
						((YggdrasilExtendedSerializable) o).deserialize(fields);
					} else {
						fields.setFields(o, y);
					}
				}
				return o;
			}
			case T_BOOLEAN_OBJ:
			case T_BYTE_OBJ:
			case T_CHAR_OBJ:
			case T_DOUBLE_OBJ:
			case T_FLOAT_OBJ:
			case T_INT_OBJ:
			case T_LONG_OBJ:
			case T_SHORT_OBJ:
				o = readPrimitive(t.getPrimitive());
				break;
			case T_BYTE:
			case T_BOOLEAN:
			case T_CHAR:
			case T_DOUBLE:
			case T_FLOAT:
			case T_INT:
			case T_LONG:
			case T_SHORT:
			case T_REFERENCE:
			case T_NULL:
			default:
				throw new StreamCorruptedException();
		}
		readObjects.add(o);
		return o;
	}
	
//	private final static class Validation implements Comparable<Validation> {
//		private final ObjectInputValidation v;
//		private final int prio;
//		
//		public Validation(final ObjectInputValidation v, final int prio) {
//			this.v = v;
//			this.prio = prio;
//		}
//		
//		private void validate() throws InvalidObjectException {
//			v.validateObject();
//		}
//		
//		@Override
//		public int compareTo(final Validation o) {
//			return o.prio - prio;
//		}
//	}
//	
//	private final SortedSet<Validation> validations = new TreeSet<>();
//	
//	public void registerValidation(final ObjectInputValidation v, final int prio) throws NotActiveException, InvalidObjectException {
//		if (depth == 0)
//			throw new NotActiveException("stream inactive");
//		if (v == null)
//			throw new InvalidObjectException("null callback");
//		validations.add(new Validation(v, prio));
//	}
//	
//	private void validate() throws InvalidObjectException {
//		for (final Validation v : validations)
//			v.validate();
//		validations.clear(); // if multiple objects are written to the stream this method will be called multiple times
//	}
	
}