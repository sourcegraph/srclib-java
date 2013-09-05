package java

import (
	"fmt"
	"os"
	"path/filepath"
	"sourcegraph.com/sourcegraph/repos"
	"strings"
)

var jdkStdlibRepo = repos.Id("hg.openjdk.java.net/jdk8/jdk8/jdk")

var originURIJARFilePrefix = "jar:file:"

var javaStdlibJARPath = originURIJARFilePrefix + filepath.Join(os.Getenv("JAVA8_HOME"))

func (pg *projectGrapher) repoForSymbolOrigin(origin string, allDeps []dep, cache map[dep]repos.Id) (repo repos.Id, err error) {
	// An empty origin, or a non-JAR origin, means that the symbol is probably
	// defined in this unit.
	if origin == "" {
		return
	}

	if !strings.HasPrefix(origin, originURIJARFilePrefix) {
		return
	}

	if strings.HasPrefix(origin, javaStdlibJARPath) {
		return jdkStdlibRepo, nil
	}

	filename := jarFilename(origin)
	for _, dep := range allDeps {
		if dep.filename == filename {
			var present bool
			repo, present = cache[dep]
			if present {
				return repo, nil
			}
		}
	}

	return "", fmt.Errorf("failed to find repo for symbol origin %q", origin)
}

// jarFilename returns the path to the JAR file from an origin URI of the form "jar:file:/path/to/file.jar!file/within/jar.class"
func jarFilename(originURI string) string {
	filename := originURI[len(originURIJARFilePrefix):]
	filename = filepath.Clean(filename)
	if i := strings.Index(filename, "!"); i != -1 {
		filename = filename[:i]
	}
	return filename
}
