package java

import (
	"bytes"
	"encoding/xml"
	"fmt"
	"github.com/sourcegraph/go-vcsurl"
	"io"
	"io/ioutil"
	"os/exec"
	"sourcegraph.com/sourcegraph/util"
	"strings"
)

func (pg *projectGrapher) useMaven() bool {
	return !strings.Contains(pg.ctx.Dir, string(jdkStdlibRepo))
}

func (pg *projectGrapher) mvn(args ...string) (err error) {
	cmd := exec.Command("mvn", args...)
	cmd.Stdout = pg.ctx.Out
	cmd.Stderr = pg.ctx.Out
	cmd.Dir = pg.absdir()
	return cmd.Run()
}

func (pg *projectGrapher) mvnDepResolve() (deps []dep, err error) {
	cmd := exec.Command("mvn", "dependency:resolve", "-DoutputAbsoluteArtifactFilename=true", "-DoutputFile=/dev/stderr")
	cmd.Stdout = pg.ctx.Out
	var buf bytes.Buffer
	cmd.Stderr = io.MultiWriter(&buf, pg.ctx.Out)
	cmd.Dir = pg.absdir()
	err = cmd.Run()
	if err != nil {
		return
	}
	deps, err = parseMavenResolveOutput(buf.String())
	return
}

func (pg *projectGrapher) mvnDepClasspath() (cp string, err error) {
	cmd := exec.Command("mvn", "dependency:build-classpath", "-Dmdep.outputFile=/dev/stderr")
	cmd.Stdout = pg.ctx.Out
	var buf bytes.Buffer
	cmd.Stderr = io.MultiWriter(&buf, pg.ctx.Out)
	cmd.Dir = pg.absdir()
	err = cmd.Run()
	if err != nil {
		return
	}
	return buf.String(), nil
}

// parseMavenResolveOutput parses the output of `mvn dependency:resolve -DoutputAbsoluteArtifactFilename=true`.
func parseMavenResolveOutput(output string) (deps []dep, err error) {
	lines := strings.Split(output, "\n")
	for _, line := range lines {
		if !strings.HasPrefix(line, "   ") {
			continue
		}
		line = strings.TrimSpace(line)
		parts := strings.Split(line, ":")
		if len(parts) < 6 {
			continue
		}
		deps = append(deps, dep{
			groupID:    parts[0],
			artifactID: parts[1],
			version:    parts[3],
			scope:      parts[4],
			filename:   parts[5],
		})
	}
	return
}

// pom represents a subset of the information in a pom.xml file.
type pom struct {
	SCM struct {
		Connection string `xml:"connection"`
	} `xml:"scm"`
}

func (p *pom) repoInfo() (info *vcsurl.RepoInfo, err error) {
	if p.SCM.Connection == "" {
		err = fmt.Errorf("POM file has no SCM URL")
		return
	}
	parts := strings.SplitN(p.SCM.Connection, ":", 3)
	switch len(parts) {
	case 3:
		info, err = vcsurl.Parse(parts[2])
		if info != nil {
			info.VCS = vcsurl.VCS(parts[1])
		}
	case 1:
		info, err = vcsurl.Parse(parts[0])
	default:
		err = fmt.Errorf("POM SCM URL is invalid: %q", p.SCM.Connection)
		return
	}
	return
}

func readPOMForMavenJAR(jarfile string) (pom pom, err error) {
	if strings.HasSuffix(jarfile, ".jar") {
		pomfile := jarfile[:len(jarfile)-4] + ".pom"
		if util.IsFile(pomfile) {
			var pomxml []byte
			pomxml, err = ioutil.ReadFile(pomfile)
			if err != nil {
				return
			}
			return parsePOM(pomxml)
		} else {
			err = fmt.Errorf("no POM file at %s", pomfile)
			return
		}
	}
	err = fmt.Errorf("no JAR file at %s", jarfile)
	return
}

func parsePOM(pomxml []byte) (pom pom, err error) {
	err = xml.Unmarshal(pomxml, &pom)
	return
}
