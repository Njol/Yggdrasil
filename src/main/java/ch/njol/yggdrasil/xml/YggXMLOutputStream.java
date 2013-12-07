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

import static ch.njol.yggdrasil.Tag.*;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.OutputStream;
import java.util.Locale;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import ch.njol.util.StringUtils;
import ch.njol.yggdrasil.Tag;
import ch.njol.yggdrasil.Yggdrasil;
import ch.njol.yggdrasil.YggdrasilConstants;
import ch.njol.yggdrasil.YggdrasilException;
import ch.njol.yggdrasil.YggdrasilOutputStream;

public final class YggXMLOutputStream extends YggdrasilOutputStream {
	
	private final OutputStream os;
	private final XMLStreamWriter out;
	
	public YggXMLOutputStream(final Yggdrasil y, final OutputStream out) throws IOException, FactoryConfigurationError {
		super(y);
		try {
			os = out;
			this.out = XMLOutputFactory.newFactory().createXMLStreamWriter(out, "UTF-8");
			this.out.writeStartDocument("utf-8", "1.0");
			this.out.writeStartElement("yggdrasil");
			writeAttribute("version", "" + YggdrasilConstants.VERSION);
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
	// private
	
	private String getTypeName(Class<?> c) throws NotSerializableException {
		String a = "";
		while (c.isArray()) {
			a += "[]";
			c = c.getComponentType();
		}
		final String s;
		final Tag type = getType(c);
		switch (type) {
			case T_OBJECT:
			case T_ENUM:
				s = y.getID(c);
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
			default:
				s = type.name;
				break;
			case T_NULL:
			case T_REFERENCE:
			case T_ARRAY:
				throw new YggdrasilException("" + c.getCanonicalName());
		}
		return s + a;
	}
	
	private void writeEndElement() throws IOException {
		try {
			out.writeEndElement();
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
	private void writeAttribute(final String s, final String value) throws IOException {
		try {
			out.writeAttribute(s, value);
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
	private void writeCharacters(final String s) throws IOException {
		try {
			out.writeCharacters(s);
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
	// Tag
	
	@Override
	protected void writeTag(final Tag t) throws IOException {
		try {
			if (t == T_NULL)
				out.writeEmptyElement(t.name);
			else
				out.writeStartElement(t.name);
			writeName();
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
	// Primitives
	
	@Override
	protected void writePrimitiveValue(final Object o) throws IOException {
		writeCharacters("" + o);
		writeEndElement();
	}
	
	@Override
	protected void writePrimitive_(final Object o) throws IOException {
		final Tag type = getPrimitiveFromWrapper(o.getClass());
		final int size;
		final long value;
		switch (type) {
			case T_BYTE:
				size = 1;
				value = 0xFFL & ((Byte) o);
				break;
			case T_SHORT:
				size = 2;
				value = 0xFFFFL & ((Short) o);
				break;
			case T_INT:
				size = 4;
				value = 0xFFFFFFFFL & ((Integer) o);
				break;
			case T_LONG:
				size = 8;
				value = (Long) o;
				break;
			case T_FLOAT:
				size = 4;
				value = 0xFFFFFFFFL & Float.floatToIntBits((Float) o);
				break;
			case T_DOUBLE:
				size = 8;
				value = Double.doubleToLongBits((Double) o);
				break;
			case T_CHAR:
				size = 2;
				value = 0xFFFFL & ((Character) o);
				break;
			case T_BOOLEAN:
				size = 0; // results in 1 character - 0 or 1
				value = ((Boolean) o) ? 1 : 0;
				break;
			//$CASES-OMITTED$
			default:
				throw new YggdrasilException("Invalid call to writePrimitive with argument " + o);
		}
		final String s = Long.toHexString(value).toUpperCase(Locale.ENGLISH);
		writeCharacters(StringUtils.multiply('0', Math.max(0, 2 * size - s.length())) + s);
	}
	
	// String
	
	@Override
	protected void writeStringValue(final String s) throws IOException {
		writeCharacters(s);
		writeEndElement();
	}
	
	// Array
	
	@Override
	protected void writeArrayComponentType(final Class<?> contentType) throws IOException {
		writeAttribute("componentType", getTypeName(contentType));
	}
	
	@Override
	protected void writeArrayLength(final int length) throws IOException {
		writeAttribute("length", "" + length);
	}
	
	@Override
	protected void writeArrayEnd() throws IOException {
		writeEndElement();
	}
	
	// Enum
	
	@Override
	protected void writeEnumType(final String type) throws IOException {
		writeAttribute("type", type);
	}
	
	@Override
	protected void writeEnumName(final String name) throws IOException {
		writeCharacters(name);
		writeEndElement();
	}
	
	// Class
	
	@Override
	protected void writeClassType(final Class<?> c) throws IOException {
		writeCharacters(getTypeName(c));
		writeEndElement();
	}
	
	// Reference
	
	@Override
	protected void writeReferenceID(final int ref) throws IOException {
		writeCharacters("" + ref);
		writeEndElement();
	}
	
	// generic Object
	
	@Override
	protected void writeObjectType(final String type) throws IOException {
		writeAttribute("type", type);
	}
	
	@Override
	protected void writeNumFields(final short numFields) throws IOException {
		writeAttribute("numFields", "" + numFields);
	}
	
	// name of the next field
	private String name = null;
	
	private final void writeName() throws IOException {
		if (name != null) {
			writeAttribute("name", name);
			name = null;
		}
	}
	
	@Override
	protected void writeFieldName(final String name) throws IOException {
		this.name = name;
	}
	
	@Override
	protected void writeObjectEnd() throws IOException {
		writeEndElement();
	}
	
	// stream
	
	@Override
	public void flush() throws IOException {
		try {
			out.flush();
			os.flush();
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
	@Override
	public void close() throws IOException {
		try {
			out.writeEndElement(); // </yggdrasil>
			out.writeEndDocument();
			out.close();
			os.close();
		} catch (final XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
}
