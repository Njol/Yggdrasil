package ch.njol.yggdrasil;

import static ch.njol.yggdrasil.Tag.*;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public abstract class YggdrasilInputStream extends ObjectInputStream {
	
	protected final Yggdrasil y;
	
	protected YggdrasilInputStream(final Yggdrasil y) throws IOException {
		this.y = y;
	}
	
	// handled by subclass in constructor
	@Override
	protected final void readStreamHeader() throws IOException, StreamCorruptedException {
		assert false;
	}
	
	@Override
	public final int read() throws IOException {
		final byte[] b = new byte[1];
		readData(b);
		return b[0];
	}
	
	@Override
	public final int read(final byte[] b) throws IOException {
		readData(b);
		return b.length;
	}
	
	@Override
	public final int read(final byte[] buf, final int off, final int len) throws IOException {
		readData(buf, off, len);
		return len;
	}
	
	@Override
	public final void readFully(final byte[] buf) throws IOException {
		readData(buf);
	}
	
	@Override
	public final void readFully(final byte[] buf, final int off, final int len) throws IOException {
		readData(buf, off, len);
	}
	
	@Override
	public final long skip(final long n) throws IOException {
		readData(new byte[(int) n]);
		return n;
	}
	
	@Override
	public final int skipBytes(final int len) throws IOException {
		readData(new byte[len]);
		return len;
	}
	
	protected final void readData(final byte[] buf) {
		readData(buf, 0, buf.length);
	}
	
	protected abstract void readData(byte[] buf, int off, int len);
	
	protected abstract Tag readTag() throws IOException;
	
	protected final void readTag(final Tag expected) throws IOException {
		final Tag t = readTag();
		if (t != expected)
			throw new StreamCorruptedException("Unexpected tag " + t + ", expected tag " + expected);
	}
	
	protected abstract byte readByte_() throws IOException;
	
	protected abstract short readShort_() throws IOException;
	
	protected abstract int readInt_() throws IOException;
	
	protected abstract long readLong_() throws IOException;
	
	protected abstract float readFloat_() throws IOException;
	
	protected abstract double readDouble_() throws IOException;
	
	protected abstract char readChar_() throws IOException;
	
	protected abstract boolean readBoolean_() throws IOException;
	
	@Override
	public byte readByte() throws IOException {
		readTag(T_BYTE);
		return readByte_();
	}
	
	@Override
	public short readShort() throws IOException {
		readTag(T_SHORT);
		return readShort_();
	}
	
	@Override
	public int readInt() throws IOException {
		readTag(T_INT);
		return readInt_();
	}
	
	@Override
	public long readLong() throws IOException {
		readTag(T_LONG);
		return readLong_();
	}
	
	@Override
	public float readFloat() throws IOException {
		readTag(T_FLOAT);
		return readFloat_();
	}
	
	@Override
	public double readDouble() throws IOException {
		readTag(T_DOUBLE);
		return readDouble_();
	}
	
	@Override
	public char readChar() throws IOException {
		readTag(T_CHAR);
		return readChar_();
	}
	
	@Override
	public boolean readBoolean() throws IOException {
		readTag(T_BOOLEAN);
		return readBoolean_();
	}
	
	@Override
	public final int readUnsignedByte() throws IOException {
		return readByte() & 0xFF;
	}
	
	@Override
	public final int readUnsignedShort() throws IOException {
		return readShort() & 0xFFFF;
	}
	
	public Object readPrimitive() throws IOException {
		final Tag t = readTag();
		return readPrimitive_(t);
	}
	
	protected Object readPrimitive_(final Tag t) throws IOException {
		switch (t) {
			case T_BYTE:
				return readByte_();
			case T_SHORT:
				return readShort_();
			case T_INT:
				return readInt_();
			case T_LONG:
				return readLong_();
			case T_FLOAT:
				return readFloat_();
			case T_DOUBLE:
				return readDouble_();
			case T_CHAR:
				return readChar_();
			case T_BOOLEAN:
				return readBoolean_();
				//$CASES-OMITTED$
			default:
				throw new StreamCorruptedException("Unexpected tag " + t + ", expected a primitive tag");
		}
	}
	
	@Override
	public final String readUTF() throws IOException {
		return readString();
	}
	
	public final String readString() throws IOException {
		readTag(T_STRING);
		return readString_();
	}
	
	protected abstract String readString_() throws IOException;
	
	/**
	 * Reads the content type & length of an array and returns a newly created array.
	 * 
	 * @return A new array with the read length & content type
	 */
	protected abstract Object readArrayStart_() throws IOException;
	
	private final void readArrayContents(final Object array) throws IOException, ClassNotFoundException {
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
	
	protected abstract Enum<?> readEnum_() throws IOException;
	
	protected abstract Class<?> readClass_() throws IOException;
	
	protected abstract int readReference_() throws IOException;
	
	protected abstract String readObjectType_() throws IOException;
	
	protected abstract short readNumFields_() throws IOException;
	
	protected abstract String readFieldName_() throws IOException;
	
	private final Deque<YggdrasilInputContext> currentContext = new ArrayDeque<>();
	
	private final class YggdrasilInputContext extends GetField {
		
		final Object o;
		
		private final YggdrasilContext context;
		
		YggdrasilInputContext(final Object o) {
			this.o = o;
			context = new YggdrasilContext(o.getClass());
		}
		
		@Override
		public ObjectStreamClass getObjectStreamClass() {
			return ObjectStreamClass.lookupAny(o.getClass());
		}
		
		@Override
		public boolean defaulted(final String name) throws IOException {
			return context.contains(name);
		}
		
		@Override
		public boolean get(final String name, final boolean val) throws IOException {
			if (defaulted(name))
				return val;
			final Object v = context.getPrimitive(name);
			if (!(v instanceof Boolean))
				throw new IllegalArgumentException();
			return (Boolean) v;
		}
		
		@Override
		public byte get(final String name, final byte val) throws IOException {
			if (defaulted(name))
				return val;
			final Object v = context.getPrimitive(name);
			if (!(v instanceof Byte))
				throw new IllegalArgumentException();
			return (Byte) v;
		}
		
		@Override
		public char get(final String name, final char val) throws IOException {
			if (defaulted(name))
				return val;
			final Object v = context.getPrimitive(name);
			if (!(v instanceof Character))
				throw new IllegalArgumentException();
			return (Character) v;
		}
		
		@Override
		public short get(final String name, final short val) throws IOException {
			if (defaulted(name))
				return val;
			final Object v = context.getPrimitive(name);
			if (!(v instanceof Short))
				throw new IllegalArgumentException();
			return (Short) v;
		}
		
		@Override
		public int get(final String name, final int val) throws IOException {
			if (defaulted(name))
				return val;
			final Object v = context.getPrimitive(name);
			if (!(v instanceof Integer))
				throw new IllegalArgumentException();
			return (Integer) v;
		}
		
		@Override
		public long get(final String name, final long val) throws IOException {
			if (defaulted(name))
				return val;
			final Object v = context.getPrimitive(name);
			if (!(v instanceof Long))
				throw new IllegalArgumentException();
			return (Long) v;
		}
		
		@Override
		public float get(final String name, final float val) throws IOException {
			if (defaulted(name))
				return val;
			final Object v = context.getPrimitive(name);
			if (!(v instanceof Float))
				throw new IllegalArgumentException();
			return (Float) v;
		}
		
		@Override
		public double get(final String name, final double val) throws IOException {
			if (defaulted(name))
				return val;
			final Object v = context.getPrimitive(name);
			if (!(v instanceof Double))
				throw new IllegalArgumentException();
			return (Double) v;
		}
		
		@Override
		public Object get(final String name, final Object val) throws IOException {
			if (defaulted(name))
				return val;
			return context.getObject(name);
		}
		
		void setFields() {
			context.setFields(o);
		}
		
		public void read() throws IOException, ClassNotFoundException {
			final short numFields = readNumFields_();
			for (int i = 0; i < numFields; i++) {
				final String name = readFieldName_();
				final Tag t = readTag();
				if (t.isPrimitive())
					context.putPrimitive(name, readPrimitive_(t));
				else
					context.putObject(name, readObject_(t, true));
			}
		}
		
	}
	
	private final void readObject_(final Object o) throws IOException, ClassNotFoundException {
		final YggdrasilInputContext context = new YggdrasilInputContext(o);
		currentContext.add(context);
		try {
			if (o instanceof Externalizable) {
				((Externalizable) o).readExternal(this);
			} else {
				try {
					final Method readObject = o.getClass().getDeclaredMethod("readObject", ObjectInputStream.class);
					readObject.setAccessible(true);
					readObject.invoke(o, this);
				} catch (final NoSuchMethodException e) {
					defaultReadObject();
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new YggdrasilException(e);
				}
			}
		} finally {
			assert currentContext.peek() == context;
			currentContext.pop();
		}
	}
	
	@Override
	public final GetField readFields() throws IOException, ClassNotFoundException {
		if (currentContext.isEmpty())
			throw new NotActiveException("not in call to readObject");
		final YggdrasilInputContext c = currentContext.peek();
		c.read();
		return c;
	}
	
	@Override
	public final void defaultReadObject() throws IOException, ClassNotFoundException {
		if (currentContext.isEmpty())
			throw new NotActiveException("not in call to readObject");
		((YggdrasilInputContext) readFields()).setFields();
	}
	
	@Override
	public final Object readUnshared() throws IOException, ClassNotFoundException {
		return readObject(false);
	}
	
	@Override
	protected final Object readObjectOverride() throws IOException, ClassNotFoundException {
		return readObject(true);
	}
	
	private int depth = 0;
	
	private final List<Object> readObjects = new ArrayList<>();
	
	private final Object readObject(final boolean shared) throws IOException, ClassNotFoundException {
		final Tag t = readTag();
		return readObject_(t, shared);
	}
	
	private final Object readObject_(final Tag t, final boolean shared) throws IOException, ClassNotFoundException {
		depth++;
		try {
			Object o;
			switch (t) {
				case T_NULL:
					return null;
				case T_ARRAY: {
					o = readArrayStart_();
					readObjects.add(shared ? o : null);
					readArrayContents(o);
					return o;
				}
				case T_CLASS:
					o = readClass_();
					break;
				case T_ENUM:
					o = readEnum_();
					break;
				case T_STRING:
					o = readString_();
					break;
				case T_OBJECT: {
					final String id = readObjectType_();
					Class<?> c = y.getClass(id);
					o = newInstance(c);
					readObjects.add(shared ? o : null);
					readObject_(o);
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
					o = readPrimitive_(t.getPrimitive());
					break;
				case T_REFERENCE:
					if (!shared)
						throw new StreamCorruptedException("Reference read as unshared");
					final int ref = readReference_();
					if (ref < 0 || ref >= readObjects.size())
						throw new StreamCorruptedException("Invalid reference " + ref);
					o = readObjects.get(ref);
					if (o == null)
						throw new StreamCorruptedException("Read reference to unshared object");
					return o;
					//$CASES-OMITTED$
				default:
					assert t.isPrimitive();
					throw new StreamCorruptedException("Unexpected tag " + t + ", expected an object tag");
			}
			readObjects.add(shared ? o : null);
			return o;
		} finally {
			depth--;
			if (depth == 0)
				validate();
		}
	}
	
	private static Method getSerializableConstructor;
	static {
		try {
			getSerializableConstructor = ObjectStreamClass.class.getDeclaredMethod("getSerializableConstructor", Class.class);
			getSerializableConstructor.setAccessible(true);
		} catch (NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
	}
	
	private final static Object newInstance(final Class<?> c) {
		final ObjectStreamClass osc = ObjectStreamClass.lookupAny(c);
		try {
			final Constructor<?> constr = (Constructor<?>) getSerializableConstructor.invoke(osc, c);
			return constr.newInstance();
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
			throw new YggdrasilException("Cannot create an instance of class " + c.getCanonicalName(), e);
		}
	}
	
	private final static class Validation implements Comparable<Validation> {
		private final ObjectInputValidation v;
		private final int prio;
		
		public Validation(final ObjectInputValidation v, final int prio) {
			this.v = v;
			this.prio = prio;
		}
		
		private void validate() throws InvalidObjectException {
			v.validateObject();
		}
		
		@Override
		public int compareTo(final Validation o) {
			return o.prio - prio;
		}
	}
	
	private final SortedSet<Validation> validations = new TreeSet<>();
	
	@Override
	public final void registerValidation(final ObjectInputValidation v, final int prio) throws NotActiveException, InvalidObjectException {
		if (depth == 0)
			throw new NotActiveException("stream inactive");
		if (v == null)
			throw new InvalidObjectException("null callback");
		validations.add(new Validation(v, prio));
	}
	
	private final void validate() throws InvalidObjectException {
		for (final Validation v : validations)
			v.validate();
		validations.clear(); // if multiple objects are written to the stream this method will be called multiple times
	}
	
	@Override
	public abstract void close() throws IOException;
	
	// ===== UNUSED METHODS =====
	
	@Override
	public final boolean markSupported() {
		return false;
	}
	
	@Override
	public final synchronized void mark(final int readlimit) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public final synchronized void reset() throws IOException {
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected final ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
		assert false;
		return null;
	}
	
	@Override
	protected final Class<?> resolveClass(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
		assert false;
		return null;
	}
	
	@Override
	protected boolean enableResolveObject(final boolean enable) throws SecurityException {
		assert false;
		return false;
	}
	
	@Override
	protected final Object resolveObject(final Object obj) throws IOException {
		assert false;
		return null;
	}
	
	@Override
	protected final Class<?> resolveProxyClass(final String[] interfaces) throws IOException, ClassNotFoundException {
		assert false;
		return null;
	}
	
	@Override
	public final String readLine() throws IOException {
		throw new UnsupportedOperationException();
	}
	
}
