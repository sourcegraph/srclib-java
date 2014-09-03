package com.sourcegraph.javagraph;

import java.util.ArrayList;

import com.google.gson.Gson;

public class DepresolveCommand {
	static class Resolution {
		
	}
	public void Execute() {
		ArrayList<Resolution> resolutions = new ArrayList<Resolution>();
		
		Gson gson = new Gson();
		System.out.println(gson.toJson(resolutions));
	}
}
