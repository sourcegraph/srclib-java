package mypkg;

public class Container {
	public class Inner {
		public Container F() {
			return new Container();
		}
	}

	public Inner F() {
		return new Inner();
	}
}
