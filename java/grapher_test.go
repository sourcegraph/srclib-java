package java

import (
	sg "sourcegraph.com/sourcegraph"
	"testing"
)

func TestConvertSymbolPath(t *testing.T) {
	tests := []struct {
		javaPath      sg.SymbolPath
		convertedPath sg.SymbolPath
	}{
		{"foo.bar", "foo/bar"},
		{"foo", "foo"},
		{"foo.bar:baz.qux", "foo/bar:baz/qux"},
		{"foo.bar:foo$baz.qux", "foo/bar:foo.baz/qux"},
	}
	for _, test := range tests {
		convertedPath := convertSymbolPath(test.javaPath)
		if test.convertedPath != convertedPath {
			t.Errorf("%q: want converted path %q, got %q", test.javaPath, test.convertedPath, convertedPath)
		}
	}
}
