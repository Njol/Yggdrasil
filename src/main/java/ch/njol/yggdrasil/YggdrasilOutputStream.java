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
import java.io.Flushable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.IdentityHashMap;

import ch.njol.yggdrasil.Fields.FieldContext;
import ch.njol.yggdrasil.YggdrasilSerializable.YggdrasilExtendedSerializable;

public abstract class YggdrasilOutputStream implements Flushable, Closeable {
	
	protected final Yggdrasil y;
	
	protected YggdrasilOutputStream(final Yggdrasil y) {
		this.y = y;
	}
	
	// Null
	
	protected abstract void writeNull() throws IOException;
	
	// Primitives
	
	protected abstract void writePrimitive(Object o) throws IOException;
	
	protected abstract void writePrimitive_(Object o) throws IOException;
	
	protected abstract void writeWrappedPrimitive(Object o) throws IOException;
	
	// String
	
	protected abstract void writeString(String s) throws IOException;
	
	// Array
	
	protected abstract void writeArrayStart(Class<?> componentType) throws IOException;
	
	protected abstract void writeArrayLength(int length) throws IOException;
	
	protected abstract void writeArrayEnd() throws IOException;
	
	private final void writeArray(final Object array) throws IOException {
		final int length = Array.getLength(array);
		final Class<?> ct = array.getClass().getComponentType();
		writeArrayStart(ct);
		writeArrayLength(length);
		if (ct.isPrimitive()) {
			for (int i = 0; i < length; i++)
				writePrimitive_(Array.get(array, i));
			writeArrayEnd();
		} else {
			for (final Object o : (Object[]) array)
				writeObject(o);
			writeArrayEnd();
		}
	}
	
	// Enum
	
	protected abstract void writeEnumStart(String type) throws IOException;
	
	protected abstract void writeEnumName(String name) throws IOException;
	
	private final void writeEnum(final Enum<?> o) throws IOException {
		writeEnumStart(y.getID(o.getDeclaringClass()));
		writeEnumName(o.name());
	}
	
	// Class
	
	protected abstract void writeClass(Class<?> c) throws IOException;
	
	// Reference
	
	protected abstract void writeReference(int ref) throws IOException;
	
	// generic Objects
	
	protected abstract void writeObjectStart(String type) throws IOException;
	
	protected abstract void writeNumFields(short numFields) throws IOException;
	
	protected abstract void writeFieldName(String name) throws IOException;
	
	protected abstract void writeObjectEnd() throws IOException;
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	private final void writeGenericObject(final Object o, int ref) throws IOException {
		final Class<?> c = o.getClass();
		if (!y.isSerializable(c))
			throw new YggdrasilException(c + " is not serialisable");
		final Fields fields;
		final YggdrasilSerializer s = y.getSerializer(c);
		if (s != null) {
			fields = s.serialize(o);
			if (fields == null)
				throw new YggdrasilException("The serializer of " + c + " returned null");
			if (!s.canBeInstantiated(c)) {
				ref = ~ref; // ~ instead of - to also get a negative value if ref is 0
				writtenObjects.put(o, ref);
			}
		} else if (o instanceof YggdrasilExtendedSerializable) {
			fields = ((YggdrasilExtendedSerializable) o).serialize();
			if (fields == null)
				throw new YggdrasilException("The serialize() method of " + c + " returned null");
		} else {
			fields = new Fields(o);
		}
		if (fields.size() > Short.MAX_VALUE)
			throw new YggdrasilException("Class " + c.getCanonicalName() + " has too many fields (" + fields.size() + ")");
		writeObjectStart(y.getID(o.getClass()));
		writeNumFields((short) fields.size());
		for (final FieldContext f : fields) {
			writeFieldName(f.name);
			if (f.isPrimitive())
				writePrimitive(f.getPrimitive());
			else
				writeObject(f.getObject());
		}
		writeObjectEnd();
		if (ref < 0)
			writtenObjects.put(o, ~ref);
	}
	
	// any Objects
	
	private int nextObjectID = 0;
	private final IdentityHashMap<Object, Integer> writtenObjects = new IdentityHashMap<Object, Integer>();
	
	public final void writeObject(final Object o) throws IOException {
		if (o == null) {
			writeNull();
			return;
		}
		if (writtenObjects.containsKey(o)) {
			final int ref = writtenObjects.get(o);
			if (ref < 0)
				throw new YggdrasilException("Uninstantiable object " + o + " is referenced in its fields' graph");
			writeReference(ref);
			return;
		}
		final int ref = nextObjectID++;
		writtenObjects.put(o, ref);
		final Tag type = getType(o.getClass());
		if (type.isWrapper()) {
			writeWrappedPrimitive(o);
			return;
		}
		switch (type) {
			case T_ARRAY:
				writeArray(o);
				return;
			case T_STRING:
				writeString((String) o);
				return;
			case T_ENUM:
				writeEnum((Enum<?>) o);
				return;
			case T_CLASS:
				writeClass((Class<?>) o);
				return;
			case T_OBJECT:
				writeGenericObject(o, ref);
				return;
				//$CASES-OMITTED$
			default:
				throw new YggdrasilException("unhandled type " + type);
		}
	}
	
}
