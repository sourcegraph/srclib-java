package java

import (
	"testing"
)

func TestJARFilename(t *testing.T) {
	if want, got := "/a/b.jar", jarFilename("jar:file:/a/b.jar!/c/d.ef"); want != got {
		t.Errorf("want %q, got %q", want, got)
	}
}
