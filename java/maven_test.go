package java

import (
	"github.com/sourcegraph/go-vcsurl"
	"reflect"
	"testing"
)

func TestParseMavenResolveOutput(t *testing.T) {
	output := `
The following files have been resolved:
   com.googlecode.json-simple:json-simple:jar:1.1.1:compile:/sg/.m2/repository/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar
   com.sun:tools:jar:1.8:system:/opt/java/jre/../lib/tools.jar

`
	want := []dep{
		{groupID: "com.googlecode.json-simple", artifactID: "json-simple", version: "1.1.1", scope: "compile", filename: "/sg/.m2/repository/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"},
		{groupID: "com.sun", artifactID: "tools", version: "1.8", scope: "system", filename: "/opt/java/jre/../lib/tools.jar"},
	}

	deps, err := parseMavenResolveOutput(output)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(want, deps) {
		t.Errorf("want deps %+v, got %+v", want, deps)
	}
}

func TestParsePOM(t *testing.T) {
	pomxml := []byte(`
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.foo</groupId>
    <artifactId>bar</artifactId>
    <name>foo-bar</name>
    <version>1.2.3</version>
    <scm>
        <connection>scm:git:http://github.com/alice/myrepo</connection>
    </scm>
</project>`)
	var wantPOM pom
	wantPOM.SCM.Connection = "scm:git:http://github.com/alice/myrepo"
	wantRepoInfo := &vcsurl.RepoInfo{
		CloneURL: "git://github.com/alice/myrepo.git",
		RepoHost: "github.com",
		Username: "alice",
		Name:     "myrepo",
		FullName: "alice/myrepo",
		VCS:      vcsurl.Git,
	}

	pom, err := parsePOM(pomxml)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(wantPOM, pom) {
		t.Errorf("want parsed pom %+v, got %+v", wantPOM, pom)
	}
	if got, err := pom.repoInfo(); err != nil {
		t.Errorf("error getting repo info: %s", err)
	} else if *wantRepoInfo != *got {
		t.Errorf("want repo info %+v, got %+v", wantRepoInfo, got)
	}
}
