package java_def

import (
	"encoding/json"
	"testing"

	"sourcegraph.com/sourcegraph/srclib/graph"
)

func TestName(t *testing.T) {

	tests := map[string]struct {
		def      graph.Def
		data     DefData
		expected map[graph.Qualification]string
	}{
		"package": {
			def: graph.Def{
				DefKey: graph.DefKey{
					Repo:     "https://foo.bar/baz",
					CommitID: "abc",
					UnitType: "JavaPackage",
					Unit:     ".",
					Path:     "baz/Qux",
				},
				Name: "mypkg",
				Kind: "package",
			},
			data: DefData{
				JavaKind: "PACKAGE",
				Package:  "mypkg",
			},
			expected: map[graph.Qualification]string{
				graph.Unqualified:             "mypkg",
				graph.ScopeQualified:          "mypkg",
				graph.DepQualified:            "mypkg",
				graph.RepositoryWideQualified: "mypkg",
				graph.LanguageWideQualified:   "https://foo.bar/baz/mypkg",
			},
		},
		"type": {
			def: graph.Def{
				DefKey: graph.DefKey{
					Repo:     "https://foo.bar/baz",
					CommitID: "abc",
					UnitType: "JavaPackage",
					Unit:     ".",
					Path:     "de/goddchen/android/gradle/helloworld/BuildConfig:type",
				},
				Name: "BuildConfig",
			},
			data: DefData{
				JavaKind:       "CLASS",
				TypeExpression: "de.goddchen.android.gradle.helloworld.BuildConfig",
				Package:        "de.goddchen.android.gradle.helloworld",
			},
			expected: map[graph.Qualification]string{
				graph.Unqualified:             "BuildConfig",
				graph.ScopeQualified:          "BuildConfig",
				graph.DepQualified:            "de.goddchen.android.gradle.helloworld.BuildConfig",
				graph.RepositoryWideQualified: "de.goddchen.android.gradle.helloworld.BuildConfig",
				graph.LanguageWideQualified:   "https://foo.bar/baz/de.goddchen.android.gradle.helloworld.BuildConfig",
			},
		},
		"var": {
			def: graph.Def{
				DefKey: graph.DefKey{
					Repo:     "https://foo.bar/baz",
					CommitID: "abc",
					UnitType: "JavaPackage",
					Unit:     ".",
					Path:     "de/goddchen/android/gradle/helloworld/BuildConfig:type/APPLICATION_ID",
				},
				Name: "APPLICATION_ID",
			},
			data: DefData{
				JavaKind:       "CLASS",
				TypeExpression: "java.lang.String",
				Package:        "de.goddchen.android.gradle.helloworld",
			},
			expected: map[graph.Qualification]string{
				graph.Unqualified:             "APPLICATION_ID",
				graph.ScopeQualified:          "BuildConfig.APPLICATION_ID",
				graph.DepQualified:            "de.goddchen.android.gradle.helloworld.BuildConfig.APPLICATION_ID",
				graph.RepositoryWideQualified: "de.goddchen.android.gradle.helloworld.BuildConfig.APPLICATION_ID",
				graph.LanguageWideQualified:   "https://foo.bar/baz/de.goddchen.android.gradle.helloworld.BuildConfig.APPLICATION_ID",
			},
		},
		"method": {
			def: graph.Def{
				DefKey: graph.DefKey{
					Repo:     "https://foo.bar/baz",
					CommitID: "abc",
					UnitType: "JavaPackage",
					Unit:     ".",
					Path:     "de/goddchen/android/gradle/helloworld/MainActivity:type/onCreate:android.os.Bundle",
				},
				Name: "onCreate",
			},
			data: DefData{
				JavaKind:       "METHOD",
				TypeExpression: "(android.os.Bundle)void",
				Package:        "de.goddchen.android.gradle.helloworld",
			},
			expected: map[graph.Qualification]string{
				graph.Unqualified:             "onCreate",
				graph.ScopeQualified:          "MainActivity.onCreate",
				graph.DepQualified:            "de.goddchen.android.gradle.helloworld.MainActivity.onCreate",
				graph.RepositoryWideQualified: "de.goddchen.android.gradle.helloworld.MainActivity.onCreate",
				graph.LanguageWideQualified:   "https://foo.bar/baz/de.goddchen.android.gradle.helloworld.MainActivity.onCreate",
			},
		},
		"inner-enum": {
			def: graph.Def{
				DefKey: graph.DefKey{
					Repo:     "https://foo.bar/baz",
					CommitID: "abc",
					UnitType: "JavaPackage",
					Unit:     ".",
					Path:     "foo/bar/baz/Qux:type/Norf:type",
				},
				Name: "Norf",
			},
			data: DefData{
				JavaKind:       "ENUM",
				TypeExpression: "foo.bar.baz.Qux.Norf",
				Package:        "foo.bar.baz",
			},
			expected: map[graph.Qualification]string{
				graph.Unqualified:             "Norf",
				graph.ScopeQualified:          "Qux.Norf",
				graph.DepQualified:            "foo.bar.baz.Qux.Norf",
				graph.RepositoryWideQualified: "foo.bar.baz.Qux.Norf",
				graph.LanguageWideQualified:   "https://foo.bar/baz/foo.bar.baz.Qux.Norf",
			},
		},
		"inner-enum-constant": {
			def: graph.Def{
				DefKey: graph.DefKey{
					Repo:     "https://foo.bar/baz",
					CommitID: "abc",
					UnitType: "JavaPackage",
					Unit:     ".",
					Path:     "foo/bar/baz/Qux:type/Norf:type/A",
				},
				Name: "A",
			},
			data: DefData{
				JavaKind:       "ENUM_CONSTANT",
				TypeExpression: "foo.bar.baz.Qux.Norf",
				Package:        "foo.bar.baz",
			},
			expected: map[graph.Qualification]string{
				graph.Unqualified:             "A",
				graph.ScopeQualified:          "Norf.A",
				graph.DepQualified:            "foo.bar.baz.Qux.Norf.A",
				graph.RepositoryWideQualified: "foo.bar.baz.Qux.Norf.A",
				graph.LanguageWideQualified:   "https://foo.bar/baz/foo.bar.baz.Qux.Norf.A",
			},
		},
	}

	for label, test := range tests {
		test.def.Data, _ = json.Marshal(test.data)
		formatter := newDefFormatter(&test.def)
		for q, value := range test.expected {
			actual := formatter.Name(q)
			if actual != value {
				t.Errorf("%s [%v]: expected %s but got %s", label, q, value, actual)
			}
		}
	}
}
