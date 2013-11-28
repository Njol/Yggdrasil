package ch.njol.yggdrasil;

import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import ch.njol.yggdrasil.xml.YggXMLInputStream;
import ch.njol.yggdrasil.xml.YggXMLOutputStream;

public final class Yggdrasil {
	
	private final List<ClassResolver> classResolvers = new ArrayList<>();
	private final Deque<FieldHandler> fieldHandlers = new LinkedList<>();
	
	public Yggdrasil() {
		classResolvers.add(new JREClassResolver());
		fieldHandlers.addLast(new JREFieldHandler());
	}
	
	public YggdrasilOutputStream2 newOutputStream(final OutputStream out) throws IOException {
		return new DefaultYggdrasilOutputStream(this, out);
	}
	
	public YggdrasilInputStream newInputStream(final InputStream in) throws IOException {
		return new DefaultYggdrasilInputStream(this, in);
	}
	
	public YggdrasilOutputStream2 newXMLOutputStream(final OutputStream out) throws IOException {
		return new YggXMLOutputStream(this, out);
	}
	
	public YggdrasilInputStream newXMLInputStream(final InputStream in) throws IOException {
		return new YggXMLInputStream(this, in);
	}
	
	public void registerClassResolver(final ClassResolver r) {
		if (!classResolvers.contains(r))
			classResolvers.add(r);
	}
	
	public void registerFieldHandler(final FieldHandler h) {
		if (!fieldHandlers.contains(h))
			fieldHandlers.addFirst(h);
	}
	
	public final static boolean isSerializable(final Class<?> c) {
		return c.isPrimitive() || c == Object.class || Serializable.class.isAssignableFrom(c);
	}
	
	public Class<?> getClass(final String id) throws ClassNotFoundException {
		for (final ClassResolver r : classResolvers) {
			final Class<?> c = r.getClass(id);
			if (c != null) { // TODO error if not serialiseable?
				assert id.equals(r.getID(c));
				return c;
			}
		}
		throw new ClassNotFoundException("No class found for ID " + id);
	}
	
	public String getID(Class<?> c) {
		if (!isSerializable(c))
			throw new NotSerializableException(c.getCanonicalName());
		assert Tag.getType(c) == Tag.T_OBJECT || Tag.getType(c) == Tag.T_ENUM;
		if (Enum.class.isAssignableFrom(c) && c.getSuperclass() != Enum.class)
			c = c.getSuperclass();
		for (final ClassResolver r : classResolvers) {
			final String id = r.getID(c);
			if (id != null) {
				assert r.getClass(id) == c;
				return id;
			}
		}
		throw new YggdrasilException("No ID found for class " + c.getName());
	}
	
	public boolean missingField(final Object o, final String name, final Object value) throws StreamCorruptedException {
		for (final FieldHandler h : fieldHandlers) {
			if (h.missingField(o, name, value))
				return true;
		}
		throw new StreamCorruptedException("Missing field " + name + " in class " + o.getClass().getCanonicalName() + " was not handled");
	}
	
	public boolean incompatibleFieldType(final Object o, final Field f, final Object value) throws StreamCorruptedException {
		for (final FieldHandler h : fieldHandlers) {
			if (h.incompatibleFieldType(o, f, value))
				return true;
		}
		throw new StreamCorruptedException("Incompatible field " + f.getName() + " in class " + o.getClass().getCanonicalName() + " of incompatible type " + value.getClass().getCanonicalName() + " was not handled");
	}
	
	// TODO command line, e.g. convert to XML
	public static void main(final String[] args) {
		System.err.println("Command line not supported yet");
		System.exit(1);
	}
	
}
