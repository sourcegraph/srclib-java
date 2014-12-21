package com.sourcegraph.javagraph;

import com.sourcegraph.javagraph.DepresolveCommand.Resolution;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

public class Symbol {
    Symbol.Key key;
    String kind;
    String name;

    String file;
    int identStart;
    int identEnd;
    int defStart;
    int defEnd;

    List<String> modifiers;

    String pkg;

    String doc;

    String typeExpr;

    public static class Key {
        private URI origin;

        private String path;

        public Key(URI origin, String path) {
            this.origin = origin;
            this.path = path;
        }

        @Override
        public String toString() {
            return "SymbolKey{origin: " + getOrigin() + " path:" + getPath() + "}";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((getOrigin() == null) ? 0 : getOrigin().hashCode());
            result = prime * result + ((getPath() == null) ? 0 : getPath().hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Key other = (Key) obj;
            if (getOrigin() == null) {
                if (other.getOrigin() != null)
                    return false;
            } else if (!getOrigin().equals(other.getOrigin()))
                return false;
            if (getPath() == null) {
                if (other.getPath() != null)
                    return false;
            } else if (!getPath().equals(other.getPath()))
                return false;
            return true;
        }

        public String formatPath() {
            return getPath().replace('.', '/').replace('$', '.');
        }

        public String formatTreePath() {
            return formatPath();
        }

        /**
         * @return true if this def's origin is from a dependency (e.g., a JAR) and false if it's unresolved or defined in the current source unit
         */
        public boolean hasRemoteOrigin() {
            return origin != null && origin.getScheme().equals("jar");
        }

        /**
         * @return the origin JAR file as a Path if its URI is a "jar:file:" or "file:" URI. For "jar:file:" URIs, the path inside the JAR after the "!" is stripped.
         */
        public Path getOriginJARFilePath() throws URISyntaxException {
            if (origin == null) return null;
            if (origin.getScheme().equals("jar")) {
                URI fileURI = new URI(origin.getSchemeSpecificPart());
                if (!fileURI.getScheme().equals("file")) {
                    throw new URISyntaxException(origin.toString(), "def origin URI must be a jar:file: URI, not jar:" + fileURI.getScheme());
                }

                // Split on the "!" (in, e.g., "jar:file:/path/to/my.jar!/path/to/class/file.class").
                String path = fileURI.getPath();
                int i = path.indexOf('!');
                if (i != -1) {
                    path = path.substring(0, i);
                }
                return FileSystems.getDefault().getPath(path);
            }
            throw new URISyntaxException(origin.toString(), "def origin URI must be a jar:file: URI");
        }

        /**
         * Attempt to resolve the symbol's origin to a remote definition.
         *
         * @param unit The RawDependency List of the current Source Unit
         * @return The resolved dependency, null if it could not be resolved.
         */
        public Resolution resolveOrigin(SourceUnit unit) {
//            if (getOrigin() != null) {
//                System.err.println("Origin getPath=" + getOrigin().getPath() + " getHost=" + getOrigin().getHost() + " getScheme=" + getOrigin().getScheme() + " getAuthority=" + getOrigin().getAuthority() + " toString=" + getOrigin().toString() + " getSSP=" + getOrigin().getSchemeSpecificPart());
//            }
            if (getOrigin() == null) {
                // Empty origin can't be resolved; usually indicates that the origin is in the current source unit.
                return null;
            }

            Path jarFile;
            try {
                jarFile = getOriginJARFilePath();
            } catch (URISyntaxException e) {
                System.err.println("Error getting origin file path for origin: " + origin.toString() + "; exception was " + e.toString());
                return null;
            }

            if (jarFile.toString().contains("jre/lib/")) {
                return Resolution.StdLib(); // JRE standard library
            }

            // TODO: Resolve nashorn.jar to
            // http://hg.openjdk.java.net/jdk8/jdk8/nashorn
            // TODO: Resolve tools.jar to
            // http://hg.openjdk.java.net/jdk8/jdk8/langtools

            RawDependency dep = null;
            try {
                dep = unit.resolveJARToPOMDependency(jarFile);
            } catch (Exception e) {
                System.err.println("Exception while resolving JAR " + jarFile + " to POM dependency: " + e.toString());
            }
            if (dep == null) {
                return null;
            }
            try {
                return dep.Resolve();
            } catch (Exception e) {
                System.err.println("Exception while resolving dep in JAR " + jarFile + " to repo URI: " + e.toString());
            }

            System.err.println("Couldn't resolve origin " + getOrigin() + " because no known raw dependencies had that JAR file");
            return null;
        }

        /**
         * Origin is the JAR or class file that this def is defined in. It is null for defs defined in the current source unit.
         */
        public URI getOrigin() {
            return origin;
        }

        public String getPath() {
            return path;
        }
    }

}
