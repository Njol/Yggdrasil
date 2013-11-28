package ch.njol.yggdrasil;

import static ch.njol.yggdrasil.Tag.*;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotActiveException;
import java.io.NotSerializableException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;

import ch.njol.yggdrasil.YggdrasilConstants.SerializationType;
import ch.njol.yggdrasil.YggdrasilContext.FieldContext;

public abstract class YggdrasilOutputStream extends ObjectOutputStream {
	
	protected final Yggdrasil y;
	
	protected short version = YggdrasilConstants.VERSION;
	
	protected YggdrasilOutputStream(final Yggdrasil y) throws IOException {
		this.y = y;
	}
	
	// handled by subclass in constructor
	@Override
	protected final void writeStreamHeader() throws IOException {
		assert false;
	}
	
	@Override
	public final void useProtocolVersion(final int version) throws IOException {
		if (version <= 0 || version > YggdrasilConstants.VERSION)
			throw new IllegalArgumentException();
		this.version = (short) version;
	}
	
	// FIXME merge subsequent calls into one
	@Override
	public final void write(final int val) throws IOException {
		write(new byte[(byte) val]);
	}
	
	@Override
	public final void write(final byte[] buf) throws IOException {
		writeData(buf);
	}
	
	@Override
	public final void write(final byte[] buf, final int off, final int len) throws IOException {
		writeData(buf, off, len);
	}
	
	@Override
	public final void writeBytes(final String s) throws IOException {
		final byte[] data = new byte[s.length()];
		for (int i = 0; i < data.length; i++)
			data[i] = (byte) s.charAt(i);
		writeData(data);
	}
	
	@Override
	public final void writeChars(final String s) throws IOException {
		final byte[] data = new byte[2 * s.length()];
		for (int i = 0; i < data.length; i++) {
			final char c = s.charAt(i);
			data[2 * i] = (byte) ((c >>> 8) & 0xFF);
			data[2 * i + 1] = (byte) (c & 0xFF);
		}
		writeData(data);
	}
	
	protected final void writeData(final byte[] data) throws IOException {
		writeData(data, 0, data.length);
	}
	
	protected abstract void writeData(byte[] data, int off, int len) throws IOException;
	
	protected abstract void writeNull() throws IOException;
	
	protected void writePrimitive(final Object o) throws IOException {
		final Tag t = Tag.getType(o.getClass());
		switch (t) {
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
				throw new YggdrasilException("invalid call to writePrimitive with argument " + o);
		}
	}
	
	protected abstract void writePrimitive_(Object o) throws IOException;
	
	protected abstract void writeWrappedPrimitive(Object o) throws IOException;
	
	protected abstract void writeString(String s) throws IOException;
	
	@Override
	public final void writeUTF(final String s) throws IOException {
		writeString(s);
	}
	
	protected abstract void writeArrayStart(Class<?> componentType, int length) throws IOException;
	
	protected abstract void writeArrayEnd() throws IOException;
	
	private final void writeArray(final Object array) throws IOException {
		final int length = Array.getLength(array);
		final Class<?> ct = array.getClass().getComponentType();
		writeArrayStart(ct, length);
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
	
	protected abstract void writeEnum(Enum<?> o) throws IOException;
	
	protected abstract void writeClass(Class<?> c) throws IOException;
	
	protected abstract void writeReference(int ref) throws IOException;
	
	protected abstract void writeObjectStart(String type, SerializationType serialType) throws IOException;
	
	protected abstract void writeFieldsStart(short numFields) throws IOException;
	
	protected abstract void writeFieldsEnd() throws IOException;
	
	protected abstract void writeFieldName(String name) throws IOException;
	
	protected abstract void writeObjectEnd() throws IOException;
	
	private final Deque<YggdrasilOutputContext> currentContext = new ArrayDeque<>();
	
	private final class YggdrasilOutputContext extends PutField {
		
		final Object o;
		
		private final YggdrasilContext context;
		
		YggdrasilOutputContext(final Object o) {
			this.o = o;
			context = new YggdrasilContext(o.getClass());
		}
		
		@Override
		public void put(final String name, final boolean val) {
			context.putPrimitive(name, val);
		}
		
		@Override
		public void put(final String name, final byte val) {
			context.putPrimitive(name, val);
		}
		
		@Override
		public void put(final String name, final char val) {
			context.putPrimitive(name, val);
		}
		
		@Override
		public void put(final String name, final short val) {
			context.putPrimitive(name, val);
		}
		
		@Override
		public void put(final String name, final int val) {
			context.putPrimitive(name, val);
		}
		
		@Override
		public void put(final String name, final long val) {
			context.putPrimitive(name, val);
		}
		
		@Override
		public void put(final String name, final float val) {
			context.putPrimitive(name, val);
		}
		
		@Override
		public void put(final String name, final double val) {
			context.putPrimitive(name, val);
		}
		
		@Override
		public void put(final String name, final Object val) {
			context.putObject(name, val);
		}
		
		void setValues() {
			context.setValues(o);
		}
		
		@Override
		public void write(final ObjectOutput out) throws IOException {
			if (out != YggdrasilOutputStream.this)
				throw new IllegalArgumentException();
			writeFieldsStart((short) context.numFields());
			for (final FieldContext c : context) {
				writeFieldName(c.field.getName());
				if (c.isPrimitive())
					writePrimitive(c.getPrimitive());
				else
					writeObject(c.getObject());
			}
			writeFieldsEnd();
		}
		
	}
	
	private final void writeGenericObject(final Object o) throws IOException {
		if (!Yggdrasil.isSerializable(o.getClass()))
			throw new NotSerializableException("Class " + o.getClass().getName() + " is not serialisable");
		final YggdrasilOutputContext context = new YggdrasilOutputContext(o);
		currentContext.push(context);
		final String id = y.getID(o.getClass());
		try {
			if (o instanceof Externalizable) {
				writeObjectStart(id, SerializationType.EXTERNALIZED);
				((Externalizable) o).writeExternal(this);
			} else {
				context.setValues();
				try {
					final Method writeObject = o.getClass().getDeclaredMethod("writeObject", ObjectOutputStream.class);
					writeObject.setAccessible(true);
					writeObjectStart(id, SerializationType.CUSTOM);
					writeObject.invoke(o, this);
				} catch (final NoSuchMethodException e) {
					writeObjectStart(id, SerializationType.DEFAULT);
					defaultWriteObject();
				} catch (final SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new YggdrasilException(e);
				}
			}
		} finally {
			writeObjectEnd();
			assert currentContext.peek() == context;
			currentContext.pop();
		}
	}
	
	@Override
	public PutField putFields() throws IOException {
		if (currentContext.isEmpty())
			throw new NotActiveException("not in call to writeObject");
		return currentContext.peek();
	}
	
	@Override
	public final void writeFields() throws IOException {
		if (currentContext.isEmpty())
			throw new NotActiveException("not in call to writeObject");
		final YggdrasilOutputContext context = currentContext.peek();
		context.write(this);
	}
	
	@Override
	public final void defaultWriteObject() throws IOException {
		if (currentContext.isEmpty())
			throw new NotActiveException("not in call to writeObject");
		writeFields();
	}
	
	private int nextObjectID = 0;
	private final IdentityHashMap<Object, Integer> writtenObjects = new IdentityHashMap<>();
	
	@Override
	public final void writeObjectOverride(final Object o) throws IOException {
		writeObject(o, true);
	}
	
	@Override
	public final void writeUnshared(final Object o) throws IOException {
		writeObject(o, false);
	}
	
	private final void writeObject(Object o, final boolean shared) throws IOException {
		if (o == null) {
			writeNull();
			return;
		}
		try {
			final Method writeReplace = o.getClass().getDeclaredMethod("writeReplace");
			writeReplace.setAccessible(true);
			final Object o2 = writeReplace.invoke(o);
			if (o2 != null)
				o = o2;
		} catch (final NoSuchMethodException e) {} catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
			throw new AssertionError();
		} catch (final InvocationTargetException e) {
			throw new IOException(e);
		}
		if (shared) {
			if (writtenObjects.containsKey(o)) {
				writeReference(writtenObjects.get(o));
				return;
			}
			writtenObjects.put(o, nextObjectID);
		}
		nextObjectID++; // increment even if unshared
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
				writeGenericObject(o);
				return;
				//$CASES-OMITTED$
			default:
				throw new YggdrasilException("unhandled type " + type);
		}
	}
	
	@Override
	protected abstract void drain() throws IOException;
	
	@Override
	public abstract void flush() throws IOException;
	
	@Override
	public abstract void close() throws IOException;
	
	// ===== UNUSED METHODS =====
	
	@Override
	protected final void writeClassDescriptor(final ObjectStreamClass desc) throws IOException {
		assert false; // only used by the default OOS
	}
	
	@Override
	protected final void annotateClass(final Class<?> cl) throws IOException {
		assert false; // unused
	}
	
	@Override
	protected final void annotateProxyClass(final Class<?> cl) throws IOException {
		assert false; // unused
	}
	
	@Override
	protected final boolean enableReplaceObject(final boolean enable) throws SecurityException {
		assert false; // unused
		return false;
	}
	
	@Override
	protected final Object replaceObject(final Object obj) throws IOException {
		assert false; // unused
		return null;
	}
	
}
