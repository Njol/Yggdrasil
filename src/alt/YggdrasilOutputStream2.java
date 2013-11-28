package ch.njol.yggdrasil;

import static ch.njol.yggdrasil.YggdrasilConstants.*;

import java.io.IOException;
import java.io.NotActiveException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Stack;

/**
 * Naming conventions:
 * <ul>
 * <li>x_(): write x with tag, info & data (e.g. T_ARRAY, content type, contents)
 * <li>x(): write info & data (e.g. content type, contents)
 * <li>_x(): write data only (e.g. contents)
 * </ul>
 * 
 * @author Peter Güttinger
 */
public abstract class YggdrasilOutputStream2 extends OutputStream {
	
	private final Yggdrasil y;
	
	protected YggdrasilOutputStream2(final Yggdrasil y) {
		this.y = y;
	}
	
	@Override
	public void write(final byte[] b) throws IOException {
		write(b, 0, b.length);
	}
	
	private void writeByte_(final byte b) throws IOException {
		write(T_BYTE);
		write(b & 0xFF);
	}
	
	public void writeByte(final byte b) throws IOException {
		write(b & 0xFF);
	}
	
	public void writeByte(final int b) throws IOException {
		write(b);
	}
	
	private void writeShort_(final short s) throws IOException {
		write(T_SHORT);
		writeShort(s);
	}
	
	public void writeShort(final short s) throws IOException {
		write((s >>> 8) & 0xFF);
		write(s & 0xFF);
	}
	
	public void writeShort(final int s) throws IOException {
		write((s >>> 8) & 0xFF);
		write(s & 0xFF);
	}
	
	private void writeInt_(final int i) throws IOException {
		write(T_INT);
		writeInt(i);
	}
	
	public void writeInt(final int i) throws IOException {
		write((i >>> 24) & 0xFF);
		write((i >>> 16) & 0xFF);
		write((i >>> 8) & 0xFF);
		write(i & 0xFF);
	}
	
	private void writeLong_(final long l) throws IOException {
		write(T_LONG);
		writeLong(l);
	}
	
	public void writeLong(final long l) throws IOException {
		write((int) ((l >>> 56) & 0xFF));
		write((int) ((l >>> 48) & 0xFF));
		write((int) ((l >>> 40) & 0xFF));
		write((int) ((l >>> 32) & 0xFF));
		write((int) ((l >>> 24) & 0xFF));
		write((int) ((l >>> 16) & 0xFF));
		write((int) ((l >>> 8) & 0xFF));
		write((int) (l & 0xFF));
	}
	
	private void writeFloat_(final float f) throws IOException {
		write(T_FLOAT);
		writeFloat(f);
	}
	
	public void writeFloat(final float f) throws IOException {
		writeInt(Float.floatToIntBits(f));
	}
	
	private void writeDouble_(final double d) throws IOException {
		write(T_DOUBLE);
		writeDouble(d);
	}
	
	public void writeDouble(final double d) throws IOException {
		writeLong(Double.doubleToLongBits(d));
	}
	
	private void writeChar_(final char c) throws IOException {
		write(T_CHAR);
		writeShort((short) c);
	}
	
	public void writeChar(final char c) throws IOException {
		writeShort((short) c);
	}
	
	public void writeChar(final int c) throws IOException {
		writeShort((short) c);
	}
	
	private void writeBoolean_(final boolean b) throws IOException {
		write(T_BOOLEAN);
		write(b ? 1 : 0);
	}
	
	public void writeBoolean(final boolean b) throws IOException {
		write(b ? 1 : 0);
	}
	
	private void writePrimitive_(final Object o) throws IOException {
		final Class<?> c = o.getClass();
		switch (getPrimitiveFromWrapper(c)) {
			case T_BYTE:
				writeByte_((Byte) o);
				break;
			case T_SHORT:
				writeShort_((Short) o);
				break;
			case T_INT:
				writeInt_((Integer) o);
				break;
			case T_LONG:
				writeLong_((Long) o);
				break;
			case T_FLOAT:
				writeFloat_((Float) o);
				break;
			case T_DOUBLE:
				writeDouble_((Double) o);
				break;
			case T_CHAR:
				writeChar_((Character) o);
				break;
			case T_BOOLEAN:
				writeBoolean_((Boolean) o);
				break;
			default:
				throw new YggdrasilException("Invalid call to writePrimitive with argument " + o);
		}
	}
	
	private void writePrimitive(final Object o) throws IOException {
		final Class<?> c = o.getClass();
		switch (getPrimitiveFromWrapper(c)) {
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
			default:
				throw new YggdrasilException("Invalid call to writePrimitive with argument " + o);
		}
	}
	
	private void writeString_(final String s) throws IOException {
		write(T_STRING);
		_writeString(s);
	}
	
	private void _writeString(final String s) throws IOException {
		final byte[] d = s.getBytes(utf8);
		writeInt(d.length);
		write(d);
	}
	
	private void writeArray_(final Object array) throws IOException {
		write(T_ARRAY);
		writeArray(array);
	}
	
	private void writeArray(final Object array) throws IOException {
		_writeClass(array.getClass().getComponentType());
		_writeArray(array);
	}
	
	private void _writeArray(final Object array) throws IOException {
		final Class<?> ct = array.getClass().getComponentType();
		final int length = Array.getLength(array);
		writeInt(length);
		if (ct.isPrimitive()) {
			for (int i = 0; i < length; i++)
				writePrimitive(Array.get(array, i)); // primitive arrays can only contain their primitive type
		} else {
			boolean sametype = true;
			for (final Object o : (Object[]) array) {
				if (o.getClass() != ct) {
					sametype = false;
					break;
				}
			}
			if (sametype) {
				write(F_SAME_TYPE);
				for (final Object o : (Object[]) array)
					_writeObject(o);
			} else {
				write(F_DIFF_TYPES);
				for (final Object o : (Object[]) array)
					writeObject(o);
			}
		}
	}
	
	private void writeClass_(final Class<?> c) throws IOException {
		write(T_CLASS);
		_writeClass(c);
	}
	
	private void _writeClass(final Class<?> c) throws IOException {
		if (c.isPrimitive()) {
			write(getType(c));
		} else if (c.isArray()) {
			write(T_ARRAY);
			_writeClass(c.getComponentType());
		} else {
			write(T_CLASS);
			writeShortString(y.getID(c));
		}
	}
	
	private void writeEnum_(final Enum<?> o) throws IOException {
		write(T_ENUM);
		writeEnum(o);
	}
	
	private void writeEnum(final Enum<?> o) throws IOException {
		writeShortString(y.getID(o.getDeclaringClass()));
		_writeEnum(o);
	}
	
	private void _writeEnum(final Enum<?> o) throws IOException {
		writeShortString(o.name());
	}
	
	private int nextObjectID = 0;
	private final IdentityHashMap<Object, Integer> writtenObjects = new IdentityHashMap<>();
	
	protected void writeObject(final Object o) throws IOException {
		if (o == null) {
			write(T_NULL);
			return;
		}
		if (writtenObjects.containsKey(o)) {
			write(T_REFERENCE);
			writeInt(writtenObjects.get(o));
			return;
		}
		writtenObjects.put(o, nextObjectID++);
		final int type = getType(o.getClass());
		switch (type) {
			case T_ARRAY:
				writeArray_(o);
				return;
			case T_STRING:
				writeString_((String) o);
				return;
			case T_ENUM:
				writeEnum_((Enum<?>) o);
				return;
			case T_CLASS:
				writeClass_((Class<?>) o);
				return;
			case T_OBJECT:
				writeGeneralObject_(o);
				return;
			default:
				assert false : type;
		}
	}
	
	/**
	 * Used for arrays
	 */
	private void _writeObject(final Object o) throws IOException {
		if (o == null) {
			write(T_NULL);
			return;
		}
		if (writtenObjects.containsKey(o)) {
			write(T_REFERENCE);
			writeInt(writtenObjects.get(o));
			return;
		}
		writtenObjects.put(o, nextObjectID++);
		final int type = getType(o.getClass());
		write(type);
		switch (type) {
			case T_ARRAY:
				_writeArray(o);
				return;
			case T_STRING:
				_writeString((String) o);
				return;
			case T_ENUM:
				_writeEnum((Enum<?>) o);
				return;
			case T_CLASS:
				_writeClass((Class<?>) o);
				return;
			case T_OBJECT:
				_writeGeneralObject(o);
				return;
			default:
				assert false : type;
		}
	}
	
	private final static HashMap<String, Integer> writtenShortStrings = new HashMap<>();
	int nextShortStringID = 0;
	
	/**
	 * Writes a class ID or Field name
	 */
	private void writeShortString(final String s) throws IOException {
		if (writtenShortStrings.containsKey(s)) {
			writeByte(T_REFERENCE);
			writeInt(writtenShortStrings.get(s));
		} else {
			final byte[] d = s.getBytes(utf8);
			if (d.length >= T_REFERENCE)
				throw new YggdrasilException("Field name or Class ID too long: " + s);
			writeByte(d.length);
			write(d);
			writtenShortStrings.put(s, nextShortStringID++);
		}
	}
	
	private final static Stack<Object> currentObjects = new Stack<>();
	
	private void writeGeneralObject_(final Object o) throws IOException {
		write(T_OBJECT);
		writeShortString(y.getID(o.getClass()));
		_writeGeneralObject(o);
	}
	
	private void _writeGeneralObject(final Object o) throws IOException {
		if (!Yggdrasil.isSerializable(o.getClass()))
			throw new YggdrasilException("Class " + o.getClass().getName() + " is not serialisable");
		currentObjects.push(o);
		try {
			final Method writeObject = o.getClass().getDeclaredMethod("writeObject", ObjectOutputStream.class);
			writeObject.setAccessible(true);
			writeObject.invoke(o, this);
		} catch (final NoSuchMethodException e) {
			defaultWriteObject();
		} catch (final SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new YggdrasilException(e);
		} finally {
			currentObjects.pop();
		}
	}
	
	@Override
	public void defaultWriteObject() throws IOException {
		if (currentObjects.isEmpty())
			throw new NotActiveException("not in call to writeObject");
		final Object o = currentObjects.peek();
		final Class<?> c = o.getClass();
		for (Class<?> sc = c; sc != null; sc = sc.getSuperclass()) {
			final Field[] fields = sc.getDeclaredFields();
			for (final Field f : fields) {
				if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()))
					continue;
				f.setAccessible(true);
				write(F_FIELD);
				writeShortString(f.getName());
				try {
					if (f.getType().isPrimitive())
						writePrimitive_(f.get(o));
					else
						writeObject(f.get(o));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new YggdrasilException(e);
				}
			}
		}
		write(F_ENDFIELDS);
	}
	
}
