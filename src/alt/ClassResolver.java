package ch.njol.yggdrasil;

public interface ClassResolver {
	
	/**
	 * Resolves a class by its ID.
	 * 
	 * @param id The ID used when storing objects
	 * @return The Class object that represents data with the given ID, or null if the ID does not belong to the implementor
	 */
	public Class<?> getClass(String id);
	
	/**
	 * Gets an ID for a Class. The ID is used to identify the type of a saved object.
	 * <p>
	 * // TODO make sure that it's unique
	 * 
	 * @param c The class to get the ID of
	 * @return The ID of the given class, or null if this is not a class of the implementor
	 */
	public String getID(Class<?> c);
	
}
