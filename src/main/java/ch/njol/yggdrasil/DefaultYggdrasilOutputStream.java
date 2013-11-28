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
import static ch.njol.yggdrasil.YggdrasilConstants.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public final class DefaultYggdrasilOutputStream extends YggdrasilOutputStream {
	
	private final OutputStream out;
	
	public DefaultYggdrasilOutputStream(final Yggdrasil y, final OutputStream out) throws IOException {
		super(y);
		this.out = out;
		writeInt(I_YGGDRASIL);
		writeShort(VERSION);
	}
	
	// private
	
	private void write(final int b) throws IOException {
		out.write(b);
	}
	
	private void writeTag(final Tag t) throws IOException {
		out.write(t.tag);
	}
	
	private final HashMap<String, Integer> writtenShortStrings = new HashMap<String, Integer>();
	int nextShortStringID = 0;
	
	/**
	 * Writes a class ID or Field name
	 */
	private void writeShortString(final String s) throws IOException {
		if (writtenShortStrings.containsKey(s)) {
			writeTag(T_REFERENCE);
			writeInt(writtenShortStrings.get(s));
		} else {
			final byte[] d = s.getBytes(utf8);
			if (d.length >= T_REFERENCE.tag)
				throw new YggdrasilException("Field name or Class ID too long: " + s);
			write(d.length);
			out.write(d);
			if (d.length > 4)
				writtenShortStrings.put(s, nextShortStringID++);
		}
	}
	
	// Null
	
	@Override
	protected void writeNull() throws IOException {
		writeTag(T_NULL);
	}
	
	// Primitives
	
	private void writeByte(final byte b) throws IOException {
		write(b & 0xFF);
	}
	
	private void writeShort(final short s) throws IOException {
		write((s >>> 8) & 0xFF);
		write(s & 0xFF);
	}
	
	private void writeInt(final int i) throws IOException {
		write((i >>> 24) & 0xFF);
		write((i >>> 16) & 0xFF);
		write((i >>> 8) & 0xFF);
		write(i & 0xFF);
	}
	
	private void writeLong(final long l) throws IOException {
		write((int) ((l >>> 56) & 0xFF));
		write((int) ((l >>> 48) & 0xFF));
		write((int) ((l >>> 40) & 0xFF));
		write((int) ((l >>> 32) & 0xFF));
		write((int) ((l >>> 24) & 0xFF));
		write((int) ((l >>> 16) & 0xFF));
		write((int) ((l >>> 8) & 0xFF));
		write((int) (l & 0xFF));
	}
	
	private void writeFloat(final float f) throws IOException {
		writeInt(Float.floatToIntBits(f));
	}
	
	private void writeDouble(final double d) throws IOException {
		writeLong(Double.doubleToLongBits(d));
	}
	
	private void writeChar(final char c) throws IOException {
		writeShort((short) c);
	}
	
	private void writeBoolean(final boolean b) throws IOException {
		write(b ? 1 : 0);
	}
	
	@Override
	protected void writePrimitive_(final Object o) throws IOException {
		switch (getPrimitiveFromWrapper(o.getClass())) {
			case T_BYTE:
				writeByte((Byte) o);
				break;
			case T_SHORT:
				writeShort((Short) o);
				break;
			case T_INT:
				writeInt((Integer) o);
				break;
			case T_LONG:
				writeLong((Long) o);
				break;
			case T_FLOAT:
				writeFloat((Float) o);
				break;
			case T_DOUBLE:
				writeDouble((Double) o);
				break;
			case T_CHAR:
				writeChar((Character) o);
				break;
			case T_BOOLEAN:
				writeBoolean((Boolean) o);
				break;
			//$CASES-OMITTED$
			default:
				throw new YggdrasilException("Invalid call to writePrimitive with argument " + o);
		}
	}
	
	@Override
	protected void writePrimitive(final Object o) throws IOException {
		final Tag type = getPrimitiveFromWrapper(o.getClass());
		writeTag(type);
		switch (type) {
			case T_BYTE:
				writeByte((Byte) o);
				break;
			case T_SHORT:
				writeShort((Short) o);
				break;
			case T_INT:
				writeInt((Integer) o);
				break;
			case T_LONG:
				writeLong((Long) o);
				break;
			case T_FLOAT:
				writeFloat((Float) o);
				break;
			case T_DOUBLE:
				writeDouble((Double) o);
				break;
			case T_CHAR:
				writeChar((Character) o);
				break;
			case T_BOOLEAN:
				writeBoolean((Boolean) o);
				break;
			//$CASES-OMITTED$
			default:
				throw new YggdrasilException("Invalid call to writePrimitive with argument " + o);
		}
	}
	
	@Override
	protected void writeWrappedPrimitive(final Object o) throws IOException {
		final Tag type = getType(o.getClass());
		writeTag(type);
		switch (type) {
			case T_BYTE_OBJ:
				writeByte((Byte) o);
				break;
			case T_SHORT_OBJ:
				writeShort((Short) o);
				break;
			case T_INT_OBJ:
				writeInt((Integer) o);
				break;
			case T_LONG_OBJ:
				writeLong((Long) o);
				break;
			case T_FLOAT_OBJ:
				writeFloat((Float) o);
				break;
			case T_DOUBLE_OBJ:
				writeDouble((Double) o);
				break;
			case T_CHAR_OBJ:
				writeChar((Character) o);
				break;
			case T_BOOLEAN_OBJ:
				writeBoolean((Boolean) o);
				break;
			//$CASES-OMITTED$
			default:
				throw new YggdrasilException("Invalid call to writePrimitive with argument " + o);
		}
	}
	
	// String
	
	@Override
	protected void writeString(final String s) throws IOException {
		writeTag(T_STRING);
		final byte[] d = s.getBytes(utf8);
		writeInt(d.length);
		out.write(d);
	}
	
	// Array
	
	@Override
	protected void writeArrayStart(final Class<?> componentType) throws IOException {
		writeTag(T_ARRAY);
		writeClass_(componentType);
	}
	
	@Override
	protected void writeArrayLength(final int length) throws IOException {
		writeInt(length);
	}
	
	@Override
	protected void writeArrayEnd() throws IOException {}
	
	// Class
	
	@Override
	protected void writeClass(final Class<?> c) throws IOException {
		writeTag(T_CLASS);
		writeClass_(c);
	}
	
	private void writeClass_(Class<?> c) throws IOException {
		while (c.isArray()) {
			writeTag(T_ARRAY);
			c = c.getComponentType();
		}
		final Tag t = getType(c);
		switch (t) {
			case T_OBJECT:
			case T_ENUM:
				writeTag(t);
				writeShortString(y.getID(c));
				break;
			case T_BOOLEAN:
			case T_BOOLEAN_OBJ:
			case T_BYTE:
			case T_BYTE_OBJ:
			case T_CHAR:
			case T_CHAR_OBJ:
			case T_DOUBLE:
			case T_DOUBLE_OBJ:
			case T_FLOAT:
			case T_FLOAT_OBJ:
			case T_INT:
			case T_INT_OBJ:
			case T_LONG:
			case T_LONG_OBJ:
			case T_SHORT:
			case T_SHORT_OBJ:
			case T_CLASS:
			case T_STRING:
				writeTag(t);
				break;
			case T_NULL:
			case T_REFERENCE:
			case T_ARRAY:
			default:
				throw new YggdrasilException("" + c.getCanonicalName());
		}
	}
	
	// Enum
	
	@Override
	protected void writeEnumStart(final String type) throws IOException {
		writeTag(T_ENUM);
		writeShortString(type);
	}
	
	@Override
	protected void writeEnumName(final String name) throws IOException {
		writeShortString(name);
	}
	
	// generic Object
	
	@Override
	protected void writeObjectStart(final String type) throws IOException {
		writeTag(T_OBJECT);
		writeShortString(type);
	}
	
	@Override
	protected void writeNumFields(final short numFields) throws IOException {
		writeShort(numFields);
	}
	
	@Override
	protected void writeFieldName(final String name) throws IOException {
		writeShortString(name);
	}
	
	@Override
	protected void writeObjectEnd() throws IOException {}
	
	// Reference
	
	@Override
	protected void writeReference(final int ref) throws IOException {
		writeTag(T_REFERENCE);
		writeInt(ref);
	}
	
	// stream
	
	@Override
	public void flush() throws IOException {
		out.flush();
	}
	
	@Override
	public void close() throws IOException {
		out.close();
	}
	
}
