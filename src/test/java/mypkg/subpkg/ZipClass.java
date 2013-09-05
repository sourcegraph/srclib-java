package mypkg.subpkg;

import mypkg.FooClass;

public class ZipClass {
	private void zap() {
		FooClass foo = new FooClass();
		System.out.println(foo.myString());
		System.out.println(Integer.toString(foo.myInt()));
		System.out.println(Integer.toString(FooClass.myStatic));
	}

	static {
		new ZipClass().zap();
	}
}
