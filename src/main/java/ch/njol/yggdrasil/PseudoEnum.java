package ch.njol.yggdrasil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A class that acts as a "pseudo-enum", i.e. a class which only has immutable, (public,) final static instances, which can be identified by their unique name. The instances don't
 * even have to be defined in their class, as they are registered in the constructor.
 * <p>
 * Please note that you cannot define a constant's id used for saving by annotating it with {@link YggdrasilID @YggdrasilID}, as the field(s) of the constant may not be known, and
 * furthermore a constant can be assigned to any number of fields.
 * <p>
 * This class defines methods similar to those in {@link Enum} with minor differences, e.g. {@link #values()} returns a {@link List} instead of an array.
 * 
 * @author Peter Güttinger
 */
public abstract class PseudoEnum<T extends PseudoEnum<T>> {
	
	private final String name;
	private final int ordinal;
	
	private final Info<T> info;
	
	@SuppressWarnings({"unchecked", "null"})
	protected PseudoEnum(final String name) {
		this.name = name;
		info = getInfo(getClass());
		if (info.map.containsKey(name))
			throw new IllegalArgumentException("Duplicate name '" + name + "'");
		ordinal = info.values.size();
		info.values.add((T) this);
		info.map.put(name, (T) this);
	}
	
	/**
	 * Returns the unique name of this constant.
	 * 
	 * @return The unique name of this constant.
	 * @see Enum#name()
	 */
	public final String name() {
		return name;
	}
	
	/**
	 * Returns {@link #name()}.
	 * 
	 * @return {@link #name()}
	 * @see Enum#toString()
	 */
	@Override
	public String toString() {
		return name;
	}
	
	/**
	 * Returns the unique ID of this constant. This will not be used by Yggdrasil and can thus change freely across version, in particular reordering and inserting constants is
	 * permitted.
	 * 
	 * @return The unique ID of this constant.
	 * @see Enum#ordinal()
	 */
	public final int ordinal() {
		return ordinal;
	}
	
	/**
	 * Uses {@link System#identityHashCode(Object)}
	 */
	@Override
	public final int hashCode() {
		return System.identityHashCode(this);
	}
	
	/**
	 * Checks for reference equality (==).
	 */
	@Override
	public final boolean equals(@Nullable final Object obj) {
		return obj == this;
	}
	
	/**
	 * Prevents cloning of pseudo-enums. If you want to make your enums cloneable, create a <tt>(name, constantToClone)</tt> constructor.
	 * 
	 * @return newer returns normally
	 * @throws CloneNotSupportedException always
	 */
	@Override
	protected final Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}
	
	/**
	 * Returns this constant's pseudo-enum class, i.e. the first non-anonymous superclass of this constant. This class is the same for all constants inheriting from a common class
	 * independently from whether they define an anonymous subclass.
	 * 
	 * @return This constant's pseudo-enum class.
	 * @see Enum#getDeclaringClass()
	 */
	@SuppressWarnings({"unchecked", "null"})
	public final Class<T> getDeclaringClass() {
		return getDeclaringClass(getClass());
	}
	
	/**
	 * Returns the common base class for constants of the given type, i.e. the first non-anonymous superclass of <tt>type</tt>.
	 * 
	 * @return The pseudo-enum class of the given class.
	 * @see Enum#getDeclaringClass()
	 */
	@SuppressWarnings("unchecked")
	public final static <T extends PseudoEnum<T>> Class<? super T> getDeclaringClass(final Class<T> type) {
		Class<?> c = type;
		while (c.isAnonymousClass())
			c = c.getSuperclass();
		return (Class<? super T>) c;
	}
	
	/**
	 * Returns all constants registered so far, ordered by their {@link #ordinal() id} (i.e. <tt>c.values().get(c.ordinal()) == c</tt> is true for any constant c).
	 * <p>
	 * This method returns an {@link Collections#unmodifiableList(List) unmodifiable view} of the internal list.
	 * 
	 * @return All constants registered so far.
	 * @see Enum#valueOf(Class, String)
	 */
	@SuppressWarnings("null")
	public final List<T> values() {
		return Collections.unmodifiableList(info.values);
	}
	
	/**
	 * Returns all constants of the given class registered so far, ordered by their {@link #ordinal() id} (i.e. <tt>c.values().get(c.ordinal()) == c</tt> is true for any constant
	 * c).
	 * <p>
	 * This method returns an {@link Collections#unmodifiableList(List) unmodifiable view} of the internal list.
	 * 
	 * @return All constants registered so far.
	 * @see Enum#valueOf(Class, String)
	 */
	@SuppressWarnings("null")
	public final static <T extends PseudoEnum<T>> List<T> values(final Class<T> c) {
		return Collections.unmodifiableList(getInfo(c).values);
	}
	
	/**
	 * Returns the constant with the given ID.
	 * 
	 * @param id The constant's ID
	 * @return The constant with the given ID.
	 * @throws IndexOutOfBoundsException if ID is < 0 or >= {@link #numConstants()}
	 */
	@SuppressWarnings("null")
	public final T getConstant(final int id) throws IndexOutOfBoundsException {
		return info.values.get(id);
	}
	
	/**
	 * @return How many constants are currently registered
	 */
	public final int numConstants() {
		return info.values.size();
	}
	
	/**
	 * @param name The name of the constant to find
	 * @return The constant with the given name, or null if no constant with that exact name was found.
	 * @see Enum#valueOf(Class, String)
	 */
	@Nullable
	public final T valueOf(final String name) {
		return info.map.get(name);
	}
	
	/**
	 * @param c The class of the constant to find
	 * @param name The name of the constant to find
	 * @return The constant with the given name, or null if no constant with that exact name was found in the given class.
	 * @see Enum#valueOf(Class, String)
	 */
	@Nullable
	public final static <T extends PseudoEnum<T>> T valueOf(final Class<T> c, final String name) {
		return getInfo(c).map.get(name);
	}
	
	private final static class Info<T extends PseudoEnum<T>> {
		final List<T> values = new ArrayList<T>();
		final Map<String, T> map = new HashMap<String, T>();
		
		public Info() {}
	}
	
	private final static <T extends PseudoEnum<T>> Info<T> getInfo(final Class<T> c) {
		Info<T> info = (Info<T>) infos.get(getDeclaringClass(c));
		if (info == null)
			infos.put(c, info = new Info<T>());
		return info;
	}
	
	private final static Map<Class<? extends PseudoEnum<?>>, Info<?>> infos = new HashMap<Class<? extends PseudoEnum<?>>, Info<?>>();
	
}
