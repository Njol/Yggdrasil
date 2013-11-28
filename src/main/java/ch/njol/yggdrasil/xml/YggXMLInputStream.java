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

package ch.njol.yggdrasil.xml;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.Array;
import java.util.NoSuchElementException;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import ch.njol.yggdrasil.Tag;
import ch.njol.yggdrasil.Yggdrasil;
import ch.njol.yggdrasil.YggdrasilConstants;
import ch.njol.yggdrasil.YggdrasilInputStream;

public final class YggXMLInputStream extends YggdrasilInputStream {
	
	private final XMLStreamReader in;
	private final InputStream is;
	
	public YggXMLInputStream(final Yggdrasil y, final InputStream in) throws IOException {
		super(y);
		is = in;
		try {
			this.in = XMLInputFactory.newFactory().createXMLStreamReader(in);
			while (this.in.next() != XMLStreamConstants.START_ELEMENT) {}
			if (!this.in.getLocalName().equals("yggdrasil"))
				throw new StreamCorruptedException("Not an Yggdrasil stream");
			final String v = getAttribute("version");
			if (v == null || !v.equals("" + YggdrasilConstants.VERSION))
				throw new StreamCorruptedException("Input was saved using a later version of Yggdrasil");
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		} catch (final FactoryConfigurationError e) {
			throw new IOException(e);
		}
	}
	
	// private
	
	private Class<?> getType(String s) throws StreamCorruptedException {
		int dim = 0;
		while (s.endsWith("[]")) {
			s = s.substring(0, s.length() - 2);
			dim++;
		}
		Class<?> c;
		final Tag t = Tag.byName(s);
		if (t != null)
			c = t.c;
		else
			c = y.getClass(s);
		if (c == null)
			throw new StreamCorruptedException("Invalid type " + s);
		if (dim == 0)
			return c;
		while (dim-- > 0)
			c = Array.newInstance(c, 0).getClass();
		return c;
	}
	
	private String getAttribute(final String name) {
		return in.getAttributeValue(null, name);
	}
	
	// Tag
	
	private Tag nextTag = null;
	
	@Override
	protected Tag readTag() throws IOException {
		if (nextTag != null) {
			final Tag t = nextTag;
			nextTag = null;
			return t;
		}
		try {
			while (in.next() != XMLStreamConstants.START_ELEMENT) {}
		} catch (final XMLStreamException e) {
			throw new StreamCorruptedException();
		} catch (final NoSuchElementException e) {
			throw new EOFException();
		}
		final Tag t = Tag.byName(in.getLocalName());
		if (t == null)
			throw new StreamCorruptedException("Invalid tag " + in.getLocalName());
		return t;
	}
	
	// Primitives
	
	@Override
	protected Object readPrimitive(final Tag type) throws IOException {
		try {
			final String v = in.getElementText();
			switch (type) {
				case T_BYTE:
					return Byte.parseByte(v);
				case T_SHORT:
					return Short.parseShort(v);
				case T_INT:
					return Integer.parseInt(v);
				case T_LONG:
					return Long.parseLong(v);
				case T_FLOAT:
					return Float.parseFloat(v);
				case T_DOUBLE:
					return Double.parseDouble(v);
				case T_BOOLEAN:
					return Boolean.parseBoolean(v);
				case T_CHAR:
					if (v.length() > 1)
						throw new StreamCorruptedException();
					return v.charAt(0);
					//$CASES-OMITTED$
				default:
					throw new StreamCorruptedException();
			}
		} catch (final XMLStreamException e) {
			throw new StreamCorruptedException();
		} catch (final NumberFormatException e) {
			throw new StreamCorruptedException();
		}
	}
	
	String primitiveData = null;
	int primitiveDataIndex = 0;
	
	@Override
	protected Object readPrimitive_(final Tag type) throws IOException {
		try {
			if (in.getEventType() == XMLStreamConstants.START_ELEMENT) {
				primitiveData = in.getElementText(); // advances stream to END_ELEMENT
				primitiveDataIndex = 0;
			}
			assert in.getEventType() == XMLStreamConstants.END_ELEMENT;
			switch (type) {
				case T_BYTE:
					return (byte) Short.parseShort(primitiveData.substring(primitiveDataIndex, primitiveDataIndex += 2), 16);
				case T_SHORT:
					return (short) Integer.parseInt(primitiveData.substring(primitiveDataIndex, primitiveDataIndex += 4), 16);
				case T_INT:
					return (int) Long.parseLong(primitiveData.substring(primitiveDataIndex, primitiveDataIndex += 8), 16);
				case T_LONG:
					return Long.parseLong(primitiveData.substring(primitiveDataIndex, primitiveDataIndex += 8), 16) << 32 | Long.parseLong(primitiveData.substring(primitiveDataIndex, primitiveDataIndex += 8), 16);
				case T_FLOAT:
					return Float.intBitsToFloat((int) Long.parseLong(primitiveData.substring(primitiveDataIndex, primitiveDataIndex += 8), 16));
				case T_DOUBLE:
					return Double.longBitsToDouble(Long.parseLong(primitiveData.substring(primitiveDataIndex, primitiveDataIndex += 8), 16) << 32 | Long.parseLong(primitiveData.substring(primitiveDataIndex, primitiveDataIndex += 8), 16));
				case T_BOOLEAN:
					final char c = primitiveData.charAt(primitiveDataIndex++);
					if (c == '1')
						return true;
					else if (c == '0')
						return false;
					throw new StreamCorruptedException();
				case T_CHAR:
					return primitiveData.charAt(primitiveDataIndex++);
					//$CASES-OMITTED$
				default:
					throw new StreamCorruptedException();
			}
		} catch (final XMLStreamException e) {
			throw new StreamCorruptedException();
		} catch (final StringIndexOutOfBoundsException e) {
			throw new StreamCorruptedException();
		} catch (final NumberFormatException e) {
			throw new StreamCorruptedException();
		}
	}
	
	// String
	
	@Override
	protected String readString() throws IOException {
		try {
			return in.getElementText();
		} catch (final XMLStreamException e) {
			throw new StreamCorruptedException();
		}
	}
	
	// Array
	
	@Override
	protected Class<?> readArrayContentType() throws IOException {
		return getType(getAttribute("contentType"));
	}
	
	@Override
	protected int readArrayLength() throws IOException {
		try {
			return Integer.parseInt(getAttribute("length"));
		} catch (final NumberFormatException e) {
			throw new StreamCorruptedException();
		}
	}
	
	// Enum
	
	@Override
	protected Class<?> readEnumType() throws IOException {
		return getType(getAttribute("type"));
	}
	
	@Override
	protected String readEnumName() throws IOException {
		try {
			return in.getElementText();
		} catch (final XMLStreamException e) {
			throw new StreamCorruptedException();
		}
	}
	
	// Class
	
	@Override
	protected Class<?> readClass() throws IOException {
		try {
			return getType(in.getElementText());
		} catch (final XMLStreamException e) {
			throw new StreamCorruptedException();
		}
	}
	
	// Reference
	
	@Override
	protected int readReference() throws IOException {
		try {
			return Integer.parseInt(in.getElementText());
		} catch (final NumberFormatException e) {
			throw new StreamCorruptedException();
		} catch (final XMLStreamException e) {
			throw new StreamCorruptedException();
		}
	}
	
	// generic Object
	
	@Override
	protected Class<?> readObjectType() throws IOException {
		return getType(getAttribute("type"));
	}
	
	@Override
	protected short readNumFields() throws IOException {
		try {
			return Short.parseShort(getAttribute("numFields"));
		} catch (final NumberFormatException e) {
			throw new StreamCorruptedException();
		}
	}
	
	@Override
	protected String readFieldName() throws IOException {
		nextTag = readTag();
		return getAttribute("name");
	}
	
	// stream
	
	@Override
	public void close() throws IOException {
		try {
			in.close();
			is.close();
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
}
