package mypkg;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

public class Deps {
	public static JSONArray jsonArray() {
		return (JSONArray) JSONValue.parse("[1,2,3]");
	}
}
