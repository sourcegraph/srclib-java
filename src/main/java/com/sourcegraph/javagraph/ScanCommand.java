package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;

public class ScanCommand {
	@Parameter(names = { "--repo" }, description = "The URI of the repository that contains the directory tree being scanned")
	String repoURI;
	
	@Parameter(names = { "--subdir" }, description = "The path of the current directory (in which the scanner is run), relative to the root directory of the repository being scanned (this is typically the root, \".\", as it is most useful to scan the entire repository)")
	String subdir;
	
	public void Execute() {
		
	}
}
