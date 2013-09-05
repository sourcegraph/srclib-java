package java

import (
	"encoding/json"
	"fmt"
	"github.com/sourcegraph/go-vcsurl"
	"github.com/sourcegraph/srcscan"
	"os"
	"os/exec"
	"path/filepath"
	sg "sourcegraph.com/sourcegraph"
	"sourcegraph.com/sourcegraph/grapher"
	"sourcegraph.com/sourcegraph/repos"
	"strings"
	"sync"
)

var javagraphJAR = filepath.Join(os.Getenv("SG_HOME"), "grapher/java/target/javagraph-0.0.1-SNAPSHOT.jar")
var jdkToolsJAR = filepath.Join(os.Getenv("JAVA8_HOME"), "lib/tools.jar")
var java8Bin = filepath.Join(os.Getenv("JAVA8_HOME"), "bin/java")

type rootGrapher struct {
	ctx grapher.Context
}

func New(ctx grapher.Context) grapher.Grapher {
	ctx = ctx.WithLang(sg.Lang_Java)
	g := &rootGrapher{ctx}
	return g
}

func (g *rootGrapher) ID() string {
	return "java"
}

func (g *rootGrapher) projectGrapher(unit srcscan.Unit) *projectGrapher {
	return &projectGrapher{rootGrapher: *g, unit: unit.(*srcscan.JavaProject)}
}

var checkPrereqsOnce sync.Once

func checkPrereqs() {
	checkPrereqsOnce.Do(func() {
		// TODO(sqs): check for jdk8
		// TODO(sqs): check for mvn
	})
}

type projectGrapher struct {
	rootGrapher
	unit *srcscan.JavaProject
}

func (g *rootGrapher) Dep(unit srcscan.Unit) (err error) {
	pg := g.projectGrapher(unit)
	if !pg.useMaven() {
		return
	}

	var deps []dep
	deps, err = pg.mvnDepResolve()
	if err != nil {
		return
	}
	for _, dep := range deps {
		var info *vcsurl.RepoInfo
		info, err = pg.repoInfoForDep(dep)
		if err != nil {
			pg.ctx.Log.Printf("warning: %s", err)
			err = nil
			continue
		}
		g.ctx.Dep(info.CloneURL)
	}

	return
}

func (pg *projectGrapher) repoInfoForDep(d dep) (info *vcsurl.RepoInfo, err error) {
	var pom pom
	pom, err = readPOMForMavenJAR(d.filename)
	if err != nil {
		return nil, fmt.Errorf("failed to read POM for dep %+v: %s", d, err)
	}

	info, err = pom.repoInfo()
	if err != nil {
		return nil, fmt.Errorf("failed to get VCS repo info for dep %+v: %s", d, err)
	}
	return
}

func (g *rootGrapher) Build(unit srcscan.Unit) (err error) {
	pg := g.projectGrapher(unit)
	if !pg.useMaven() {
		return
	}

	err = pg.mvn("compile")
	if err != nil {
		return
	}
	return
}

func (g *rootGrapher) Analyze(unit srcscan.Unit) (err error) {
	pg := g.projectGrapher(unit)

	var depClasspath string
	var deps []dep
	depRepoCache := make(map[dep]repos.Id)
	if pg.useMaven() {
		depClasspath, err = pg.mvnDepClasspath()
		if err != nil {
			return
		}

		// Re-resolve so that we can map from JVM origin to repo. We also perform
		// this step in Dep, but the grapher state does not persist.
		deps, err = pg.mvnDepResolve()
		if err != nil {
			return
		}
		for _, dep := range deps {
			var info *vcsurl.RepoInfo
			info, err = pg.repoInfoForDep(dep)
			if err != nil {
				continue
			}
			depRepoCache[dep] = repos.MakeId(info.CloneURL)
		}
	}

	fullClasspath := pg.unit.ProjectClasspath + ":" + depClasspath
	args := []string{"-cp", jdkToolsJAR + ":" + javagraphJAR, "com.sourcegraph.javagraph.Main", fullClasspath, ""}
	var out []byte
	args = append(args, pg.unit.SrcFiles...)
	cmd := exec.Command(java8Bin, args...)
	pg.ctx.Log.Printf("Running: java %v", args)
	cmd.Stderr = pg.ctx.Out
	cmd.Dir = pg.absdir()
	cmd.Env = []string{"JAVA_HOME=" + os.Getenv("JAVA8_HOME")}
	out, err = cmd.Output()
	if err != nil {
		return
	}

	var data struct {
		Symbols []*Symbol `json:"symbols"`
		Refs    []*Ref    `json:"refs"`
	}
	err = json.Unmarshal(out, &data)
	if err != nil {
		return
	}

	for _, s := range data.Symbols {
		sym, warn := s.asSymbol()
		if warn != nil {
			pg.ctx.Log.Println(warn)
			continue
		}
		sym.File = filepath.Join(pg.absdir(), s.File)
		pg.ctx.Symbol(sym)

		if s.Doc != "" {
			pg.ctx.Doc(&sg.Doc{
				SymbolKey: sym.SymbolKey,
				Body:      s.Doc,
			})
		}
	}
	for _, r := range data.Refs {
		ref := r.asRef()
		if r.SymbolOrigin != "" {
			// empty origin means it's defined in this project
			ref.SymbolRepo, err = pg.repoForSymbolOrigin(r.SymbolOrigin, deps, depRepoCache)
			if err != nil {
				pg.ctx.Log.Printf("warning: %s", err)
				err = nil
				continue
			}
		}
		ref.File = filepath.Join(pg.absdir(), r.File)
		pg.ctx.Ref(ref)
	}

	return
}

func (pg *projectGrapher) absdir() string {
	return filepath.Join(pg.ctx.Dir, pg.unit.Dir)
}

func convertSymbolPath(javaPath sg.SymbolPath) sg.SymbolPath {
	return sg.SymbolPath(strings.Replace(strings.Replace(string(javaPath), ".", "/", -1), "$", ".", -1))
}

type Symbol struct {
	sg.SymbolKey
	Kind sg.SymbolKind `json:"kind"`
	Name string        `json:"name"`

	File       string `json:"file"`
	IdentStart int    `json:"identStart"`
	IdentEnd   int    `json:"identEnd"`
	DefStart   int    `json:"defStart"`
	DefEnd     int    `json:"defEnd"`

	Modifiers []string      `json:"modifiers"`
	Pkg       sg.SymbolPath `json:"pkg"`
	Doc       string        `json:"doc"`
	TypeExpr  string        `json:"typeExpr"`
}

func (s *Symbol) asSymbol() (sym *sg.Symbol, warn error) {
	sym = new(sg.Symbol)
	s.SymbolKey.Path = convertSymbolPath(s.SymbolKey.Path)
	sym = &sg.Symbol{
		SymbolKey:  s.SymbolKey,
		Name:       s.Name,
		Kind:       s.Kind,
		File:       s.File,
		IdentStart: s.IdentStart,
		IdentEnd:   s.IdentEnd,
		DefStart:   s.DefStart,
		DefEnd:     s.DefEnd,
		Pkg:        convertSymbolPath(sg.SymbolPath(s.Pkg)),
	}
	switch s.Kind {
	case "PARAMETER", "FIELD", "ENUM_CONSTANT", "LOCAL_VARIABLE", "EXCEPTION_PARAMETER", "RESOURCE_VARIABLE":
		sym.Kind = sg.Var
		sym.VarData = &sg.VarData{TypeExpr: s.TypeExpr}
	case "METHOD", "CONSTRUCTOR":
		sym.Kind = sg.Func
		sym.FuncData = &sg.FuncData{Signature: s.TypeExpr}
	case "ENUM", "CLASS", "INTERFACE", "ANNOTATION_TYPE":
		sym.Kind = sg.Type
		sym.TypeData = &sg.TypeData{Definition: s.TypeExpr}
	case "PACKAGE":
		sym.Kind = sg.Package
		sym.PackageData = &sg.PackageData{Path: string(s.Pkg)}
	default:
		warn = fmt.Errorf("skipping unknown Java symbol kind: %q", s.Kind)
		return
	}
	for _, mod := range s.Modifiers {
		if mod == "public" {
			sym.Exported = true
		}
	}
	return
}

type Ref struct {
	SymbolOrigin string        `json:"symbolOrigin"`
	SymbolPath   sg.SymbolPath `json:"symbolPath"`
	sg.Location
	Kind sg.RefKind `json:"kind"`
}

func (r *Ref) asRef() (ref *sg.Ref) {
	ref = new(sg.Ref)
	ref.SymbolPath = convertSymbolPath(r.SymbolPath)
	ref.Kind = sg.Ident
	ref.Location = r.Location
	return
}
