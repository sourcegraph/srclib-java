/**
 * 
 */
package mypkg;

import java.util.ArrayList;
import java.util.List;

/**
 * FooClass is a class.
 * 
 * @author Fred
 * 
 */
public class FooClass {
	public static int myStatic;

	public static String myStringStatic() {
		String s = "hello";
		return s;
	}

	public FooClass foo;

	final List<Integer> myIntegers = new ArrayList<Integer>(5);
	final int numIntegers = myIntegers.size();

	static {
		System.out.println("Hello!");
	}

	/**
	 * myInt is a method.
	 * 
	 * @return an awesome int
	 */
	public int myInt() {
		int len = myString().length();
		return len;
	}

	public String myString() {
		return FooClass.myStringStatic();
	}
}
