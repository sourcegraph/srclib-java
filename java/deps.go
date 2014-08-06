package java

// dep represents a resolved Maven dependency.
type dep struct {
	groupID    string
	artifactID string
	version    string
	scope      string
	filename   string
}
