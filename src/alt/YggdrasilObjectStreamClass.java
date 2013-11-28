package ch.njol.yggdrasil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class YggdrasilObjectStreamClass {
	
	/** serialPersistentFields value indicating no serializable fields */
	public static final ObjectStreamField[] NO_FIELDS =
			new ObjectStreamField[0];
	
	private static final long serialVersionUID = -6120832682080437368L;
	private static final ObjectStreamField[] serialPersistentFields =
			NO_FIELDS;
	
	private static class Caches {
		/** cache mapping local classes -> descriptors */
		static final ConcurrentMap<WeakClassKey, Reference<?>> localDescs =
				new ConcurrentHashMap<>();
		
		/** queue for WeakReferences to local classes */
		private static final ReferenceQueue<Class<?>> localDescsQueue =
				new ReferenceQueue<>();
	}
	
	/** class associated with this descriptor (if any) */
	private Class<?> cl;
	/** name of class represented by this descriptor */
	private String name;
	/** serialVersionUID of represented class (null if not computed yet) */
	private volatile Long suid;
	
	/** true if represents dynamic proxy class */
	private boolean isProxy;
	/** true if represents enum type */
	private boolean isEnum;
	/** true if represented class implements Serializable */
	private boolean serializable;
	/** true if represented class implements Externalizable */
	private boolean externalizable;
	/** true if desc has data written by class-defined writeObject method */
	private boolean hasWriteObjectData;
	/**
	 * true if desc has externalizable data written in block data format; this
	 * must be true by default to accommodate ObjectInputStream subclasses which
	 * override readClassDescriptor() to return class descriptors obtained from
	 * YggdrasilObjectStreamClass.lookup() (see 4461737)
	 */
	private boolean hasBlockExternalData = true;
	
	/**
	 * Contains information about InvalidClassException instances to be thrown
	 * when attempting operations on an invalid class. Note that instances of
	 * this class are immutable and are potentially shared among
	 * YggdrasilObjectStreamClass instances.
	 */
	private static class ExceptionInfo {
		private final String className;
		private final String message;
		
		ExceptionInfo(final String cn, final String msg) {
			className = cn;
			message = msg;
		}
		
		/**
		 * Returns (does not throw) an InvalidClassException instance created
		 * from the information in this object, suitable for being thrown by
		 * the caller.
		 */
		InvalidClassException newInvalidClassException() {
			return new InvalidClassException(className, message);
		}
	}
	
	/** exception (if any) thrown while attempting to resolve class */
	private ClassNotFoundException resolveEx;
	/** exception (if any) to throw if non-enum deserialization attempted */
	private ExceptionInfo deserializeEx;
	/** exception (if any) to throw if non-enum serialization attempted */
	private ExceptionInfo serializeEx;
	/** exception (if any) to throw if default serialization attempted */
	private ExceptionInfo defaultSerializeEx;
	
	/** serializable fields */
	private ObjectStreamField[] fields;
	/** aggregate marshalled size of primitive fields */
	private int primDataSize;
	/** number of non-primitive fields */
	private int numObjFields;
	/** data layout of serialized objects described by this class desc */
	private volatile ClassDataSlot[] dataLayout;
	
	/** serialization-appropriate constructor, or null if none */
	private Constructor<?> cons;
	/** class-defined writeObject method, or null if none */
	private Method writeObjectMethod;
	/** class-defined readObject method, or null if none */
	private Method readObjectMethod;
	/** class-defined readObjectNoData method, or null if none */
	private Method readObjectNoDataMethod;
	/** class-defined writeReplace method, or null if none */
	private Method writeReplaceMethod;
	/** class-defined readResolve method, or null if none */
	private Method readResolveMethod;
	
	/** local class descriptor for represented class (may point to self) */
	private YggdrasilObjectStreamClass localDesc;
	/** superclass descriptor appearing in stream */
	private YggdrasilObjectStreamClass superDesc;
	
	/**
	 * Initializes native code.
	 */
	private static native void initNative();
	
	static {
		initNative();
	}
	
	/**
	 * Find the descriptor for a class that can be serialized. Creates an
	 * YggdrasilObjectStreamClass instance if one does not exist yet for class. Null is
	 * returned if the specified class does not implement java.io.Serializable
	 * or java.io.Externalizable.
	 * 
	 * @param cl class for which to get the descriptor
	 * @return the class descriptor for the specified class
	 */
	public static YggdrasilObjectStreamClass lookup(final Class<?> cl) {
		return lookup(cl, false);
	}
	
	/**
	 * Returns the descriptor for any class, regardless of whether it
	 * implements {@link Serializable}.
	 * 
	 * @param cl class for which to get the descriptor
	 * @return the class descriptor for the specified class
	 * @since 1.6
	 */
	public static YggdrasilObjectStreamClass lookupAny(final Class<?> cl) {
		return lookup(cl, true);
	}
	
	/**
	 * Returns the name of the class described by this descriptor.
	 * This method returns the name of the class in the format that
	 * is used by the {@link Class#getName} method.
	 * 
	 * @return a string representing the name of the class
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Return the serialVersionUID for this class. The serialVersionUID
	 * defines a set of classes all with the same name that have evolved from a
	 * common root class and agree to be serialized and deserialized using a
	 * common format. NonSerializable classes have a serialVersionUID of 0L.
	 * 
	 * @return the SUID of the class described by this descriptor
	 */
	public long getSerialVersionUID() {
		// REMIND: synchronize instead of relying on volatile?
		if (suid == null) {
			suid = AccessController.doPrivileged(
					new PrivilegedAction<Long>() {
						@Override
						public Long run() {
							return computeDefaultSUID(cl);
						}
					}
					);
		}
		return suid.longValue();
	}
	
	/**
	 * Return the class in the local VM that this version is mapped to. Null
	 * is returned if there is no corresponding local class.
	 * 
	 * @return the <code>Class</code> instance that this descriptor represents
	 */
	public Class<?> forClass() {
		return cl;
	}
	
	/**
	 * Return an array of the fields of this serializable class.
	 * 
	 * @return an array containing an element for each persistent field of
	 *         this class. Returns an array of length zero if there are no
	 *         fields.
	 * @since 1.2
	 */
	public ObjectStreamField[] getFields() {
		return getFields(true);
	}
	
	/**
	 * Get the field of this class by name.
	 * 
	 * @param name the name of the data field to look for
	 * @return The ObjectStreamField object of the named field or null if
	 *         there is no such named field.
	 */
	public ObjectStreamField getField(final String name) {
		return getField(name, null);
	}
	
	/**
	 * Return a string describing this YggdrasilObjectStreamClass.
	 */
	@Override
	public String toString() {
		return name + ": static final long serialVersionUID = " +
				getSerialVersionUID() + "L;";
	}
	
	/**
	 * Looks up and returns class descriptor for given class, or null if class
	 * is non-serializable and "all" is set to false.
	 * 
	 * @param cl class to look up
	 * @param all if true, return descriptors for all classes; if false, only
	 *            return descriptors for serializable classes
	 */
	static YggdrasilObjectStreamClass lookup(final Class<?> cl, final boolean all) {
		if (!(all || Serializable.class.isAssignableFrom(cl))) {
			return null;
		}
		processQueue(Caches.localDescsQueue, Caches.localDescs);
		final WeakClassKey key = new WeakClassKey(cl, Caches.localDescsQueue);
		Reference<?> ref = Caches.localDescs.get(key);
		Object entry = null;
		if (ref != null) {
			entry = ref.get();
		}
		EntryFuture future = null;
		if (entry == null) {
			final EntryFuture newEntry = new EntryFuture();
			final Reference<?> newRef = new SoftReference<>(newEntry);
			do {
				if (ref != null) {
					Caches.localDescs.remove(key, ref);
				}
				ref = Caches.localDescs.putIfAbsent(key, newRef);
				if (ref != null) {
					entry = ref.get();
				}
			} while (ref != null && entry == null);
			if (entry == null) {
				future = newEntry;
			}
		}
		
		if (entry instanceof YggdrasilObjectStreamClass) {  // check common case first
			return (YggdrasilObjectStreamClass) entry;
		}
		if (entry instanceof EntryFuture) {
			future = (EntryFuture) entry;
			if (future.getOwner() == Thread.currentThread()) {
				/*
				 * Handle nested call situation described by 4803747: waiting
				 * for future value to be set by a lookup() call further up the
				 * stack will result in deadlock, so calculate and set the
				 * future value here instead.
				 */
				entry = null;
			} else {
				entry = future.get();
			}
		}
		if (entry == null) {
			try {
				entry = new YggdrasilObjectStreamClass(cl);
			} catch (final Throwable th) {
				entry = th;
			}
			if (future.set(entry)) {
				Caches.localDescs.put(key, new SoftReference<>(entry));
			} else {
				// nested lookup call already set future
				entry = future.get();
			}
		}
		
		if (entry instanceof YggdrasilObjectStreamClass) {
			return (YggdrasilObjectStreamClass) entry;
		} else if (entry instanceof RuntimeException) {
			throw (RuntimeException) entry;
		} else if (entry instanceof Error) {
			throw (Error) entry;
		} else {
			throw new InternalError("unexpected entry: " + entry);
		}
	}
	
	/**
	 * Placeholder used in class descriptor and field reflector lookup tables
	 * for an entry in the process of being initialized. (Internal) callers
	 * which receive an EntryFuture belonging to another thread as the result
	 * of a lookup should call the get() method of the EntryFuture; this will
	 * return the actual entry once it is ready for use and has been set(). To
	 * conserve objects, EntryFutures synchronize on themselves.
	 */
	private static class EntryFuture {
		
		private static final Object unset = new Object();
		private final Thread owner = Thread.currentThread();
		private Object entry = unset;
		
		/**
		 * Attempts to set the value contained by this EntryFuture. If the
		 * EntryFuture's value has not been set already, then the value is
		 * saved, any callers blocked in the get() method are notified, and
		 * true is returned. If the value has already been set, then no saving
		 * or notification occurs, and false is returned.
		 */
		synchronized boolean set(final Object entry) {
			if (this.entry != unset) {
				return false;
			}
			this.entry = entry;
			notifyAll();
			return true;
		}
		
		/**
		 * Returns the value contained by this EntryFuture, blocking if
		 * necessary until a value is set.
		 */
		synchronized Object get() {
			boolean interrupted = false;
			while (entry == unset) {
				try {
					wait();
				} catch (final InterruptedException ex) {
					interrupted = true;
				}
			}
			if (interrupted) {
				AccessController.doPrivileged(
						new PrivilegedAction<Void>() {
							@Override
							public Void run() {
								Thread.currentThread().interrupt();
								return null;
							}
						}
						);
			}
			return entry;
		}
		
		/**
		 * Returns the thread that created this EntryFuture.
		 */
		Thread getOwner() {
			return owner;
		}
	}
	
	/**
	 * Creates local class descriptor representing given class.
	 */
	private YggdrasilObjectStreamClass(final Class<?> cl) {
		this.cl = cl;
		name = cl.getName();
		isProxy = Proxy.isProxyClass(cl);
		isEnum = Enum.class.isAssignableFrom(cl);
		serializable = Serializable.class.isAssignableFrom(cl);
		externalizable = Externalizable.class.isAssignableFrom(cl);
		
		final Class<?> superCl = cl.getSuperclass();
		superDesc = (superCl != null) ? lookup(superCl, false) : null;
		localDesc = this;
		
		if (serializable) {
			AccessController.doPrivileged(new PrivilegedAction<Void>() {
				@Override
				public Void run() {
					if (isEnum) {
						suid = Long.valueOf(0);
						fields = NO_FIELDS;
						return null;
					}
					if (cl.isArray()) {
						fields = NO_FIELDS;
						return null;
					}
					
					suid = getDeclaredSUID(cl);
					try {
						fields = getSerialFields(cl);
					} catch (final InvalidClassException e) {
						serializeEx = deserializeEx =
								new ExceptionInfo(e.classname, e.getMessage());
						fields = NO_FIELDS;
					}
					
					if (externalizable) {
						cons = getExternalizableConstructor(cl);
					} else {
						cons = getSerializableConstructor(cl);
						writeObjectMethod = getPrivateMethod(cl, "writeObject",
								new Class<?>[] {ObjectOutputStream.class},
								Void.TYPE);
						readObjectMethod = getPrivateMethod(cl, "readObject",
								new Class<?>[] {ObjectInputStream.class},
								Void.TYPE);
						readObjectNoDataMethod = getPrivateMethod(
								cl, "readObjectNoData", null, Void.TYPE);
						hasWriteObjectData = (writeObjectMethod != null);
					}
					writeReplaceMethod = getInheritableMethod(
							cl, "writeReplace", null, Object.class);
					readResolveMethod = getInheritableMethod(
							cl, "readResolve", null, Object.class);
					return null;
				}
			});
		} else {
			suid = Long.valueOf(0);
			fields = NO_FIELDS;
		}
		
		if (deserializeEx == null) {
			if (isEnum) {
				deserializeEx = new ExceptionInfo(name, "enum type");
			} else if (cons == null) {
				deserializeEx = new ExceptionInfo(name, "no valid constructor");
			}
		}
		for (int i = 0; i < fields.length; i++) {
			if (fields[i].getField() == null) {
				defaultSerializeEx = new ExceptionInfo(
						name, "unmatched serializable field(s) declared");
			}
		}
	}
	
	/**
	 * Creates blank class descriptor which should be initialized via a
	 * subsequent call to initProxy(), initNonProxy() or readNonProxy().
	 */
	YggdrasilObjectStreamClass() {}
	
	/**
	 * Initializes class descriptor representing a proxy class.
	 */
	void initProxy(final Class<?> cl,
			final ClassNotFoundException resolveEx,
			final YggdrasilObjectStreamClass superDesc)
			throws InvalidClassException
	{
		this.cl = cl;
		this.resolveEx = resolveEx;
		this.superDesc = superDesc;
		isProxy = true;
		serializable = true;
		suid = Long.valueOf(0);
		fields = NO_FIELDS;
		
		if (cl != null) {
			localDesc = lookup(cl, true);
			if (!localDesc.isProxy) {
				throw new InvalidClassException(
						"cannot bind proxy descriptor to a non-proxy class");
			}
			name = localDesc.name;
			externalizable = localDesc.externalizable;
			cons = localDesc.cons;
			writeReplaceMethod = localDesc.writeReplaceMethod;
			readResolveMethod = localDesc.readResolveMethod;
			deserializeEx = localDesc.deserializeEx;
		}
	}
	
	/**
	 * Initializes class descriptor representing a non-proxy class.
	 */
	void initNonProxy(final YggdrasilObjectStreamClass model,
			final Class<?> cl,
			final ClassNotFoundException resolveEx,
			final YggdrasilObjectStreamClass superDesc)
			throws InvalidClassException
	{
		this.cl = cl;
		this.resolveEx = resolveEx;
		this.superDesc = superDesc;
		name = model.name;
		suid = Long.valueOf(model.getSerialVersionUID());
		isProxy = false;
		isEnum = model.isEnum;
		serializable = model.serializable;
		externalizable = model.externalizable;
		hasBlockExternalData = model.hasBlockExternalData;
		hasWriteObjectData = model.hasWriteObjectData;
		fields = model.fields;
		primDataSize = model.primDataSize;
		numObjFields = model.numObjFields;
		
		if (cl != null) {
			localDesc = lookup(cl, true);
			if (localDesc.isProxy) {
				throw new InvalidClassException(
						"cannot bind non-proxy descriptor to a proxy class");
			}
			if (isEnum != localDesc.isEnum) {
				throw new InvalidClassException(isEnum ?
						"cannot bind enum descriptor to a non-enum class" :
						"cannot bind non-enum descriptor to an enum class");
			}
			
			if (serializable == localDesc.serializable &&
					!cl.isArray() &&
					suid.longValue() != localDesc.getSerialVersionUID())
			{
				throw new InvalidClassException(localDesc.name,
						"local class incompatible: " +
								"stream classdesc serialVersionUID = " + suid +
								", local class serialVersionUID = " +
								localDesc.getSerialVersionUID());
			}
			
			if (!classNamesEqual(name, localDesc.name)) {
				throw new InvalidClassException(localDesc.name,
						"local class name incompatible with stream class " +
								"name \"" + name + "\"");
			}
			
			if (!isEnum) {
				if ((serializable == localDesc.serializable) &&
						(externalizable != localDesc.externalizable))
				{
					throw new InvalidClassException(localDesc.name,
							"Serializable incompatible with Externalizable");
				}
				
				if ((serializable != localDesc.serializable) ||
						(externalizable != localDesc.externalizable) ||
						!(serializable || externalizable))
				{
					deserializeEx = new ExceptionInfo(
							localDesc.name, "class invalid for deserialization");
				}
			}
			
			cons = localDesc.cons;
			writeObjectMethod = localDesc.writeObjectMethod;
			readObjectMethod = localDesc.readObjectMethod;
			readObjectNoDataMethod = localDesc.readObjectNoDataMethod;
			writeReplaceMethod = localDesc.writeReplaceMethod;
			readResolveMethod = localDesc.readResolveMethod;
			if (deserializeEx == null) {
				deserializeEx = localDesc.deserializeEx;
			}
		}
	}
	
	/**
	 * Returns ClassNotFoundException (if any) thrown while attempting to
	 * resolve local class corresponding to this class descriptor.
	 */
	ClassNotFoundException getResolveException() {
		return resolveEx;
	}
	
	/**
	 * Throws an InvalidClassException if object instances referencing this
	 * class descriptor should not be allowed to deserialize. This method does
	 * not apply to deserialization of enum constants.
	 */
	void checkDeserialize() throws InvalidClassException {
		if (deserializeEx != null) {
			throw deserializeEx.newInvalidClassException();
		}
	}
	
	/**
	 * Throws an InvalidClassException if objects whose class is represented by
	 * this descriptor should not be allowed to serialize. This method does
	 * not apply to serialization of enum constants.
	 */
	void checkSerialize() throws InvalidClassException {
		if (serializeEx != null) {
			throw serializeEx.newInvalidClassException();
		}
	}
	
	/**
	 * Throws an InvalidClassException if objects whose class is represented by
	 * this descriptor should not be permitted to use default serialization
	 * (e.g., if the class declares serializable fields that do not correspond
	 * to actual fields, and hence must use the GetField API). This method
	 * does not apply to deserialization of enum constants.
	 */
	void checkDefaultSerialize() throws InvalidClassException {
		if (defaultSerializeEx != null) {
			throw defaultSerializeEx.newInvalidClassException();
		}
	}
	
	/**
	 * Returns superclass descriptor. Note that on the receiving side, the
	 * superclass descriptor may be bound to a class that is not a superclass
	 * of the subclass descriptor's bound class.
	 */
	YggdrasilObjectStreamClass getSuperDesc() {
		return superDesc;
	}
	
	/**
	 * Returns the "local" class descriptor for the class associated with this
	 * class descriptor (i.e., the result of
	 * YggdrasilObjectStreamClass.lookup(this.forClass())) or null if there is no class
	 * associated with this descriptor.
	 */
	YggdrasilObjectStreamClass getLocalDesc() {
		return localDesc;
	}
	
	/**
	 * Returns arrays of ObjectStreamFields representing the serializable
	 * fields of the represented class. If copy is true, a clone of this class
	 * descriptor's field array is returned, otherwise the array itself is
	 * returned.
	 */
	ObjectStreamField[] getFields(final boolean copy) {
		return copy ? fields.clone() : fields;
	}
	
	/**
	 * Looks up a serializable field of the represented class by name and type.
	 * A specified type of null matches all types, Object.class matches all
	 * non-primitive types, and any other non-null type matches assignable
	 * types only. Returns matching field, or null if no match found.
	 */
	ObjectStreamField getField(final String name, final Class<?> type) {
		for (int i = 0; i < fields.length; i++) {
			final ObjectStreamField f = fields[i];
			if (f.getName().equals(name)) {
				if (type == null ||
						(type == Object.class && !f.isPrimitive()))
				{
					return f;
				}
				final Class<?> ftype = f.getType();
				if (ftype != null && type.isAssignableFrom(ftype)) {
					return f;
				}
			}
		}
		return null;
	}
	
	/**
	 * Returns true if class descriptor represents a dynamic proxy class, false
	 * otherwise.
	 */
	boolean isProxy() {
		return isProxy;
	}
	
	/**
	 * Returns true if class descriptor represents an enum type, false
	 * otherwise.
	 */
	boolean isEnum() {
		return isEnum;
	}
	
	/**
	 * Returns true if represented class implements Externalizable, false
	 * otherwise.
	 */
	boolean isExternalizable() {
		return externalizable;
	}
	
	/**
	 * Returns true if represented class implements Serializable, false
	 * otherwise.
	 */
	boolean isSerializable() {
		return serializable;
	}
	
	/**
	 * Returns true if class descriptor represents externalizable class that
	 * has written its data in 1.2 (block data) format, false otherwise.
	 */
	boolean hasBlockExternalData() {
		return hasBlockExternalData;
	}
	
	/**
	 * Returns true if class descriptor represents serializable (but not
	 * externalizable) class which has written its data via a custom
	 * writeObject() method, false otherwise.
	 */
	boolean hasWriteObjectData() {
		return hasWriteObjectData;
	}
	
	/**
	 * Returns true if represented class is serializable/externalizable and can
	 * be instantiated by the serialization runtime--i.e., if it is
	 * externalizable and defines a public no-arg constructor, or if it is
	 * non-externalizable and its first non-serializable superclass defines an
	 * accessible no-arg constructor. Otherwise, returns false.
	 */
	boolean isInstantiable() {
		return (cons != null);
	}
	
	/**
	 * Returns true if represented class is serializable (but not
	 * externalizable) and defines a conformant writeObject method. Otherwise,
	 * returns false.
	 */
	boolean hasWriteObjectMethod() {
		return (writeObjectMethod != null);
	}
	
	/**
	 * Returns true if represented class is serializable (but not
	 * externalizable) and defines a conformant readObject method. Otherwise,
	 * returns false.
	 */
	boolean hasReadObjectMethod() {
		return (readObjectMethod != null);
	}
	
	/**
	 * Returns true if represented class is serializable (but not
	 * externalizable) and defines a conformant readObjectNoData method.
	 * Otherwise, returns false.
	 */
	boolean hasReadObjectNoDataMethod() {
		return (readObjectNoDataMethod != null);
	}
	
	/**
	 * Returns true if represented class is serializable or externalizable and
	 * defines a conformant writeReplace method. Otherwise, returns false.
	 */
	boolean hasWriteReplaceMethod() {
		return (writeReplaceMethod != null);
	}
	
	/**
	 * Returns true if represented class is serializable or externalizable and
	 * defines a conformant readResolve method. Otherwise, returns false.
	 */
	boolean hasReadResolveMethod() {
		return (readResolveMethod != null);
	}
	
	/**
	 * Creates a new instance of the represented class. If the class is
	 * externalizable, invokes its public no-arg constructor; otherwise, if the
	 * class is serializable, invokes the no-arg constructor of the first
	 * non-serializable superclass. Throws UnsupportedOperationException if
	 * this class descriptor is not associated with a class, if the associated
	 * class is non-serializable or if the appropriate no-arg constructor is
	 * inaccessible/unavailable.
	 */
	Object newInstance()
			throws InstantiationException, InvocationTargetException,
			UnsupportedOperationException
	{
		if (cons != null) {
			try {
				return cons.newInstance();
			} catch (final IllegalAccessException ex) {
				// should not occur, as access checks have been suppressed
				throw new InternalError();
			}
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Invokes the writeObject method of the represented serializable class.
	 * Throws UnsupportedOperationException if this class descriptor is not
	 * associated with a class, or if the class is externalizable,
	 * non-serializable or does not define writeObject.
	 */
	void invokeWriteObject(final Object obj, final ObjectOutputStream out)
			throws IOException, UnsupportedOperationException
	{
		if (writeObjectMethod != null) {
			try {
				writeObjectMethod.invoke(obj, new Object[] {out});
			} catch (final InvocationTargetException ex) {
				final Throwable th = ex.getTargetException();
				if (th instanceof IOException) {
					throw (IOException) th;
				} else {
					throwMiscException(th);
				}
			} catch (final IllegalAccessException ex) {
				// should not occur, as access checks have been suppressed
				throw new InternalError();
			}
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Invokes the readObject method of the represented serializable class.
	 * Throws UnsupportedOperationException if this class descriptor is not
	 * associated with a class, or if the class is externalizable,
	 * non-serializable or does not define readObject.
	 */
	void invokeReadObject(final Object obj, final ObjectInputStream in)
			throws ClassNotFoundException, IOException,
			UnsupportedOperationException
	{
		if (readObjectMethod != null) {
			try {
				readObjectMethod.invoke(obj, new Object[] {in});
			} catch (final InvocationTargetException ex) {
				final Throwable th = ex.getTargetException();
				if (th instanceof ClassNotFoundException) {
					throw (ClassNotFoundException) th;
				} else if (th instanceof IOException) {
					throw (IOException) th;
				} else {
					throwMiscException(th);
				}
			} catch (final IllegalAccessException ex) {
				// should not occur, as access checks have been suppressed
				throw new InternalError();
			}
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Invokes the readObjectNoData method of the represented serializable
	 * class. Throws UnsupportedOperationException if this class descriptor is
	 * not associated with a class, or if the class is externalizable,
	 * non-serializable or does not define readObjectNoData.
	 */
	void invokeReadObjectNoData(final Object obj)
			throws IOException, UnsupportedOperationException
	{
		if (readObjectNoDataMethod != null) {
			try {
				readObjectNoDataMethod.invoke(obj, (Object[]) null);
			} catch (final InvocationTargetException ex) {
				final Throwable th = ex.getTargetException();
				if (th instanceof ObjectStreamException) {
					throw (ObjectStreamException) th;
				} else {
					throwMiscException(th);
				}
			} catch (final IllegalAccessException ex) {
				// should not occur, as access checks have been suppressed
				throw new InternalError();
			}
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Invokes the writeReplace method of the represented serializable class and
	 * returns the result. Throws UnsupportedOperationException if this class
	 * descriptor is not associated with a class, or if the class is
	 * non-serializable or does not define writeReplace.
	 */
	Object invokeWriteReplace(final Object obj)
			throws IOException, UnsupportedOperationException
	{
		if (writeReplaceMethod != null) {
			try {
				return writeReplaceMethod.invoke(obj, (Object[]) null);
			} catch (final InvocationTargetException ex) {
				final Throwable th = ex.getTargetException();
				if (th instanceof ObjectStreamException) {
					throw (ObjectStreamException) th;
				} else {
					throwMiscException(th);
					throw new InternalError();  // never reached
				}
			} catch (final IllegalAccessException ex) {
				// should not occur, as access checks have been suppressed
				throw new InternalError();
			}
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Invokes the readResolve method of the represented serializable class and
	 * returns the result. Throws UnsupportedOperationException if this class
	 * descriptor is not associated with a class, or if the class is
	 * non-serializable or does not define readResolve.
	 */
	Object invokeReadResolve(final Object obj)
			throws IOException, UnsupportedOperationException
	{
		if (readResolveMethod != null) {
			try {
				return readResolveMethod.invoke(obj, (Object[]) null);
			} catch (final InvocationTargetException ex) {
				final Throwable th = ex.getTargetException();
				if (th instanceof ObjectStreamException) {
					throw (ObjectStreamException) th;
				} else {
					throwMiscException(th);
					throw new InternalError();  // never reached
				}
			} catch (final IllegalAccessException ex) {
				// should not occur, as access checks have been suppressed
				throw new InternalError();
			}
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Class representing the portion of an object's serialized form allotted
	 * to data described by a given class descriptor. If "hasData" is false,
	 * the object's serialized form does not contain data associated with the
	 * class descriptor.
	 */
	static class ClassDataSlot {
		
		/** class descriptor "occupying" this slot */
		final YggdrasilObjectStreamClass desc;
		/** true if serialized form includes data for this slot's descriptor */
		final boolean hasData;
		
		ClassDataSlot(final YggdrasilObjectStreamClass desc, final boolean hasData) {
			this.desc = desc;
			this.hasData = hasData;
		}
	}
	
	/**
	 * Returns array of ClassDataSlot instances representing the data layout
	 * (including superclass data) for serialized objects described by this
	 * class descriptor. ClassDataSlots are ordered by inheritance with those
	 * containing "higher" superclasses appearing first. The final
	 * ClassDataSlot contains a reference to this descriptor.
	 */
	ClassDataSlot[] getClassDataLayout() throws InvalidClassException {
		// REMIND: synchronize instead of relying on volatile?
		if (dataLayout == null) {
			dataLayout = getClassDataLayout0();
		}
		return dataLayout;
	}
	
	private ClassDataSlot[] getClassDataLayout0()
			throws InvalidClassException
	{
		final ArrayList<ClassDataSlot> slots = new ArrayList<>();
		Class<?> start = cl, end = cl;
		
		// locate closest non-serializable superclass
		while (end != null && Serializable.class.isAssignableFrom(end)) {
			end = end.getSuperclass();
		}
		
		final HashSet<String> oscNames = new HashSet<>(3);
		
		for (YggdrasilObjectStreamClass d = this; d != null; d = d.superDesc) {
			if (oscNames.contains(d.name)) {
				throw new InvalidClassException("Circular reference.");
			} else {
				oscNames.add(d.name);
			}
			
			// search up inheritance hierarchy for class with matching name
			final String searchName = (d.cl != null) ? d.cl.getName() : d.name;
			Class<?> match = null;
			for (Class<?> c = start; c != end; c = c.getSuperclass()) {
				if (searchName.equals(c.getName())) {
					match = c;
					break;
				}
			}
			
			// add "no data" slot for each unmatched class below match
			if (match != null) {
				for (Class<?> c = start; c != match; c = c.getSuperclass()) {
					slots.add(new ClassDataSlot(
							YggdrasilObjectStreamClass.lookup(c, true), false));
				}
				start = match.getSuperclass();
			}
			
			// record descriptor/class pairing
			slots.add(new ClassDataSlot(d.getVariantFor(match), true));
		}
		
		// add "no data" slot for any leftover unmatched classes
		for (Class<?> c = start; c != end; c = c.getSuperclass()) {
			slots.add(new ClassDataSlot(
					YggdrasilObjectStreamClass.lookup(c, true), false));
		}
		
		// order slots from superclass -> subclass
		Collections.reverse(slots);
		return slots.toArray(new ClassDataSlot[slots.size()]);
	}
	
	/**
	 * Returns aggregate size (in bytes) of marshalled primitive field values
	 * for represented class.
	 */
	int getPrimDataSize() {
		return primDataSize;
	}
	
	/**
	 * Returns number of non-primitive serializable fields of represented
	 * class.
	 */
	int getNumObjFields() {
		return numObjFields;
	}
	
	/**
	 * If given class is the same as the class associated with this class
	 * descriptor, returns reference to this class descriptor. Otherwise,
	 * returns variant of this class descriptor bound to given class.
	 */
	private YggdrasilObjectStreamClass getVariantFor(final Class<?> cl)
			throws InvalidClassException
	{
		if (this.cl == cl) {
			return this;
		}
		final YggdrasilObjectStreamClass desc = new YggdrasilObjectStreamClass();
		if (isProxy) {
			desc.initProxy(cl, null, superDesc);
		} else {
			desc.initNonProxy(this, cl, null, superDesc);
		}
		return desc;
	}
	
	/**
	 * Returns public no-arg constructor of given class, or null if none found.
	 * Access checks are disabled on the returned constructor (if any), since
	 * the defining class may still be non-public.
	 */
	private static Constructor<?> getExternalizableConstructor(final Class<?> cl) {
		try {
			final Constructor<?> cons = cl.getDeclaredConstructor((Class<?>[]) null);
			cons.setAccessible(true);
			return ((cons.getModifiers() & Modifier.PUBLIC) != 0) ?
					cons : null;
		} catch (final NoSuchMethodException ex) {
			return null;
		}
	}
	
	/**
	 * Returns subclass-accessible no-arg constructor of first non-serializable
	 * superclass, or null if none found. Access checks are disabled on the
	 * returned constructor (if any).
	 */
	private static Constructor<?> getSerializableConstructor(final Class<?> cl) {
		Class<?> initCl = cl;
		while (Serializable.class.isAssignableFrom(initCl)) {
			if ((initCl = initCl.getSuperclass()) == null) {
				return null;
			}
		}
		try {
			Constructor<?> cons = initCl.getDeclaredConstructor((Class<?>[]) null);
			final int mods = cons.getModifiers();
			if ((mods & Modifier.PRIVATE) != 0 ||
					((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0 &&
					!packageEquals(cl, initCl)))
			{
				return null;
			}
			cons = reflFactory.newConstructorForSerialization(cl, cons);
			cons.setAccessible(true);
			return cons;
		} catch (final NoSuchMethodException ex) {
			return null;
		}
	}
	
	/**
	 * Returns non-static, non-abstract method with given signature provided it
	 * is defined by or accessible (via inheritance) by the given class, or
	 * null if no match found. Access checks are disabled on the returned
	 * method (if any).
	 */
	private static Method getInheritableMethod(final Class<?> cl, final String name,
			final Class<?>[] argTypes,
			final Class<?> returnType)
	{
		Method meth = null;
		Class<?> defCl = cl;
		while (defCl != null) {
			try {
				meth = defCl.getDeclaredMethod(name, argTypes);
				break;
			} catch (final NoSuchMethodException ex) {
				defCl = defCl.getSuperclass();
			}
		}
		
		if ((meth == null) || (meth.getReturnType() != returnType)) {
			return null;
		}
		meth.setAccessible(true);
		final int mods = meth.getModifiers();
		if ((mods & (Modifier.STATIC | Modifier.ABSTRACT)) != 0) {
			return null;
		} else if ((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0) {
			return meth;
		} else if ((mods & Modifier.PRIVATE) != 0) {
			return (cl == defCl) ? meth : null;
		} else {
			return packageEquals(cl, defCl) ? meth : null;
		}
	}
	
	/**
	 * Returns non-static private method with given signature defined by given
	 * class, or null if none found. Access checks are disabled on the
	 * returned method (if any).
	 */
	private static Method getPrivateMethod(final Class<?> cl, final String name,
			final Class<?>[] argTypes,
			final Class<?> returnType)
	{
		try {
			final Method meth = cl.getDeclaredMethod(name, argTypes);
			meth.setAccessible(true);
			final int mods = meth.getModifiers();
			return ((meth.getReturnType() == returnType) &&
					((mods & Modifier.STATIC) == 0) && ((mods & Modifier.PRIVATE) != 0)) ? meth : null;
		} catch (final NoSuchMethodException ex) {
			return null;
		}
	}
	
	/**
	 * Returns true if classes are defined in the same runtime package, false
	 * otherwise.
	 */
	private static boolean packageEquals(final Class<?> cl1, final Class<?> cl2) {
		return (cl1.getClassLoader() == cl2.getClassLoader() && getPackageName(cl1).equals(getPackageName(cl2)));
	}
	
	/**
	 * Returns package name of given class.
	 */
	private static String getPackageName(final Class<?> cl) {
		String s = cl.getName();
		int i = s.lastIndexOf('[');
		if (i >= 0) {
			s = s.substring(i + 2);
		}
		i = s.lastIndexOf('.');
		return (i >= 0) ? s.substring(0, i) : "";
	}
	
	/**
	 * Compares class names for equality, ignoring package names. Returns true
	 * if class names equal, false otherwise.
	 */
	private static boolean classNamesEqual(String name1, String name2) {
		name1 = name1.substring(name1.lastIndexOf('.') + 1);
		name2 = name2.substring(name2.lastIndexOf('.') + 1);
		return name1.equals(name2);
	}
	
	/**
	 * Returns JVM type signature for given class.
	 */
	private static String getClassSignature(Class<?> cl) {
		final StringBuilder sbuf = new StringBuilder();
		while (cl.isArray()) {
			sbuf.append('[');
			cl = cl.getComponentType();
		}
		if (cl.isPrimitive()) {
			if (cl == Integer.TYPE) {
				sbuf.append('I');
			} else if (cl == Byte.TYPE) {
				sbuf.append('B');
			} else if (cl == Long.TYPE) {
				sbuf.append('J');
			} else if (cl == Float.TYPE) {
				sbuf.append('F');
			} else if (cl == Double.TYPE) {
				sbuf.append('D');
			} else if (cl == Short.TYPE) {
				sbuf.append('S');
			} else if (cl == Character.TYPE) {
				sbuf.append('C');
			} else if (cl == Boolean.TYPE) {
				sbuf.append('Z');
			} else if (cl == Void.TYPE) {
				sbuf.append('V');
			} else {
				throw new InternalError();
			}
		} else {
			sbuf.append('L' + cl.getName().replace('.', '/') + ';');
		}
		return sbuf.toString();
	}
	
	/**
	 * Returns JVM type signature for given list of parameters and return type.
	 */
	private static String getMethodSignature(final Class<?>[] paramTypes,
			final Class<?> retType)
	{
		final StringBuilder sbuf = new StringBuilder();
		sbuf.append('(');
		for (int i = 0; i < paramTypes.length; i++) {
			sbuf.append(getClassSignature(paramTypes[i]));
		}
		sbuf.append(')');
		sbuf.append(getClassSignature(retType));
		return sbuf.toString();
	}
	
	/**
	 * Convenience method for throwing an exception that is either a
	 * RuntimeException, Error, or of some unexpected type (in which case it is
	 * wrapped inside an IOException).
	 */
	private static void throwMiscException(final Throwable th) throws IOException {
		if (th instanceof RuntimeException) {
			throw (RuntimeException) th;
		} else if (th instanceof Error) {
			throw (Error) th;
		} else {
			final IOException ex = new IOException("unexpected exception type");
			ex.initCause(th);
			throw ex;
		}
	}
	
	/**
	 * Returns ObjectStreamField array describing the serializable fields of
	 * the given class. Serializable fields backed by an actual field of the
	 * class are represented by ObjectStreamFields with corresponding non-null
	 * Field objects. Throws InvalidClassException if the (explicitly
	 * declared) serializable fields are invalid.
	 */
	private static ObjectStreamField[] getSerialFields(final Class<?> cl)
			throws InvalidClassException
	{
		ObjectStreamField[] fields;
		if (Serializable.class.isAssignableFrom(cl) &&
				!Externalizable.class.isAssignableFrom(cl) &&
				!Proxy.isProxyClass(cl) &&
				!cl.isInterface())
		{
			if ((fields = getDeclaredSerialFields(cl)) == null) {
				fields = getDefaultSerialFields(cl);
			}
			Arrays.sort(fields);
		} else {
			fields = NO_FIELDS;
		}
		return fields;
	}
	
	/**
	 * Returns serializable fields of given class as defined explicitly by a
	 * "serialPersistentFields" field, or null if no appropriate
	 * "serialPersistentFields" field is defined. Serializable fields backed
	 * by an actual field of the class are represented by ObjectStreamFields
	 * with corresponding non-null Field objects. For compatibility with past
	 * releases, a "serialPersistentFields" field with a null value is
	 * considered equivalent to not declaring "serialPersistentFields". Throws
	 * InvalidClassException if the declared serializable fields are
	 * invalid--e.g., if multiple fields share the same name.
	 */
	private static ObjectStreamField[] getDeclaredSerialFields(final Class<?> cl)
			throws InvalidClassException
	{
		ObjectStreamField[] serialPersistentFields = null;
		try {
			final Field f = cl.getDeclaredField("serialPersistentFields");
			final int mask = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
			if ((f.getModifiers() & mask) == mask) {
				f.setAccessible(true);
				serialPersistentFields = (ObjectStreamField[]) f.get(null);
			}
		} catch (final Exception ex) {}
		if (serialPersistentFields == null) {
			return null;
		} else if (serialPersistentFields.length == 0) {
			return NO_FIELDS;
		}
		
		final ObjectStreamField[] boundFields =
				new ObjectStreamField[serialPersistentFields.length];
		final Set<String> fieldNames = new HashSet<>(serialPersistentFields.length);
		
		for (int i = 0; i < serialPersistentFields.length; i++) {
			final ObjectStreamField spf = serialPersistentFields[i];
			
			final String fname = spf.getName();
			if (fieldNames.contains(fname)) {
				throw new InvalidClassException(
						"multiple serializable fields named " + fname);
			}
			fieldNames.add(fname);
			
			try {
				final Field f = cl.getDeclaredField(fname);
				if ((f.getType() == spf.getType()) &&
						((f.getModifiers() & Modifier.STATIC) == 0))
				{
					boundFields[i] =
							new ObjectStreamField(f, spf.isUnshared(), true);
				}
			} catch (final NoSuchFieldException ex) {}
			if (boundFields[i] == null) {
				boundFields[i] = new ObjectStreamField(
						fname, spf.getType(), spf.isUnshared());
			}
		}
		return boundFields;
	}
	
	/**
	 * Returns array of ObjectStreamFields corresponding to all non-static
	 * non-transient fields declared by given class. Each ObjectStreamField
	 * contains a Field object for the field it represents. If no default
	 * serializable fields exist, NO_FIELDS is returned.
	 */
	private static ObjectStreamField[] getDefaultSerialFields(final Class<?> cl) {
		final Field[] clFields = cl.getDeclaredFields();
		final ArrayList<ObjectStreamField> list = new ArrayList<>();
		final int mask = Modifier.STATIC | Modifier.TRANSIENT;
		
		for (int i = 0; i < clFields.length; i++) {
			if ((clFields[i].getModifiers() & mask) == 0) {
				list.add(new ObjectStreamField(clFields[i], false, true));
			}
		}
		final int size = list.size();
		return (size == 0) ? NO_FIELDS :
				list.toArray(new ObjectStreamField[size]);
	}
	
	/**
	 * Returns explicit serial version UID value declared by given class, or
	 * null if none.
	 */
	private static Long getDeclaredSUID(final Class<?> cl) {
		try {
			final Field f = cl.getDeclaredField("serialVersionUID");
			final int mask = Modifier.STATIC | Modifier.FINAL;
			if ((f.getModifiers() & mask) == mask) {
				f.setAccessible(true);
				return Long.valueOf(f.getLong(null));
			}
		} catch (final Exception ex) {}
		return null;
	}
	
	/**
	 * Computes the default serial version UID value for the given class.
	 */
	private static long computeDefaultSUID(final Class<?> cl) {
		if (!Serializable.class.isAssignableFrom(cl) || Proxy.isProxyClass(cl))
		{
			return 0L;
		}
		
		try {
			final ByteArrayOutputStream bout = new ByteArrayOutputStream();
			final DataOutputStream dout = new DataOutputStream(bout);
			
			dout.writeUTF(cl.getName());
			
			int classMods = cl.getModifiers() &
					(Modifier.PUBLIC | Modifier.FINAL |
							Modifier.INTERFACE | Modifier.ABSTRACT);
			
			/*
			 * compensate for javac bug in which ABSTRACT bit was set for an
			 * interface only if the interface declared methods
			 */
			final Method[] methods = cl.getDeclaredMethods();
			if ((classMods & Modifier.INTERFACE) != 0) {
				classMods = (methods.length > 0) ?
						(classMods | Modifier.ABSTRACT) :
						(classMods & ~Modifier.ABSTRACT);
			}
			dout.writeInt(classMods);
			
			if (!cl.isArray()) {
				/*
				 * compensate for change in 1.2FCS in which
				 * Class.getInterfaces() was modified to return Cloneable and
				 * Serializable for array classes.
				 */
				final Class<?>[] interfaces = cl.getInterfaces();
				final String[] ifaceNames = new String[interfaces.length];
				for (int i = 0; i < interfaces.length; i++) {
					ifaceNames[i] = interfaces[i].getName();
				}
				Arrays.sort(ifaceNames);
				for (int i = 0; i < ifaceNames.length; i++) {
					dout.writeUTF(ifaceNames[i]);
				}
			}
			
			final Field[] fields = cl.getDeclaredFields();
			final MemberSignature[] fieldSigs = new MemberSignature[fields.length];
			for (int i = 0; i < fields.length; i++) {
				fieldSigs[i] = new MemberSignature(fields[i]);
			}
			Arrays.sort(fieldSigs, new Comparator<MemberSignature>() {
				@Override
				public int compare(final MemberSignature ms1, final MemberSignature ms2) {
					return ms1.name.compareTo(ms2.name);
				}
			});
			for (int i = 0; i < fieldSigs.length; i++) {
				final MemberSignature sig = fieldSigs[i];
				final int mods = sig.member.getModifiers() &
						(Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
								Modifier.STATIC | Modifier.FINAL | Modifier.VOLATILE |
						Modifier.TRANSIENT);
				if (((mods & Modifier.PRIVATE) == 0) ||
						((mods & (Modifier.STATIC | Modifier.TRANSIENT)) == 0))
				{
					dout.writeUTF(sig.name);
					dout.writeInt(mods);
					dout.writeUTF(sig.signature);
				}
			}
			
			if (hasStaticInitializer(cl)) {
				dout.writeUTF("<clinit>");
				dout.writeInt(Modifier.STATIC);
				dout.writeUTF("()V");
			}
			
			final Constructor<?>[] cons = cl.getDeclaredConstructors();
			final MemberSignature[] consSigs = new MemberSignature[cons.length];
			for (int i = 0; i < cons.length; i++) {
				consSigs[i] = new MemberSignature(cons[i]);
			}
			Arrays.sort(consSigs, new Comparator<MemberSignature>() {
				@Override
				public int compare(final MemberSignature ms1, final MemberSignature ms2) {
					return ms1.signature.compareTo(ms2.signature);
				}
			});
			for (int i = 0; i < consSigs.length; i++) {
				final MemberSignature sig = consSigs[i];
				final int mods = sig.member.getModifiers() &
						(Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
								Modifier.STATIC | Modifier.FINAL |
								Modifier.SYNCHRONIZED | Modifier.NATIVE |
								Modifier.ABSTRACT | Modifier.STRICT);
				if ((mods & Modifier.PRIVATE) == 0) {
					dout.writeUTF("<init>");
					dout.writeInt(mods);
					dout.writeUTF(sig.signature.replace('/', '.'));
				}
			}
			
			final MemberSignature[] methSigs = new MemberSignature[methods.length];
			for (int i = 0; i < methods.length; i++) {
				methSigs[i] = new MemberSignature(methods[i]);
			}
			Arrays.sort(methSigs, new Comparator<MemberSignature>() {
				@Override
				public int compare(final MemberSignature ms1, final MemberSignature ms2) {
					int comp = ms1.name.compareTo(ms2.name);
					if (comp == 0) {
						comp = ms1.signature.compareTo(ms2.signature);
					}
					return comp;
				}
			});
			for (int i = 0; i < methSigs.length; i++) {
				final MemberSignature sig = methSigs[i];
				final int mods = sig.member.getModifiers() &
						(Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
								Modifier.STATIC | Modifier.FINAL |
								Modifier.SYNCHRONIZED | Modifier.NATIVE |
								Modifier.ABSTRACT | Modifier.STRICT);
				if ((mods & Modifier.PRIVATE) == 0) {
					dout.writeUTF(sig.name);
					dout.writeInt(mods);
					dout.writeUTF(sig.signature.replace('/', '.'));
				}
			}
			
			dout.flush();
			
			final MessageDigest md = MessageDigest.getInstance("SHA");
			final byte[] hashBytes = md.digest(bout.toByteArray());
			long hash = 0;
			for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--) {
				hash = (hash << 8) | (hashBytes[i] & 0xFF);
			}
			return hash;
		} catch (final IOException ex) {
			throw new InternalError();
		} catch (final NoSuchAlgorithmException ex) {
			throw new SecurityException(ex.getMessage());
		}
	}
	
	/**
	 * Returns true if the given class defines a static initializer method,
	 * false otherwise.
	 */
	private native static boolean hasStaticInitializer(Class<?> cl);
	
	/**
	 * Class for computing and caching field/constructor/method signatures
	 * during serialVersionUID calculation.
	 */
	private static class MemberSignature {
		
		public final Member member;
		public final String name;
		public final String signature;
		
		public MemberSignature(final Field field) {
			member = field;
			name = field.getName();
			signature = getClassSignature(field.getType());
		}
		
		public MemberSignature(final Constructor<?> cons) {
			member = cons;
			name = cons.getName();
			signature = getMethodSignature(
					cons.getParameterTypes(), Void.TYPE);
		}
		
		public MemberSignature(final Method meth) {
			member = meth;
			name = meth.getName();
			signature = getMethodSignature(
					meth.getParameterTypes(), meth.getReturnType());
		}
	}
	
	/**
	 * Removes from the specified map any keys that have been enqueued
	 * on the specified reference queue.
	 */
	static void processQueue(final ReferenceQueue<Class<?>> queue,
			final ConcurrentMap<? extends
			WeakReference<Class<?>>, ?> map)
	{
		Reference<? extends Class<?>> ref;
		while ((ref = queue.poll()) != null) {
			map.remove(ref);
		}
	}
	
	/**
	 * Weak key for Class objects.
	 **/
	static class WeakClassKey extends WeakReference<Class<?>> {
		/**
		 * saved value of the referent's identity hash code, to maintain
		 * a consistent hash code after the referent has been cleared
		 */
		private final int hash;
		
		/**
		 * Create a new WeakClassKey to the given object, registered
		 * with a queue.
		 */
		WeakClassKey(final Class<?> cl, final ReferenceQueue<Class<?>> refQueue) {
			super(cl, refQueue);
			hash = System.identityHashCode(cl);
		}
		
		/**
		 * Returns the identity hash code of the original referent.
		 */
		@Override
		public int hashCode() {
			return hash;
		}
		
		/**
		 * Returns true if the given object is this identical
		 * WeakClassKey instance, or, if this object's referent has not
		 * been cleared, if the given object is another WeakClassKey
		 * instance with the identical non-null referent as this one.
		 */
		@Override
		public boolean equals(final Object obj) {
			if (obj == this) {
				return true;
			}
			
			if (obj instanceof WeakClassKey) {
				final Object referent = get();
				return (referent != null) &&
						(referent == ((WeakClassKey) obj).get());
			} else {
				return false;
			}
		}
	}
//    
//	YggdrasilObjectStreamClass osc;
//	
//	YggdrasilYggdrasilObjectStreamClass(final Class<?> c) {
//		osc = YggdrasilObjectStreamClass.lookup(c);
//	}
//	
//	private final static class M {
//		private Method m;
//		
//		M(final String name, final Class<?>... args) {
//			try {
//				m = YggdrasilObjectStreamClass.class.getDeclaredMethod(name, args);
//				m.setAccessible(true);
//			} catch (NoSuchMethodException | SecurityException e) {
//				throw new AssertionError(e);
//			}
//		}
//		
//		Object invoke(final Object o, final Object... args) throws IOException, ClassNotFoundException {
//			try {
//				return m.invoke(o, args);
//			} catch (IllegalAccessException | IllegalArgumentException e) {
//				throw new AssertionError();
//			} catch (final InvocationTargetException e) {
//				throw e.getCause();
//			}
//		}
//	}
//	
//	// instance methods
//	private final static M newInstance = new M("newInstance"),
//			hasWriteReplaceMethod = new M("hasWriteReplaceMethod", Object.class), invokeWriteReplace = new M("invokeWriteReplace", Object.class),
//			hasWriteObjectMethod = new M("hasWriteObjectMethod", Object.class), invokeWriteObject = new M("invokeWriteObject", Object.class, ObjectOutputStream.class),
//			hasReadResolveMethod = new M("hasReadResolveMethod", Object.class), invokeReadResolve = new M("invokeReadResolve", Object.class),
//			hasReadObjectMethod = new M("hasReadObjectMethod", Object.class), invokeReadObject = new M("invokeReadObject", Object.class, ObjectInputStream.class);
//	
//	public Object newInstance() throws IOException, ClassNotFoundException {
//		return newInstance.invoke(osc);
//	}
//
//	public Object getWriteReplace(Object o) throws IOException, ClassNotFoundException {
//		if ((boolean) hasWriteReplaceMethod.invoke(osc))
//			return invokeWriteReplace.invoke(osc, o);
//		return null;
//	}
//
//	public Object getReadReplace(Object o) throws IOException, ClassNotFoundException {
//		if ((boolean) hasReadResolveMethod.invoke(osc))
//			return invokeReadResolve.invoke(osc, o);
//		return null;
//	}
//	
//	public void writeObject(Object o, ObjectOutputStream out) throws IOException, ClassNotFoundException {
//		if ((boolean) hasWriteObjectMethod.invoke(osc)) {
//			invokeWriteObject.invoke(osc, o, out);
//		} else {
//			out.defaultWriteObject();
//		}
//	}
//	
//	// static methods
//	private final static M getSerializableConstructor = new M("getSerializableConstructor", Class.class), getExternalizableConstructor = new M("getExternalizableConstructor", Class.class),
//			getDeclaredSerialFields = new M("getDeclaredSerialFields", Class.class), getDefaultSerialFields = new M("getDefaultSerialFields", Class.class);
//	
//	public final Constructor<?> getConstructor() {
//		if (Externalizable.class.isAssignableFrom(osc.forClass()))
//			return (Constructor<?>) getExternalizableConstructor.invoke(null, osc.forClass());
//		return (Constructor<?>) getSerializableConstructor.invoke(null, osc.forClass());
//	}
//	
//	public final ObjectStreamField[] getSerialFields() {
//		final ObjectStreamField[] decl = (ObjectStreamField[]) getDeclaredSerialFields.invoke(null, osc.forClass());
//		if (decl != null)
//			return decl;
//		return (ObjectStreamField[]) getDefaultSerialFields.invoke(null, osc.forClass());
//	}
	
}
