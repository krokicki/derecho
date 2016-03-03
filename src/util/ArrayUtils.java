package util;

import java.util.ArrayList;

/**
 * Utilities for dealing with arrays.
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class ArrayUtils {

	public static void ensureSize(ArrayList<?> list, int size) {
		list.ensureCapacity(size);
		while (list.size() < size) {
			list.add(null);
		}
	}
}
